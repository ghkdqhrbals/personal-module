package org.ghkdqhrbals.client.config.listener

import com.fasterxml.jackson.databind.ObjectMapper
import org.ghkdqhrbals.client.ai.LlmClient
import org.ghkdqhrbals.message.kafka.config.ConditionalOnKafkaEnabled
import org.ghkdqhrbals.message.kafka.sender.SagaMessageSender
import org.ghkdqhrbals.message.saga.definition.SagaTopics
import org.ghkdqhrbals.message.service.EventStoreService
import org.ghkdqhrbals.message.util.toMap
import org.ghkdqhrbals.model.event.SagaResponse
import org.ghkdqhrbals.model.event.SagaStatus
import org.ghkdqhrbals.model.event.parser.EventParser
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.messaging.handler.annotation.Header
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Component
import java.time.OffsetDateTime
import java.time.ZoneOffset


@Component
@ConditionalOnKafkaEnabled
class KafkaListeners(
    private val eventParser: EventParser,
    private val sender: SagaMessageSender,
    private val eventStoreService: EventStoreService,
    private val objectMapper: ObjectMapper,
    private val llmClient: LlmClient,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    @KafkaListener(
        topics = [SagaTopics.AiProcess.PREPROCESSING_COMMAND],
        groupId = "\${saga.consumer-group:ai-preprocessor}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    suspend fun handleSagaResponse(
        @Payload message: String,
        @Header(name = "kafka_receivedMessageKey", required = false) key: String?
    ) {
        try {
            log.info("Received saga response: key={}", key)
            val event = eventParser.parseEvent(message)

            // Saga 상태 확인
            val sagaState = eventStoreService.getSagaState(event.sagaId)
            if (sagaState == null) {
                log.warn("Saga not found for response: sagaId={}", event.sagaId)
                return
            }

            // 상태에 따라 적절한 핸들러 호출
            when (sagaState.status) {
                SagaStatus.IN_PROGRESS, SagaStatus.STARTED -> {
                    val response = llmClient.summarizePaper(
                        event.payload["abstract"] as String,
                        event.payload["maxLength"] as? Int ?: 150,
                        event.payload["journalRef"] as? String ?: ""
                    )
                    val map = response.toMap()
                }
                else -> {
                    log.warn("Received response for saga in unexpected state: sagaId={}, status={}",
                        event.sagaId, sagaState.status)
                }
            }
        } catch (e: Exception) {
            log.error("Failed to process saga response: {}", message, e)
        }
    }
}