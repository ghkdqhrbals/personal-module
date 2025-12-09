package org.ghkdqhrbals.model.event

import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * Saga 이벤트의 기본 인터페이스
 */
interface SagaEvent {
    val eventId: String
    val sagaId: String
    val eventType: SagaEventType
    val timestamp: OffsetDateTime
    val payload: Map<String, Any>
}

/**
 * Saga 이벤트의 기본 구현체
 */
data class BaseSagaEvent(
    override val eventId: String = UUID.randomUUID().toString(),
    override val sagaId: String,
    override val eventType: SagaEventType,
    override val timestamp: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC),
    override val payload: Map<String, Any> = emptyMap()
) : SagaEvent

/**
 * Saga 명령 이벤트 - 서비스에 전송되는 명령
 */
data class SagaCommandEvent(
    override val eventId: String = UUID.randomUUID().toString(),
    override val sagaId: String,
    override val eventType: SagaEventType,
    override val timestamp: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC),
    override val payload: Map<String, Any> = emptyMap(),
    val stepName: String,
    val stepIndex: Int,
    val commandTopic: String,
    val responseTopic: String
) : SagaEvent

/**
 * Saga 응답 이벤트 - 서비스로부터 받는 응답
 */
data class SagaResponseEvent(
    override val eventId: String = UUID.randomUUID().toString(),
    override val sagaId: String,
    override val eventType: SagaEventType,
    override val timestamp: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC),
    override val payload: Map<String, Any> = emptyMap(),
    val stepName: String,
    val stepIndex: Int,
    val sourceService: String,
    val success: Boolean,
    val errorMessage: String? = null
) : SagaEvent


