package org.ghkdqhrbals.client.paper

import org.ghkdqhrbals.client.config.logger
import org.ghkdqhrbals.client.eventsourcing.domain.PaperSearchAndStoreEvent
import org.ghkdqhrbals.client.eventsourcing.publisher.EventPublisher
import org.ghkdqhrbals.client.paper.repository.PaperRepository
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * arXiv 논문 검색 서비스 (단순화 버전)
 * - 검색 요청을 이벤트로 발행하고 즉시 eventId 반환
 * - 실제 검색/저장은 PaperSearchAndStoreStreamListener가 처리
 * - 진행상태는 Redis에서 직접 조회 (프로젝션 없이 단일 raw 데이터 사용)
 */
@Service
class ArxivService(
    private val eventPublisher: EventPublisher,
    private val paperRepository: PaperRepository,
    private val redisTemplate: StringRedisTemplate
) {
    /**
     * 비동기 arXiv 검색 시작 - 이벤트 ID만 즉시 반환
     */
    fun searchAsync(
        query: String? = null,
        categories: List<String>? = null,
        maxResults: Int = 10,
        page: Int = 0,
        fromDate: String? = null,
        summarize: Boolean = true
    ): String {
        val searchEventId = UUID.randomUUID().toString()

        try {
            // PaperSearchAndStoreEvent 발행
            val event = PaperSearchAndStoreEvent(
                searchEventId = searchEventId,
                query = query,
                categories = categories,
                maxResults = maxResults,
                page = page,
                fromDate = fromDate,
                shouldSummarize = summarize,
                ver = 1,
                meta = mapOf(
                    "requestedAt" to System.currentTimeMillis()
                )
            )

            eventPublisher.publish(event)

            logger().info(
                "[ArxivService] Published PaperSearchAndStoreEvent: " +
                "searchEventId=$searchEventId, query=$query, categories=$categories, summarize=$summarize"
            )
        } catch (e: Exception) {
            logger().error("Failed to publish PaperSearchAndStoreEvent for searchEventId=$searchEventId", e)

            // 실패 상태를 Redis에 저장
            redisTemplate.opsForHash<String, String>().putAll(
                "search:$searchEventId:progress",
                mapOf(
                    "status" to "FAILED",
                    "error" to (e.message ?: "Unknown error")
                )
            )
            redisTemplate.expire("search:$searchEventId:progress", 3600, java.util.concurrent.TimeUnit.SECONDS)
        }

        return searchEventId
    }

    /**
     * 검색 상태 조회
     * Redis에서 단일 raw 데이터를 직접 조회 (프로젝션 없이 효율적)
     */
    fun getSearchStatus(searchEventId: String): ArxivSearchStatusResponse {
        val progressKey = "search:$searchEventId:progress"
        val progressEntries = redisTemplate.opsForHash<String, String>().entries(progressKey)

        if (progressEntries.isEmpty()) {
            return ArxivSearchStatusResponse(
                eventId = searchEventId,
                status = SearchStatus.NOT_FOUND,
                batch = null,
                summary = null,
                papers = null
            )
        }

        val status = progressEntries["status"] ?: "PENDING"
        val total = progressEntries["total"]?.toIntOrNull() ?: 0
        val completed = progressEntries["completed"]?.toIntOrNull() ?: 0
        val failed = progressEntries["failed"]?.toIntOrNull() ?: 0
        val processing = (total - completed - failed).coerceAtLeast(0)
        val progressPercent = if (total > 0) {
            (completed + failed).toDouble() / total.toDouble() * 100.0
        } else {
            0.0
        }
        val isDone = status == "COMPLETED" || status == "FAILED" || (total > 0 && (completed + failed) >= total)

        val searchStatus = when (status) {
            "COMPLETED" -> SearchStatus.COMPLETED
            "FAILED" -> SearchStatus.FAILED
            "IN_PROGRESS" -> SearchStatus.IN_PROGRESS
            else -> SearchStatus.PENDING
        }

        // 논문 목록 조회 (최근 100개로 임시)
        val papers = if (total > 0) {
            paperRepository.findTop100ByOrderBySearchDateDesc().map { entity ->
                Paper(
                    title = entity.title,
                    authors = entity.author?.split(",")?.map { it.trim() } ?: emptyList(),
                    journal = entity.journal,
                    publicationDate = entity.publishedAt?.toString(),
                    doi = null,
                    abstract = null,
                    url = entity.url,
                    citations = null,
                    impactFactor = entity.impactFactor,
                    summary = entity.summary,
                    novelty = entity.novelty
                )
            }
        } else {
            null
        }

        logger().info(
            "[ArxivService] Search status for searchEventId=$searchEventId: " +
            "status=$status, total=$total, completed=$completed, failed=$failed"
        )

        return ArxivSearchStatusResponse(
            eventId = searchEventId,
            status = searchStatus,
            batch = BatchInfo(
                totalPapers = total,
                category = null,
                startedAt = null
            ),
            summary = SummaryInfo(
                total = total,
                completed = completed,
                failed = failed,
                processing = processing,
                progressPercent = String.format("%.2f", progressPercent).toDouble(),
                isDone = isDone
            ),
            papers = papers
        )
    }
}
