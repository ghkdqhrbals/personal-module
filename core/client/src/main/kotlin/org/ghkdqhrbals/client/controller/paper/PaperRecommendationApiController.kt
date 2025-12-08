package org.ghkdqhrbals.client.controller.paper

import org.ghkdqhrbals.repository.paper.PaperSubscribe
import org.ghkdqhrbals.client.domain.paper.service.PaperRecommendationService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/papers")
class PaperRecommendationApiController(
    private val paperRecommendationService: PaperRecommendationService
) {

    /**
     * 사용자 맞춤 추천 논문 조회
     */
    @GetMapping("/recommendations/user/{userId}")
    fun getRecommendedPapers(
        @PathVariable userId: Long,
        @RequestParam(defaultValue = "0.5") minScore: Double
    ): ResponseEntity<List<PaperRecommendationResponse>> {
        val recommendations = paperRecommendationService.getRecommendedPapersForUser(userId, minScore)
        return ResponseEntity.ok(recommendations.map { PaperRecommendationResponse.from(it) })
    }

    /**
     * 특정 구독 주제의 관련 논문 조회
     */
    @GetMapping("/subscribe/{subscribeId}")
    fun getPapersBySubscribe(
        @PathVariable subscribeId: Long,
        @RequestParam(defaultValue = "0.5") minScore: Double
    ): ResponseEntity<List<PaperRecommendationResponse>> {
        val papers = paperRecommendationService.getPapersBySubscribe(subscribeId, minScore)
        return ResponseEntity.ok(papers.map { PaperRecommendationResponse.from(it) })
    }

    /**
     * 특정 논문의 관련 구독 주제 조회
     */
    @GetMapping("/{paperId}/subscribes")
    fun getSubscribesForPaper(@PathVariable paperId: Long): ResponseEntity<List<PaperSubscribeInfoResponse>> {
        val subscribes = paperRecommendationService.getSubscribesForPaper(paperId)
        return ResponseEntity.ok(subscribes.map { PaperSubscribeInfoResponse.from(it) })
    }

    /**
     * 논문 자동 매칭
     */
    @PostMapping("/{paperId}/auto-match")
    fun autoMatchPaper(@PathVariable paperId: Long): ResponseEntity<Map<String, String>> {
        paperRecommendationService.autoMatchPaperWithAllSubscribes(paperId)
        return ResponseEntity.ok(mapOf("message" to "논문이 모든 구독 주제와 매칭되었습니다."))
    }
}

data class PaperRecommendationResponse(
    val paperSubscribeId: Long,
    val paperId: Long,
    val arxivId: String?,
    val title: String?,
    val author: String?,
    val summary: String?,
    val url: String?,
    val subscribeName: String,
    val subscribeType: String,
    val matchScore: Double,
    val matchReason: String?,
    val matchedAt: String,
    val relevanceLevel: String
) {
    companion object {
        fun from(ps: PaperSubscribe): PaperRecommendationResponse {
            return PaperRecommendationResponse(
                paperSubscribeId = ps.id,
                paperId = ps.paper.id ?: 0L,
                arxivId = ps.paper.arxivId,
                title = ps.paper.title,
                author = ps.paper.author,
                summary = ps.paper.summary,
                url = ps.paper.url,
                subscribeName = ps.subscribe.name,
                subscribeType = ps.subscribe.subscribeType.name,
                matchScore = ps.matchScore,
                matchReason = ps.matchReason,
                matchedAt = ps.matchedAt.toString(),
                relevanceLevel = when {
                    ps.matchScore >= 0.7 -> "HIGH"
                    ps.matchScore >= 0.4 -> "MEDIUM"
                    else -> "LOW"
                }
            )
        }
    }
}

data class PaperSubscribeInfoResponse(
    val subscribeId: Long,
    val subscribeName: String,
    val subscribeType: String,
    val matchScore: Double,
    val matchReason: String?
) {
    companion object {
        fun from(ps: PaperSubscribe): PaperSubscribeInfoResponse {
            return PaperSubscribeInfoResponse(
                subscribeId = ps.subscribe.id,
                subscribeName = ps.subscribe.name,
                subscribeType = ps.subscribe.subscribeType.name,
                matchScore = ps.matchScore,
                matchReason = ps.matchReason
            )
        }
    }
}

