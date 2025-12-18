package org.ghkdqhrbals.model.event

import java.time.OffsetDateTime

data class EventStoreModel(
    val id: Long? = null,
    val eventId: String,
    val sagaId: String,
    val sequenceNumber: Long,
    val eventType: SagaEventType,
    val timestamp: OffsetDateTime,
    val payload: Map<String, Any>,
    val stepName: String?,
    val stepIndex: Int?,
    val success: Boolean,
    val errorMessage: String?,
)

data class SagaStateModel(
    val sagaId: String,
    val sagaType: String,
    var status: SagaStatus,
    var currentStepIndex: Int,
    val totalSteps: Int,
    var sagaData: Map<String, Any>?,
    val createdAt: OffsetDateTime,
    var updatedAt: OffsetDateTime
) {
    fun isCompleted(): Boolean {
        return status == SagaStatus.COMPLETED || status == SagaStatus.FAILED
    }
}

