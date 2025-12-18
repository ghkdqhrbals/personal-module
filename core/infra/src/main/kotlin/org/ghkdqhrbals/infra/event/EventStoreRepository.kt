package org.ghkdqhrbals.infra.event

import org.ghkdqhrbals.model.event.EventStoreModel
import org.ghkdqhrbals.model.event.SagaStateModel
import org.ghkdqhrbals.model.event.SagaStatus
import org.ghkdqhrbals.model.event.SagaEventType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface EventStoreJdbcRepository : CrudRepository<EventStoreEntity, Long> {

    /**
     * sagaId로 모든 이벤트를 순서대로 조회
     */
    fun findBySagaIdOrderBySequenceNumberAsc(sagaId: String): List<EventStoreEntity>

    /**
     * sagaId의 마지막 시퀀스 번호 조회
     */
    @Query("SELECT COALESCE(MAX(e.sequenceNumber), 0) FROM EventStoreEntity e WHERE e.sagaId = :sagaId")
    fun findMaxSequenceNumberBySagaId(@Param("sagaId") sagaId: String): Long

    /**
     * 특정 이벤트 타입으로 조회
     */
    fun findBySagaIdAndEventType(sagaId: String, eventType: SagaEventType): List<EventStoreEntity>

    /**
     * 특정 스텝의 이벤트 조회
     */
    fun findBySagaIdAndStepIndex(sagaId: String, stepIndex: Int): List<EventStoreEntity>
}

@Repository
interface SagaStateJdbcRepository : JpaRepository<SagaStateEntity, String> {

    /**
     * 상태별 Saga 조회
     */
    fun findByStatus(status: SagaStatus): List<SagaStateEntity>

    /**
     * Saga 타입과 상태로 조회
     */
    fun findBySagaTypeAndStatus(sagaType: String, status: SagaStatus): List<SagaStateEntity>

    /**
     * 진행 중인 Saga 조회
     */
    @Query("SELECT s FROM SagaStateEntity s WHERE s.status NOT IN (org.ghkdqhrbals.model.event.SagaStatus.COMPLETED, org.ghkdqhrbals.model.event.SagaStatus.FAILED, org.ghkdqhrbals.model.event.SagaStatus.COMPENSATION_COMPLETED)")
    fun findActiveSagas(): List<SagaStateEntity>
}

/**
 * EventStore 헥사고날 인터페이스 (도메인 레이어)
 */
interface EventStoreRepository {
    fun findMaxSequenceNumberBySagaId(sagaId: String): Long
    fun findBySagaId(sagaId: String): List<EventStoreModel>
    fun findMaxSequenceNumber(sagaId: String): Long
    fun findByEventType(sagaId: String, eventType: SagaEventType): List<EventStoreModel>
    fun findByStepIndex(sagaId: String, stepIndex: Int): List<EventStoreModel>
    fun save(event: EventStoreModel): EventStoreModel
    fun saveAll(events: List<EventStoreModel>): List<EventStoreModel>
    fun findBySagaIdOrderBySequenceNumberAsc(sagaId: String): List<EventStoreModel>
}

/**
 * SagaState 헥사고날 인터페이스 (도메인 레이어)
 */
interface SagaStateRepository {
    fun findById(sagaId: String): SagaStateModel?
    fun findByStatus(status: SagaStatus): List<SagaStateModel>
    fun findBySagaTypeAndStatus(sagaType: String, status: SagaStatus): List<SagaStateModel>
    fun findActiveSagas(): List<SagaStateModel>
    fun save(state: SagaStateModel): SagaStateModel
    fun deleteById(sagaId: String)
    fun insert(state: SagaStateModel): SagaStateModel
}

