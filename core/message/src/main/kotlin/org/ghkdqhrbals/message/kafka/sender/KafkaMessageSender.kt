package org.ghkdqhrbals.message.kafka.sender

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture

/**
 * 범용 Kafka 메시지 전송 컴포넌트
 */
@Component
class KafkaMessageSender(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(KafkaMessageSender::class.java)

    /**
     * 객체를 JSON으로 변환하여 전송
     */
    fun <T> sendObject(topic: String, key: String, obj: T): CompletableFuture<SendResult> {
        val message = objectMapper.writeValueAsString(obj)
        return send(topic, key, message)
    }

    /**
     * Map을 JSON으로 변환하여 전송
     */
    fun sendMap(topic: String, key: String, data: Map<String, Any>): CompletableFuture<SendResult> {
        val message = objectMapper.writeValueAsString(data)
        return send(topic, key, message)
    }

    /**
     * 문자열 메시지 전송
     */
    fun send(topic: String, key: String, message: String): CompletableFuture<SendResult> {
        val future = CompletableFuture<SendResult>()

        kafkaTemplate.send(topic, key, message)
            .thenAccept { result ->
                val sendResult = SendResult(
                    success = true,
                    topic = topic,
                    partition = result.recordMetadata.partition(),
                    offset = result.recordMetadata.offset(),
                    timestamp = result.recordMetadata.timestamp()
                )
                log.debug("Message sent: topic={}, partition={}, offset={}",
                    topic, sendResult.partition, sendResult.offset)
                future.complete(sendResult)
            }
            .exceptionally { ex ->
                val sendResult = SendResult(
                    success = false,
                    topic = topic,
                    errorMessage = ex.message
                )
                log.error("Failed to send message: topic={}, key={}, error={}",
                    topic, key, ex.message)
                future.complete(sendResult)
                null
            }

        return future
    }

    /**
     * 키 없이 전송
     */
    fun send(topic: String, message: String): CompletableFuture<SendResult> {
        val future = CompletableFuture<SendResult>()

        kafkaTemplate.send(topic, message)
            .thenAccept { result ->
                val sendResult = SendResult(
                    success = true,
                    topic = topic,
                    partition = result.recordMetadata.partition(),
                    offset = result.recordMetadata.offset(),
                    timestamp = result.recordMetadata.timestamp()
                )
                future.complete(sendResult)
            }
            .exceptionally { ex ->
                val sendResult = SendResult(
                    success = false,
                    topic = topic,
                    errorMessage = ex.message
                )
                future.complete(sendResult)
                null
            }

        return future
    }

    /**
     * 동기 전송 (블로킹)
     */
    fun sendSync(topic: String, key: String, message: String, timeoutMs: Long = 10000): SendResult {
        return send(topic, key, message).get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
    }

    /**
     * 배치 전송
     */
    fun sendBatch(topic: String, messages: List<Pair<String, String>>): List<CompletableFuture<SendResult>> {
        return messages.map { (key, message) -> send(topic, key, message) }
    }

    /**
     * 배치 전송 후 모든 결과 대기
     */
    fun sendBatchAndWait(
        topic: String,
        messages: List<Pair<String, String>>,
        timeoutMs: Long = 30000
    ): List<SendResult> {
        val futures = sendBatch(topic, messages)
        return CompletableFuture.allOf(*futures.toTypedArray())
            .thenApply { futures.map { it.get() } }
            .get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
    }
}

