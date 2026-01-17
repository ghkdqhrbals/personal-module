package org.ghkdqhrbals.client.controller.paper

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.ghkdqhrbals.client.ai.LlmClient
import org.ghkdqhrbals.client.domain.paper.service.ArxivService
import org.ghkdqhrbals.client.domain.stream.StreamService
import org.ghkdqhrbals.message.event.EventPublisher
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/api/papers")
@Tag(name = "Academic Papers", description = "학술 논문 검색 API")
class PaperController(
    private val llmClient: LlmClient,
    private val publisher: EventPublisher,
    private val arxivService: ArxivService,
    private val streamService: StreamService,
) {
    @PostMapping("/summarize")
    @Operation(
        summary = "논문 초록 요약",
        description = "AI를 사용하여 논문 초록을 간결하게 요약합니다"
    )
    suspend fun summarizePaper(
        @RequestBody request: SummarizeRequest,
    ): ResponseEntity<SummarizeResponse> {
        val summary = llmClient.summarizePaper(request.abstract, request.maxLength ?: 150)
        return ResponseEntity.ok(SummarizeResponse(summary.coreContribution))
    }

    @PostMapping("/keywords")
    @Operation(
        summary = "논문 키워드 전체 검색",
        description = "AI를 사용하여 논문 초록에서 주요 키워드를 추출합니다"
    )
    fun summarizeKeywords(
        @RequestParam keyword: String,
        @RequestParam page: Int,
    ): String {
        val search = arxivService.search(keyword, maxResults = 100, page = page)
        val eventId = UUID.randomUUID().toString()
        search.forEach() {
            streamService.send(
                topic = "summary:0",
                payload = it.toSummaryEvent(eventId)
            )
        }

        return eventId
    }
}

data class SummarizeRequest(
    val abstract: String,
    val maxLength: Int? = 150
)

data class SummarizeResponse(
    val summary: String
)

