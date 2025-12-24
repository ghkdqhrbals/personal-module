package org.ghkdqhrbals.message.event

import org.ghkdqhrbals.message.redis.RedisStreamSender
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.core.StringRedisTemplate

@Configuration
class MessageQueueConfiguration(
    private val redisStringTemplate: StringRedisTemplate
) {
    @Bean
    fun eventSender() = RedisStreamSender(redisStringTemplate)
}