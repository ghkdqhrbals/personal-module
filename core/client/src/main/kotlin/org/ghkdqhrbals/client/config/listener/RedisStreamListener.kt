package org.ghkdqhrbals.client.config.listener

import kotlinx.coroutines.runBlocking
import org.ghkdqhrbals.client.ai.LlmClient
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
    private val streamService: StreamService,
    private val llmClient: LlmClient
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
        // 1~1000 사이의 랜덤한 숫자 생성
//        val randomNum = (1..1000).random()
//        Thread.sleep(randomNum.toLong())
        // 1초 ~ 10초 사이 sleep 하도록
        runBlocking {
            val summary = llmClient.summarizePaper(
                abstract = message.value,
                maxLength = 150
            )

            logger().info("Generated summary: $summary")
        }

//        ackDel(
//            topic = message.stream!!,
//            group = RedisStreamConfiguration.CONSUMER_GROUP_NAME,
//            recordId = recordId,
//        )
        streamService.ack(
            key = message.stream!!,
            group = RedisStreamConfiguration.CONSUMER_GROUP_NAME,
            recordId = recordId,
        )
    }
}