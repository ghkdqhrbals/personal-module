package org.ghkdqhrbals.client.common

import org.ghkdqhrbals.client.config.log.logger
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Service
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Redis 기반 슬라이딩 윈도우 레이트 리미터
 * - 지정된 windowSec 동안 최대 limit 만큼만 허용
 * - 분산 환경에서도 원자적으로 동작 (Lua 스크립트)
 * - 대기 시 가장 오래된 요청의 만료 시간을 계산하여 효율적으로 대기
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

    private val getOldestTimestampScript: DefaultRedisScript<Long> = DefaultRedisScript<Long>().apply {
        setScriptText(
            """
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local windowMillis = tonumber(ARGV[2])

            -- 오래된 항목 제거
            redis.call('ZREMRANGEBYSCORE', key, '-inf', now - windowMillis)

            -- 가장 오래된 항목의 타임스탬프 반환
            local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
            if #oldest >= 2 then
                return tonumber(oldest[2])
            else
                return -1
            end
            """.trimIndent()
        )
        setResultType(Long::class.java)
    }

    /**
     * 토큰 획득 후 람다 실행, 타임아웃 시 LockTimeoutException 발생
     *
     * @param key Redis 키
     * @param windowSec 슬라이딩 윈도우 크기 (초)
     * @param limit 윈도우 내 최대 허용 요청 수
     * @param maxWaitMillis 최대 대기 시간 (밀리초)
     * @param block 토큰 획득 후 실행할 람다
     * @return 람다 실행 결과
     * @throws LockTimeoutException 타임아웃 시 발생
     */
    fun <T> acquire(
        key: String,
        windowSec: Long,
        limit: Int,
        maxWaitMillis: Long = TimeUnit.SECONDS.toMillis(15),
        block: () -> T
    ): T {
        val acquired = tryAcquire(key, windowSec, limit, maxWaitMillis)
        if (!acquired.success) {
            throw LockTimeoutException(
                message = "Rate limiter timeout for key=$key after ${acquired.elapsedMillis}ms (${acquired.attemptCount} attempts)",
                key = key,
                attemptCount = acquired.attemptCount,
                elapsedMillis = acquired.elapsedMillis
            )
        }
        return block()
    }

    /**
     * 토큰 획득 시 true, 타임아웃으로 실패 시 false
     *
     * @param key Redis 키
     * @param windowSec 슬라이딩 윈도우 크기 (초)
     * @param limit 윈도우 내 최대 허용 요청 수
     * @param maxWaitMillis 최대 대기 시간 (밀리초)
     * @return 토큰 획득 성공 시 true, 타임아웃 시 false
     */
    fun acquire(
        key: String,
        windowSec: Long,
        limit: Int,
        maxWaitMillis: Long = TimeUnit.SECONDS.toMillis(15)
    ): Boolean {
        return tryAcquire(key, windowSec, limit, maxWaitMillis).success
    }

    /**
     * 내부 구현: 토큰 획득 시도 결과 반환
     */
    private fun tryAcquire(
        key: String,
        windowSec: Long,
        limit: Int,
        maxWaitMillis: Long
    ): AcquireResult {
        val start = System.currentTimeMillis()
        val windowMillis = TimeUnit.SECONDS.toMillis(windowSec)
        val expireSec = (windowSec * 2).coerceAtLeast(5)
        var attemptCount = 0

        while (true) {
            attemptCount++
            val now = System.currentTimeMillis()
            val member = now.toString() + ":" + UUID.randomUUID().toString()

            // 토큰 획득 시도
            val allowed = redisTemplate.execute(
                acquireScript,
                listOf(key),
                now.toString(),
                windowMillis.toString(),
                limit.toString(),
                expireSec.toString(),
                member
            ) ?: 0L

            if (allowed == 1L) {
                if (attemptCount > 1) {
                    logger().debug("Rate limiter acquired token after $attemptCount attempts (waited ${now - start}ms)")
                }
                return AcquireResult(success = true, attemptCount = attemptCount, elapsedMillis = now - start)
            }

            // 타임아웃 체크
            val elapsed = System.currentTimeMillis() - start
            if (elapsed >= maxWaitMillis) {
                logger().warn("Rate limiter timeout after $attemptCount attempts (${elapsed}ms)")
                return AcquireResult(success = false, attemptCount = attemptCount, elapsedMillis = elapsed)
            }

            // 가장 오래된 요청의 만료 시간을 계산하여 대기
            val waitTime = calculateWaitTime(key, now, windowMillis, maxWaitMillis - elapsed)

            if (waitTime > 0) {
                logger().debug("Rate limiter waiting ${waitTime}ms (attempt $attemptCount)")
                Thread.sleep(waitTime)
            }
        }
    }

    /**
     * 토큰 획득 시도 결과
     */
    private data class AcquireResult(
        val success: Boolean,
        val attemptCount: Int,
        val elapsedMillis: Long
    )

    /**
     * 다음 시도까지 대기할 시간을 계산
     * 가장 오래된 요청이 윈도우 밖으로 나갈 때까지의 시간을 계산
     */
    private fun calculateWaitTime(
        key: String,
        now: Long,
        windowMillis: Long,
        remainingTimeMillis: Long
    ): Long {
        try {
            val oldestTimestamp = redisTemplate.execute(
                getOldestTimestampScript,
                listOf(key),
                now.toString(),
                windowMillis.toString()
            ) ?: -1L

            if (oldestTimestamp > 0) {
                // 가장 오래된 요청이 윈도우 밖으로 나가는 시간 계산
                val timeUntilExpire = (oldestTimestamp + windowMillis) - now

                if (timeUntilExpire > 0) {
                    // 약간의 버퍼를 추가 (10ms)하고, 남은 시간 이내로 제한
                    val waitTime = (timeUntilExpire + 10).coerceAtMost(remainingTimeMillis)
                    return waitTime.coerceAtLeast(0)
                }
            }
        } catch (e: Exception) {
            logger().warn("Failed to calculate wait time, using default: ${e.message}")
        }

        // 기본값: 짧은 대기 시간
        return 100L.coerceAtMost(remainingTimeMillis)
    }
}
