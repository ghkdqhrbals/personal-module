package org.ghkdqhrbals.model.event

import java.time.OffsetDateTime

/**
 * Saga 이벤트 소싱 전체 데이터
 */
data class EventHistory(
    val sagaId: String,
    val sagaType: String,
    val currentStatus: SagaStatus,
    val currentStepIndex: Int,
    val totalSteps: Int,
    val events: List<Event>,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime
)

/**
 * 개별 이벤트 DTO
 */
data class Event(
    val eventId: String,
    val sequenceNumber: Long,
    val eventType: SagaEventType,
    val timestamp: OffsetDateTime,
    val stepName: String?,
    val stepIndex: Int?,
    val success: Boolean,
    val errorMessage: String?,
    val payload: Map<String, Any>
)