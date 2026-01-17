package org.ghkdqhrbals.client.domain.stream

import io.lettuce.core.Consumer
import io.lettuce.core.XAutoClaimArgs
import io.lettuce.core.cluster.api.async.RedisAdvancedClusterAsyncCommands
import io.lettuce.core.models.stream.ClaimedMessages
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

    fun ackDel(topic: String, group: String, recordId: String) {
        ack(topic, group, recordId)
        delete(topic, recordId)
    }

    fun ack(key: String, group: String, recordId: String) {
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

    fun send(topic: String, key:String, payload: Any): RecordId? {
        key
        return redisTemplate.opsForStream<String, String>().add(
            StreamRecords.newRecord().`in`(topic).ofObject(payload),
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