package org.ghkdqhrbals.client.config.listener

import org.ghkdqhrbals.client.config.log.logger
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.stream.Consumer
import org.springframework.data.redis.connection.stream.ReadOffset
import org.springframework.data.redis.connection.stream.StreamOffset
import org.springframework.data.redis.core.ScanOptions
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.stream.StreamMessageListenerContainer
import java.net.NetworkInterface
import java.time.Duration
import java.util.*

@Configuration
class RedisStreamConfiguration(
    private val redisStreamListener: RedisStreamListener,
    private val redisTemplate: StringRedisTemplate,
) {
    private val myNodeId: String = PodContext.id

    init {
        initListener()
        logger().info("[NOTIFICATION STREAM] Notification event now listening")
    }

    private fun initListener() {

        // find stream keys with prefix summary:
        val keyPartitions = redisTemplate.scan(ScanOptions.scanOptions().match("summary:*").count(100).build()).stream().toList()


        val listenerContainer = StreamMessageListenerContainer.create(
            redisTemplate.connectionFactory,
            StreamMessageListenerContainer.StreamMessageListenerContainerOptions.builder()
                .targetType(String::class.java)
                .pollTimeout(Duration.ofMillis(streamConfig.pollTimeoutMs))
                .build(),
        )
        listenerContainer.receive(
            Consumer.from(streamConfig.consumerGroupName, PodContext.id),
            StreamOffset.create(streamConfig.streamKey, ReadOffset.lastConsumed()),
            listener,
        )

        listenerContainer.start()
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
