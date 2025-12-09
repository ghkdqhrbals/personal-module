package org.ghkdqhrbals.infra.subscribe

import jakarta.persistence.*
import org.ghkdqhrbals.model.paper.PaperSearchAndStoreEvent
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import org.springframework.data.domain.Pageable
import java.time.OffsetDateTime

/**
 * 구독 주제 엔티티
 * arXiv 카테고리나 키워드 등 구독할 수 있는 주제를 정의
 */
@Entity
@Table(name = "subscribes")
class Subscribe(
    @Column(nullable = false, unique = true)
    var name: String,

    var description: String? = null,

    /**
     * 구독 타입 (CATEGORY, KEYWORD, AUTHOR 등)
     */
    @Enumerated(EnumType.STRING)
    var subscribeType: SubscribeType = SubscribeType.KEYWORD,

    var activated: Boolean = true
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0

    @CreationTimestamp
    @Column(columnDefinition = "DATETIME(6)", nullable = true)
    var createdAt: OffsetDateTime? = OffsetDateTime.now()

    @UpdateTimestamp
    @Column(columnDefinition = "DATETIME(6)", nullable = true)
    var updatedAt: OffsetDateTime? = OffsetDateTime.now()

    @OneToMany(mappedBy = "subscribe", cascade = [CascadeType.ALL])
    var userSubscribes: MutableList<UserSubscribe> = mutableListOf()

    fun activate() {
        this.activated = true
    }

    fun deactivate() {
        this.activated = false
    }

    fun toPaperSearchAndStoreEvent(pageable: Pageable,summary: Boolean = false): PaperSearchAndStoreEvent {
        return PaperSearchAndStoreEvent(
            searchEventId = "subscribe-${this.id}-${System.currentTimeMillis()}",
            query = if (this.subscribeType == SubscribeType.KEYWORD) this.name else "",
            categories = if (this.subscribeType == SubscribeType.CATEGORY) listOf(this.name) else null,
            maxResults = pageable.pageSize,
            page = pageable.pageNumber,
            shouldSummarize = summary
        )
    }
}

enum class SubscribeType {
    CATEGORY,   // arXiv 카테고리 (예: cs.AI, cs.LG)
    KEYWORD,    // 키워드 기반
    AUTHOR,     // 특정 저자
    CUSTOM      // 사용자 정의
}