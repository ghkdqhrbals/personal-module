package org.ghkdqhrbals.client.controller.paper

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.ghkdqhrbals.client.ai.LlmClient
import org.ghkdqhrbals.client.domain.paper.service.ArxivService
import org.ghkdqhrbals.client.domain.stream.StreamConfigManager
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
    private val manager: StreamConfigManager,
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

    @PostMapping("/keywords/partition")
    @Operation(
        summary = "논문 키워드 전체 검색 partition",
        description = "AI를 사용하여 논문 초록에서 주요 키워드를 추출합니다"
    )
    fun summarizeKeywordsPartition(
        @RequestParam keyword: String,
        @RequestParam page: Int,
    ): String {
        val search = arxivService.search(keyword, maxResults = 100, page = page)
        val eventId = UUID.randomUUID().toString()
        search.forEach() {
            streamService.send(
                config = manager.cachedSummaryConfig,
                partitionKey = it.arxivId,
                payload = it
            )
        }
        return eventId
    }

    @PostMapping("/keywords/range")
    @Operation(
        summary = "논문 키워드 전체 검색 (page range)",
        description = "page 범위를 start~end(포함)로 받아 여러 페이지를 합쳐 검색합니다"
    )
    fun summarizeKeywordsRange(
        @RequestParam keyword: String,
        @RequestParam start: Int,
        @RequestParam end: Int,
        @RequestParam(required = false, defaultValue = "100") maxResults: Int,
    ): String {
        require(start >= 0) { "start는 0 이상이어야 합니다." }
        require(end >= start) { "end는 start 이상이어야 합니다." }

        // 안전장치: 한 번에 너무 많은 페이지를 긁지 않도록 제한
        val maxPages = 20
        require((end - start + 1) <= maxPages) { "한 번에 조회 가능한 page 범위는 최대 ${maxPages}개입니다." }

        val eventId = UUID.randomUUID().toString()

        (start..end).forEach { page ->
            val search = arxivService.search(keyword, maxResults = maxResults, page = page)
            search.forEach {
                streamService.send(
                    topic = "summary:0",
                    payload = it.toSummaryEvent(eventId)
                )
            }
        }

        return eventId
    }

    @PostMapping("/keywords/partition/range")
    @Operation(
        summary = "논문 키워드 전체 검색 partition (page range)",
        description = "page 범위를 start~end(포함)로 받아 여러 페이지를 합쳐 검색하고, arxivId로 파티션 라우팅하여 전송합니다"
    )
    fun summarizeKeywordsPartitionRange(
        @RequestParam keyword: String,
        @RequestParam start: Int,
        @RequestParam end: Int,
        @RequestParam(required = false, defaultValue = "100") maxResults: Int,
    ): String {
//        require(start >= 0) { "start는 0 이상이어야 합니다." }
//        require(end >= start) { "end는 start 이상이어야 합니다." }

        val maxPages = 20
//        require((end - start + 1) <= maxPages) { "한 번에 조회 가능한 page 범위는 최대 ${maxPages}개입니다." }

        val eventId = UUID.randomUUID().toString()
        val config = manager.cachedSummaryConfig

        (start..end).forEach { page ->
            val search = arxivService.search(keyword, maxResults = maxResults, page = page)
            search.forEach {
                streamService.send(
                    config = config,
                    partitionKey = it.arxivId,
                    payload = it.toSummaryEvent(eventId)
                )
            }
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

