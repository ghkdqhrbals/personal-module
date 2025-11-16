package org.ghkdqhrbals.client.paper

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.ghkdqhrbals.client.ai.LlmClient
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/papers")
@Tag(name = "Academic Papers", description = "학술 논문 검색 API")
class PaperController(
    private val paperSearchService: PaperSearchService,
    private val llmClient: LlmClient
) {

    @PostMapping("/search")
    @Operation(
        summary = "카테고리별 논문 검색",
        description = "특정 카테고리에서 Impact Factor가 높은 저널의 최신 논문을 검색합니다"
    )
    fun searchPapers(
        @RequestBody request: PaperSearchRequest
    ): ResponseEntity<PaperSearchResponse> {
        val response = paperSearchService.searchPapers(request)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/journal/{journalName}")
    @Operation(
        summary = "저널별 논문 검색",
        description = "특정 저널의 최신 논문을 검색합니다"
    )
    fun searchByJournal(
        @PathVariable journalName: String,
        @RequestParam(defaultValue = "10") maxResults: Int
    ): ResponseEntity<List<Paper>> {
        val papers = paperSearchService.searchPapersByJournal(journalName, maxResults)
        return ResponseEntity.ok(papers)
    }

    @GetMapping("/journals/{category}")
    @Operation(
        summary = "카테고리별 주요 저널 조회",
        description = "특정 카테고리의 Impact Factor가 높은 저널 목록을 조회합니다"
    )
    fun getTopJournals(
        @PathVariable category: String,
        @RequestParam(required = false) minImpactFactor: Double?
    ): ResponseEntity<List<Journal>> {
        val journals = paperSearchService.getTopJournals(category, minImpactFactor)
        return ResponseEntity.ok(journals)
    }

    @GetMapping("/categories")
    @Operation(
        summary = "지원 카테고리 목록",
        description = "검색 가능한 학술 카테고리 목록을 조회합니다"
    )
    fun getSupportedCategories(): ResponseEntity<List<String>> {
        val categories = listOf(
            "Computer Science",
            "Medicine",
            "Physics",
            "Biology"
        )
        return ResponseEntity.ok(categories)
    }

    @PostMapping("/summarize")
    @Operation(
        summary = "논문 초록 요약",
        description = "AI를 사용하여 논문 초록을 간결하게 요약합니다"
    )
    fun summarizePaper(
        @RequestBody request: SummarizeRequest
    ): ResponseEntity<SummarizeResponse> {
        val summary = llmClient.summarizePaper(request.abstract, request.maxLength ?: 150)
        return ResponseEntity.ok(SummarizeResponse(summary.coreContribution))
    }
}

data class SummarizeRequest(
    val abstract: String,
    val maxLength: Int? = 150
)

data class SummarizeResponse(
    val summary: String
)

