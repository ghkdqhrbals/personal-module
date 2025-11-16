package org.ghkdqhrbals.client.paper.dto

import com.fasterxml.jackson.annotation.JsonInclude


interface ResponseModel

/**
 * 논문 분석 응답 DTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class PaperAnalysisResponse(
    val coreContribution: String = "",
    val noveltyAgainstPreviousWorks: String = "",
    val journalName: String? = null,
    val year: Int? = null,
    val impactFactor: Double? = null,
    val impactFactorYear: Int? = null,
    val methodology: String? = null,
    val keyFindings: List<String>? = null,
    val limitations: String? = null,
    val futureWork: String? = null
) : ResponseModel

/**
 * 논문 응답 DTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class PaperResponse(
    val title: String,
    val authors: List<String>,
    val journal: String?,
    val publicationDate: String?,
    val doi: String?,
    val abstract: String?,
    val url: String?,
    val citations: Int?,
    val impactFactor: Double?,
    val impactFactorYear: Int?,
    val summary: String?,
    val analysis: PaperAnalysisResponse? = null
) : ResponseModel

/**
 * 논문 검색 응답 DTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class PaperSearchResponseDTO(
    val papers: List<PaperResponse>,
    val count: Int,
    val source: String,
    val pagination: PaginationInfo?
) : ResponseModel

/**
 * 페이지네이션 정보
 */
data class PaginationInfo(
    val page: Int,
    val size: Int,
    val totalResults: Int?,
    val totalPages: Int?
)

/**
 * 저널 정보 응답 DTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class JournalResponse(
    val name: String,
    val impactFactor: Double?,
    val impactFactorYear: Int?,
    val issn: String?,
    val publisher: String?,
    val category: String?,
    val metricSource: String?
) : ResponseModel

/**
 * 에러 응답 DTO
 */
data class ErrorResponse(
    val message: String,
    val code: String?,
    val timestamp: String = java.time.LocalDateTime.now().toString()
) : ResponseModel
