package org.ghkdqhrbals.client.paper.entity

import jakarta.persistence.*
import java.time.LocalDate

@Entity
@Table(name = "paper")
data class PaperEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long? = null,

    @Column(name = "arxiv_id", unique = true, length = 32)
    val arxivId: String? = null,

    @Column(name = "title")
    val title: String? = null,

    @Column(name = "author")
    val author: String? = null,

    @Column(name = "published_at")
    val publishedAt: LocalDate? = null,

    @Column(name = "search_date")
    val searchDate: LocalDate? = null,

    @Column(name = "summary_date")
    val summaryDate: LocalDate? = null,

    @Column(name = "url")
    val url: String? = null,

    @Column(name = "journal")
    val journal: String? = null,

    @Column(name = "impact_factor")
    val impactFactor: Double? = null,

    @Column(name = "summary", columnDefinition = "TEXT")
    val summary: String? = null
)

