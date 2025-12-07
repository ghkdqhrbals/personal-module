package org.ghkdqhrbals.client.controller.paper.dto

import org.springframework.stereotype.Component

@Component
class PaperResponseMapper {

    /**
     * Paper 도메인 모델을 PaperResponse DTO로 변환
     */
    fun toResponse(
        paper: Paper,
        impactFactorYear: Int? = null,
        analysis: PaperAnalysisResponse? = null
    ): PaperResponse {
        return PaperResponse(
            title = paper.title,
            authors = paper.authors,
            journal = paper.journal,
            publicationDate = paper.publicationDate,
            doi = paper.doi,
            abstract = paper.abstract,
            url = paper.url,
            citations = paper.citations,
            impactFactor = paper.impactFactor,
            impactFactorYear = impactFactorYear,
            summary = paper.summary,
            analysis = analysis
        )
    }

    /**
     * PaperSearchResponse 도메인 모델을 PaperSearchResponseDTO로 변환
     */
    fun toResponseDTO(searchResponse: PaperSearchResponse): PaperSearchResponseDTO {
        val pagination = if (searchResponse.page != null && searchResponse.size != null) {
            PaginationInfo(
                page = searchResponse.page,
                size = searchResponse.size,
                totalResults = searchResponse.totalResults,
                totalPages = searchResponse.totalResults?.let { total ->
                    (total + searchResponse.size - 1) / searchResponse.size
                }
            )
        } else null

        return PaperSearchResponseDTO(
            papers = searchResponse.papers.map { toResponse(it) },
            count = searchResponse.count,
            source = searchResponse.source,
            pagination = pagination
        )
    }

    /**
     * Journal 도메인 모델을 JournalResponse DTO로 변환
     */
    fun toJournalResponse(journal: Journal): JournalResponse {
        return JournalResponse(
            name = journal.name,
            impactFactor = journal.impactFactor,
            impactFactorYear = journal.year,
            issn = journal.issn,
            publisher = journal.publisher,
            category = journal.category,
            metricSource = journal.metricSource
        )
    }

    /**
     * 여러 Paper를 PaperResponse 리스트로 변환
     */
    fun toResponseList(papers: List<Paper>): List<PaperResponse> {
        return papers.map { toResponse(it) }
    }

    /**
     * JSON 문자열을 PaperAnalysisResponse로 파싱
     * 예: {"core_contribution":"...", "novelty_against_previous_works":"..."}
     */
    fun parseAnalysisJson(json: String): PaperAnalysisResponse? {
        return try {
            val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
            val node = mapper.readTree(json)

            PaperAnalysisResponse(
                coreContribution = node.get("core_contribution")?.asText() ?: "",
                noveltyAgainstPreviousWorks = node.get("novelty_against_previous_works")?.asText() ?: "",
                methodology = node.get("methodology")?.asText(),
                keyFindings = node.get("key_findings")?.map { it.asText() },
                limitations = node.get("limitations")?.asText(),
                futureWork = node.get("future_work")?.asText()
            )
        } catch (e: Exception) {
            null
        }
    }
}

