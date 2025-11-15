package org.ghkdqhrbals.client.paper

import org.ghkdqhrbals.client.paper.repository.PaperRepository
import org.springframework.stereotype.Component

@Component
class DedupService(
    private val paperRepository: PaperRepository
) {
    fun alreadyExists(paper: Paper): Boolean {
        // URL 먼저 확인
        if (!paper.url.isNullOrBlank() && paperRepository.existsByUrl(paper.url)) return true

        // arXiv ID 확인
        val arxivId = extractArxivId(paper.url)
        if (!arxivId.isNullOrBlank() && paperRepository.existsByArxivId(arxivId)) return true

        return false
    }

    private fun extractArxivId(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val regex = Regex("arxiv\\.org/abs/([0-9.]+(?:v[0-9]+)?)")
        return regex.find(url)?.groupValues?.getOrNull(1)
    }
}

