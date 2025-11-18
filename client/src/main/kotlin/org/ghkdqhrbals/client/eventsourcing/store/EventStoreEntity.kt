package org.ghkdqhrbals.client.eventsourcing.store

import jakarta.persistence.*
import java.time.Instant

/**
 * 이벤트 저장소 엔티티
 */
@Entity
@Table(
    name = "event_store",
    indexes = [
        Index(name = "idx_aggregate_id", columnList = "aggregate_id"),
        Index(name = "idx_event_type", columnList = "event_type"),
        Index(name = "idx_timestamp", columnList = "timestamp")
    ]
)
data class EventStoreEntity(
    @Id
    @Column(name = "event_id", nullable = false, length = 36)
    val eventId: String,

    @Column(name = "aggregate_id", nullable = false, length = 100)
    val aggregateId: String,

    @Column(name = "event_type", nullable = false, length = 100)
    val eventType: String,

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    val payload: String,

    @Column(name = "timestamp", nullable = false)
    val timestamp: Instant,

    @Column(name = "version", nullable = false)
    val version: Long,

    @Column(name = "metadata", columnDefinition = "TEXT")
    val metadata: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
)

