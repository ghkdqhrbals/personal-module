package org.ghkdqhrbals.client.domain.paper.entity

import jakarta.persistence.*
import java.time.LocalDate
import java.time.OffsetDateTime

@Entity
@Table(name = "paper")
data class PaperEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long? = null,

    @Column(name = "arxiv_id", length = 32)
    val arxivId: String?,

    @Column(name = "title")
    val title: String? = null,

    // TEXT 타입으로 변경
    @Column(name = "author", columnDefinition = "TEXT")
    val author: String? = null,

    @Column(name = "published_at")
    val publishedAt: LocalDate? = null,

    @Column(name = "search_date", columnDefinition = "TIMESTAMP(6)")
    val searchDate: LocalDate? = null,

    @Column(name = "summarized_at", columnDefinition = "TIMESTAMP(6)")
    val summarizedAt: OffsetDateTime? = null,

    @Column(name = "url")
    val url: String? = null,

    @Column(name = "journal")
    val journal: String? = null,

    @Column(name = "impact_factor")
    val impactFactor: Double? = null,

    @Column(name = "summary", columnDefinition = "TEXT")
    val summary: String? = null,

    val novelty: String? = null
)

