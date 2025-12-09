package org.ghkdqhrbals.client.domain.subscribe.api

import org.ghkdqhrbals.infra.subscribe.SubscribeType
import org.ghkdqhrbals.infra.subscribe.UserSubscribe
import org.ghkdqhrbals.client.domain.subscribe.service.SubscribeService
import org.ghkdqhrbals.infra.subscribe.Subscribe
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/subscriptions")
class SubscribeApiController(
    private val subscribeService: SubscribeService
) {
    /**
     * 활성 구독 주제 전체 목록 조회
     */
    @GetMapping
    fun getAllActiveSubscribes(): ResponseEntity<List<SubscribeResponse>> {
        val subscribes = subscribeService.getAllActiveSubscribes()
        return ResponseEntity.ok(subscribes.map { SubscribeResponse.from(it) })
    }

    /**
     * 특정 타입의 구독 주제 목록 조회
     */
    @GetMapping("/type/{type}")
    fun getSubscribesByType(@PathVariable type: SubscribeType): ResponseEntity<List<SubscribeResponse>> {
        val subscribes = subscribeService.getSubscribesByType(type)
        return ResponseEntity.ok(subscribes.map { SubscribeResponse.from(it) })
    }

    /**
     * 사용자의 활성 구독 목록 조회
     */
    @GetMapping("/user/{userId}")
    fun getUserSubscriptions(@PathVariable userId: Long): ResponseEntity<List<UserSubscribeResponse>> {
        val userSubscribes = subscribeService.getUserActiveSubscriptions(userId)
        return ResponseEntity.ok(userSubscribes.map { UserSubscribeResponse.from(it) })
    }

    /**
     * 구독 추가
     */
    @PostMapping("/user/{userId}/subscribe/{subscribeId}")
    fun subscribeToTopic(
        @PathVariable userId: Long,
        @PathVariable subscribeId: Long,
        @RequestParam(defaultValue = "3") priority: Int
    ): ResponseEntity<UserSubscribeResponse> {
        val userSubscribe = subscribeService.subscribeToTopic(userId, subscribeId, priority)
        return ResponseEntity.ok(UserSubscribeResponse.from(userSubscribe))
    }

    /**
     * 구독 취소
     */
    @DeleteMapping("/user/{userId}/subscribe/{subscribeId}")
    fun unsubscribeFromTopic(
        @PathVariable userId: Long,
        @PathVariable subscribeId: Long
    ): ResponseEntity<Unit> {
        subscribeService.unsubscribeFromTopic(userId, subscribeId)
        return ResponseEntity.noContent().build()
    }

    /**
     * 새로운 구독 주제 생성 (관리자용)
     */
    @PostMapping
    fun createSubscription(
        @RequestBody request: CreateSubscribeRequest
    ): ResponseEntity<SubscribeResponse> {
        val subscribe = subscribeService.addSubscription(
            name = request.name,
            description = request.description,
            type = request.type
        )
        return ResponseEntity.ok(SubscribeResponse.from(subscribe))
    }
}

data class SubscribeResponse(
    val id: Long,
    val name: String,
    val description: String?,
    val type: SubscribeType,
    val isActive: Boolean
) {
    companion object {
        fun from(subscribe: Subscribe): SubscribeResponse {
            return SubscribeResponse(
                id = subscribe.id,
                name = subscribe.name,
                description = subscribe.description,
                type = subscribe.subscribeType,
                isActive = subscribe.activated
            )
        }
    }
}

data class UserSubscribeResponse(
    val id: Long,
    val subscribeName: String,
    val subscribeType: SubscribeType,
    val priority: Int,
    val notificationEnabled: Boolean,
    val subscribedAt: String
) {
    companion object {
        fun from(userSubscribe: UserSubscribe): UserSubscribeResponse {
            return UserSubscribeResponse(
                id = userSubscribe.id,
                subscribeName = userSubscribe.subscribe.name,
                subscribeType = userSubscribe.subscribe.subscribeType,
                priority = userSubscribe.priority,
                notificationEnabled = userSubscribe.notificationEnabled,
                subscribedAt = userSubscribe.subscribedAt.toString()
            )
        }
    }
}

data class CreateSubscribeRequest(
    val name: String,
    val description: String?,
    val type: SubscribeType
)

