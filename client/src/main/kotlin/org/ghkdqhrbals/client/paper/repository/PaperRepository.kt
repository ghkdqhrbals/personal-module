package org.ghkdqhrbals.client.paper.repository

import org.ghkdqhrbals.client.paper.entity.PaperEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

interface PaperRepository : JpaRepository<PaperEntity, Long> {
    fun findByArxivId(arxivId: String): Optional<PaperEntity>
    fun existsByArxivId(arxivId: String): Boolean
    fun existsByUrl(url: String): Boolean
    fun findTop100ByOrderBySearchDateDesc(): List<PaperEntity>
    fun findAllByArxivIdIn(arxivIds: List<String>): List<PaperEntity>
}
