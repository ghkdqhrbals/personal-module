package org.ghkdqhrbals.client.domain.paper.service

import org.ghkdqhrbals.infra.paper.PaperEntity
import org.ghkdqhrbals.infra.paper.PaperJdbcRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PaperService(
    private val paperJdbcRepository: PaperJdbcRepository
) {
    /**
     * 중복된 arxivId가 있는 논문은 제외하고 저장. 이후 요약이 필요한 논문 리스트 반환
     */
    @Transactional
    fun upsertPapersAndGetUnsummarized(papers: List<PaperEntity>): List<PaperEntity> {
        val foundPapers = paperJdbcRepository.findAllByArxivIdIn(papers.mapNotNull { it.arxivId })
        val map = foundPapers.associateBy { it.arxivId }
        val new = papers.filter { newPaper ->
            map[newPaper.arxivId] == null
        }

        // 신규 or 있는데 요약이 없는 애들
        val needToSummarize = papers.filter { newPaper -> map[newPaper.arxivId]?.summary.isNullOrEmpty() }
        paperJdbcRepository.saveAll(new)
        return needToSummarize
    }
}