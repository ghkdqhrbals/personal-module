package org.ghkdqhrbals.message.saga.builder

import org.ghkdqhrbals.model.event.SagaResponse
import org.ghkdqhrbals.model.event.SagaEventType
import java.time.Instant
import java.util.UUID

/**
 * Saga 응답 메시지 빌더
 */
object SagaResponseBuilder {

    /**
     * 성공 응답 생성
     */
    fun success(
        sagaId: String,
        stepName: String,
        stepIndex: Int,
        sourceService: String,
        payload: Map<String, Any> = emptyMap()
    ): SagaResponse {
        return SagaResponse(
            eventId = UUID.randomUUID().toString(),
            sagaId = sagaId,
            eventType = SagaEventType.STEP_RESPONSE,
            timestamp = Instant.now(),
            stepName = stepName,
            stepIndex = stepIndex,
            sourceService = sourceService,
            success = true,
            errorMessage = null,
            payload = payload
        )
    }

    /**
     * 실패 응답 생성
     */
    fun failure(
        sagaId: String,
        stepName: String,
        stepIndex: Int,
        sourceService: String,
        errorMessage: String,
        payload: Map<String, Any> = emptyMap()
    ): SagaResponse {
        return SagaResponse(
            eventId = UUID.randomUUID().toString(),
            sagaId = sagaId,
            eventType = SagaEventType.STEP_RESPONSE,
            timestamp = Instant.now(),
            stepName = stepName,
            stepIndex = stepIndex,
            sourceService = sourceService,
            success = false,
            errorMessage = errorMessage,
            payload = payload
        )
    }

    /**
     * 보상 성공 응답 생성
     */
    fun compensationSuccess(
        sagaId: String,
        stepName: String,
        stepIndex: Int,
        sourceService: String,
        payload: Map<String, Any> = emptyMap()
    ): SagaResponse {
        return SagaResponse(
            eventId = UUID.randomUUID().toString(),
            sagaId = sagaId,
            eventType = SagaEventType.SAGA_COMPENSATION_COMPLETED,
            timestamp = Instant.now(),
            stepName = stepName,
            stepIndex = stepIndex,
            sourceService = sourceService,
            success = true,
            errorMessage = null,
            payload = payload + mapOf("isCompensation" to true)
        )
    }

    /**
     * 보상 실패 응답 생성
     */
    fun compensationFailure(
        sagaId: String,
        stepName: String,
        stepIndex: Int,
        sourceService: String,
        errorMessage: String,
        payload: Map<String, Any> = emptyMap()
    ): SagaResponse {
        return SagaResponse(
            eventId = UUID.randomUUID().toString(),
            sagaId = sagaId,
            eventType = SagaEventType.STEP_RESPONSE,
            timestamp = Instant.now(),
            stepName = stepName,
            stepIndex = stepIndex,
            sourceService = sourceService,
            success = false,
            errorMessage = errorMessage,
            payload = payload + mapOf("isCompensation" to true)
        )
    }

    /**
     * Map 형태로 성공 응답 생성 (레거시 호환)
     */
    fun successAsMap(
        sagaId: String,
        stepName: String,
        stepIndex: Int,
        sourceService: String,
        payload: Map<String, Any> = emptyMap()
    ): Map<String, Any> {
        return mapOf(
            "eventId" to UUID.randomUUID().toString(),
            "sagaId" to sagaId,
            "eventType" to "STEP_RESPONSE",
            "timestamp" to Instant.now().toString(),
            "stepName" to stepName,
            "stepIndex" to stepIndex,
            "sourceService" to sourceService,
            "success" to true,
            "payload" to payload
        )
    }

    /**
     * Map 형태로 실패 응답 생성 (레거시 호환)
     */
    fun failureAsMap(
        sagaId: String,
        stepName: String,
        stepIndex: Int,
        sourceService: String,
        errorMessage: String,
        payload: Map<String, Any> = emptyMap()
    ): Map<String, Any> {
        return mapOf(
            "eventId" to UUID.randomUUID().toString(),
            "sagaId" to sagaId,
            "eventType" to "STEP_RESPONSE",
            "timestamp" to Instant.now().toString(),
            "stepName" to stepName,
            "stepIndex" to stepIndex,
            "sourceService" to sourceService,
            "success" to false,
            "errorMessage" to errorMessage,
            "payload" to payload
        )
    }
}

