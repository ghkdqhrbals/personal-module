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

    /**
     * 이미 요약이 완료된 논문인지 확인
     */
    fun alreadySummarized(arxivId: String?): Boolean {
        if (arxivId.isNullOrBlank()) return false

        val paper = paperRepository.findByArxivId(arxivId).orElse(null) ?: return false

        // summary 필드가 비어있지 않으면 이미 요약 완료
        return !paper.summary.isNullOrBlank()
    }

    private fun extractArxivId(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val regex = Regex("arxiv\\.org/abs/([0-9.]+(?:v[0-9]+)?)")
        return regex.find(url)?.groupValues?.getOrNull(1)
    }
}

