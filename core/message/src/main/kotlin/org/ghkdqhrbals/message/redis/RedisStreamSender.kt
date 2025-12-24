package org.ghkdqhrbals.message.redis

import org.ghkdqhrbals.message.event.EventSender
import org.springframework.data.redis.core.StringRedisTemplate

class RedisStreamSender(
    private val redisTemplate: StringRedisTemplate,
) : EventSender {
    override fun <T : Any> send(topic: String, event: T): String {
        val id = redisTemplate.add(topic, event)
        return id.toString()
    }
}