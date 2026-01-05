package org.ghkdqhrbals.client.domain.monitoring

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.ghkdqhrbals.client.config.listener.RedisStreamConfiguration
import org.ghkdqhrbals.client.config.log.logger
import org.ghkdqhrbals.client.domain.stream.SummaryStreamConfig
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

    private val monitoringStreams = SummaryStreamConfig.getAllStreamKeys()

    // 메트릭 값을 저장하는 맵
    private val streamLengthMap = ConcurrentHashMap<String, AtomicLong>()
    private val groupConsumersMap = ConcurrentHashMap<String, AtomicLong>()
    private val groupPendingMap = ConcurrentHashMap<String, AtomicLong>()
    private val groupLagMap = ConcurrentHashMap<String, AtomicLong>()
    private val consumerPendingMap = ConcurrentHashMap<String, AtomicLong>()
    private val consumerIdleMap = ConcurrentHashMap<String, AtomicLong>()

    // 메모리 관련 메트릭 맵
    private val streamMemoryMap = ConcurrentHashMap<String, AtomicLong>()
    private val streamMaxMemoryMap = ConcurrentHashMap<String, AtomicLong>()
    private val streamMemoryPercentageMap = ConcurrentHashMap<String, Double>()

    init {
        logger().info("Initialized RedisStreamMetricsCollector for streams: $monitoringStreams")
    }
    /**
     * 1초마다 Redis Stream 메트릭 수집 (테스트용, 프로덕션은 10초 권장)
     */
    @Scheduled(fixedDelay = 1000, initialDelay = 5000)
    fun collectStreamMetrics() {
        try {
            logger().info("Collecting Redis Stream metrics...")
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
            collectMemoryUsage(streamKey)
        } catch (e: Exception) {
            logger().error("Error collecting metrics for stream {}: {}", streamKey, e.message)
        }
    }

    private fun collectMemoryUsage(streamKey: String) {
        try {
            val nodeMemoryInfo = streamMonitoringService.infoByKeyNode(streamKey) ?: return

            logger().debug("Stream {} node memory info: nodeId={}, host={}, port={}, maxMemory={}, usedMemory={}, percentage={}",
                streamKey, nodeMemoryInfo.nodeId, nodeMemoryInfo.host, nodeMemoryInfo.port,
                nodeMemoryInfo.maxMemory, nodeMemoryInfo.usedMemory, nodeMemoryInfo.memoryUsagePercentage)

            val nodeLabel = "${nodeMemoryInfo.host}:${nodeMemoryInfo.port}"

            // Stream이 저장된 노드 정보 (info 메트릭)
            Gauge.builder("redis_stream_node", 1) { 1.0 }
                .tag("stream", streamKey)
                .tag("node_id", nodeMemoryInfo.nodeId)
                .tag("host", nodeMemoryInfo.host)
                .tag("port", nodeMemoryInfo.port.toString())
                .description("Redis Stream node location information")
                .register(meterRegistry)

            // Stream의 노드 사용 메모리 (바이트)
            val streamMemoryHolder = streamMemoryMap.computeIfAbsent(streamKey) { key ->
                val holder = AtomicLong(0)
                Gauge.builder("redis_node_used_memory_bytes", holder) { it.get().toDouble() }
                    .tag("stream", key)
                    .tag("node_id", nodeMemoryInfo.nodeId)
                    .tag("node_address", nodeLabel)
                    .description("Used memory of the Redis node hosting this stream in bytes")
                    .register(meterRegistry)
                holder
            }
            streamMemoryHolder.set(nodeMemoryInfo.usedMemory)

            // Redis 노드의 최대 메모리 설정
            val maxMemoryHolder = streamMaxMemoryMap.computeIfAbsent(streamKey) { key ->
                val holder = AtomicLong(0)
                Gauge.builder("redis_node_max_memory_bytes", holder) { it.get().toDouble() }
                    .tag("stream", key)
                    .tag("node_id", nodeMemoryInfo.nodeId)
                    .tag("node_address", nodeLabel)
                    .description("Max memory configuration of the Redis node")
                    .register(meterRegistry)
                holder
            }
            maxMemoryHolder.set(nodeMemoryInfo.maxMemory)

            // 메모리 사용률 (%) - Gauge 직접 등록
            streamMemoryPercentageMap.computeIfAbsent(streamKey) { key ->
                Gauge.builder("redis_node_memory_usage_percentage") { nodeMemoryInfo.memoryUsagePercentage }
                    .tag("stream", key)
                    .tag("node_id", nodeMemoryInfo.nodeId)
                    .tag("node_address", nodeLabel)
                    .description("Memory usage percentage of the Redis node")
                    .register(meterRegistry)
                nodeMemoryInfo.memoryUsagePercentage
            }


            // Stream 바이트 크기 메트릭 추가
            Gauge.builder("redis_stream_bytes", nodeMemoryInfo) { nodeMemoryInfo.streamBytes.toDouble() }
                .tag("stream", streamKey)
                .tag("node_id", nodeMemoryInfo.nodeId)
                .tag("node_address", nodeLabel)
                .description("Serialized length of the Redis Stream in bytes")
                .register(meterRegistry)
        } catch (e: Exception) {
            logger().error("Error collecting memory usage for {}: {}", streamKey, e.message, e)
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

