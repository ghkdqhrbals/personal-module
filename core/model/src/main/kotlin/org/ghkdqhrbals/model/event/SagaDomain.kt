package org.ghkdqhrbals.model.event

/**
 * Saga 이벤트 타입
 */
enum class SagaEventType {
    SAGA_STARTED,
    SAGA_STEP_STARTED,
    SAGA_STEP_COMPLETED,
    SAGA_STEP_FAILED,
    SAGA_COMPENSATING,
    SAGA_COMPENSATION_COMPLETED,
    SAGA_COMPLETED,
    SAGA_FAILED,
    STEP_RESPONSE,
    SAGA_RESPONSE
}

