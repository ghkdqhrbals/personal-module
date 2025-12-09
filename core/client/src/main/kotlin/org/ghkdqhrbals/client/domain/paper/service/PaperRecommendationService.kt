package org.ghkdqhrbals.client.domain.paper.service

import org.ghkdqhrbals.infra.paper.PaperEntity
import org.ghkdqhrbals.infra.paper.PaperSubscribe
import org.ghkdqhrbals.infra.paper.PaperJdbcRepository
import org.ghkdqhrbals.infra.paper.PaperSubscribeRepository
import org.ghkdqhrbals.infra.subscribe.Subscribe
import org.ghkdqhrbals.infra.subscribe.SubscribeRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class PaperRecommendationService(
    private val paperJdbcRepository: PaperJdbcRepository,
    private val paperSubscribeRepository: PaperSubscribeRepository,
    private val subscribeRepository: SubscribeRepository
) {

    /**
     * 논문과 구독 주제 간의 매칭을 생성
     * AI 분석 결과를 바탕으로 관련도 점수를 계산하여 저장
     */
    fun matchPaperWithSubscribes(paperId: Long, subscribeIds: List<Long>, matchScores: Map<Long, Double>? = null) {
        val paper = paperJdbcRepository.findById(paperId)
            .orElseThrow { IllegalArgumentException("논문을 찾을 수 없습니다. ID: $paperId") }

        subscribeIds.forEach { subscribeId ->
            val subscribe = subscribeRepository.findById(subscribeId)
                .orElseThrow { IllegalArgumentException("구독 주제를 찾을 수 없습니다. ID: $subscribeId") }

            val matchScore = matchScores?.get(subscribeId) ?: calculateMatchScore(paper, subscribe)

            val paperSubscribe = PaperSubscribe(
                paper = paper,
                subscribe = subscribe,
                matchScore = matchScore,
                matchReason = generateMatchReason(paper, subscribe, matchScore)
            )

            paperSubscribeRepository.save(paperSubscribe)
        }
    }

    /**
     * 사용자의 구독 기반 추천 논문 조회
     */
    @Transactional(readOnly = true)
    fun getRecommendedPapersForUser(userId: Long, minScore: Double = 0.5): List<PaperSubscribe> {
        return paperSubscribeRepository.findRecommendedPapersForUser(userId, minScore)
    }

    /**
     * 특정 구독 주제에 대한 관련 논문 조회
     */
    @Transactional(readOnly = true)
    fun getPapersBySubscribe(subscribeId: Long, minScore: Double = 0.5): List<PaperSubscribe> {
        return paperSubscribeRepository.findHighRelevancePapersBySubscribeId(subscribeId, minScore)
    }

    /**
     * 논문의 관련 구독 주제 조회
     */
    @Transactional(readOnly = true)
    fun getSubscribesForPaper(paperId: Long): List<PaperSubscribe> {
        return paperSubscribeRepository.findByPaperId(paperId)
    }

    /**
     * 매칭 점수 계산 (간단한 키워드 기반)
     * TODO: 실제로는 AI 모델을 사용하여 더 정교한 점수 계산
     */
    private fun calculateMatchScore(paper: PaperEntity, subscribe: Subscribe): Double {
        val title = paper.title?.lowercase() ?: ""
        val summary = paper.summary?.lowercase() ?: ""
        val subscribeName = subscribe.name.lowercase()

        var score = 0.0

        // 제목에서 매칭
        if (title.contains(subscribeName)) {
            score += 0.5
        }

        // 요약에서 매칭
        if (summary.contains(subscribeName)) {
            score += 0.3
        }

        // 저자 매칭 (AUTHOR 타입인 경우)
        if (subscribe.subscribeType.name == "AUTHOR") {
            val authors = paper.author?.lowercase() ?: ""
            if (authors.contains(subscribeName)) {
                score += 0.7
            }
        }

        return score.coerceIn(0.0, 1.0)
    }

    /**
     * 매칭 이유 생성
     */
    private fun generateMatchReason(paper: PaperEntity, subscribe: Subscribe, score: Double): String {
        return when {
            score >= 0.7 -> "높은 관련도: 제목 또는 요약에서 '${subscribe.name}' 키워드가 발견됨"
            score >= 0.4 -> "중간 관련도: 내용 일부에서 '${subscribe.name}' 관련 내용 포함"
            else -> "낮은 관련도: '${subscribe.name}'과 부분적으로 관련됨"
        }
    }

    /**
     * 자동 매칭: 새로운 논문에 대해 모든 활성 구독 주제와 매칭 시도
     */
    fun autoMatchPaperWithAllSubscribes(paperId: Long) {
        val activeSubscribes = subscribeRepository.findAllByActivatedIsTrue()

        val paper = paperJdbcRepository.findById(paperId)
            .orElseThrow { IllegalArgumentException("논문을 찾을 수 없습니다. ID: $paperId") }

        activeSubscribes.forEach { subscribe ->
            val matchScore = calculateMatchScore(paper, subscribe)

            // 최소 관련도 0.3 이상일 때만 저장
            if (matchScore >= 0.3) {
                val paperSubscribe = PaperSubscribe(
                    paper = paper,
                    subscribe = subscribe,
                    matchScore = matchScore,
                    matchReason = generateMatchReason(paper, subscribe, matchScore)
                )
                paperSubscribeRepository.save(paperSubscribe)
            }
        }
    }
}

