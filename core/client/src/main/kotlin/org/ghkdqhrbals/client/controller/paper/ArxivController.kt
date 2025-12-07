package org.ghkdqhrbals.client.controller.paper

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.ghkdqhrbals.client.config.log.logger
import org.ghkdqhrbals.client.domain.paper.service.ArxivService
import org.ghkdqhrbals.client.controller.paper.dto.ArxivSearchStatusResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/papers/arxiv")
@Tag(name = "arXiv", description = "arXiv 논문 검색 API")
class ArxivController(
    private val arxivService: ArxivService
) {
    data class ArxivSearchRequest(
        val query: String,
        val categories: List<String>? = null,
        val maxResults: Int = 10,
        val page: Int = 0,
        val fromDate: String? = null,
        val summarize: Boolean? = true
    )

    data class ArxivSearchResponse(
        val eventId: String,
        val message: String = "Search initiated. Use GET /api/papers/arxiv/search/{eventId}/status to check progress."
    )

    @PostMapping("/search")
    @Operation(
        summary = "arXiv 비동기 검색 시작",
        description = "arXiv 논문 검색을 시작하고 이벤트 ID를 즉시 반환. 상태 조회는 GET /api/papers/arxiv/search/{eventId}/status 사용"
    )
    fun search(@RequestBody req: ArxivSearchRequest): ResponseEntity<ArxivSearchResponse> {
        logger().info("arXiv Search Request: $req (page=${req.page}, size=${req.maxResults})")
        val eventId = arxivService.searchAsync(
            query = req.query,
            categories = req.categories,
            maxResults = req.maxResults,
            page = req.page,
            fromDate = req.fromDate,
            summarize = req.summarize ?: true
        )
        return ResponseEntity.accepted().body(
            ArxivSearchResponse(eventId = eventId)
        )
    }

    @GetMapping("/search/{eventId}/status")
    @Operation(
        summary = "arXiv 검색 상태 조회",
        description = "이벤트 ID로 검색 진행 상태 및 결과 조회"
    )
    fun getSearchStatus(@PathVariable eventId: String): ResponseEntity<ArxivSearchStatusResponse> {
        val status = arxivService.getSearchStatus(eventId)
        return ResponseEntity.ok(status)
    }
}
