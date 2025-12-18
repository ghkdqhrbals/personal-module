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
    COMPENSATION_COMPLETED;
    companion object {
        fun SagaStatus.isFinished(): Boolean = this in FINISHED
        private val FINISHED = setOf(COMPLETED, COMPENSATION_COMPLETED, FAILED)
    }
}