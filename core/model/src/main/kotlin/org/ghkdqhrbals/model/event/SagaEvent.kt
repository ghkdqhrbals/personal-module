package org.ghkdqhrbals.model.event

import java.time.Instant

/**
 * Saga 이벤트의 기본 인터페이스
 */
interface SagaEvent {
    val eventId: String?
    val sagaId: String?
    val eventType: SagaEventType
    val timestamp: Instant
    val payload: Map<String, Any>
}

/**
 * Saga 이벤트의 기본 구현체
 */
data class BaseSagaEvent(
    override val eventId: String? = null,
    override val sagaId: String? = null,
    override val eventType: SagaEventType,
    override val timestamp: Instant = Instant.now(),
    override val payload: Map<String, Any> = emptyMap()
) : SagaEvent {
    companion object {
        fun create(
            eventType: SagaEventType,
            payload: Map<String, Any> = emptyMap()
        ): BaseSagaEvent {
            return BaseSagaEvent(
                eventId = null,
                sagaId = null,
                eventType = eventType,
                payload = payload
            )
        }
    }
}

/**
 * Saga 명령 이벤트 - 서비스에 전송되는 명령
 */
data class SagaCommandEvent(
    override val eventId: String,
    override val sagaId: String,
    override val eventType: SagaEventType,
    override val timestamp: Instant = Instant.now(),
    override val payload: Map<String, Any> = emptyMap()
) : SagaEvent

/**
 * Saga 응답 이벤트 - 서비스로부터 받는 응답
 */
data class SagaResponseEvent(
    override val eventId: String,
    override val sagaId: String,
    override val eventType: SagaEventType,
    override val timestamp: Instant = Instant.now(),
    override val payload: Map<String, Any> = emptyMap(),
    val sourceService: String,
    val success: Boolean,
    val errorMessage: String? = null
) : SagaEvent


