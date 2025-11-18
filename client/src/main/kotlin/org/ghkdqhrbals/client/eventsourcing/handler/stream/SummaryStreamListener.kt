package org.ghkdqhrbals.client.eventsourcing.handler.stream

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.*
import org.ghkdqhrbals.client.ai.LlmClient
import org.ghkdqhrbals.client.config.Jackson
import org.ghkdqhrbals.client.config.logger
import org.ghkdqhrbals.client.config.LogTitle
import org.ghkdqhrbals.client.config.title
import org.ghkdqhrbals.client.eventsourcing.domain.SummaryEvent
import org.ghkdqhrbals.client.paper.repository.PaperRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.connection.stream.Consumer
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.connection.stream.ReadOffset
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.stream.StreamListener
import org.springframework.data.redis.stream.StreamMessageListenerContainer
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDate

/**
 * SummaryEvent 리스너
 * - LLM으로 논문 요약 생성
 * - DB 업데이트
 * - Redis 진행상태 업데이트
 */
@Component
class SummaryStreamListener(
    private val redisTemplate: StringRedisTemplate,
    private val llmClient: LlmClient,
    private val paperRepository: PaperRepository,
    @Value("\${redis.stream.events.summary:domain:events:summary}") private val streamKey: String,
    @Value("\${redis.stream.events.group:event-handlers}") private val groupName: String,
    @Value("\${redis.stream.summary.consumer-count:20}") private val consumerCount: Int,
    @Value("\${redis.stream.summary.poll-timeout-ms:3000}") private val pollTimeoutMs: Long,
    @Value("\${redis.stream.summary.consumer-name-prefix:summary-consumer}") private val consumerNamePrefix: String,
) : StreamListener<String, MapRecord<String, String, String>> {

    private val mapper: ObjectMapper = Jackson.getMapper()
    private lateinit var container: StreamMessageListenerContainer<String, MapRecord<String, String, String>>

    @PostConstruct
    fun start() {
        initializeConsumerGroup()

        container = StreamMessageListenerContainer.create(
            redisTemplate.connectionFactory!!,
            StreamMessageListenerContainer.StreamMessageListenerContainerOptions.builder()
                .pollTimeout(Duration.ofMillis(pollTimeoutMs))
                .build()
        )

        // consumerCount 개수만큼 여러 컨슈머 등록
        repeat(consumerCount) { index ->
            val consumerName = "$consumerNamePrefix-$index"
            container.receive(
                Consumer.from(groupName, consumerName),
                StreamOffset.create(streamKey, ReadOffset.lastConsumed()),
                this
            )
            logger().title(LogTitle.STREAM, "Registered consumer name=$consumerName for stream=$streamKey")
        }

        container.start()
        logger().title(LogTitle.STREAM, "Started listening on stream=$streamKey, consumerCount=$consumerCount, pollTimeoutMs=$pollTimeoutMs")
    }

    private fun initializeConsumerGroup() {
        runCatching {
            if (!redisTemplate.hasKey(streamKey)) {
                redisTemplate.opsForStream<String, String>()
                    .add(streamKey, mapOf("init" to "bootstrap"))
                logger().title(LogTitle.STREAM, "Created stream: $streamKey")
            }
            redisTemplate.opsForStream<String, String>()
                .createGroup(streamKey, ReadOffset.latest(), groupName)
            logger().title(LogTitle.STREAM, "Created consumer group: $groupName")
        }.onFailure {
            logger().debug("[SummaryListener] Consumer group already exists: $groupName")
        }
    }

    override fun onMessage(message: MapRecord<String, String, String>) {
        // receive ~ 처리 ~ ACK 를 하나의 동기 흐름으로 처리
        runBlocking {
            handleSummaryEvent(message)
        }
    }

    private suspend fun handleSummaryEvent(message: MapRecord<String, String, String>) {
        logger().title(LogTitle.SUMMARY, "Received message: id=${message.id}")

        try {
            val payload = message.value["payload"] ?: return
            val event: SummaryEvent = mapper.readValue(payload)

            logger().title(LogTitle.SUMMARY,"Processing summary for paperId=${event.paperId}, searchEventId=${event.searchEventId}")

            val startTime = System.currentTimeMillis()

            // LLM 호출 (IO 작업)
            val analysis = withContext(Dispatchers.IO) {
                llmClient.summarizePaper(
                    event.abstract ?: "",
                    event.maxLength,
                    event.journalRefRaw
                )
            }

            val duration = System.currentTimeMillis() - startTime
            logger().title(LogTitle.SUMMARY, "LLM completed in ${duration}ms for paperId=${event.paperId}")

            // DB 업데이트
            event.arxivId?.let { arxivId ->
                val paper = withContext(Dispatchers.IO) {
                    paperRepository.findByArxivId(arxivId).orElse(null)
                }

                if (paper != null) {
                    val updated = paper.copy(
                        summary = analysis.coreContribution,
                        novelty = analysis.noveltyAgainstPreviousWorks,
                        summaryDate = LocalDate.now(),
                        journal = analysis.journalName ?: paper.journal,
                        impactFactor = analysis.impactFactor ?: paper.impactFactor
                    )
                    withContext(Dispatchers.IO) {
                        paperRepository.save(updated)
                    }
                    logger().title(LogTitle.PAPER, "Updated paper: arxivId=$arxivId")

                    // 진행상태 업데이트 (성공)
                    incrementProgress(event.searchEventId, "completed")
                } else {
                    logger().warn("[SummaryListener] Paper not found: arxivId=$arxivId")
                    incrementProgress(event.searchEventId, "failed")
                }
            }

            // 완료 체크
            checkAndMarkCompleted(event.searchEventId)

            // ACK
            redisTemplate.opsForStream<String, String>()
                .acknowledge(streamKey, groupName, message.id)

            logger().title(LogTitle.SUMMARY, "✅ Completed summary for paperId=${event.paperId}")

        } catch (e: Exception) {
            logger().error("[SummaryListener] ❌ Failed to process message: id=${message.id}", e)

            try {
                val payload = message.value["payload"]
                val event: SummaryEvent = mapper.readValue(payload!!)
                incrementProgress(event.searchEventId, "failed")
                checkAndMarkCompleted(event.searchEventId)
            } catch (parseError: Exception) {
                logger().error("[SummaryListener] Failed to parse event for error handling", parseError)
            }

            redisTemplate.opsForStream<String, String>()
                .acknowledge(streamKey, groupName, message.id)
        }
    }

    private fun incrementProgress(searchEventId: String, field: String) {
        val key = "search:$searchEventId:progress"
        redisTemplate.opsForHash<String, String>().increment(key, field, 1)
        redisTemplate.expire(key, 3600, java.util.concurrent.TimeUnit.SECONDS)
        logger().debug("[SummaryListener] Incremented $field for searchEventId=$searchEventId")
    }

    private fun checkAndMarkCompleted(searchEventId: String) {
        val key = "search:$searchEventId:progress"
        val entries = redisTemplate.opsForHash<String, String>().entries(key)

        val total = entries["total"]?.toIntOrNull() ?: 0
        val completed = entries["completed"]?.toIntOrNull() ?: 0
        val failed = entries["failed"]?.toIntOrNull() ?: 0

        // 모든 작업이 완료되었는지 확인
        if (total > 0 && (completed + failed) >= total) {
            val status = entries["status"]
            // 아직 완료로 마크되지 않았다면 완료 처리
            if (status != "COMPLETED") {
                redisTemplate.opsForHash<String, String>().put(key, "status", "COMPLETED")
                redisTemplate.expire(key, 3600, java.util.concurrent.TimeUnit.SECONDS)
                logger().title(LogTitle.SUMMARY, "🎉 All summaries completed for searchEventId=$searchEventId (total=$total, completed=$completed, failed=$failed)")
            }
        }
    }

    @PreDestroy
    fun shutdown() {
        container.stop()
        logger().title(LogTitle.STREAM, "Stopped")
    }
}
