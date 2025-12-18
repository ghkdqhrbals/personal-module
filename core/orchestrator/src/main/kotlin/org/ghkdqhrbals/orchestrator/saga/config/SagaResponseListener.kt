package org.ghkdqhrbals.orchestrator.saga.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.ghkdqhrbals.model.event.SagaEventType
import org.ghkdqhrbals.model.event.SagaResponseEvent
import org.ghkdqhrbals.model.event.SagaStatus
import org.ghkdqhrbals.model.event.parser.EventParser
import org.ghkdqhrbals.orchestrator.saga.orchestrator.SagaOrchestrator
import org.ghkdqhrbals.orchestrator.saga.service.SagaEventStreamService
import org.ghkdqhrbals.message.service.EventStoreService

import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.messaging.handler.annotation.Header
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Component
import java.util.*

/**
 * 단일 응답 토픽에서 모든 Saga 응답을 처리하는 리스너
 */
@Component
class SagaResponseListener(
    private val eventParser: EventParser,
    private val sagaOrchestrator: SagaOrchestrator,
    private val eventStoreService: EventStoreService,
    private val sagaEventStreamService: SagaEventStreamService,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(SagaResponseListener::class.java)

    /**
     * 단일 응답 토픽에서 모든 응답 처리
     * 토픽 이름은 application.yml에서 설정
     */
    @KafkaListener(
        topics = ["\${saga.response-topic:saga-response}"],
        groupId = "\${saga.consumer-group:saga-orchestrator-group}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    fun handleSagaResponse(
        @Payload message: String,
        @Header(name = "kafka_receivedMessageKey", required = false) key: String?
    ) {
        try {
            log.debug("Received saga response: key={}", key)
            val response = eventParser.parseEvent(message)

            // Saga 상태 확인
            val sagaState = eventStoreService.getSagaState(response.sagaId)
            if (sagaState == null) {
                log.warn("Saga not found for response: sagaId={}", response.sagaId)
                return
            }

            // 상태에 따라 적절한 핸들러 호출
            when (sagaState.status) {
                SagaStatus.IN_PROGRESS, SagaStatus.STARTED -> {
                    sagaOrchestrator.handleResponse(response)
                    // SSE 클라이언트에게 이벤트 전파
                    publishEventToSSE(response)
                }
                SagaStatus.COMPENSATING -> {
                    sagaOrchestrator.handleCompensationResponse(response)
                    // SSE 클라이언트에게 이벤트 전파
                    publishEventToSSE(response)
                }
                else -> {
                    log.warn("Received response for saga in unexpected state: sagaId={}, status={}",
                        response.sagaId, sagaState.status)
                }
            }
        } catch (e: Exception) {
            log.error("Failed to process saga response: {}", message, e)
        }
    }

    /**
     * SSE 클라이언트에게 이벤트 발행
     */
    private fun publishEventToSSE(response: SagaResponseEvent) {
        try {
            val sagaState = eventStoreService.getSagaState(response.sagaId) ?: return

            val eventData = mapOf(
                "eventId" to response.eventId,
                "sagaId" to response.sagaId,
                "eventType" to response.eventType.name,
                "stepName" to response.stepName,
                "stepIndex" to response.stepIndex,
                "status" to sagaState.status.name,
                "success" to response.success,
                "errorMessage" to response.errorMessage,
                "currentStepIndex" to sagaState.currentStepIndex,
                "totalSteps" to sagaState.totalSteps,
                "timestamp" to System.currentTimeMillis()
            )

            sagaEventStreamService.publishEvent(response.sagaId, eventData)
            log.debug("Published SSE event for sagaId: {}", response.sagaId)
        } catch (e: Exception) {
            log.error("Failed to publish SSE event for sagaId: {}", response.sagaId, e)
        }
    }

    /**
     * 응답 메시지 파싱
     */
    @Suppress("UNCHECKED_CAST")
    private fun parseResponse(message: String): SagaResponseEvent {
        val map: Map<String, Any> = objectMapper.readValue(message)

        return SagaResponseEvent(
            eventId = map["eventId"] as? String ?: UUID.randomUUID().toString(),
            sagaId = map["sagaId"] as String,
            eventType = map["eventType"] as? SagaEventType ?: SagaEventType.SAGA_RESPONSE,
            stepName = map["stepName"] as String,
            stepIndex = (map["stepIndex"] as Number).toInt(),
            success = map["success"] as? Boolean ?: true,
            errorMessage = map["errorMessage"] as? String,
            payload = (map["payload"] as? Map<String, Any>) ?: emptyMap()
        )
    }
}

