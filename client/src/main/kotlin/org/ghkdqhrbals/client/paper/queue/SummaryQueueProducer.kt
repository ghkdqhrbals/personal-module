package org.ghkdqhrbals.client.paper.queue

import org.springframework.stereotype.Service
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.StreamOperations
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.connection.stream.RecordId
import org.ghkdqhrbals.client.config.logger
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.time.Instant

@Service
class SummaryQueueProducer(
    private val redisTemplate: StringRedisTemplate,
    @Value("\${redis.stream.summary}") private val streamKey: String
) {
    private val mapper = jacksonObjectMapper()
    private val streamOps: StreamOperations<String, String, String> by lazy { redisTemplate.opsForStream() }

    fun enqueue(request: SummaryJobRequest, maxRetries: Int = 3): RecordId {
        val json = mapper.writeValueAsString(request)
        var attempt = 0
        var lastError: Exception? = null
        while (attempt < maxRetries) {
            try {
                val record = MapRecord.create(streamKey, mapOf(
                    "payload" to json,
                    "ts" to Instant.now().toString(),
                    "tries" to attempt.toString()
                ))
                val id = streamOps.add(record) ?: throw IllegalStateException("Stream add returned null")
                logger().info("[STREAM] Enqueued summary job id=$id paperTitle='${request.title?.take(80)}' attempt=$attempt")
                return id
            } catch (e: Exception) {
                lastError = e
                logger().warn("[STREAM] Enqueue failed attempt=$attempt error=${e.message}")
                attempt++
            }
        }
        throw IllegalStateException("Failed to enqueue after $maxRetries attempts: ${lastError?.message}")
    }

    fun enqueueProgressInit(batchId: String) {
        val key = "batch:$batchId:progress"
        redisTemplate.opsForHash<String, String>().putAll(key, mapOf(
            "total" to "0",
            "completed" to "0",
            "failed" to "0"
        ))
        // TTL 1분 설정
        redisTemplate.expire(key, 60, java.util.concurrent.TimeUnit.SECONDS)
        logger().info("[STREAM] Init progress batchId=$batchId (TTL: 60s)")
    }

    fun updateTotal(batchId: String, total: Int) {
        val key = "batch:$batchId:progress"
        redisTemplate.opsForHash<String, String>().put(key, "total", total.toString())
        // TTL 1분 재설정 (갱신)
        redisTemplate.expire(key, 60, java.util.concurrent.TimeUnit.SECONDS)
        logger().info("[STREAM] Set total=$total batchId=$batchId (TTL: 60s)")
    }

    fun incrCompleted(batchId: String) {
        val key = "batch:$batchId:progress"
        redisTemplate.opsForHash<String, String>().increment(key, "completed", 1)
    }

    fun incrFailed(batchId: String) {
        val key = "batch:$batchId:progress"
        redisTemplate.opsForHash<String, String>().increment(key, "failed", 1)
    }
}

data class SummaryJobRequest(
    val arxivId: String? = null,
    val title: String? = null,
    val abstract: String? = null,
    val journalRefRaw: String? = null,
    val maxLength: Int = 150,
    val eventId: String? = null
)
