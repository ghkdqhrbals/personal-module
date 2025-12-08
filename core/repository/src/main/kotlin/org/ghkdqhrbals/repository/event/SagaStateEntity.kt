package org.ghkdqhrbals.repository.event

import jakarta.persistence.*
import org.ghkdqhrbals.model.event.SagaStatus
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Saga 상태를 추적하는 Entity
 */
@Entity
@Table(
    name = "saga_state",
    indexes = [
        Index(name = "idx_saga_state_status", columnList = "status"),
        Index(name = "idx_saga_state_type", columnList = "sagaType")
    ]
)
data class SagaStateEntity(
    @Id
    @Column(length = 36)
    val sagaId: String,

    @Column(nullable = false, length = 100)
    val sagaType: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var status: SagaStatus,

    @Column(nullable = false)
    var currentStepIndex: Int = 0,

    @Column(nullable = false)
    val totalSteps: Int,

    @Lob
    @Column(columnDefinition = "TEXT")
    var sagaData: String? = null,

    @Column(nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC),

    @Column(nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC),

    @Version
    val version: Long = 0
) {
    fun isCompleted(): Boolean {
        return status == SagaStatus.COMPLETED || status == SagaStatus.COMPENSATION_COMPLETED
    }
}
