package org.ghkdqhrbals.client.config.listener

import org.ghkdqhrbals.client.config.log.logger
import org.ghkdqhrbals.client.domain.stream.StreamService
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
    private val streamService: StreamService
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
        Thread.sleep(1000)

//        ackDel(
//            topic = message.stream!!,
//            group = RedisStreamConfiguration.CONSUMER_GROUP_NAME,
//            recordId = recordId,
//        )
//        ack(
//            topic = message.stream!!,
//            group = RedisStreamConfiguration.CONSUMER_GROUP_NAME,
//            recordId = recordId,
//        )
    }
}