package org.ghkdqhrbals.repository.subscribe

import org.ghkdqhrbals.repository.user.UserEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface UserSubscribeRepository : JpaRepository<UserSubscribe, Long> {
    fun findByUserId(userId: Long): List<UserSubscribe>

    @Query("SELECT us FROM UserSubscribe us WHERE us.user.id = :userId AND us.unsubscribedAt IS NULL")
    fun findActiveSubscriptionsByUserId(userId: Long): List<UserSubscribe>

    fun findByUserAndSubscribeId(user: UserEntity, subscribeId: Long): UserSubscribe?

    fun existsByUserIdAndSubscribeId(userId: Long, subscribeId: Long): Boolean
}

