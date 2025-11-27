package org.ghkdqhrbals.client.domain.event.listener

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.*
import org.ghkdqhrbals.client.ai.LlmClient
import org.ghkdqhrbals.client.config.Jackson
import org.ghkdqhrbals.client.config.log.logger
import org.ghkdqhrbals.client.config.log.LogTitle
import org.ghkdqhrbals.client.config.log.title
import org.ghkdqhrbals.client.domain.event.SummaryEvent
import org.ghkdqhrbals.client.domain.paper.entity.repository.PaperRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.SmartLifecycle
import org.springframework.context.annotation.DependsOn
import org.springframework.data.redis.connection.stream.Consumer
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.connection.stream.ReadOffset
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.stream.StreamListener
import org.springframework.data.redis.stream.StreamMessageListenerContainer
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

/**
 * SummaryEvent 리스너
 * - LLM으로 논문 요약 생성
 * - DB 업데이트
 * - Redis 진행상태 업데이트
 */
@Component
@DependsOn("redisConnectionFactory")
class SummaryStreamListener(
    private val redisTemplate: StringRedisTemplate,
    private val llmClient: LlmClient,
    private val paperRepository: PaperRepository,
    @Value("\${redis.stream.events.summary:domain:events:summary}") private val streamKey: String,
    @Value("\${redis.stream.events.group:event-handlers}") private val groupName: String,
    @Value("\${redis.stream.summary.consumer-count:20}") private val consumerCount: Int,
    @Value("\${redis.stream.summary.poll-timeout-ms:3000}") private val pollTimeoutMs: Long,
    @Value("\${redis.stream.summary.consumer-name-prefix:summary-consumer}") private val consumerNamePrefix: String,
) : GracefulConsumer() {

    private val mapper: ObjectMapper = Jackson.getMapper()
    private lateinit var container: StreamMessageListenerContainer<String, MapRecord<String, String, String>>

    private lateinit var executor: ExecutorService

    @PostConstruct
    fun startup() {

    }

    override fun start() {
        initializeConsumerGroup()

        val options = StreamMessageListenerContainer.StreamMessageListenerContainerOptions.builder()
            .pollTimeout(Duration.ofMillis(pollTimeoutMs))
            .errorHandler { ex ->
                if (shuttingDown) {
                    return@errorHandler
                }

                logger().error("[StreamContainer] Unexpected error", ex)
            }
            .build()

        container = StreamMessageListenerContainer.create(
            redisTemplate.connectionFactory!!,
            options
        )

        // consumerCount 개수만큼 여러 컨슈머 등록
        repeat(consumerCount) { index ->
            val consumerName = "$consumerNamePrefix-$index"
            container.receive(
                Consumer.from(groupName, consumerName),
                StreamOffset.create(streamKey, ReadOffset.lastConsumed()),
                this
            )
        }


        container.start()
        logger().title(
            LogTitle.STREAM,
            "Started listening on stream=$streamKey, consumerCount=$consumerCount, pollTimeoutMs=$pollTimeoutMs"
        )

        if (!running) {
            running = true
        }
    }

    override fun stop() {
        logger().title(LogTitle.STREAM, "SummaryEvent 리스너 종료 중...")
        shuttingDown = true
        container.stop()
        running = false
        logger().title(LogTitle.STREAM, "SummaryEvent 리스너 종료 완료")
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

        var searchEventId: String? = null
        var paperId: String? = null

        try {
            val payload = message.value["payload"] ?: run {
                logger().warn("[SummaryListener] Empty payload in message: id=${message.id}")
                acknowledge(message)
                return
            }

            val event: SummaryEvent = try {
                mapper.readValue(payload)
            } catch (e: Exception) {
                logger().error("[SummaryListener] ❌ Failed to parse event payload: ${e.message}")
                logger().error("[SummaryListener] Payload (first 500 chars): ${payload.take(500)}")
                acknowledge(message)
                return
            }

            searchEventId = event.searchEventId
            paperId = event.paperId

            logger().title(
                LogTitle.SUMMARY,
                "Processing summary for paperId=${paperId}, searchEventId=${searchEventId}"
            )

            val startTime = System.currentTimeMillis()


            // LLM 호출 (IO 작업)
            val analysis = try {
                llmClient.summarizePaper(
                    event.abstract ?: "",
                    event.maxLength,
                    event.journalRefRaw
                )
            } catch (e: IllegalStateException) {
                logger().error("[SummaryListener] ❌ LLM processing failed for paperId=${paperId}: ${e.message}")
                incrementProgress(searchEventId, "failed")
                checkAndMarkCompleted(searchEventId)
                acknowledge(message)
                return
            } catch (e: com.fasterxml.jackson.core.JsonProcessingException) {
                logger().error("[SummaryListener] ❌ JSON parsing failed for LLM response, paperId=${paperId}: ${e.message}")
                incrementProgress(searchEventId, "failed")
                checkAndMarkCompleted(searchEventId)
                acknowledge(message)
                return
            } catch (e: Exception) {
                logger().error("[SummaryListener] ❌ Unexpected error during LLM call for paperId=${paperId}: ${e.message}")
                incrementProgress(searchEventId, "failed")
                checkAndMarkCompleted(searchEventId)
                acknowledge(message)
                return
            }

            val duration = System.currentTimeMillis() - startTime
            logger().title(LogTitle.SUMMARY, "LLM completed in ${duration}ms for paperId=${paperId}")

            // DB 업데이트 - arxivId로 Paper 찾기
            val arxivId = event.arxivId
            if (arxivId.isNullOrBlank()) {
                logger().warn("[SummaryListener] ⚠️ No arxivId in event for paperId=${paperId}, skipping DB update")
                incrementProgress(searchEventId, "failed")
                checkAndMarkCompleted(searchEventId)
                acknowledge(message)
                return
            }

            val paper = paperRepository.findByArxivId(arxivId)

            if (paper == null) {
                logger().warn("[SummaryListener] ⚠️ Paper not found for arxivId=$arxivId, skipping (may not be saved yet)")
                incrementProgress(searchEventId, "failed")
                checkAndMarkCompleted(searchEventId)
                acknowledge(message)
                return
            }

            // Paper에 요약 정보 업데이트
            val updated = paper.copy(
                summary = analysis.coreContribution,
                novelty = analysis.noveltyAgainstPreviousWorks,
                summarizedAt = java.time.OffsetDateTime.now(),
                journal = analysis.journalName ?: paper.journal,
                impactFactor = analysis.impactFactor ?: paper.impactFactor
            )

            paperRepository.save(updated)


            logger().title(LogTitle.PAPER, "✅ Updated paper summary: arxivId=$arxivId")
            logger().debug("   ├─ Core: ${analysis.coreContribution.take(50)}...")
            logger().debug("   ├─ Novelty: ${analysis.noveltyAgainstPreviousWorks.take(50)}...")
            if (analysis.journalName != null) {
                logger().debug("   └─ Journal: ${analysis.journalName} (IF: ${analysis.impactFactor})")
            }

            // 진행상태 업데이트 (성공)
            incrementProgress(searchEventId, "completed")

            // 완료 체크
            checkAndMarkCompleted(searchEventId)

            // ACK
            acknowledge(message)

            logger().title(LogTitle.SUMMARY, "✅ Completed summary for paperId=${paperId}")

        } catch (e: Exception) {
            logger().error("[SummaryListener] ❌ Unexpected error processing message: id=${message.id}: ${e.message}")

            searchEventId?.let {
                try {
                    incrementProgress(it, "failed")
                    checkAndMarkCompleted(it)
                } catch (progressError: Exception) {
                    logger().error("[SummaryListener] Failed to update progress: ${progressError.message}")
                }
            }

            acknowledge(message)
        }
    }

    private fun acknowledge(message: MapRecord<String, String, String>) {
        try {
            redisTemplate.opsForStream<String, String>()
                .acknowledge(streamKey, groupName, message.id)
        } catch (e: Exception) {
            logger().error("[SummaryListener] Failed to acknowledge message: id=${message.id}: ${e.message}")
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
                logger().title(
                    LogTitle.SUMMARY,
                    "🎉 All summaries completed for searchEventId=$searchEventId (total=$total, completed=$completed, failed=$failed)"
                )
            }
        }
    }

    @PreDestroy
    fun shutdown() {
        shuttingDown = true
        runCatching { container.stop() }
        runCatching {
            executor.shutdown()
            executor.awaitTermination(3, TimeUnit.SECONDS)
        }
        logger().title(LogTitle.STREAM, "Stopped")
    }
}
