package org.ghkdqhrbals.infra.event

import jakarta.persistence.*
import org.ghkdqhrbals.model.event.SagaEventType
import java.time.OffsetDateTime

/**
 * Event Store Entity - 모든 Saga 이벤트를 순서대로 저장
 */
@Entity
@Table(
    name = "saga_event_store",
    indexes = [
        Index(name = "idx_saga_id", columnList = "sagaId"),
        Index(name = "idx_saga_id_sequence", columnList = "sagaId, sequenceNumber"),
        Index(name = "idx_event_type", columnList = "eventType"),
        Index(name = "idx_timestamp", columnList = "timestamp")
    ]
)
data class EventStoreEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, length = 36)
    val eventId: String,

    @Column(nullable = false, length = 36)
    val sagaId: String,

    @Column(nullable = false)
    val sequenceNumber: Long,

    @Enumerated(EnumType.STRING)
    val eventType: SagaEventType,

    @Column(nullable = false)
    val timestamp: OffsetDateTime,

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    val payload: String,

    @Column(length = 100)
    val stepName: String? = null,

    @Column
    val stepIndex: Int? = null,

    @Column(nullable = false)
    val success: Boolean = true,

    @Column(columnDefinition = "TEXT")
    val errorMessage: String? = null,

    @Version
    val version: Long = 0
)
