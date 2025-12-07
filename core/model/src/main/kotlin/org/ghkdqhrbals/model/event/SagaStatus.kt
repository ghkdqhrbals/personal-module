package org.ghkdqhrbals.model.event

/**
 * Saga 상태 열거형
 */
enum class SagaStatus {
    STARTED,
    IN_PROGRESS,
    COMPENSATING,
    COMPLETED,
    FAILED,
    COMPENSATION_COMPLETED
}
