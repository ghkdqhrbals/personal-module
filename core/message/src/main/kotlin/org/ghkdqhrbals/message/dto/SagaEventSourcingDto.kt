package org.ghkdqhrbals.message.dto

import org.ghkdqhrbals.model.event.SagaEventType
import org.ghkdqhrbals.model.event.SagaStatus
import java.time.Instant
import java.time.OffsetDateTime

/**
 * Saga 이벤트 소싱 전체 데이터
 */
data class SagaEventSourcingDto(
    val sagaId: String,
    val sagaType: String,
    val currentStatus: SagaStatus,
    val currentStepIndex: Int,
    val totalSteps: Int,
    val events: List<EventDto>,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime
)

/**
 * 개별 이벤트 DTO
 */
data class EventDto(
    val eventId: String,
    val sequenceNumber: Long,
    val eventType: SagaEventType,
    val timestamp: Instant,
    val stepName: String?,
    val stepIndex: Int?,
    val success: Boolean,
    val errorMessage: String?,
    val payload: Map<String, Any>
)

