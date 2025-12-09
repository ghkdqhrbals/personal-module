package org.ghkdqhrbals.infra.paper

import jakarta.persistence.*
import org.ghkdqhrbals.infra.subscribe.Subscribe
import org.hibernate.annotations.CreationTimestamp
import java.time.OffsetDateTime

/**
 * 논문-구독주제 관계 엔티티
 * 특정 논문이 어떤 구독 주제에 매칭되는지 관리
 */
@Entity
@Table(
    name = "paper_subscribes",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["paper_id", "subscribe_id"])
    ],
    indexes = [
        Index(name = "idx_paper_id", columnList = "paper_id"),
        Index(name = "idx_subscribe_id", columnList = "subscribe_id")
    ]
)
class PaperSubscribe(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paper_id", nullable = false)
    var paper: PaperEntity,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscribe_id", nullable = false)
    var subscribe: Subscribe,

    /**
     * 매칭 점수 (0.0 ~ 1.0)
     * AI가 판단한 관련도 점수
     */
    var matchScore: Double = 0.0,

    /**
     * 매칭 이유
     */
    @Column(columnDefinition = "TEXT")
    var matchReason: String? = null
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0

    @CreationTimestamp
    @Column(columnDefinition = "DATETIME(6)", nullable = true)
    var matchedAt: OffsetDateTime? = OffsetDateTime.now()

    /**
     * 높은 관련도 판별 (0.7 이상)
     */
    fun isHighRelevance(): Boolean {
        return matchScore >= 0.7
    }

    /**
     * 중간 관련도 판별 (0.4 ~ 0.7)
     */
    fun isMediumRelevance(): Boolean {
        return matchScore >= 0.4 && matchScore < 0.7
    }
}

