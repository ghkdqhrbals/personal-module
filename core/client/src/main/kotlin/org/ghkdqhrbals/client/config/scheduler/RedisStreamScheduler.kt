package org.ghkdqhrbals.client.config.scheduler

import org.ghkdqhrbals.client.config.listener.PodContext
import org.ghkdqhrbals.client.config.log.logger
import org.ghkdqhrbals.client.domain.monitoring.RedisStreamMonitoringService
import org.ghkdqhrbals.client.domain.stream.StreamConfigManager
import org.ghkdqhrbals.client.domain.stream.StreamService
import org.ghkdqhrbals.model.config.Jackson
import org.springframework.data.domain.Range
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
    private val manager: StreamConfigManager,
    private val monitoringService: RedisStreamMonitoringService,

    ) {
    companion object {
        const val IDLE_CONSUMER_THRESHOLD_MS = 60_000L // 1분
    }

    private val monitoringStreams = manager.cachedSummaryConfig.getAllStreamKeys()
    private val monitoringCdlStreams = manager.cachedSummaryConfig.getAllCdlKeys()
    private val allStream = all()

    /**
     * cdl 포함
     */
    final fun all(): List<String> {
        val allStreamKeys = manager.cachedSummaryConfig.getAllStreamKeys()
        val allCdlKeys = manager.cachedSummaryConfig.getAllCdlKeys()
        return allStreamKeys + allCdlKeys

    }

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
                reprocessStreamPendingMessages(streamKey, manager.cachedSummaryConfig.retryIntervalMs)
            }
        } catch (e: Exception) {
            logger().error("Error in pending message reprocessing scheduler: {}", e.message, e)
        }
    }

    @Scheduled(fixedDelay = 1000, initialDelay = 1000)
    fun reprocessPendingMessagesCdl() {
        try {
//            logger().info("Starting CDL pending message reprocessing scheduler $monitoringCdlStreams")
            monitoringCdlStreams.forEach { streamKey ->
                reprocessStreamPendingMessagesCdl(streamKey, manager.cachedSummaryConfig.cdlRetryIntervalMs)
            }
        } catch (e: Exception) {
            logger().error("Error in pending message reprocessing scheduler: {}", e.message, e)
        }
    }

    /**
     * 개별 stream의 pending 메시지 재처리
     * @param streamKey Redis Stream 키
     */
    private fun reprocessStreamPendingMessages(streamKey: String, minIdleTime: Long) {
        try {
            // 스트림의 모든 consumer group 조회
            val groups = redisTemplate.execute { conn ->
                conn.streamCommands().xInfoGroups(streamKey.toByteArray())
            }

            if (groups == null) {
//                logger().info("Stream {} has no groups", streamKey)
                return
            }

//            logger().info("Stream {} has {} groups", streamKey, groups.size())

            groups.forEach { groupInfo ->
                val groupName = groupInfo.groupName()

                handlePendings(streamKey, groupName, 10, minIdleTime)
            }
        } catch (e: Exception) {
            logger().error("Error reprocessing pending messages for stream {}: {}", streamKey, e.message, e)
        }
    }

    private fun reprocessStreamPendingMessagesCdl(streamKey: String, minIdleTime: Long) {
        try {
            // 스트림의 모든 consumer group 조회
            val groups = redisTemplate.execute { conn ->
                conn.streamCommands().xInfoGroups(streamKey.toByteArray())
            }

            if (groups == null) {
//                logger().info("Stream {} has no groups", streamKey)
                return
            }

//            logger().info("Stream {} has {} groups", streamKey, groups.size())

            groups.forEach { groupInfo ->
                val groupName = groupInfo.groupName()
                handlePendings(streamKey, groupName, 10, minIdleTime)
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
    @Scheduled(fixedDelay = IDLE_CONSUMER_THRESHOLD_MS, initialDelay = IDLE_CONSUMER_THRESHOLD_MS)
    fun removeIdleActiveConsumers() {
        try {
            allStream.forEach { streamKey ->
                removeStreamIdleConsumers(streamKey)
            }
        } catch (e: Exception) {
            logger().error("consumer group 제거 에러 : {}", e.message, e)
        }
    }

    /**
     * 개별 stream 의 idle active consumer 제거
     * @param streamKey Redis Stream 키
     */
    private fun removeStreamIdleConsumers(streamKey: String) {
        try {
            val groups = redisTemplate.execute { conn ->
                conn.streamCommands().xInfoGroups(streamKey.toByteArray())
            }

            if (groups == null || groups.isEmpty()) {
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
            val consumers = redisTemplate.execute { conn ->
                conn.streamCommands().xInfoConsumers(streamKey.toByteArray(), groupName)
            }

            if (consumers == null) {
                logger().debug("Stream {} group {} has no consumers", streamKey, groupName)
                return
            }

            val consumerList = consumers.toList()
            if (consumerList.isEmpty()) {
                logger().debug("Stream {} group {} has no consumers", streamKey, groupName)
                return
            }

            logger().info("Stream {} group {} has {} consumers", streamKey, groupName, consumerList.size)

            // active consumer와 idle consumer 분류
            val activeConsumers = mutableListOf<String>()
            val idleConsumers = mutableListOf<String>()

            consumerList.forEach { consumerInfo ->
                val consumerName = consumerInfo.consumerName()
                val idleMs = consumerInfo.idleTime().toMillis()

                if (idleMs >= IDLE_CONSUMER_THRESHOLD_MS) {
                    idleConsumers.add(consumerName)
                } else {
                    activeConsumers.add(consumerName)
                }
            }

            var removedCount = 0

            // idle consumer 처리
            idleConsumers.forEach { idleConsumerName ->
                // active consumer가 있을 때만 pending 메시지 재할당
                if (activeConsumers.isNotEmpty()) {
                    reassignPendingMessages(streamKey, groupName, idleConsumerName, activeConsumers)
                }

                // pending 메시지 재할당 후 consumer 삭제
                if (removeConsumer(streamKey, groupName, idleConsumerName)) {
                    logger().info("Removed idle consumer: stream={}, group={}, consumer={}",
                        streamKey, groupName, idleConsumerName)
                    removedCount++
                    logger().info("$idleConsumerName idle consumers removed")
                }
            }

            if (removedCount > 0) {

            }
        } catch (e: Exception) {
            logger().warn("Error removing idle consumers for stream {} group {}: {}",
                streamKey, groupName, e.message)
        }
    }

    /**
     * Idle consumer의 pending 메시지들을 active consumer들에게 분산 할당
     */
    private fun reassignPendingMessages(
        streamKey: String,
        groupName: String,
        idleConsumerName: String,
        activeConsumerNames: List<String>
    ) {
        try {
            var totalReassigned = 0L
            var cursor = "-"  // "-" 부터 시작 (가장 오래된 pending 메시지부터)
            var pageCount = 0

            while (true) {
                // idle consumer의 pending 메시지 조회 (pagination: 1000개씩)
                val pendingMessagesRaw = redisTemplate.execute { conn ->
                    conn.streamCommands().xPending(
                        streamKey.toByteArray(),
                        groupName,
                        idleConsumerName,
                        Range.closed(cursor, "+"),
                        1000
                    )
                }

                val pendingMessages = (pendingMessagesRaw ?: emptyList()).toList()

                if (pendingMessages.isEmpty()) {
                    logger().debug("No more pending messages for idle consumer: stream={}, group={}, idleConsumer={}",
                        streamKey, groupName, idleConsumerName)
                    break
                }

                pageCount++
                logger().info("Processing page {} ({} messages) from idle consumer: stream={}, group={}, idleConsumer={}",
                    pageCount, pendingMessages.size, streamKey, groupName, idleConsumerName)

                // pending 메시지들을 active consumer들에게 round-robin으로 분산 할당
                pendingMessages.forEachIndexed { index, pendingMsg ->
                    try {
                        val targetConsumer = activeConsumerNames[((totalReassigned + index) % activeConsumerNames.size).toInt()]
                        val messageId = pendingMsg.id

                        logger().info("메세지 이관 message: messageId={}, from consumer={}, to consumer={}",
                            messageId, idleConsumerName, targetConsumer)
                        redisTemplate.execute { conn ->
                            conn.streamCommands().xClaim(
                                streamKey.toByteArray(),
                                groupName,
                                targetConsumer,
                                java.time.Duration.ZERO,
                                messageId
                            )
                        }

                        logger().debug("Reassigned message: messageId={}, to consumer={}", messageId, targetConsumer)

                    } catch (e: Exception) {
                        logger().error("Error reassigning message {}: {}", pendingMsg.id, e.message)
                    }
                }

                totalReassigned += pendingMessages.size

                // 1000개 미만이면 마지막 페이지
                if (pendingMessages.size < 1000) {
                    break
                }

                // 다음 페이지를 위해 cursor를 마지막 메시지ID의 다음으로 설정
                val lastMessageId = pendingMessages.last().id
                cursor = "($lastMessageId"  // 마지막 ID 다음부터 시작
            }

            if (totalReassigned > 0) {
                logger().info("Successfully reassigned {} pending messages from idle consumer: stream={}, group={}, idleConsumer={}",
                    totalReassigned, streamKey, groupName, idleConsumerName)
            }
        } catch (e: Exception) {
            logger().error("Error reassigning pending messages: stream={}, group={}, idleConsumer={}: {}",
                streamKey, groupName, idleConsumerName, e.message, e)
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

    private fun handlePendings(streamKey: String, groupName: String, batchSize: Long, minIdleTime: Long ) {
        val claims = streamService.autoClaim(streamKey, groupName = groupName, PodContext.id, count = batchSize, minIdleTimeMs = minIdleTime).messages?.toList()
        claims?.forEach { msg ->
            try {
                streamService.pending(streamKey, groupName, msg.id.toString())?.apply {
                    // CDL stream 여부 판단
                    val isCdlStream = streamKey.contains(manager.cachedSummaryConfig.cdlPostfix)
                    logger().info("Processing pending message {} from stream {} group {} (isCdlStream: {})",
                        msg.id, streamKey, groupName, isCdlStream)

                    // CDL stream이면 cdlRetryCount, 일반 stream이면 maxTryCount 사용
                    val maxRetryCount = if (isCdlStream) manager.cachedSummaryConfig.cdlRetryCount else manager.cachedSummaryConfig.maxTryCount

                    if (totalDeliveryCount > maxRetryCount) {
                        if (!isCdlStream) {
                            // CDL stream으로 이동
                            streamService.send(manager.cachedSummaryConfig.getCdlStreamKey(streamKey), msg.body)
                        }
                        streamService.ackDel(streamKey, groupName, msg.id.toString())

                        logger().warn("Deleted message {} from stream {} group {} after {} delivery attempts (maxRetryCount: {})",
                            msg.id, streamKey, groupName, totalDeliveryCount, maxRetryCount)
                        return@forEach
                    }
                }
                // check delivered counted
                val map = msg.body.entries.associate { String(it.key) to String(it.value) }
                val json = Jackson.getMapper().writeValueAsString(map)

                logger().info(json)
            } catch (e: Exception) {
                logger().error("Error handling pending message: {}", e.message, e)
            }
        }
    }
}


