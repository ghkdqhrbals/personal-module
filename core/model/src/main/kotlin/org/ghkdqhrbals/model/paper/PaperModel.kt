package org.ghkdqhrbals.model.paper

import org.ghkdqhrbals.model.time.AuditProps
import java.time.OffsetDateTime

data class PaperModel(
    override val id: String? = null,
    override val createdAt: OffsetDateTime,
    override val updatedAt: OffsetDateTime,
    override val arxivId: String?,
    override val title: String,
    override val authors: List<String>,
    override val abstract: String,
    override val url: String,
    override val pdfUrl: String,
    override val journalRefRaw: String?,
    override val impactFactor: Double?,
    override val publishedAt: OffsetDateTime,
    override val summary: String? = null,
    override val novelty: String? = null,
    override val coreContribution: String? = null,
    override val limitations: String? = null,
    override val futureWork: String? = null,
) : AuditProps, Paper, PaperAnalysis

interface Paper {
    val id: String?
    val arxivId: String?
    val title: String
    val authors: List<String>
    val abstract: String
    val url: String
    val pdfUrl: String
    val journalRefRaw: String?
    val impactFactor: Double?
    val publishedAt: OffsetDateTime
    val summary: String?
    val novelty: String?
}

interface PaperAnalysis {
    val coreContribution: String?
    val novelty: String?
    val limitations: String?
    val futureWork: String?
}