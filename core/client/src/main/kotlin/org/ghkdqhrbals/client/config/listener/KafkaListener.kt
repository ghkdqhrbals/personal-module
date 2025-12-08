package org.ghkdqhrbals.client.config.listener

import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.ghkdqhrbals.client.ai.LlmClient
import org.ghkdqhrbals.client.config.log.logger
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
class KafkaListeners(
    private val eventParser: EventParser,
    private val sender: SagaMessageSender,
    private val eventStoreService: EventStoreService,
    private val objectMapper: ObjectMapper,
    private val llmClient: LlmClient,
) {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val processingScope = CoroutineScope(Dispatchers.IO)

    @KafkaListener(
        topics = [SagaTopics.AiProcess.INFERENCE_COMMAND],
        groupId = "\${saga.consumer-group:ai-preprocessor}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    fun handleSagaRequest(
        @Payload message: String,
        @Header(name = "kafka_receivedMessageKey", required = false) key: String?
    ) {
        log.info("Received saga response: key={}", key)

        // 별도 코루틴에서 비동기 처리
        processingScope.launch {
            try {
                val event = eventParser.parseEvent(message)
                logger().info("Parsed saga response event: {}", event.payload["abstract"])

                // Saga 상태 확인
                val sagaState = eventStoreService.getSagaState(event.sagaId)
                if (sagaState == null) {
                    log.warn("Saga not found for response: sagaId={}", event.sagaId)
                    return@launch
                }

                // 상태에 따라 적절한 핸들러 호출
                when (sagaState.status) {
                    SagaStatus.IN_PROGRESS, SagaStatus.STARTED -> {
                        val response = llmClient.summarizePaper(
                            event.payload["abstract"] as String,
                            event.payload["maxLength"] as? Int ?: 150,
                            event.payload["journalRef"] as? String ?: "arxiv"
                        )
                        val map = response.toMap()

                        sender.sendResponse(
                            sagaId = event.sagaId,
                            SagaResponse(
                                eventId = event.eventId,
                                sagaId = event.sagaId,
                                eventType = event.eventType,
                                sourceService = "AiPreprocessor",
                                timestamp = OffsetDateTime.now(ZoneOffset.UTC).toInstant(),
                                stepName = "AiProcess",
                                stepIndex = event.stepIndex + 1,
                                success = true,
                                payload = map
                            )
                        )
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
}