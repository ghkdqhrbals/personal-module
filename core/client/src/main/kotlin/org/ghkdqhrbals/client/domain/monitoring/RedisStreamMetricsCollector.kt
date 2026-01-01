package org.ghkdqhrbals.client.domain.monitoring

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.ghkdqhrbals.client.config.log.logger
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Redis Stream 메트릭을 주기적으로 수집하여 Prometheus에 노출
 */
@Component
class RedisStreamMetricsCollector(
    private val streamMonitoringService: RedisStreamMonitoringService,
    private val meterRegistry: MeterRegistry
) {

    private val monitoringStreams = listOf(
        "summary:1",
        "summary:2",
        "summary:3"
    )

    // 메트릭 값을 저장하는 맵
    private val streamLengthMap = ConcurrentHashMap<String, AtomicLong>()
    private val groupConsumersMap = ConcurrentHashMap<String, AtomicLong>()
    private val groupPendingMap = ConcurrentHashMap<String, AtomicLong>()
    private val groupLagMap = ConcurrentHashMap<String, AtomicLong>()
    private val consumerPendingMap = ConcurrentHashMap<String, AtomicLong>()
    private val consumerIdleMap = ConcurrentHashMap<String, AtomicLong>()

    /**
     * 1초마다 Redis Stream 메트릭 수집 (테스트용, 프로덕션은 10초 권장)
     */
    @Scheduled(fixedDelay = 10000, initialDelay = 5000)
    fun collectStreamMetrics() {
        try {
            monitoringStreams.forEach { streamKey ->
                collectMetricsForStream(streamKey)
            }
        } catch (e: Exception) {
            // nop
        }
    }

    private fun collectMetricsForStream(streamKey: String) {
        try {
            collectStreamInfo(streamKey)
            collectGroupInfo(streamKey)
        } catch (e: Exception) {
            logger().error("Error collecting metrics for stream {}: {}", streamKey, e.message)
        }
    }

    private fun collectStreamInfo(streamKey: String) {
        try {
            val streamInfo = streamMonitoringService.getStreamInfo(streamKey)
            if (streamInfo == null) {
                logger().warn("Stream info not found for: {}", streamKey)
                return
            }

            val length = streamInfo.length ?: 0L
            logger().debug("Stream {} length: {}", streamKey, length)

            // AtomicLong을 가져오거나 생성하고 Gauge 등록
            val lengthHolder = streamLengthMap.computeIfAbsent(streamKey) { key ->
                val holder = AtomicLong(0)
                Gauge.builder("redis_stream_length", holder) { it.get().toDouble() }
                    .tag("stream", key)
                    .description("Total number of messages in the stream")
                    .register(meterRegistry)
                holder
            }
            lengthHolder.set(length)

        } catch (e: Exception) {
            logger().error("Error collecting stream info for {}: {}", streamKey, e.message, e)
        }
    }

    private fun collectGroupInfo(streamKey: String) {
        try {
            val groups = streamMonitoringService.getStreamGroups(streamKey)
            if (groups == null) {
                logger().warn("Group info not found for: {}", streamKey)
                return
            }

            groups.forEach { group ->
                val groupKey = "$streamKey:${group.name}"
                logger().debug("Group {}: consumers={}, pending={}, lag={}",
                    groupKey, group.consumers, group.pending, group.lag)

                // Consumer 수
                val consumersHolder = groupConsumersMap.computeIfAbsent(groupKey) { key ->
                    val holder = AtomicLong(0)
                    Gauge.builder("redis_stream_group_consumers", holder) { it.get().toDouble() }
                        .tag("stream", streamKey)
                        .tag("group", group.name)
                        .description("Number of consumers in the group")
                        .register(meterRegistry)
                    holder
                }
                consumersHolder.set(group.consumers)

                // Pending 메시지 수
                val pendingHolder = groupPendingMap.computeIfAbsent(groupKey) { key ->
                    val holder = AtomicLong(0)
                    Gauge.builder("redis_stream_group_pending", holder) { it.get().toDouble() }
                        .tag("stream", streamKey)
                        .tag("group", group.name)
                        .description("Number of pending messages in the group")
                        .register(meterRegistry)
                    holder
                }
                pendingHolder.set(group.pending)

                // Lag (중요 메트릭!)
                val lagHolder = groupLagMap.computeIfAbsent(groupKey) { key ->
                    val holder = AtomicLong(0)
                    Gauge.builder("redis_stream_group_lag", holder) { it.get().toDouble() }
                        .tag("stream", streamKey)
                        .tag("group", group.name)
                        .description("Number of messages not yet delivered to the group")
                        .register(meterRegistry)
                    holder
                }
                lagHolder.set(group.lag)

                // 각 Consumer의 메트릭
                group.consumerInfo.forEach { consumer ->
                    val consumerKey = "$groupKey:${consumer.name}"

                    // Consumer pending
                    val consumerPendingHolder = consumerPendingMap.computeIfAbsent(consumerKey) { key ->
                        val holder = AtomicLong(0)
                        Gauge.builder("redis_stream_consumer_pending", holder) { it.get().toDouble() }
                            .tag("stream", streamKey)
                            .tag("group", group.name)
                            .tag("consumer", consumer.name)
                            .description("Number of pending messages for the consumer")
                            .register(meterRegistry)
                        holder
                    }
                    consumerPendingHolder.set(consumer.pending)

                    // Consumer idle time
                    val consumerIdleHolder = consumerIdleMap.computeIfAbsent(consumerKey) { key ->
                        val holder = AtomicLong(0)
                        Gauge.builder("redis_stream_consumer_idle_millis", holder) { it.get().toDouble() }
                            .tag("stream", streamKey)
                            .tag("group", group.name)
                            .tag("consumer", consumer.name)
                            .description("Consumer idle time in milliseconds")
                            .register(meterRegistry)
                        holder
                    }
                    consumerIdleHolder.set(consumer.idleTime.millis)
                }
            }

        } catch (e: Exception) {
            logger().error("Error collecting group info for {}: {}", streamKey, e.message, e)
        }
    }
}

