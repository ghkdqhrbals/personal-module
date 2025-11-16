package org.ghkdqhrbals.client.paper

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.ghkdqhrbals.client.config.logger
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/papers/arxiv")
@Tag(name = "arXiv", description = "arXiv 논문 검색 API")
class ArxivController(
    private val arxivService: ArxivService
) {
    data class ArxivSearchRequest(
        val query: String? = null,
        val categories: List<String>? = null,
        val maxResults: Int = 10,
        val page: Int = 0,
        val fromDate: String? = null,
        val summarize: Boolean? = true
    )

    @PostMapping("/search")
    @Operation(summary = "arXiv 검색 (페이지네이션)", description = "arXiv에서 최신 논문 검색. page=0부터 시작, maxResults는 페이지 크기")
    fun search(@RequestBody req: ArxivSearchRequest): ResponseEntity<PaperSearchResponse> {
        logger().info("arXiv Search Request: $req (page=${req.page}, size=${req.maxResults})")
        val res = arxivService.search(
            query = req.query,
            categories = req.categories,
            maxResults = req.maxResults,
            page = req.page,
            fromDate = req.fromDate,
            summarize = req.summarize ?: true
        )
        return ResponseEntity.ok(res)
    }
}
