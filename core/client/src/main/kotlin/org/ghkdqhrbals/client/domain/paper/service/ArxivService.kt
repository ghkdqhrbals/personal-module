package org.ghkdqhrbals.client.domain.paper.service

import org.ghkdqhrbals.client.config.log.logger
import org.ghkdqhrbals.client.controller.paper.dto.*
import org.ghkdqhrbals.repository.paper.PaperEntity
import org.ghkdqhrbals.repository.paper.PaperRepository
import org.ghkdqhrbals.model.paper.PaperSearchAndStoreEvent
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * arXiv 논문 검색 서비스 (단순화 버전)
 * - 검색 요청을 이벤트로 발행하고 즉시 eventId 반환
 * - 실제 검색/저장은 PaperSearchAndStoreStreamListener가 처리
 * - 진행상태는 Redis에서 직접 조회 (프로젝션 없이 단일 raw 데이터 사용)
 */
@Service
class ArxivService(
    private val paperRepository: PaperRepository,
    private val redisTemplate: StringRedisTemplate,
    private val arxivHttpClient: ArxivHttpClient,
) {
    fun existsById(arxivId: String): Boolean {
        return paperRepository.existsByArxivId(arxivId)
    }

    /**
     * 신규 논문 발견 시 저장하고 SummaryEvent 반환
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun analyze(event: PaperSearchAndStoreEvent): Map<ArxivPaper, PaperEntity?>? {
        val returnMaps = mutableMapOf<ArxivPaper, PaperEntity?>()

        val response = arxivHttpClient.search(event)

        // 1. 이번 응답에서 온 arxivId들만 수집
        val incomingIds = response.map { it.arxivId }
        if (incomingIds.isEmpty()) return null

        // 2. 그 arxivId들 중에서 이미 DB에 존재하는 애들만 한 번에 조회
        val papers = paperRepository.findAllByArxivIdIn(incomingIds)
        val existingIds: Set<String?> = papers.map { it.arxivId }.toSet()

        // 이미 있는 논문 넣기. 없으면 Null
        returnMaps.putAll(
            response.associateWith { paper ->
                papers.firstOrNull { it.arxivId == paper.arxivId }
            }
        )

        // returnMaps 에서 PaperEntity null 인 애들
        val newPapers = returnMaps.filterValues { it == null }

        if (newPapers.isEmpty()) {
            logger().info("신규 논문 없음. totalResponse=${incomingIds.size}")
            return null
        }

        logger().info("📄 신규 논문 ${newPapers.size}건 발견. totalResponse=${incomingIds.size}")

        val saves = paperRepository.saveAll(newPapers.keys.map { it.toPaperEntity() })

        returnMaps.replaceAll { k, v -> v ?: saves.firstOrNull { it.arxivId == k.arxivId } }
        return returnMaps
    }

    /**
     * 비동기 arXiv 검색 시작 - 이벤트 ID만 즉시 반환
     */
    @Transactional
    fun searchAsync(
        query: String,
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
            )
            // 여기서 이벤트 쏘면 됨.

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
