package org.ghkdqhrbals.message.redis

import org.ghkdqhrbals.message.event.EventPublisher
import org.springframework.data.redis.core.StringRedisTemplate

class RedisStreamPublisher(
    private val redisTemplate: StringRedisTemplate,
) : EventPublisher {
    override fun <T : Any> send(topic: String, event: T): String {
        val id = redisTemplate.add(topic, event)
        return id.toString()
    }
}