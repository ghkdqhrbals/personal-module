package org.ghkdqhrbals.client.controller.paper

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.ghkdqhrbals.client.ai.LlmClient
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/papers")
@Tag(name = "Academic Papers", description = "학술 논문 검색 API")
class PaperController(
    private val llmClient: LlmClient
) {

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
    suspend fun summarizePaper(
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

