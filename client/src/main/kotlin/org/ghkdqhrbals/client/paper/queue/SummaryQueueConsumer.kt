package org.ghkdqhrbals.client.paper.queue

import jakarta.annotation.PostConstruct
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.connection.stream.ReadOffset
import org.springframework.data.redis.connection.stream.Consumer
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.connection.stream.StreamReadOptions
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.StreamOperations
import org.springframework.stereotype.Component
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.ghkdqhrbals.client.ai.LlmClient
import org.ghkdqhrbals.client.paper.repository.PaperRepository
import org.ghkdqhrbals.client.config.logger
import java.time.Duration
import java.time.LocalDate

@Component
class SummaryQueueConsumer(
    private val redisTemplate: StringRedisTemplate,
    private val llmClient: LlmClient,
    private val paperRepository: PaperRepository,
    @Value("\${redis.stream.summary}") private val streamKey: String,
    @Value("\${redis.stream.group}") private val groupName: String,
    @Value("\${redis.stream.consumer}") private val consumerName: String
) {
    private val mapper = jacksonObjectMapper()
    private val scope = CoroutineScope(Dispatchers.IO)
    private val streamOps: StreamOperations<String, String, String> by lazy { redisTemplate.opsForStream() }

    @PostConstruct
    fun start() {
        // 그룹 초기화 (스트림이 없는 경우 먼저 더미 엔트리 추가하여 생성)
        runCatching {
            if (!redisTemplate.hasKey(streamKey)) {
                streamOps.add(MapRecord.create(streamKey, mapOf("init" to "bootstrap")))
            }
            streamOps.createGroup(streamKey, ReadOffset.latest(), groupName)
            logger().info("[STREAM] Created group='$groupName' on '$streamKey'")
        }.onFailure { logger().info("[STREAM] Group already exists or create failed: $groupName (${it.message})") }

        scope.launch {
            logger().info("[STREAM] Summary consumer started consumer=$consumerName group=$groupName stream=$streamKey")
            while (true) {
                try {
                    val messages: List<MapRecord<String, String, String>> = streamOps.read(
                        Consumer.from(groupName, consumerName),
                        StreamReadOptions.empty().count(5).block(Duration.ofMillis(2000)),
                        StreamOffset.create(streamKey, ReadOffset.lastConsumed())
                    ) ?: emptyList()

                    if (messages.isEmpty()) {
                        continue
                    }

                    for (msg in messages) {
                        val payload = msg.value["payload"] ?: continue
                        val job: SummaryJobRequest = mapper.readValue(payload)
                        logger().info("[STREAM] Processing summary job id=${msg.id.value} title='${job.title?.take(60)}'")
                        try {
                            val analysis = llmClient.summarizePaper(job.abstract ?: "", job.maxLength, job.journalRefRaw)
                            val entityOpt = job.arxivId?.let { paperRepository.findByArxivId(it) }
                            val entity = entityOpt?.orElse(null)
                            if (entity != null) {
                                val updated = entity.copy(
                                    summary = analysis.coreContribution,
                                    novelty = analysis.noveltyAgainstPreviousWorks,
                                    summaryDate = LocalDate.now(),
                                    journal = analysis.journalName ?: entity.journal,
                                    impactFactor = analysis.impactFactor ?: entity.impactFactor
                                )
                                paperRepository.save(updated)
                                logger().info("[STREAM] Updated PaperEntity id=${entity.id} summary length=${analysis.coreContribution.length}")
                            }
                            if (job.eventId != null) {
                                // 완료 카운트 증가
                                redisTemplate.opsForHash<String, String>().increment("batch:${job.eventId}:progress", "completed", 1)
                            }
                            streamOps.acknowledge(streamKey, groupName, msg.id)
                        } catch (e: Exception) {
                            logger().error("[STREAM] Summary job failed id=${msg.id.value} error=${e.message}")
                            if (job.eventId != null) {
                                redisTemplate.opsForHash<String, String>().increment("batch:${job.eventId}:progress", "failed", 1)
                            }
                        }
                    }
                } catch (e: Exception) {
                    logger().error("[STREAM] Consumer loop error: ${e.message}")
                    delay(1000)
                }
            }
        }
    }
}
