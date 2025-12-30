package org.ghkdqhrbals.client.config.listener

import org.ghkdqhrbals.client.config.log.logger
import org.ghkdqhrbals.message.redis.ConditionalOnRedisStreamEnabled
import org.springframework.context.annotation.Profile
import org.springframework.data.redis.connection.stream.ObjectRecord
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.stream.StreamListener
import org.springframework.stereotype.Component

@Component
@Profile("!test")
@ConditionalOnRedisStreamEnabled
class  RedisStreamListener(
    private val redisTemplate: StringRedisTemplate,
) : StreamListener<String, ObjectRecord<String, String>> {

    override fun onMessage(message: ObjectRecord<String, String>) {
        logger().info("[STREAM] Receive ${message.id} -> $message")
        val recordId = message.id.value

        try {
            handle(recordId, message)
        } catch (e: Exception) {
            logger().error("Error processing message with recordId: $recordId", e)
        }
    }

    fun handle(
        recordId: String,
        message: ObjectRecord<String, String>,
    ) {
        logger().info("처리할 메시지 ID: $recordId, 내용: $message")

//        ackDel(
//            topic = message.stream!!,
//            group = RedisStreamConfiguration.CONSUMER_GROUP_NAME,
//            recordId = recordId,
//        )
    }

    private fun ackDel(topic: String, group: String, recordId: String) {
        ack(topic, group, recordId)
        delete(topic, recordId)
    }

    private fun ack(topic: String, group: String, recordId: String) {
        redisTemplate.opsForStream<String, String>().acknowledge(topic, group, recordId)
    }

    private fun delete(topic: String, recordId: String) {
        redisTemplate.opsForStream<String, String>().delete(topic, recordId)
    }
}