package org.ghkdqhrbals.client.eventsourcing.store

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface EventStoreRepository : JpaRepository<EventStoreEntity, String> {

    /**
     * Aggregate ID로 모든 이벤트 조회 (버전 순서대로)
     */
    fun findByAggregateIdOrderByVersionAsc(aggregateId: String): List<EventStoreEntity>

    /**
     * Aggregate ID와 버전 범위로 이벤트 조회
     */
    fun findByAggregateIdAndVersionGreaterThanEqualOrderByVersionAsc(
        aggregateId: String,
        version: Long
    ): List<EventStoreEntity>

    /**
     * 이벤트 타입으로 조회
     */
    fun findByEventTypeOrderByTimestampDesc(eventType: String): List<EventStoreEntity>

    /**
     * 특정 시간 이후의 이벤트 조회
     */
    fun findByTimestampAfterOrderByTimestampAsc(timestamp: Instant): List<EventStoreEntity>

    /**
     * Aggregate ID와 이벤트 타입으로 조회
     */
    fun findByAggregateIdAndEventTypeOrderByVersionAsc(
        aggregateId: String,
        eventType: String
    ): List<EventStoreEntity>

    /**
     * 최신 버전 조회
     */
    @Query("SELECT MAX(e.version) FROM EventStoreEntity e WHERE e.aggregateId = :aggregateId")
    fun findLatestVersion(aggregateId: String): Long?
}

