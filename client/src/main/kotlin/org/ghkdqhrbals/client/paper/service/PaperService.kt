package org.ghkdqhrbals.client.paper.service

import org.ghkdqhrbals.client.paper.entity.PaperEntity
import org.ghkdqhrbals.client.paper.repository.PaperRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PaperService(
    private val paperRepository: PaperRepository
) {
    /**
     * 중복된 arxivId가 있는 논문은 제외하고 저장. 이후 요약이 필요한 논문 리스트 반환
     */
    @Transactional
    fun upsertPapersAndGetUnsummarized(papers: List<PaperEntity>): List<PaperEntity> {
        val foundPapers = paperRepository.findAllByArxivIdIn(papers.map { it.arxivId })
        val map = foundPapers.associateBy { it.arxivId }
        val new = papers.filter { newPaper ->
            map[newPaper.arxivId] == null
        }

        // 신규 or 있는데 요약이 없는 애들
        val needToSummarize = papers.filter { newPaper -> map[newPaper.arxivId]?.summary.isNullOrEmpty() }
        paperRepository.saveAll(new)
        return needToSummarize
    }
}