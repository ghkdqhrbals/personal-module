package org.ghkdqhrbals.model.event

import java.time.Instant
import java.util.*

/**
 * Saga 응답 Domain Model
 */
data class SagaResponse(
    val eventId: String = UUID.randomUUID().toString(),
    val sagaId: String,
    val eventType: SagaEventType,
    val timestamp: Instant = Instant.now(),
    val stepName: String,
    val stepIndex: Int,
    val sourceService: String,
    val success: Boolean,
    val errorMessage: String? = null,
    val payload: Map<String, Any?> = emptyMap()
) {
    fun isSuccess(): Boolean = success
    fun isFailure(): Boolean = !success
    fun hasError(): Boolean = errorMessage != null
}