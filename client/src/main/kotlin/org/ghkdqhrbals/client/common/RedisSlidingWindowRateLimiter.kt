package org.ghkdqhrbals.client.common

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Service
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Redis 기반 슬라이딩 윈도우 레이트 리미터
 * - 지정된 windowSec 동안 최대 limit 만큼만 허용
 * - 분산 환경에서도 원자적으로 동작 (Lua 스크립트)
 */
@Service
class RedisSlidingWindowRateLimiter(
    private val redisTemplate: StringRedisTemplate
) {
    private val acquireScript: DefaultRedisScript<Long> = DefaultRedisScript<Long>().apply {
        setScriptText(
            """
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local windowMillis = tonumber(ARGV[2])
            local limit = tonumber(ARGV[3])
            local expireSec = tonumber(ARGV[4])
            local member = ARGV[5]

            -- 오래된 항목 제거
            redis.call('ZREMRANGEBYSCORE', key, '-inf', now - windowMillis)

            -- 현재 카운트 확인
            local count = redis.call('ZCARD', key)
            if count < limit then
                redis.call('ZADD', key, now, member)
                redis.call('EXPIRE', key, expireSec)
                return 1
            else
                return 0
            end
            """.trimIndent()
        )
        setResultType(Long::class.java)
    }

    /**
     * 토큰 획득 시 true, 타임아웃으로 실패 시 false
     */
    fun acquire(
        key: String,
        windowSec: Long,
        limit: Int,
        maxWaitMillis: Long = TimeUnit.SECONDS.toMillis(15)
    ): Boolean {
        val start = System.currentTimeMillis()
        val windowMillis = TimeUnit.SECONDS.toMillis(windowSec)
        val expireSec = (windowSec * 2).coerceAtLeast(5)

        while (true) {
            val now = System.currentTimeMillis()
            val member = now.toString() + ":" + UUID.randomUUID().toString()
            val allowed: Long = redisTemplate.execute(
                acquireScript,
                listOf(key),
                now.toString(),
                windowMillis.toString(),
                limit.toString(),
                expireSec.toString(),
                member
            ) ?: 0L

            if (allowed == 1L) return true

            val elapsed = System.currentTimeMillis() - start
            if (elapsed >= maxWaitMillis) return false

            // 다음 기회까지 짧게 대기 (최소 100ms)
            Thread.sleep(100)
        }
    }
}
