package org.ghkdqhrbals.client.domain.paper.repository

import org.ghkdqhrbals.client.domain.paper.entity.PaperEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface PaperRepository : JpaRepository<PaperEntity, Long> {
    fun findByArxivId(arxivId: String): PaperEntity?
    fun existsByArxivId(arxivId: String): Boolean
    fun existsByUrl(url: String): Boolean
    fun findTop100ByOrderBySearchDateDesc(): List<PaperEntity>
    fun findAllByArxivIdIn(arxivIds: List<String>): List<PaperEntity>
}
