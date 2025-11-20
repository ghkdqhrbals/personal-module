package org.ghkdqhrbals.client.domain.paper.dto

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * 논문 검색 요청
 */
data class PaperSearchRequest(
    val category: String, // e.g., "Computer Science", "Medicine", "Physics"
    val minImpactFactor: Double? = null,
    val maxResults: Int = 10,
    val fromDate: String? = null, // YYYY-MM-DD format
    val journals: List<String>? = null, // 특정 저널 이름 리스트
    val summarize: Boolean? = true // 요약 생성 여부
)

/**
 * 논문 정보
 */
data class Paper(
    val title: String?,
    val authors: List<String>,
    val journal: String?,
    val publicationDate: String?,
    val doi: String?,
    val abstract: String?,
    val url: String?,
    val citations: Int? = null,
    val impactFactor: Double? = null,
    val summary: String? = null,
    val novelty: String? = null,
    val journalRefRaw: String? = null  // LLM 전달용
)

/**
 * 논문 검색 응답
 */
data class PaperSearchResponse(
    val papers: List<Paper>,
    val count: Int,
    val source: String, // "arXiv", "CrossRef", "Google Scholar" 등
    val page: Int? = null,  // 현재 페이지 번호
    val size: Int? = null,  // 페이지 크기
    val totalResults: Int? = null,  // 전체 결과 수 (arXiv opensearch:totalResults)
    val eventId: String? = null // 요약 비동기 이벤트 ID
)

/**
 * arXiv 검색 상태 응답
 */
data class ArxivSearchStatusResponse(
    val eventId: String,
    val status: SearchStatus,
    val batch: BatchInfo?,
    val summary: SummaryInfo?,
    val papers: List<Paper>?
)

enum class SearchStatus {
    PENDING,        // 검색 대기 중
    IN_PROGRESS,    // 검색/요약 진행 중
    COMPLETED,      // 완료
    FAILED,         // 실패
    NOT_FOUND       // 이벤트 없음
}

data class BatchInfo(
    val totalPapers: Int,
    val category: String?,
    val startedAt: String?
)

data class SummaryInfo(
    val total: Int,
    val completed: Int,
    val failed: Int,
    val processing: Int,
    val progressPercent: Double,
    val isDone: Boolean
)

/**
 * 저널 정보
 */
data class Journal(
    val name: String,
    val impactFactor: Double?,
    val issn: String?,
    val publisher: String?,
    val category: String?,
    val year: Int? = null,
    val metricSource: String? = null
)

/**
 * Crossref API 응답 모델
 */
data class CrossrefResponse(
    val status: String,
    val message: CrossrefMessage
)

data class CrossrefMessage(
    @JsonProperty("total-results")
    val totalResults: Int,
    val items: List<CrossrefItem>
)

data class CrossrefItem(
    val title: List<String>?,
    val author: List<CrossrefAuthor>?,
    @JsonProperty("container-title")
    val containerTitle: List<String>?,
    @JsonProperty("published-print")
    val publishedPrint: CrossrefDate?,
    @JsonProperty("published-online")
    val publishedOnline: CrossrefDate?,
    val DOI: String?,
    val abstract: String?,
    val URL: String?,
    @JsonProperty("is-referenced-by-count")
    val citationCount: Int?
)

data class CrossrefAuthor(
    val given: String?,
    val family: String?
)

data class CrossrefDate(
    @JsonProperty("date-parts")
    val dateParts: List<List<Int>>?
)

/**
 * Semantic Scholar API 응답 모델
 */
data class SemanticScholarResponse(
    val total: Int,
    val data: List<SemanticScholarPaper>
)

data class SemanticScholarPaper(
    val paperId: String?,
    val title: String?,
    val authors: List<SemanticScholarAuthor>?,
    val venue: String?,
    val year: Int?,
    val citationCount: Int?,
    val url: String?,
    @JsonProperty("abstract")
    val paperAbstract: String?,
    val externalIds: Map<String, String>?
)

data class SemanticScholarAuthor(
    val authorId: String?,
    val name: String?
)
