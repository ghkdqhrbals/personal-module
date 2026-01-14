package org.ghkdqhrbals.client.domain.stream

import com.fasterxml.jackson.databind.ObjectMapper
import org.ghkdqhrbals.client.config.log.logger
import org.ghkdqhrbals.message.redis.SummaryConfig
import org.ghkdqhrbals.model.config.Jackson
import org.ghkdqhrbals.model.config.toJson
import org.springframework.data.redis.connection.Message
import org.springframework.data.redis.connection.MessageListener
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.listener.PatternTopic
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicLong

@Component
class StreamConfigManager(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
    private val redisMessageListenerContainer: RedisMessageListenerContainer
) : MessageListener {
    // SummaryConfig 싱글톤 캐시 - lazy로 처음 접근할 때 초기화
    val cachedSummaryConfig: SummaryConfig by lazy {
        load()
    }

    companion object {
        const val DEFAULT_MAX_LEN = 10000L
        const val SUMMARY_CONFIG_REDIS_KEY = "summary:config"
        const val SUMMARY_CONFIG_CHANNEL = "summary:config:update"
        const val UPDATE_TYPE = "BUS_REFRESH"
    }

    private val maxLen = AtomicLong(DEFAULT_MAX_LEN)

    init {
        load()
        // Redis Pub/Sub 구독
        redisMessageListenerContainer.addMessageListener(
            this,
            PatternTopic(SUMMARY_CONFIG_CHANNEL)
        )
        logger().info("StreamConfigManager initialized with MAX_LEN={}", maxLen.get())
    }

    fun update(maxLen: Long): SummaryConfig {
        val config = load()
        config.maxLen = maxLen
        // publish redis
        val json = Jackson.getMapper().writeValueAsString(config)
        redisTemplate.opsForValue().set(SUMMARY_CONFIG_REDIS_KEY, json)
        redisTemplate.convertAndSend(SUMMARY_CONFIG_CHANNEL, json)
        return config!!
    }

    private fun load(): SummaryConfig {
        val savedValue = redisTemplate.opsForValue().get(SUMMARY_CONFIG_REDIS_KEY)
        return savedValue?.let {
            val config = Jackson.getMapper().readValue(it, SummaryConfig::class.java)
            config
        } ?: let {
            logger().error("No config")
            val default = SummaryConfig.default()

            redisTemplate.opsForValue().set(SUMMARY_CONFIG_REDIS_KEY, default.toJson())
            default
        }

    }

    /**
     * Redis Pub/Sub 메시지 수신 - SummaryConfig 변경 감지
     */
    override fun onMessage(message: Message, pattern: ByteArray?) {
        try {
            val channel = String(message.channel)
            if (channel == SUMMARY_CONFIG_CHANNEL) {
                logger().debug("Received SummaryConfig update via Pub/Sub")
                this.load()
            }
        } catch (e: Exception) {
            logger().error("Error processing Pub/Sub message: {}", e.message, e)
        }
    }
}







