package org.ghkdqhrbals.message.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.ghkdqhrbals.message.event.EventSender
import org.ghkdqhrbals.repository.event.EventStoreEntity
import org.ghkdqhrbals.repository.event.EventStoreRepository
import org.ghkdqhrbals.repository.event.SagaStateEntity
import org.ghkdqhrbals.repository.event.SagaStateRepository
import org.ghkdqhrbals.model.event.SagaCommandEvent
import org.ghkdqhrbals.model.event.SagaEvent
import org.ghkdqhrbals.model.event.SagaResponseEvent
import org.ghkdqhrbals.model.event.SagaStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.OffsetDateTime

/**
 * Event Store 서비스 - 모든 Saga 이벤트를 순서대로 저장하고 조회
 */
@Service
class EventStoreService(
    private val eventStoreRepository: EventStoreRepository,
    private val sagaStateRepository: SagaStateRepository,
    private val eventSender: EventSender,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(EventStoreService::class.java)

    fun sendEvent(topic: String, event: SagaEvent): SagaEvent {
        val timestamp = Instant.now()
        val eventWithTimestamp = when (event) {
            is SagaCommandEvent -> event.copy(timestamp = timestamp)
            is SagaResponseEvent -> event.copy(timestamp = timestamp)
            else -> event
        }
        val eventId = eventSender.send(topic, eventWithTimestamp)
        log.info("Send event to topic {}: sagaId={}, eventType={}",
            topic, eventId, event.eventType)
        eventStoreRepository.save(
            EventStoreEntity(
                topic = topic,
                eventId = eventId,
                sagaId = eventId,
                sequenceNumber = 1,
                eventType = eventWithTimestamp.eventType,
                timestamp = eventWithTimestamp.timestamp,
                payload = objectMapper.writeValueAsString(eventWithTimestamp.payload),
                success = when (eventWithTimestamp) {
                    is SagaResponseEvent -> eventWithTimestamp.success
                    else -> true
                },
                errorMessage = when (eventWithTimestamp) {
                    is SagaResponseEvent -> eventWithTimestamp.errorMessage
                    else -> null
                }
            )
        )
        return eventWithTimestamp
    }

    /**
     * Saga의 모든 이벤트를 순서대로 조회
     */
    @Transactional(readOnly = true)
    fun getEventsBySagaId(sagaId: String): List<EventStoreEntity> {
        return eventStoreRepository.findBySagaIdOrderBySequenceNumberAsc(sagaId)
    }

    /**
     * Saga 상태 생성
     */
    @Transactional
    fun createSagaState(
        sagaId: String,
        sagaType: String,
        totalSteps: Int,
        sagaData: Map<String, Any>? = null
    ): SagaStateEntity {
        val state = SagaStateEntity(
            sagaId = sagaId,
            sagaType = sagaType,
            status = SagaStatus.STARTED,
            currentStepIndex = 0,
            totalSteps = totalSteps,
            sagaData = sagaData?.let { objectMapper.writeValueAsString(it) }
        )
        val saved = sagaStateRepository.save(state)
        log.info("Saga state created: sagaId={}, type={}, totalSteps={}",
            sagaId, sagaType, totalSteps)
        return saved
    }

    /**
     * Saga 상태 업데이트
     */
    @Transactional
    fun updateSagaState(
        sagaId: String,
        status: SagaStatus? = null,
        currentStepIndex: Int? = null,
        sagaData: Map<String, Any>? = null
    ): SagaStateEntity {
        val state = sagaStateRepository.findById(sagaId)
            .orElseThrow { IllegalStateException("Saga not found: $sagaId") }

        status?.let { state.status = it }
        currentStepIndex?.let { state.currentStepIndex = it }
        sagaData?.let { state.sagaData = objectMapper.writeValueAsString(it) }
        state.updatedAt = OffsetDateTime.now()

        val saved = sagaStateRepository.save(state)
        log.debug("Saga state updated: sagaId={}, status={}, currentStep={}",
            sagaId, state.status, state.currentStepIndex)
        return saved
    }

    /**
     * Saga 상태 조회
     */
    @Transactional(readOnly = true)
    fun getSagaState(sagaId: String): SagaStateEntity? {
        return sagaStateRepository.findById(sagaId).orElse(null)
    }

    /**
     * 활성 Saga 목록 조회
     */
    @Transactional(readOnly = true)
    fun getActiveSagas(): List<SagaStateEntity> {
        return sagaStateRepository.findActiveSagas()
    }

    /**
     * Saga 데이터를 Map으로 파싱
     */
    @Suppress("UNCHECKED_CAST")
    fun parseSagaData(sagaData: String?): Map<String, Any> {
        return sagaData?.let {
            objectMapper.readValue(it, Map::class.java) as Map<String, Any>
        } ?: emptyMap()
    }
}

