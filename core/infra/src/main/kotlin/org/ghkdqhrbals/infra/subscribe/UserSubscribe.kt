package org.ghkdqhrbals.infra.subscribe

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.*
import org.ghkdqhrbals.infra.user.UserEntity
import org.hibernate.annotations.CreationTimestamp
import java.time.OffsetDateTime

/**
 * 사용자-구독 관계 엔티티
 * 특정 사용자가 어떤 주제를 구독하는지 관리
 */
@Entity
@Table(
    name = "user_subscribes",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["user_id", "subscribe_id"])
    ]
)
class UserSubscribe(
    @Column(name = "user_id")
    val userId: Long,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscribe_id", nullable = false)
    var subscribe: Subscribe,

    /**
     * 알림 활성화 여부
     */
    var notificationEnabled: Boolean = true,

    /**
     * 우선순위 (1-5, 5가 가장 높음)
     */
    var priority: Int = 3
) {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", updatable = false, insertable = false)
    lateinit var user: UserEntity

    @CreationTimestamp
    var subscribedAt: OffsetDateTime = OffsetDateTime.now()

    var unsubscribedAt: OffsetDateTime? = null

    fun unsubscribe() {
        this.unsubscribedAt = OffsetDateTime.now()
        this.notificationEnabled = false
    }

    fun resubscribe() {
        this.unsubscribedAt = null
        this.notificationEnabled = true
    }

    fun isActive(): Boolean {
        return unsubscribedAt == null
    }
}