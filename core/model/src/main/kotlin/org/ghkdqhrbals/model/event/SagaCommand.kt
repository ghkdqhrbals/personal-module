package org.ghkdqhrbals.model.event

import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

/**
 * Saga 명령 Domain Model
 */
data class SagaCommand(
    val eventId: String = UUID.randomUUID().toString(),
    val sagaId: String,
    val eventType: SagaEventType,
    val timestamp: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC),
    val stepName: String,
    val stepIndex: Int,
    val commandTopic: String,
    val responseTopic: String,
    val payload: Map<String, Any>
) {
    fun isCompensation(): Boolean = payload["isCompensation"] as? Boolean ?: false
}