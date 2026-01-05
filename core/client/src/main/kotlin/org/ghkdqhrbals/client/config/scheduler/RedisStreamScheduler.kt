package org.ghkdqhrbals.client.config.scheduler

import org.ghkdqhrbals.client.config.listener.PodContext
import org.ghkdqhrbals.client.config.log.logger
import org.ghkdqhrbals.client.domain.stream.StreamService
import org.ghkdqhrbals.client.domain.stream.SummaryStreamConfig
import org.ghkdqhrbals.model.config.Jackson
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Redis Stream 스케줄러
 * 1. Pending 메시지 자동 재처리
 * 2. Idle 상태 active consumer 제거
 */
@Component
class RedisStreamScheduler(
    private val redisTemplate: StringRedisTemplate,
    private val streamService: StreamService,

    ) {
    companion object {
        const val IDLE_CONSUMER_THRESHOLD_MS = 5 * 60 * 1000L  // 5분
        const val RETRY_INTERVAL_MS = SummaryStreamConfig.RETRY_INTERVAL_MS
        const val MAX_RETRY_COUNT = SummaryStreamConfig.MAX_RETRY_COUNT
        const val CDL_RETRY_COUNT = SummaryStreamConfig.CDL_RETRY_COUNT
        const val CDL_RETRY_INTERVAL_MS = SummaryStreamConfig.CDL_RETRY_INTERVAL_MS

    }

    private val monitoringStreams = SummaryStreamConfig.getDataStreamKey()
    private val allStream = SummaryStreamConfig.getAllStreamKeys()

    /**
     * Pending 메시지 자동 재처리 스케줄러
     * - 주기: 30초마다
     * - 기능: 각 stream의 모든 consumer group에서 pending된 메시지를 자동으로 재처리
     * - XAUTOCLAIM을 사용하여 idle 상태인 consumer의 메시지를 다른 consumer로 이동
     */
    @Scheduled(fixedDelay = 1000, initialDelay = 1000)
    fun reprocessPendingMessages() {
        try {
            logger().debug("Starting pending message reprocessing scheduler")
            monitoringStreams.forEach { streamKey ->
                reprocessStreamPendingMessages(streamKey)
            }
        } catch (e: Exception) {
            logger().error("Error in pending message reprocessing scheduler: {}", e.message, e)
        }
    }

    /**
     * 개별 stream의 pending 메시지 재처리
     * @param streamKey Redis Stream 키
     */
    private fun reprocessStreamPendingMessages(streamKey: String) {
        try {
            // 스트림의 모든 consumer group 조회
            val groups = redisTemplate.execute { conn ->
                conn.streamCommands().xInfoGroups(streamKey.toByteArray())
            }

            if (groups == null) {
                logger().debug("Stream {} has no groups", streamKey)
                return
            }

            logger().debug("Stream {} has {} groups", streamKey, groups.size())

            groups.forEach { groupInfo ->
                val groupName = groupInfo.groupName()

                handlePendings(streamKey, groupName, 1)
            }
        } catch (e: Exception) {
            logger().error("Error reprocessing pending messages for stream {}: {}", streamKey, e.message, e)
        }
    }

    /**
     * Idle active consumer 제거 스케줄러
     * - 주기: 2분마다
     * - 기능: 5분 이상 idle 상태인 active consumer를 자동으로 제거
     */
    @Scheduled(fixedDelay = 120000, initialDelay = 15000)
    fun removeIdleActiveConsumers() {
        try {
            logger().debug("Starting idle active consumer removal scheduler")
            allStream.forEach { streamKey ->
                removeStreamIdleConsumers(streamKey)
            }
        } catch (e: Exception) {
            logger().error("Error in idle consumer removal scheduler: {}", e.message, e)
        }
    }

    /**
     * 개별 stream의 idle active consumer 제거
     * @param streamKey Redis Stream 키
     */
    private fun removeStreamIdleConsumers(streamKey: String) {
        try {
            // 스트림의 모든 consumer group 조회
            val groups = redisTemplate.execute { conn ->
                conn.streamCommands().xInfoGroups(streamKey.toByteArray())
            }

            if (groups == null) {
                logger().debug("Stream {} has no groups", streamKey)
                return
            }

            groups.forEach { groupInfo ->
                val groupName = groupInfo.groupName()
                removeGroupIdleConsumers(streamKey, groupName)
            }
        } catch (e: Exception) {
            logger().error("Error removing idle consumers for stream {}: {}", streamKey, e.message, e)
        }
    }

    /**
     * 특정 consumer group의 idle active consumer 제거
     * @param streamKey Redis Stream 키
     * @param groupName Consumer Group 이름
     */
    private fun removeGroupIdleConsumers(streamKey: String, groupName: String) {
        try {
            // 그룹의 모든 consumer 정보 조회
            val consumers = redisTemplate.execute { conn ->
                conn.streamCommands().xInfoConsumers(streamKey.toByteArray(), groupName)
            }

            if (consumers == null) {
                logger().debug("Stream {} group {} has no consumers", streamKey, groupName)
                return
            }

            logger().debug("Stream {} group {} has {} consumers", streamKey, groupName, consumers.size())

            var removedCount = 0

            consumers.forEach { consumerInfo ->
                val consumerName = consumerInfo.consumerName()
                val idleMs = consumerInfo.idleTime().toMillis()
                val pendingCount = consumerInfo.pendingCount()

                logger().debug(
                    "Stream {} group {} consumer {} - idle: {}ms, pending: {}",
                    streamKey, groupName, consumerName, idleMs, pendingCount
                )

                // 5분 이상 idle인 consumer 제거
                if (idleMs >= IDLE_CONSUMER_THRESHOLD_MS) {
                    if (removeConsumer(streamKey, groupName, consumerName)) {
                        logger().info(
                            "Removed idle consumer: stream={}, group={}, consumer={}, idleMs={}",
                            streamKey, groupName, consumerName, idleMs
                        )
                        removedCount++
                    }
                }
            }

            if (removedCount > 0) {
                logger().info(
                    "Stream {} group {} - Removed {} idle consumers",
                    streamKey, groupName, removedCount
                )
            }
        } catch (e: Exception) {
            logger().warn(
                "Error removing idle consumers for stream {} group {}: {}",
                streamKey, groupName, e.message
            )
        }
    }

    /**
     * Consumer 제거
     * @param streamKey Redis Stream 키
     * @param groupName Consumer Group 이름
     * @param consumerName 제거할 Consumer 이름
     * @return 제거 성공 여부
     */
    private fun removeConsumer(streamKey: String, groupName: String, consumerName: String): Boolean {
        return try {
            val result = redisTemplate.execute { conn ->
                conn.streamCommands().xGroupDelConsumer(
                    streamKey.toByteArray(),
                    groupName,
                    consumerName
                )
            }
            result ?: false
        } catch (e: Exception) {
            logger().error(
                "Error removing consumer {} from stream {} group {}: {}",
                consumerName, streamKey, groupName, e.message
            )
            false
        }
    }

    private fun handlePendings(streamKey: String, groupName: String, batchSize: Long) {
        val claims = streamService.autoClaim(streamKey, groupName = groupName, PodContext.id, count = batchSize).messages?.toList()
        claims?.forEach { msg ->
            try {
                streamService.pending(streamKey, groupName, msg.id.toString())?.apply {
                    if (totalDeliveryCount > MAX_RETRY_COUNT) {
                        streamService.save(SummaryStreamConfig.getCdlStreamKey(streamKey), msg.body)
                        streamService.ackDel(streamKey, groupName, msg.id.toString())
                        
                        logger().warn("Deleted message {} from stream {} group {} after {} delivery attempts",
                            msg.id, streamKey, groupName, totalDeliveryCount)
                        return@forEach
                    }
                }
                // check delivered counted
                val map = msg.body.entries.associate { String(it.key) to String(it.value) }
                val json = Jackson.getMapper().writeValueAsString(map)

//                val payload = Jackson.getMapper().readValue(json, String::class.java)

                logger().info(json)
            } finally {
            }
        }
    }
}


