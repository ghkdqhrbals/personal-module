package org.ghkdqhrbals.client.config.listener

import org.ghkdqhrbals.client.config.log.logger
import org.springframework.context.annotation.Profile
import org.springframework.data.redis.connection.stream.ObjectRecord
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.stream.StreamListener
import org.springframework.stereotype.Component

@Component
@Profile("!test")
class  RedisStreamListener(
    private val redisTemplate: StringRedisTemplate,
) : StreamListener<String, ObjectRecord<String, String>> {

    private val streamConfig = RedisStreamConfiguration.MSG_STREAM

    override fun onMessage(message: ObjectRecord<String, String>) {
        logger().info("[NOTIFICATION STREAM] Receive ${message.id} -> $message")
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

        ack(recordId)
        delete(recordId)
    }

    private fun ack(recordId: String) {
        redisTemplate.opsForStream<String, String>().acknowledge(streamConfig.streamKey, streamConfig.consumerGroupName, recordId)
    }

    private fun delete(recordId: String) {
        redisTemplate.opsForStream<String, String>().delete(RedisStreamConfiguration.MSG_STREAM.streamKey, recordId)
    }
}

