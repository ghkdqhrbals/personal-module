package org.ghkdqhrbals.client.domain.stream

import io.lettuce.core.Consumer
import io.lettuce.core.XAutoClaimArgs
import io.lettuce.core.cluster.api.async.RedisAdvancedClusterAsyncCommands
import io.lettuce.core.models.stream.ClaimedMessages
import org.ghkdqhrbals.message.redis.PartitionedStream
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Range
import org.springframework.data.redis.connection.stream.PendingMessage
import org.springframework.data.redis.connection.stream.RecordId
import org.springframework.data.redis.connection.stream.StreamRecords
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

@Component
@Profile("!test")
class StreamService(
    private val redisTemplate: StringRedisTemplate,
) {

    companion object {
        /** maxLen 근사치 판단을 위한 여유분(예: 2% + 최소 50) */
        private const val DEFAULT_MAXLEN_SAFETY_MARGIN_RATIO = 0.02
        private const val DEFAULT_MAXLEN_SAFETY_MARGIN_MIN = 50L
    }

    /**
     * 전송 전 maxLen 근사치일 때 던지는 예외.
     * (상위 레이어에서 topic/currentLength/maxLen 값을 읽어 사용자에게 에러를 전달할 수 있게 공개 필드로 둠)
     */
    @Suppress("unused")
    class StreamMaxLenExceededException(
        val topic: String,
        val currentLength: Long?,
        val maxLen: Long,
        message: String,
    ) : IllegalStateException(message)

    private fun enforceMaxLenBeforeSend(topic: String, maxLen: Long) {
        if (maxLen <= 0) return

        val currentLen = try {
            redisTemplate.execute { it.streamCommands().xInfo(topic.toByteArray()) }?.streamLength()
        } catch (e: Exception) {
            // 길이 조회 실패 시에는 기존 동작 유지(전송 시도)
            return
        }

        val safetyMargin = maxOf(
            (maxLen * DEFAULT_MAXLEN_SAFETY_MARGIN_RATIO).toLong(),
            DEFAULT_MAXLEN_SAFETY_MARGIN_MIN
        )
        val nearLimit = (currentLen ?: 0L) >= (maxLen - safetyMargin).coerceAtLeast(0L)

        if (nearLimit) {
            throw StreamMaxLenExceededException(
                topic = topic,
                currentLength = currentLen,
                maxLen = maxLen,
                message = "MAXLEN near-limit: topic=$topic currentLength=$currentLen maxLen=$maxLen safetyMargin=$safetyMargin"
            )
        }
    }

    suspend fun ackDel(topic: String, group: String, recordId: String) {
        ack(topic, group, recordId)
        delete(topic, recordId)
    }

    suspend fun  ack(key: String, group: String, recordId: String) {
        redisTemplate.opsForStream<String, String>().acknowledge(key, group, recordId)
    }


    fun delete(key: String, recordId: String) {
        redisTemplate.opsForStream<String, String>().delete(key, recordId)
    }

    fun send(topic: String, payload: Any): RecordId? {
        return redisTemplate.opsForStream<String, String>().add(
            StreamRecords.newRecord().`in`(topic).ofObject(payload),
        )
    }

    /**
     * maxLen 근사치면 전송 전에 MAXLEN 에러(예외)로 반환.
     */
    @Suppress("unused")
    fun send(topic: String, payload: Any, maxLen: Long): RecordId? {
        enforceMaxLenBeforeSend(topic, maxLen)
        return send(topic, payload)
    }

    /**
     * 파티셔닝된 스트림에 메시지 전송
     * @param config 파티셔닝된 스트림 설정
     * @param partitionKey 파티션을 결정하는 키
     * @param payload 전송할 메시지 페이로드
     */
    fun send(config: PartitionedStream, partitionKey: String, payload: Any) {
        val resolvePartition = config.resolvePartition(partitionKey)
        val partitionedKey = config.getKey(resolvePartition)

        // partitioned stream도 각 파티션 키 기준으로 maxLen 체크
        enforceMaxLenBeforeSend(partitionedKey, config.maxLen)

        redisTemplate.opsForStream<String, String>().add(
            StreamRecords.newRecord().`in`(partitionedKey).ofObject(payload),
        )
    }


    fun trim(topic: String, payload: Any, maxLen: Long) {
        redisTemplate.opsForStream<String, String>().trim(topic, maxLen)
    }

    fun autoClaim(
        streamKey: String,
        groupName: String,
        consumerName: String,
        count: Long,
        minIdleTimeMs: Long,
    ): ClaimedMessages<ByteArray, ByteArray> {
        return redisTemplate.execute { conn ->
            val native = conn.nativeConnection
            @Suppress("UNCHECKED_CAST")
            val commands = native as RedisAdvancedClusterAsyncCommands<ByteArray, ByteArray>

            val consumer = Consumer.from(groupName.toByteArray(), consumerName.toByteArray())
            val args = XAutoClaimArgs.Builder.xautoclaim(consumer, Duration.ofMillis(minIdleTimeMs), "0-0").count(count)

            commands.xautoclaim(streamKey.toByteArray(), args).get()
        }!!
    }

    fun pending(streamKey: String, groupName: String, messageId: String): PendingMessage? {
        return redisTemplate.execute { conn ->
            conn.streamCommands().xPending(
                streamKey.toByteArray(),
                groupName,
                Range.closed(messageId, messageId),
                1L
            )?.firstOrNull()
        }
    }
}