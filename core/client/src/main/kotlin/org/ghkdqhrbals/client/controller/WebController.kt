package org.ghkdqhrbals.client.controller

import org.ghkdqhrbals.client.domain.paper.service.PaperRecommendationService
import org.ghkdqhrbals.client.domain.subscribe.service.SubscribeService
import org.ghkdqhrbals.client.domain.user.service.UserService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

@Controller
class WebController(
    private val userService: UserService,
    private val subscribeService: SubscribeService,
    private val paperRecommendationService: PaperRecommendationService
) {

    @GetMapping("/")
    fun index(): String {
        return "redirect:/login"
    }

    @GetMapping("/paper-search")
    fun paperSearch(): String {
        return "paper-search"
    }

    @GetMapping("/dashboard")
    fun dashboard(model: Model): String {
        // Mock data for demo
        model.addAttribute("totalPapers", 24)
        model.addAttribute("recentCount", 5)
        model.addAttribute("pendingSummaries", 8)

        // Mock recent papers
        val mockPapers = listOf(
            mapOf(
                "id" to 1,
                "title" to "Attention Is All You Need",
                "authors" to "Vaswani et al.",
                "arxivId" to "1706.03762",
                "summary" to "We propose a new simple network architecture, the Transformer, based solely on attention mechanisms...",
                "summaryReady" to true,
                "updatedAt" to "2024-01-15",
                "version" to 2
            ),
            mapOf(
                "id" to 2,
                "title" to "BERT: Pre-training of Deep Bidirectional Transformers",
                "authors" to "Devlin et al.",
                "arxivId" to "1810.04805",
                "summary" to "We introduce BERT, designed to pre-train deep bidirectional representations...",
                "summaryReady" to false,
                "updatedAt" to "2024-01-14",
                "version" to 1
            ),
            mapOf(
                "id" to 3,
                "title" to "GPT-4 Technical Report",
                "authors" to "OpenAI",
                "arxivId" to "2303.08774",
                "summary" to "We report the development of GPT-4, a large-scale multimodal model...",
                "summaryReady" to true,
                "updatedAt" to "2024-01-13",
                "version" to 3
            )
        )
        model.addAttribute("recentPapers", mockPapers)

        return "dashboard"
    }

    @GetMapping("/profile")
    fun userProfile(model: Model): String {
        // TODO: Security Context에서 현재 사용자 가져오기
        try {
            val user = userService.getUserById(1L)
            val activeSubscriptions = subscribeService.getUserActiveSubscriptions(1L)

            model.addAttribute("user", user)
            model.addAttribute("subscriptions", activeSubscriptions)
        } catch (e: Exception) {
            model.addAttribute("error", "사용자 정보를 불러올 수 없습니다.")
        }

        return "profile"
    }

    @GetMapping("/subscriptions")
    fun subscriptions(model: Model): String {
        try {
            val allSubscribes = subscribeService.getAllActiveSubscribes()
            val userSubscriptions = subscribeService.getUserActiveSubscriptions(1L) // TODO: 현재 사용자 ID

            model.addAttribute("allSubscribes", allSubscribes)
            model.addAttribute("userSubscriptions", userSubscriptions)
        } catch (e: Exception) {
            model.addAttribute("error", "구독 정보를 불러올 수 없습니다.")
        }

        return "subscriptions"
    }

    @GetMapping("/recommendations")
    fun recommendations(model: Model): String {
        try {
            val userId = 1L // TODO: 현재 로그인 사용자 ID
            val recommendedPapers = paperRecommendationService.getRecommendedPapersForUser(userId, 0.4)

            model.addAttribute("recommendedPapers", recommendedPapers)
            model.addAttribute("totalRecommendations", recommendedPapers.size)
        } catch (e: Exception) {
            model.addAttribute("error", "추천 논문을 불러올 수 없습니다.")
        }

        return "recommendations"
    }

    @GetMapping("/monitoring")
    fun monitoring(): String {
        return "monitoring"
    }

    @GetMapping("/monitoring2")
    fun monitoring2(): String {
        return "monitoring2"
    }

    @GetMapping("/monitoring3")
    fun monitoring3(): String {
        return "monitoring3"
    }
}
