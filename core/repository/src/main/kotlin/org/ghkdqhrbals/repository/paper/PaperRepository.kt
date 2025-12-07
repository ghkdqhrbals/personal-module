package org.ghkdqhrbals.repository.paper

import org.springframework.data.jpa.repository.JpaRepository

interface PaperRepository : JpaRepository<PaperEntity, Long> {
    fun findByArxivId(arxivId: String): PaperEntity?
    fun existsByArxivId(arxivId: String): Boolean
    fun existsByUrl(url: String): Boolean
    fun findTop100ByOrderBySearchDateDesc(): List<PaperEntity>
    fun findAllByArxivIdIn(arxivIds: List<String>): List<PaperEntity>
}
