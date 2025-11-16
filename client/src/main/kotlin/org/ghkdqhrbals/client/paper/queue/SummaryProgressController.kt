package org.ghkdqhrbals.client.paper.queue

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.ResponseEntity

@RestController
@RequestMapping("/api/summary")
class SummaryProgressController(
    private val redisTemplate: StringRedisTemplate
) {
    data class ProgressResponse(
        val eventId: String,
        val total: Long,
        val completed: Long,
        val failed: Long,
        val remaining: Long,
        val progressPercent: Double,
        val successPercent: Double,
        val isDone: Boolean
    )

    @GetMapping("/progress/{eventId}")
    fun getProgress(@PathVariable eventId: String): ResponseEntity<ProgressResponse> {
        val key = "batch:$eventId:progress"
        val entries = redisTemplate.opsForHash<String, String>().entries(key)
        val total = entries["total"]?.toLongOrNull() ?: 0L
        val completed = entries["completed"]?.toLongOrNull() ?: 0L
        val failed = entries["failed"]?.toLongOrNull() ?: 0L
        val percent = if (total > 0) (completed.toDouble() / total.toDouble()) * 100.0 else 0.0
        val successPercent = if (completed + failed > 0) (completed.toDouble() / (completed + failed).toDouble()) * 100.0 else 0.0
        val remaining = total - completed - failed
        val done = total > 0 && (completed + failed) >= total
        return ResponseEntity.ok(
            ProgressResponse(
                eventId = eventId,
                total = total,
                completed = completed,
                failed = failed,
                remaining = remaining.coerceAtLeast(0),
                progressPercent = String.format("%.2f", percent).toDouble(),
                successPercent = String.format("%.2f", successPercent).toDouble(),
                isDone = done
            )
        )
    }
}
