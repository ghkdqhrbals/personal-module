package org.ghkdqhrbals.client.domain.event

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.ghkdqhrbals.client.config.Jackson
import org.ghkdqhrbals.client.config.log.logger
import org.ghkdqhrbals.client.domain.paper.service.ArxivHttpClient
import org.ghkdqhrbals.client.domain.paper.service.ArxivPaper
import org.ghkdqhrbals.client.domain.paper.service.ArxivXmlParser
import org.ghkdqhrbals.client.domain.paper.service.PaperService
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.connection.stream.Consumer
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.connection.stream.ReadOffset
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.stream.StreamListener
import org.springframework.data.redis.stream.StreamMessageListenerContainer
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder
import java.time.Duration

/**
 * PaperSearchAndStoreEvent 리스너
 * - arXiv API 호출
 * - 검색 결과 저장
 * - 필요시 SummaryEvent 발행
 * - Redis에 진행상태 업데이트
 */
@Component
class PaperSearchAndStoreStreamListener(
    private val redisTemplate: StringRedisTemplate,
    private val arxivHttpClient: ArxivHttpClient,
    private val xmlParser: ArxivXmlParser,
    private val paperService: PaperService,
    private val eventPublisher: EventPublisher,
    @Value("\${redis.stream.events.paper-search-and-store:domain:events:paper-search-and-store}") private val streamKey: String,
    @Value("\${redis.stream.events.group:event-handlers}") private val groupName: String
) : StreamListener<String, MapRecord<String, String, String>> {

    private val mapper: ObjectMapper = Jackson.getMapper()
    private lateinit var container: StreamMessageListenerContainer<String, MapRecord<String, String, String>>

    companion object {
        private const val ARXIV_API_BASE_URL = "https://export.arxiv.org/api/query"
        private const val DEFAULT_QUERY = "all:machine+all:learning"
    }

    @PostConstruct
    fun start() {
        initializeConsumerGroup()

        container = StreamMessageListenerContainer.create(
            redisTemplate.connectionFactory!!,
            StreamMessageListenerContainer.StreamMessageListenerContainerOptions.builder()
                .pollTimeout(Duration.ofMillis(100))
                .build()
        )

        container.receive(
            Consumer.from(groupName, "paper-search-and-store-consumer"),
            StreamOffset.create(streamKey, ReadOffset.lastConsumed()),
            this
        )

        container.start()
        logger().info("[PaperSearchAndStoreListener] Started listening on stream=$streamKey")
    }

    private fun initializeConsumerGroup() {
        runCatching {
            if (!redisTemplate.hasKey(streamKey)) {
                redisTemplate.opsForStream<String, String>()
                    .add(streamKey, mapOf("init" to "bootstrap"))
                logger().info("[PaperSearchAndStoreListener] Created stream: $streamKey")
            }
            redisTemplate.opsForStream<String, String>()
                .createGroup(streamKey, ReadOffset.latest(), groupName)
            logger().info("[PaperSearchAndStoreListener] Created consumer group: $groupName")
        }.onFailure {
            logger().debug("[PaperSearchAndStoreListener] Consumer group already exists: $groupName")
        }
    }

    override fun onMessage(message: MapRecord<String, String, String>) {
        logger().info("[PaperSearchAndStoreListener] Received message: id=${message.id}")

        try {
            val payload = message.value["payload"] ?: return
            val event: PaperSearchAndStoreEvent = mapper.readValue(payload)

            logger().info("[PaperSearchAndStoreListener] Processing searchEventId=${event.searchEventId}, query=${event.query}")

            // Redis 진행상태 초기화
            initializeProgress(event.searchEventId)

            // arXiv 검색 및 저장 수행
            val needToSummarize = searchAndStore(event)

            // 진행상태 업데이트
            updateProgress(event.searchEventId, "total", needToSummarize.size)

            // 요약 이벤트 발행 (optional)
            if (event.shouldSummarize && needToSummarize.isNotEmpty()) {
                needToSummarize.forEach { paper ->
                    if (paper.arxivId != null && paper.title.isNotBlank()) {
                        val summaryEvent = SummaryEvent(
                            searchEventId = event.searchEventId,
                            paperId = paper.arxivId!!,
                            arxivId = paper.arxivId,
                            title = paper.title,
                            abstract = paper.abstract,
                            journalRefRaw = paper.journalRefRaw,
                            maxLength = 120,
                            ver = 1,
                            meta = mapOf("source" to "arXiv")
                        )
                        eventPublisher.publish(summaryEvent)
                    }
                }
                logger().info("[PaperSearchAndStoreListener] Published ${needToSummarize.size} SummaryEvents")
            } else {
                // 요약 없이 즉시 완료
                updateProgress(event.searchEventId, "completed", needToSummarize.size)
                markAsCompleted(event.searchEventId)
            }

            // ACK
            redisTemplate.opsForStream<String, String>()
                .acknowledge(streamKey, groupName, message.id)

            logger().info("[PaperSearchAndStoreListener] ✅ Processed searchEventId=${event.searchEventId}, saved=${needToSummarize.size} papers")

        } catch (e: Exception) {
            logger().error("[PaperSearchAndStoreListener] ❌ Failed to process message: id=${message.id}", e)

            // 실패 상태 기록
            try {
                val payload = message.value["payload"]
                val event: PaperSearchAndStoreEvent = mapper.readValue(payload!!)
                markAsFailed(event.searchEventId, e.message ?: "Unknown error")
            } catch (parseError: Exception) {
                logger().error("[PaperSearchAndStoreListener] Failed to parse event for error handling", parseError)
            }

            redisTemplate.opsForStream<String, String>()
                .acknowledge(streamKey, groupName, message.id)
        }
    }

    fun searchAndStore(event: PaperSearchAndStoreEvent): List<ArxivPaper> {
        val response = arxivHttpClient.search(event)
        val entities = response.papers.map { it.toPaperEntity() }
        val savedEntities = paperService.upsertPapersAndGetUnsummarized(entities)
        val needToSummarize = response.papers.filter { paper ->
            savedEntities.any { it.arxivId == paper.arxivId }
        }

        logger().info("[PaperSearchAndStoreListener] Saved ${needToSummarize.size} new papers")
        return needToSummarize
    }

    private fun buildRequestUrl(
        query: String?,
        categories: List<String>?,
        maxResults: Int,
        page: Int
    ): String {
        val searchQuery = buildSearchQuery(query, categories)
        val start = maxResults * page

        return UriComponentsBuilder.fromUriString(ARXIV_API_BASE_URL)
            .queryParam("search_query", searchQuery)
            .queryParam("start", start)
            .queryParam("sortBy", "submittedDate")
            .queryParam("sortOrder", "descending")
            .queryParam("max_results", maxResults)
            .build(false)
            .toUriString()
    }

    private fun buildSearchQuery(query: String?, categories: List<String>?): String {
        val tokens = mutableListOf<String>()

        query?.trim()?.takeIf { it.isNotBlank() }?.let { q ->
            val words = q.split(Regex("\\s+"))
            tokens += if (words.size == 1) {
                "all:${words[0]}"
            } else {
                "all:${words.joinToString("+")}"
            }
        }

        categories?.filter { it.isNotBlank() }?.forEach { cat ->
            tokens += "cat:$cat"
        }

        return tokens.takeIf { it.isNotEmpty() }?.joinToString("+") ?: DEFAULT_QUERY
    }

    private fun initializeProgress(searchEventId: String) {
        val key = "search:$searchEventId:progress"
        redisTemplate.opsForHash<String, String>().putAll(key, mapOf(
            "total" to "0",
            "completed" to "0",
            "failed" to "0",
            "status" to "IN_PROGRESS"
        ))
        redisTemplate.expire(key, 3600, java.util.concurrent.TimeUnit.SECONDS) // 1시간 TTL
        logger().info("[PaperSearchAndStoreListener] Initialized progress for searchEventId=$searchEventId")
    }

    private fun updateProgress(searchEventId: String, field: String, value: Int) {
        val key = "search:$searchEventId:progress"
        redisTemplate.opsForHash<String, String>().put(key, field, value.toString())
        redisTemplate.expire(key, 3600, java.util.concurrent.TimeUnit.SECONDS)
    }

    private fun markAsCompleted(searchEventId: String) {
        val key = "search:$searchEventId:progress"
        redisTemplate.opsForHash<String, String>().put(key, "status", "COMPLETED")
        redisTemplate.expire(key, 3600, java.util.concurrent.TimeUnit.SECONDS)
        logger().info("[PaperSearchAndStoreListener] Marked as completed: searchEventId=$searchEventId")
    }

    private fun markAsFailed(searchEventId: String, error: String) {
        val key = "search:$searchEventId:progress"
        redisTemplate.opsForHash<String, String>().putAll(key, mapOf(
            "status" to "FAILED",
            "error" to error
        ))
        redisTemplate.expire(key, 3600, java.util.concurrent.TimeUnit.SECONDS)
        logger().error("[PaperSearchAndStoreListener] Marked as failed: searchEventId=$searchEventId, error=$error")
    }

    @PreDestroy
    fun shutdown() {
        container.stop()
        logger().info("[PaperSearchAndStoreListener] Stopped")
    }
}

