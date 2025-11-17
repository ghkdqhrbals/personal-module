package org.ghkdqhrbals.client.paper.queue

import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.connection.stream.Consumer
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.connection.stream.ReadOffset
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.stream.StreamListener
import org.springframework.data.redis.stream.StreamMessageListenerContainer
import org.springframework.stereotype.Component
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.ghkdqhrbals.client.ai.LlmClient
import org.ghkdqhrbals.client.paper.repository.PaperRepository
import org.ghkdqhrbals.client.config.logger
import java.time.Duration
import java.time.LocalDate
import java.util.concurrent.ExecutorService
import kotlinx.coroutines.*

@Component
class SummaryQueueConsumer(
    private val redisTemplate: StringRedisTemplate,
    private val llmClient: LlmClient,
    private val paperRepository: PaperRepository,
    @Value("\${redis.stream.summary}") private val streamKey: String,
    @Value("\${redis.stream.group}") private val groupName: String,
    @Value("\${redis.stream.consumer.name}") private val consumerName: String,
    @Value("\${redis.stream.consumer.threads:3}") private val consumerThreads: Int,
    @Qualifier("summaryStreamExecutor") private val executor: ExecutorService
) : StreamListener<String, MapRecord<String, String, String>> {

    // 코루틴 스코프 추가 - executor를 dispatcher로 변환
    private val coroutineScope = CoroutineScope(executor.asCoroutineDispatcher() + SupervisorJob())

    private val mapper = jacksonObjectMapper()
    private val listenerContainers =
        mutableListOf<StreamMessageListenerContainer<String, MapRecord<String, String, String>>>()

    @PostConstruct
    fun start() {
        initializeConsumerGroup()
        initListeners()
        logger().info("[STREAM] Summary consumer started with $consumerThreads threads on stream=$streamKey")
    }

    private fun initializeConsumerGroup() {
        runCatching {
            if (!redisTemplate.hasKey(streamKey)) {
                redisTemplate.opsForStream<String, String>()
                    .add(streamKey, mapOf("init" to "bootstrap"))
                logger().info("[STREAM] Created stream: $streamKey")
            }
            redisTemplate.opsForStream<String, String>()
                .createGroup(streamKey, ReadOffset.latest(), groupName)
            logger().info("[STREAM] Created consumer group: $groupName")
        }.onFailure {
            logger().info("[STREAM] Consumer group already exists: $groupName (${it.message})")
        }
    }

    private fun initListeners() {
        repeat(consumerThreads) { idx ->
            val cid = "$consumerName-$idx"

            // 각 consumer마다 개별 Container 생성 -> 진정한 병렬 처리
            val container = StreamMessageListenerContainer.create(
                redisTemplate.connectionFactory!!,
                StreamMessageListenerContainer.StreamMessageListenerContainerOptions.builder()
                    .executor(executor)
                    .pollTimeout(Duration.ofMillis(100))  // 빠른 폴링
                    .batchSize(1)  // 한번에 1개씩 처리 (병렬성 극대화)
                    .build()
            )

            container.receive(
                Consumer.from(groupName, cid),
                StreamOffset.create(streamKey, ReadOffset.lastConsumed()),
                this
            )

            container.start()
            listenerContainers.add(container)

            logger().info("[STREAM] Started independent consumer=$cid (container #${idx + 1})")
        }

        logger().info("[STREAM] All $consumerThreads parallel consumers are now active")
    }

    override fun onMessage(message: MapRecord<String, String, String>) {
        logger().info("[STREAM] Received message id=${message.id.value} for processing")
        // StreamListener는 blocking을 기대하므로 runBlocking 사용
        runBlocking {
            processMessage(message)
        }
    }

    private suspend fun processMessage(message: MapRecord<String, String, String>) {
        val startTime = System.currentTimeMillis()
        val threadName = Thread.currentThread().name
        val payload = message.value["payload"]

        if (payload == null) {
            logger().warn("[STREAM:$threadName] Missing payload in message id=${message.id.value}")
            acknowledgeMessage(message.id.value)
            return
        }

        try {
            val job: SummaryJobRequest = mapper.readValue(payload)
            logger().info("[STREAM:$threadName] ⏱️ START job id=${message.id.value} title='${job.title?.take(60)}'")

            val llmStartTime = System.currentTimeMillis()
            val analysis = llmClient.summarizePaper(job.abstract ?: "", job.maxLength, job.journalRefRaw)
            val llmDuration = System.currentTimeMillis() - llmStartTime
            logger().info("[STREAM:$threadName] 🤖 LLM 응답 완료 - 소요 시간: ${llmDuration}ms (${llmDuration / 1000.0}초)")


            val entityOpt = job.arxivId?.let { paperRepository.findByArxivId(it) }
            val entity = entityOpt?.orElse(null)

            if (entity != null) {
                val dbStartTime = System.currentTimeMillis()
                val updated = entity.copy(
                    summary = analysis.coreContribution,
                    novelty = analysis.noveltyAgainstPreviousWorks,
                    summaryDate = LocalDate.now(),
                    journal = analysis.journalName ?: entity.journal,
                    impactFactor = analysis.impactFactor ?: entity.impactFactor
                )
                // DB 호출을 IO dispatcher에서 실행
                withContext(Dispatchers.IO) {
                    paperRepository.save(updated)
                }
                val dbDuration = System.currentTimeMillis() - dbStartTime
                logger().info("[STREAM:$threadName] 💾 DB 저장 완료 - 소요 시간: ${dbDuration}ms, summary length=${analysis.coreContribution.length}")
            }

            if (job.eventId != null) {
                redisTemplate.opsForHash<String, String>()
                    .increment("batch:${job.eventId}:progress", "completed", 1)
            }

            acknowledgeMessage(message.id.value)


            val totalDuration = System.currentTimeMillis() - startTime
            logger().info("[STREAM:$threadName] ✅ COMPLETED job id=${message.id.value} - 총 소요 시간: ${totalDuration}ms (${totalDuration / 1000.0}초)")

        } catch (e: Exception) {
            logger().error("[STREAM:$threadName] Job failed id=${message.id.value} error=${e.message}", e)


            try {
                val job: SummaryJobRequest = mapper.readValue(payload)
                if (job.eventId != null) {
                    redisTemplate.opsForHash<String, String>()
                        .increment("batch:${job.eventId}:progress", "failed", 1)
                }
            } catch (parseError: Exception) {
                logger().error("[STREAM:$threadName] Failed to parse job for error handling: ${parseError.message}")
            }

            // 실패해도 ACK (재시도 로직 필요시 DLQ로 이동)
            acknowledgeMessage(message.id.value)

        }
    }

    private fun acknowledgeMessage(messageId: String) {
        try {
            redisTemplate.opsForStream<String, String>()
                .acknowledge(streamKey, groupName, messageId)
        } catch (e: Exception) {
            logger().error("[STREAM] Failed to acknowledge message id=$messageId: ${e.message}")
        }
    }

    @PreDestroy
    fun shutdown() {
        logger().info("[STREAM] Shutting down ${listenerContainers.size} StreamMessageListenerContainers...")
        listenerContainers.forEach { it.stop() }

        // 코루틴 스코프 취소
        coroutineScope.cancel()

        executor.shutdown()
        if (!executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)) {
            executor.shutdownNow()
        }
        logger().info("[STREAM] All consumers shutdown complete")
    }
}
