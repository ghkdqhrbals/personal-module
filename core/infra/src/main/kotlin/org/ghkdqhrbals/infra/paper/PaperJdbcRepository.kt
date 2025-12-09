package org.ghkdqhrbals.infra.paper

import org.ghkdqhrbals.model.paper.PaperModel
import org.springframework.data.repository.CrudRepository

interface PaperJdbcRepository : CrudRepository<PaperEntity, Long> {
    fun findByArxivId(arxivId: String): PaperEntity?
    fun existsByArxivId(arxivId: String): Boolean
    fun existsByUrl(url: String): Boolean
    fun findTop100ByOrderBySearchDateDesc(): List<PaperEntity>
    fun findAllByArxivIdIn(arxivIds: List<String>): List<PaperEntity>
}

class PaperRepositoryImpl(
    private val paperJdbcRepository: PaperJdbcRepository,
): PaperRepository {
    override fun findByArxivId(arxivId: String): PaperModel? {
        return paperJdbcRepository.findByArxivId(arxivId)?.toModel()
    }

    override fun existsByArxivId(arxivId: String): Boolean {
        return paperJdbcRepository.existsByArxivId(arxivId)
    }

    override fun existsByUrl(url: String): Boolean {
        return paperJdbcRepository.existsByUrl(url)
    }

    override fun findTop100ByOrderBySearchDateDesc(): List<PaperModel> {
        return paperJdbcRepository.findTop100ByOrderBySearchDateDesc().map { it.toModel() }
    }

    override fun findAllByArxivIdIn(arxivIds: List<String>): List<PaperModel> {
        return paperJdbcRepository.findAllByArxivIdIn(arxivIds).map { it.toModel() }
    }

    override fun saveAll(papers: List<PaperModel>): List<PaperModel> {
        val entities = papers.map { it.fromModel() }
        paperJdbcRepository.saveAll(entities).also {
            return it.map { entity -> entity.toModel() }
        }
    }

    private fun PaperEntity.toModel() =
            PaperModel(
                createdAt = this.createdAt,
                updatedAt = this.updatedAt,
                id = this.id.toString(),
                arxivId = this.arxivId,
                title = this.title,
                authors = this.author?.split(",")?: emptyList(),
                abstract = this.abstract,
                url = this.url,
                pdfUrl = this.url, // TODO 변경필요
                journalRefRaw = this.journal,
                publishedAt = this.publishedAt,
                impactFactor = this.impactFactor,
                coreContribution = this.summary,
                novelty = this.novelty,
            )

    private fun PaperModel.fromModel() = PaperEntity(
        id = this.id?.toLong(),
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        arxivId = this.arxivId,
        title = this.title,
        author = this.authors.joinToString(","),
        abstract = this.abstract,
        url = this.url,
        journal = this.journalRefRaw,
        publishedAt = this.publishedAt,
        impactFactor = this.impactFactor,
    )
}