package org.ghkdqhrbals.client.domain.scheduler.job

import kotlinx.coroutines.runBlocking
import org.ghkdqhrbals.client.ai.LlmClient
import org.ghkdqhrbals.client.common.LockTimeoutException
import org.ghkdqhrbals.repository.paper.PaperRepository
import org.ghkdqhrbals.client.domain.paper.service.ArxivApiException
import org.ghkdqhrbals.client.domain.paper.service.ArxivHttpClient
import org.ghkdqhrbals.client.domain.paper.service.ArxivService
import org.ghkdqhrbals.repository.subscribe.Subscribe
import org.ghkdqhrbals.model.paper.PaperSearchAndStoreEvent
import org.ghkdqhrbals.repository.subscribe.SubscribeType
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import java.time.OffsetDateTime
import java.util.UUID

/**
 * 각 Subscribe에 대해 페이지네이션 기반으로 논문을 처리하는 청크 프로세서
 */
@Component
class SubscribePaperChunkProcessor(
    private val arxivService: ArxivService,
    private val httpClient: ArxivHttpClient,
    private val llmClient: LlmClient,
    private val paperRepository: PaperRepository,
) {
    private val logger = LoggerFactory.getLogger(SubscribePaperChunkProcessor::class.java)

    /**
     * Subscribe 하나에 대해 모든 페이지의 논문을 청크 단위로 처리
     *
     * @param subscribe 구독 정보
     * @param pageSize 페이지당 논문 수
     * @param maxConsecutiveFailures 연속 실패 허용 횟수
     * @return 총 처리된 논문 수
     */
    fun processAllPages(
        subscribe: Subscribe,
        pageSize: Int = 10,
        maxConsecutiveFailures: Int = 3
    ): Int {
        val subscribeInfo = "[Subscribe#${subscribe.id}] '${subscribe.name}' (${subscribe.subscribeType})"

        logger.info("╔═══════════════════════════════════════════════════════════════════")
        logger.info("║ 📚 구독 처리 시작")
        logger.info("║ ├─ 구독 ID: ${subscribe.id}")
        logger.info("║ ├─ 구독 이름: ${subscribe.name}")
        logger.info("║ ├─ 구독 타입: ${subscribe.subscribeType}")
        logger.info("║ └─ 페이지 크기: ${pageSize}개/페이지")
        logger.info("╚═══════════════════════════════════════════════════════════════════")

        var currentPage = 0
        var totalProcessed = 0
        var consecutiveFailures = 0
        var totalSkippedPages = 0

        try {
            while (true) {
                val pageInfo = "Page#${currentPage}"

                try {
                    logger.info("🔍 $subscribeInfo $pageInfo - ArXiv 검색 시작...")

                    // 현재 페이지의 논문 검색
                    val processedCount = processPage(subscribe, currentPage, pageSize)

                    // 더 이상 논문이 없으면 중단
                    if (processedCount == 0) {
                        logger.info("✓ $subscribeInfo $pageInfo - 논문 없음 (검색 종료)")
                        break
                    }

                    totalProcessed += processedCount
                    consecutiveFailures = 0 // 성공 시 카운터 리셋
                    currentPage++

                    logger.info("✅ $subscribeInfo $pageInfo - 성공: ${processedCount}개 논문 처리 | 누적: ${totalProcessed}개")

                } catch (e: LockTimeoutException) {
                    consecutiveFailures++
                    val failureInfo = "연속 실패: ${consecutiveFailures}/${maxConsecutiveFailures}"

                    logger.warn("⚠️  $subscribeInfo $pageInfo - Rate Limit 타임아웃 ($failureInfo)")
                    logger.warn("   └─ 사유: ${e.message}")

                    if (consecutiveFailures >= maxConsecutiveFailures) {
                        logger.error("❌ $subscribeInfo - 연속 ${consecutiveFailures}회 실패로 구독 처리 중단")
                        break
                    }

                    // Rate limit이므로 잠시 대기 후 재시도
                    val waitTime = 5000L * consecutiveFailures
                    logger.info("⏳ $subscribeInfo $pageInfo - ${waitTime}ms 대기 후 재시도...")
                    Thread.sleep(waitTime)
                    // 같은 페이지 재시도

                } catch (e: ArxivApiException) {
                    consecutiveFailures++
                    totalSkippedPages++
                    val failureInfo = "연속 실패: ${consecutiveFailures}/${maxConsecutiveFailures}"

                    logger.error("⚠️  $subscribeInfo $pageInfo - ArXiv API 에러 ($failureInfo)")
                    logger.error("   └─ 사유: ${e.message}")

                    if (consecutiveFailures >= maxConsecutiveFailures) {
                        logger.error("❌ $subscribeInfo - 연속 ${consecutiveFailures}회 실패로 구독 처리 중단")
                        break
                    }

                    logger.info("⏭️  $subscribeInfo $pageInfo - 페이지 스킵 후 다음 페이지로 이동")
                    currentPage++

                } catch (e: Exception) {
                    consecutiveFailures++
                    totalSkippedPages++
                    val failureInfo = "연속 실패: ${consecutiveFailures}/${maxConsecutiveFailures}"

                    logger.error("⚠️  $subscribeInfo $pageInfo - 예기치 않은 오류 ($failureInfo)", e)

                    if (consecutiveFailures >= maxConsecutiveFailures) {
                        logger.error("❌ $subscribeInfo - 연속 ${consecutiveFailures}회 실패로 구독 처리 중단")
                        break
                    }

                    logger.info("⏭️  $subscribeInfo $pageInfo - 페이지 스킵 후 다음 페이지로 이동")
                    currentPage++
                }
            }

            logger.info("╔═══════════════════════════════════════════════════════════════════")
            logger.info("║ 🎯 구독 처리 완료")
            logger.info("║ ├─ 구독: ${subscribe.name} (${subscribe.subscribeType})")
            logger.info("║ ├─ 처리된 논문: ${totalProcessed}개")
            logger.info("║ ├─ 처리된 페이지: ${currentPage}개")
            logger.info("║ ├─ 스킵된 페이지: ${totalSkippedPages}개")
            logger.info("║ └─ 최종 실패 횟수: ${consecutiveFailures}회")
            logger.info("╚═══════════════════════════════════════════════════════════════════")

        } catch (e: Exception) {
            logger.error("╔═══════════════════════════════════════════════════════════════════")
            logger.error("║ ❌ 구독 처리 중 치명적 오류")
            logger.error("║ ├─ 구독: ${subscribe.name}")
            logger.error("║ └─ 오류: ${e.message}")
            logger.error("╚═══════════════════════════════════════════════════════════════════", e)
            throw e
        }

        return totalProcessed
    }

    /**
     * 특정 페이지의 논문을 처리
     *
     * @return 처리된 논문 수
     */
    private fun processPage(subscribe: Subscribe, page: Int, pageSize: Int): Int {
        val pageRequest = PageRequest.of(page, pageSize)
        val event = subscribe.toPaperSearchAndStoreEvent(pageRequest)

        // ArXiv에서 논문 검색 및 신규논문 저장
        val papers = arxivService.analyze(event)?: return 0
        val map = papers.keys.map { it.toSummaryEvent() }

        logger.debug("   └─ 검색 결과: ${papers.size}개 논문 발견")

        // 직접 요약 실행
        runBlocking {
            map.forEach { event ->
                val analysis = llmClient.summarizePaper(
                    event.abstract ?: "",
                    150,
                    event.journalRefRaw
                )

                // 이걸로 paperRepository 업데이트.
                val paper = paperRepository.findByArxivId(event.arxivId!!)
                val updated = paper!!.copy(
                    summary = analysis.coreContribution,
                    novelty = analysis.noveltyAgainstPreviousWorks,
                    summarizedAt = OffsetDateTime.now(),
                    journal = analysis.journalName ?: paper.journal,
                    impactFactor = analysis.impactFactor ?: paper.impactFactor
                )
                paperRepository.save(updated)
            }

        }

        return papers.size
    }
}

/**
 * Subscribe를 PaperSearchAndStoreEvent로 변환하는 확장 함수
 */
private fun Subscribe.toPaperSearchAndStoreEvent(page: PageRequest): PaperSearchAndStoreEvent {
    // Subscribe 타입에 따라 다른 쿼리 생성
    val query = when (this.subscribeType) {
        SubscribeType.CATEGORY -> {
            "cat:${this.name}"
        }
        SubscribeType.KEYWORD -> {
            "all:${this.name}"
        }
        SubscribeType.AUTHOR -> {
            "au:${this.name}"
        }
        else -> {
            this.name
        }
    }

    return PaperSearchAndStoreEvent(
        searchEventId = UUID.randomUUID().toString(),
        query = query,
        categories = null,
        maxResults = page.pageSize,
        page = page.pageNumber,
        fromDate = null
    )
}

