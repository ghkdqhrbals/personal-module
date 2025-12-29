package org.ghkdqhrbals.message.kafka.sender

import com.fasterxml.jackson.databind.ObjectMapper
import org.ghkdqhrbals.message.event.EventPublisher
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate

/**
 * Saga 메시지 전송 컴포넌트
 */
class SagaMessagePublisher(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper
): EventPublisher {
    private val log = LoggerFactory.getLogger(this::class.java)
    override fun <T : Any> send(topic: String, event: T): String {
        val id = java.util.UUID.randomUUID().toString()
        val message = objectMapper.writeValueAsString(event)

        kafkaTemplate.send(topic, id, message)
            .thenAccept { result ->
                log.debug("Message sent: topic={}, partition={}, offset={}",
                    topic, result.recordMetadata.partition(), result.recordMetadata.offset())
            }
            .exceptionally { ex ->
                log.error("Failed to send message: topic={}, key={}, error={}",
                    topic, id, ex.message)
                null
            }
        return id
    }
}