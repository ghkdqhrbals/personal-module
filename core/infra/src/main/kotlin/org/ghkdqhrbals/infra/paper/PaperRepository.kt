package org.ghkdqhrbals.infra.paper

import org.ghkdqhrbals.model.paper.PaperModel

interface PaperRepository {
    fun findByArxivId(arxivId: String): PaperModel?
    fun existsByArxivId(arxivId: String): Boolean
    fun existsByUrl(url: String): Boolean
    fun findTop100ByOrderBySearchDateDesc(): List<PaperModel>
    fun findAllByArxivIdIn(arxivIds: List<String>): List<PaperModel>
    fun saveAll(papers: List<PaperModel>): List<PaperModel>
}