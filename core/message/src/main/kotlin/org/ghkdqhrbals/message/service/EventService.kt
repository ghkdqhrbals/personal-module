package org.ghkdqhrbals.message.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.ghkdqhrbals.message.event.EventPublisher
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
class EventService(
    private val eventStoreRepository: EventStoreRepository,
    private val sagaStateRepository: SagaStateRepository,
    private val eventPublisher: EventPublisher,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(EventService::class.java)

    @Transactional
    fun sendEvent(topic: String, event: SagaEvent): SagaEvent {
        val eventId = eventPublisher.send(topic, event)
        log.info("Send event to topic {}: eventId={}, sagaId={}, eventType={}",
            topic, eventId, event.sagaId, event.eventType)
        eventStoreRepository.save(
            EventStoreEntity(
                topic = topic,
                eventId = eventId,
                sagaId = event.sagaId ?: eventId, // sagaId가 없으면 eventId를 사용
                sequenceNumber = 1,
                eventType = event.eventType,
                timestamp = event.timestamp,
                payload = objectMapper.writeValueAsString(event.payload),
                success = when (event) {
                    is SagaResponseEvent -> event.success
                    else -> true
                },
                errorMessage = when (event) {
                    is SagaResponseEvent -> event.errorMessage
                    else -> null
                }
            )
        )

        // eventId를 포함한 새 이벤트 반환
        return when (event) {
            is SagaCommandEvent -> event.copy(eventId = eventId)
            is SagaResponseEvent -> event.copy(eventId = eventId)
            else -> event
        }
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

