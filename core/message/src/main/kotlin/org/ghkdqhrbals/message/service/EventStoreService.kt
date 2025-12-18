package org.ghkdqhrbals.message.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.ghkdqhrbals.infra.event.EventStoreEntity
import org.ghkdqhrbals.infra.event.EventStoreRepository
import org.ghkdqhrbals.infra.event.SagaStateEntity
import org.ghkdqhrbals.infra.event.SagaStateRepository
import org.ghkdqhrbals.model.event.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

/**
 * Event Store 서비스 - 모든 Saga 이벤트를 순서대로 저장하고 조회
 */
@Service
class EventStoreService(
    private val eventStoreRepository: EventStoreRepository,
    private val sagaStateRepository: SagaStateRepository,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(EventStoreService::class.java)

    /**
     * 이벤트를 Event Store에 저장
     */
    @Transactional
    fun appendEvent(event: SagaEvent): EventStoreModel {
        val nextSequence = eventStoreRepository.findMaxSequenceNumberBySagaId(event.sagaId) + 1

        val model = EventStoreModel(
            eventId = event.eventId,
            sagaId = event.sagaId,
            sequenceNumber = nextSequence,
            eventType = event.eventType,
            timestamp = event.timestamp,
            payload = event.payload,
            stepName = (event as? SagaStepEvent)?.stepName,
            stepIndex = (event as? SagaStepEvent)?.stepIndex,
            success = (event as? SagaResultEvent)?.success ?: true,
            errorMessage = (event as? SagaResultEvent)?.errorMessage
        )

        val saved = eventStoreRepository.save(model)
        log.debug(
            "Event appended: sagaId={}, sequence={}, type={}",
            event.sagaId, nextSequence, event.eventType
        )
        return saved
    }

    /**
     * Saga의 모든 이벤트를 순서대로 조회
     */
    @Transactional(readOnly = true)
    fun getEventsBySagaId(sagaId: String): List<EventStoreModel> {
        return eventStoreRepository.findBySagaIdOrderBySequenceNumberAsc(sagaId)
    }

    /**
     * Saga의 전체 이벤트 소싱 데이터 조회 (이벤트 + 최종 상태)
     */
    @Transactional(readOnly = true)
    fun getSagaEventSourcing(sagaId: String): EventHistory? {
        val state = getSagaState(sagaId) ?: return null
        val events = getEventsBySagaId(sagaId)

        return EventHistory(
            sagaId = state.sagaId,
            sagaType = state.sagaType,
            currentStatus = state.status,
            currentStepIndex = state.currentStepIndex,
            totalSteps = state.totalSteps,
            events = events.map { event ->
                Event(
                    eventId = event.eventId,
                    sequenceNumber = event.sequenceNumber,
                    eventType = event.eventType,
                    timestamp = event.timestamp,
                    stepName = event.stepName,
                    stepIndex = event.stepIndex,
                    success = event.success,
                    errorMessage = event.errorMessage,
                    payload = event.payload
                )
            },
            createdAt = state.createdAt,
            updatedAt = state.updatedAt
        )
    }

    /**
     * 여러 Saga의 이벤트 소싱 데이터를 한번에 조회
     */
    @Transactional(readOnly = true)
    fun getSagaEventSourcingBatch(sagaIds: List<String>): Map<String, EventHistory?> {
        return sagaIds.associateWith { sagaId ->
            getSagaEventSourcing(sagaId)
        }
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
    ): SagaStateModel {
        val state = SagaStateModel(
            sagaId = sagaId,
            sagaType = sagaType,
            status = SagaStatus.STARTED,
            currentStepIndex = 0,
            totalSteps = totalSteps,
            sagaData = sagaData ?: emptyMap(),
            createdAt = OffsetDateTime.now(),
            updatedAt = OffsetDateTime.now()
        )
        val saved = sagaStateRepository.insert(state)
        log.info(
            "Saga state created: sagaId={}, type={}, totalSteps={}",
            sagaId, sagaType, totalSteps
        )
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
    ): SagaStateModel {
        val state = sagaStateRepository.findById(sagaId)?: throw IllegalStateException("Saga not found: $sagaId")

        status?.let { state.status = it }
        currentStepIndex?.let { state.currentStepIndex = it }
        state.updatedAt = OffsetDateTime.now()

        val saved = sagaStateRepository.save(state)
        log.debug(
            "Saga state updated: sagaId={}, status={}, currentStep={}",
            sagaId, state.status, state.currentStepIndex
        )
        return saved
    }

    /**
     * Saga 상태 조회
     */
    @Transactional(readOnly = true)
    fun getSagaState(sagaId: String): SagaStateModel? {
        return sagaStateRepository.findById(sagaId)
    }

    /**
     * 활성 Saga 목록 조회
     */
    @Transactional(readOnly = true)
    fun getActiveSagas(): List<SagaStateModel> {
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

