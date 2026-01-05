package org.ghkdqhrbals.client.config.listener

import org.ghkdqhrbals.client.config.log.logger
import org.ghkdqhrbals.client.domain.stream.SummaryStreamConfig
import org.ghkdqhrbals.message.redis.ConditionalOnRedisStreamEnabled
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.stream.Consumer
import org.springframework.data.redis.connection.stream.ObjectRecord
import org.springframework.data.redis.connection.stream.ReadOffset
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.core.ScanOptions
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.stream.StreamMessageListenerContainer
import java.net.NetworkInterface
import java.time.Duration
import java.util.*
import kotlin.concurrent.thread

@Configuration
@ConditionalOnRedisStreamEnabled
class RedisStreamConfiguration(
    private val redisStreamListener: RedisStreamListener,
    private val redisTemplate: StringRedisTemplate,
) {
    @Volatile
    private var container: StreamMessageListenerContainer<String, ObjectRecord<String, String>>? = null
    private val myNodeId: String = PodContext.id
    companion object {
        const val STREAM_KEY_PREFIX = SummaryStreamConfig.STREAM_KEY_PREFIX
        const val MAX_PARTITIONS = 100L
        const val POLL_BATCH_SIZE: Int = 1
        const val CONSUMER_GROUP_NAME = SummaryStreamConfig.CONSUMER_GROUP_NAME
    }

    init { initListener() }

    private fun initListener() {
        // find stream keys with prefix summary:
        val keyPartitions = redisTemplate.scan(ScanOptions.scanOptions().match("$STREAM_KEY_PREFIX*").count(MAX_PARTITIONS).build()).stream().toList()
        logger().info("발견된 샤딩 파티션 키: $keyPartitions")

        // 각 stream에 대해 consumer group 생성
        keyPartitions.forEach { streamKey ->
            try {
                redisTemplate.opsForStream<String, String>()
                    .createGroup(streamKey, CONSUMER_GROUP_NAME)
                logger().info("Consumer group '$CONSUMER_GROUP_NAME' created for stream '$streamKey'")
            } catch (e: Exception) {
                // 이미 존재하는 경우 무시
                if (e.message?.contains("BUSYGROUP") == true) {
                    logger().debug("Consumer group '$CONSUMER_GROUP_NAME' already exists for stream '$streamKey'")
                } else {
                    logger().warn("Failed to create consumer group for stream '$streamKey': ${e.message}")
                }
            }
        }
        val options = StreamMessageListenerContainer.StreamMessageListenerContainerOptions.builder()
            .targetType(String::class.java)
            .pollTimeout(Duration.ofMillis(1000))
            .batchSize(POLL_BATCH_SIZE)
            .errorHandler { t ->
                logger().error("Stream listener error", t)
                scheduleReconnect()
            }
            .build()

        val listenerContainer = StreamMessageListenerContainer.create(redisTemplate.connectionFactory, options)
        container = listenerContainer

        keyPartitions.forEach { streamKey ->
            logger().info("Registering listener for stream '$streamKey' with consumer ID '$myNodeId'")
            listenerContainer.receive(
                Consumer.from(CONSUMER_GROUP_NAME, PodContext.id),
                StreamOffset.create(streamKey, ReadOffset.from(">")),
                redisStreamListener,
            )
        }
        listenerContainer.start()
    }
    private fun scheduleReconnect() {
        thread(isDaemon = true) {
            Thread.sleep(5000)
            container?.stop()
            container = null
            initListener()
        }
    }
}



object PodContext {
    private fun getIpPrefix(): String {
        return try {
            val ip = NetworkInterface.getNetworkInterfaces()
                .toList()
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { !it.isLoopbackAddress && it.hostAddress.indexOf(':') == -1 }
                ?.hostAddress
                ?: "unknown-ip"
            ip
        } catch (e: Exception) {
            "unknown-ip"
        }
    }

    val id: String = "${getIpPrefix()}-${UUID.randomUUID()}"
}
