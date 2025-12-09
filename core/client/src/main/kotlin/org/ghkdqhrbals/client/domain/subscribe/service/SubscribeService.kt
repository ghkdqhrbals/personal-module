package org.ghkdqhrbals.client.domain.subscribe.service

import org.ghkdqhrbals.infra.subscribe.Subscribe
import org.ghkdqhrbals.infra.subscribe.SubscribeType
import org.ghkdqhrbals.infra.subscribe.UserSubscribe
import org.ghkdqhrbals.infra.subscribe.SubscribeRepository
import org.ghkdqhrbals.infra.subscribe.UserSubscribeRepository
import org.ghkdqhrbals.infra.user.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 주제를 추가하는 서비스
 *
 * 주제 추가하기전에 관련된 주제가 이미 존재하는지 확인하는 로직 필요.
 */
@Service
@Transactional
class SubscribeService(
    private val subscribeRepository: SubscribeRepository,
    private val userSubscribeRepository: UserSubscribeRepository,
    private val userRepository: UserRepository
) {
    /**
     * 새로운 구독 주제 추가
     * 이미 존재하는 이름의 주제가 있다면 예외 발생
     */
    fun addSubscription(name: String, description: String? = null, type: SubscribeType = SubscribeType.KEYWORD): Subscribe {
        // 이미 존재하는지 확인
        if (subscribeRepository.existsByName(name)) {
            throw IllegalArgumentException("구독 주제 '$name'는 이미 존재합니다.")
        }

        val subscribe = Subscribe(
            name = name,
            description = description,
            subscribeType = type
        )

        return subscribeRepository.save(subscribe)
    }

    /**
     * 사용자가 특정 주제를 구독
     */
    fun subscribeToTopic(userId: Long, subscribeId: Long, priority: Int = 3): UserSubscribe {
        val user = userRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("사용자를 찾을 수 없습니다. ID: $userId") }

        val subscribe = subscribeRepository.findById(subscribeId)
            .orElseThrow { IllegalArgumentException("구독 주제를 찾을 수 없습니다. ID: $subscribeId") }

        // 이미 구독중인지 확인
        val existing = userSubscribeRepository.findByUserAndSubscribeId(user, subscribeId)
        if (existing != null) {
            // 이전에 구독 취소했다면 재구독
            if (!existing.isActive()) {
                existing.resubscribe()
                return userSubscribeRepository.save(existing)
            }
            throw IllegalArgumentException("이미 구독중인 주제입니다.")
        }

        val userSubscribe = UserSubscribe(
            user = user,
            subscribe = subscribe,
            priority = priority
        )

        return userSubscribeRepository.save(userSubscribe)
    }

    /**
     * 사용자의 구독 취소
     */
    fun unsubscribeFromTopic(userId: Long, subscribeId: Long) {
        val user = userRepository.findById(userId)
            .orElseThrow { IllegalArgumentException("사용자를 찾을 수 없습니다. ID: $userId") }

        val userSubscribe = userSubscribeRepository.findByUserAndSubscribeId(user, subscribeId)
            ?: throw IllegalArgumentException("구독 정보를 찾을 수 없습니다.")

        userSubscribe.unsubscribe()
        userSubscribeRepository.save(userSubscribe)
    }

    /**
     * 사용자의 활성 구독 목록 조회
     */
    @Transactional(readOnly = true)
    fun getUserActiveSubscriptions(userId: Long): List<UserSubscribe> {
        return userSubscribeRepository.findActiveSubscriptionsByUserId(userId)
    }

    /**
     * 모든 활성 구독 주제 조회
     */
    @Transactional(readOnly = true)
    fun getAllActiveSubscribes(): List<Subscribe> {
        return subscribeRepository.findAllByActivatedIsTrue()
    }

    /**
     * 특정 ��입의 구독 주제 조회
     */
    @Transactional(readOnly = true)
    fun getSubscribesByType(type: SubscribeType): List<Subscribe> {
        return subscribeRepository.findBySubscribeType(type)
    }
}

