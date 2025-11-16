package org.ghkdqhrbals.client.paper

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.ghkdqhrbals.client.config.logger
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/papers/scholar")
@Tag(name = "Google Scholar", description = "Google Scholar (SerpAPI) 검색 API")
class ScholarController(
    private val scholarService: ScholarService
) {
    data class ScholarSearchRequest(
        val query: String,
        val maxResults: Int = 10,
        val summarize: Boolean? = true
    )

    @PostMapping("/search")
    @Operation(summary = "Google Scholar 검색", description = "SerpAPI로 논문 검색 후 초록/요약")
    fun search(@RequestBody req: ScholarSearchRequest): ResponseEntity<PaperSearchResponse> {
        logger().info("Scholar Search Request: $req")
        val res = scholarService.search(
            query = req.query,
            maxResults = req.maxResults,
            summarize = req.summarize ?: true
        )
        return ResponseEntity.ok(res)
    }
}
