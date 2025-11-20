package org.ghkdqhrbals.client.common

import com.redis.testcontainers.RedisContainer
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

@Testcontainers
class RedisSlidingWindowRateLimiterTest {

    companion object {
        @Container
        private val redisContainer = RedisContainer(DockerImageName.parse("redis:7.2-alpine"))
            .withExposedPorts(6379)
    }

    private lateinit var connectionFactory: LettuceConnectionFactory
    private lateinit var redisTemplate: StringRedisTemplate
    private lateinit var rateLimiter: RedisSlidingWindowRateLimiter

    @BeforeEach
    fun setUp() {
        redisContainer.start()

        val config = RedisStandaloneConfiguration(redisContainer.host, redisContainer.firstMappedPort)
        connectionFactory = LettuceConnectionFactory(config)
        connectionFactory.afterPropertiesSet()

        redisTemplate = StringRedisTemplate(connectionFactory)
        rateLimiter = RedisSlidingWindowRateLimiter(redisTemplate)
    }

    @AfterEach
    fun tearDown() {
        // Redis 데이터 정리
        redisTemplate.connectionFactory?.connection?.serverCommands()?.flushAll()
        connectionFactory.destroy()
    }

    @Test
    fun `토큰 획득 성공 - limit 이내`() {
        // given
        val key = "test:user:1"
        val windowSec = 10L
        val limit = 5

        // when & then
        for (i in 1..limit) {
            val result = rateLimiter.acquire(key, windowSec, limit, maxWaitMillis = 1000)
            assertTrue(result, "요청 $i 는 성공해야 함")
        }
    }

    @Test
    fun `토큰 획득 실패 - limit 초과 시 대기 후 타임아웃`() {
        // given
        val key = "test:user:2"
        val windowSec = 10L
        val limit = 3
        val maxWaitMillis = 500L

        // when - limit 만큼 먼저 획득
        for (i in 1..limit) {
            val result = rateLimiter.acquire(key, windowSec, limit, maxWaitMillis = 1000)
            assertTrue(result)
        }

        // then - 추가 요청은 타임아웃으로 실패
        val startTime = System.currentTimeMillis()
        val result = rateLimiter.acquire(key, windowSec, limit, maxWaitMillis = maxWaitMillis)
        val elapsed = System.currentTimeMillis() - startTime

        assertFalse(result, "limit 초과 시 타임아웃으로 실패해야 함")
        assertTrue(elapsed >= maxWaitMillis, "최소 maxWaitMillis 만큼 대기해야 함")
    }

    @Test
    fun `슬라이딩 윈도우 - 시간 경과 후 새 토큰 획득 가능`() {
        // given
        val key = "test:user:3"
        val windowSec = 2L
        val limit = 2

        // when - limit 만큼 획득
        assertTrue(rateLimiter.acquire(key, windowSec, limit, maxWaitMillis = 1000))
        assertTrue(rateLimiter.acquire(key, windowSec, limit, maxWaitMillis = 1000))

        // then - 즉시 추가 요청은 실패
        assertFalse(rateLimiter.acquire(key, windowSec, limit, maxWaitMillis = 500))

        // when - 윈도우 시간 경과 대기
        Thread.sleep(TimeUnit.SECONDS.toMillis(windowSec + 1))

        // then - 시간 경과 후 새로운 토큰 획득 가능
        assertTrue(rateLimiter.acquire(key, windowSec, limit, maxWaitMillis = 1000))
    }

    @Test
    fun `병렬 요청 - 정확히 limit 만큼만 허용`() {
        // given
        val key = "test:user:4"
        val windowSec = 10L
        val limit = 5
        val threadCount = 20
        val successCount = AtomicInteger(0)
        val failureCount = AtomicInteger(0)
        val latch = CountDownLatch(threadCount)

        // when - 여러 스레드에서 동시에 요청
        val threads = (1..threadCount).map {
            thread {
                try {
                    val result = rateLimiter.acquire(key, windowSec, limit, maxWaitMillis = 500)
                    if (result) {
                        successCount.incrementAndGet()
                    } else {
                        failureCount.incrementAndGet()
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await(30, TimeUnit.SECONDS)
        threads.forEach { it.join() }

        // then - 정확히 limit 만큼만 성공
        assertEquals(limit, successCount.get(), "정확히 limit 만큼만 성공해야 함")
        assertEquals(threadCount - limit, failureCount.get(), "나머지는 실패해야 함")
    }

    @Test
    fun `다른 키는 독립적으로 동작`() {
        // given
        val key1 = "test:user:5"
        val key2 = "test:user:6"
        val windowSec = 10L
        val limit = 2

        // when & then
        assertTrue(rateLimiter.acquire(key1, windowSec, limit, maxWaitMillis = 1000))
        assertTrue(rateLimiter.acquire(key1, windowSec, limit, maxWaitMillis = 1000))
        assertFalse(rateLimiter.acquire(key1, windowSec, limit, maxWaitMillis = 500))

        // key2는 독립적으로 동작
        assertTrue(rateLimiter.acquire(key2, windowSec, limit, maxWaitMillis = 1000))
        assertTrue(rateLimiter.acquire(key2, windowSec, limit, maxWaitMillis = 1000))
        assertFalse(rateLimiter.acquire(key2, windowSec, limit, maxWaitMillis = 500))
    }

    @Test
    fun `Redis 키 만료 확인`() {
        // given
        val key = "test:user:7"
        val windowSec = 2L
        val limit = 1

        // when
        assertTrue(rateLimiter.acquire(key, windowSec, limit, maxWaitMillis = 1000))

        // then - 키가 존재하고 TTL이 설정되어 있어야 함
        val exists = redisTemplate.hasKey(key)
        assertTrue(exists, "키가 존재해야 함")

        val ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS)
        assertTrue(ttl!! > 0, "TTL이 설정되어 있어야 함")
        assertTrue(ttl <= windowSec * 2, "TTL은 windowSec * 2 이하여야 함")
    }

    @Test
    fun `정확한 슬라이딩 윈도우 동작 검증`() {
        // given
        val key = "test:user:8"
        val windowSec = 3L
        val limit = 3

        // when - 0초에 3개 요청
        assertTrue(rateLimiter.acquire(key, windowSec, limit, maxWaitMillis = 1000))
        assertTrue(rateLimiter.acquire(key, windowSec, limit, maxWaitMillis = 1000))
        assertTrue(rateLimiter.acquire(key, windowSec, limit, maxWaitMillis = 1000))

        // then - 즉시 추가 요청은 실패
        assertFalse(rateLimiter.acquire(key, windowSec, limit, maxWaitMillis = 500))

        // when - 1.5초 대기 (아직 윈도우 내)
        Thread.sleep(1500)

        // then - 여전히 실패
        assertFalse(rateLimiter.acquire(key, windowSec, limit, maxWaitMillis = 500))

        // when - 추가로 2초 대기 (총 3.5초, 윈도우 밖)
        Thread.sleep(2000)

        // then - 성공
        assertTrue(rateLimiter.acquire(key, windowSec, limit, maxWaitMillis = 1000))
    }

    @Test
    fun `대기 기능 - 윈도우 경과 후 자동으로 토큰 획득`() {
        // given
        val key = "test:user:9"
        val windowSec = 2L
        val limit = 2

        // when - limit 만큼 획득
        assertTrue(rateLimiter.acquire(key, windowSec, limit, maxWaitMillis = 1000))
        assertTrue(rateLimiter.acquire(key, windowSec, limit, maxWaitMillis = 1000))

        // then - 충분한 대기 시간을 주면 자동으로 성공
        val startTime = System.currentTimeMillis()
        val result = rateLimiter.acquire(key, windowSec, limit, maxWaitMillis = 5000)
        val elapsed = System.currentTimeMillis() - startTime

        assertTrue(result, "충분한 대기 시간을 주면 성공해야 함")
        assertTrue(elapsed >= TimeUnit.SECONDS.toMillis(windowSec), "최소 windowSec 만큼 대기했어야 함")
        assertTrue(elapsed < 5000, "불필요하게 오래 대기하면 안됨")
    }

    @Test
    fun `대기 기능 - 효율적인 대기 시간 계산`() {
        // given
        val key = "test:user:10"
        val windowSec = 3L
        val limit = 1

        // when - 첫 번째 요청
        assertTrue(rateLimiter.acquire(key, windowSec, limit, maxWaitMillis = 1000))

        // then - 두 번째 요청은 정확히 윈도우 시간만큼 대기 후 성공
        val startTime = System.currentTimeMillis()
        val result = rateLimiter.acquire(key, windowSec, limit, maxWaitMillis = 10000)
        val elapsed = System.currentTimeMillis() - startTime

        assertTrue(result, "대기 후 성공해야 함")
        val expectedWait = TimeUnit.SECONDS.toMillis(windowSec)
        assertTrue(elapsed >= expectedWait - 50, "최소 windowSec 만큼 대기했어야 함 (약간의 오차 허용)")
        assertTrue(elapsed < expectedWait + 500, "과도하게 대기하면 안됨 (500ms 버퍼)")
    }

    @Test
    fun `대기 기능 - 여러 요청이 순차적으로 대기하며 획득`() {
        // given
        val key = "test:user:11"
        val windowSec = 1L
        val limit = 1
        val requestCount = 3

        // when - 여러 요청을 순차적으로 시도
        val startTime = System.currentTimeMillis()
        val results = mutableListOf<Boolean>()

        repeat(requestCount) { i ->
            val result = rateLimiter.acquire(key, windowSec, limit, maxWaitMillis = 5000)
            results.add(result)
        }

        val totalElapsed = System.currentTimeMillis() - startTime

        // then - 모든 요청이 성공해야 함
        assertEquals(requestCount, results.count { it }, "모든 요청이 성공해야 함")

        // 최소 (requestCount - 1) * windowSec 만큼 대기했어야 함
        val minExpectedTime = TimeUnit.SECONDS.toMillis(windowSec) * (requestCount - 1)
        assertTrue(totalElapsed >= minExpectedTime - 100, "최소 대기 시간을 만족해야 함 (약간의 오차 허용)")
    }

    @Test
    fun `람다 방식 - 토큰 획득 후 람다 실행`() {
        // given
        val key = "test:user:12"
        val windowSec = 10L
        val limit = 3

        // when & then
        val result1 = rateLimiter.acquire(key, windowSec, limit, maxWaitMillis = 1000) {
            "success1"
        }
        assertEquals("success1", result1)

        val result2 = rateLimiter.acquire(key, windowSec, limit, maxWaitMillis = 1000) {
            42
        }
        assertEquals(42, result2)

        val result3 = rateLimiter.acquire(key, windowSec, limit, maxWaitMillis = 1000) {
            listOf(1, 2, 3)
        }
        assertEquals(listOf(1, 2, 3), result3)
    }

    @Test
    fun `람다 방식 - 타임아웃 시 LockTimeoutException 발생`() {
        // given
        val key = "test:user:13"
        val windowSec = 10L
        val limit = 2

        // when - limit 만큼 먼저 획득
        rateLimiter.acquire(key, windowSec, limit, maxWaitMillis = 1000) { "first" }
        rateLimiter.acquire(key, windowSec, limit, maxWaitMillis = 1000) { "second" }

        // then - 추가 요청은 LockTimeoutException 발생
        val exception = assertThrows<LockTimeoutException> {
            rateLimiter.acquire(key, windowSec, limit, maxWaitMillis = 500) {
                "should not execute"
            }
        }

        assertTrue(exception.message!!.contains(key))
        assertTrue(exception.elapsedMillis >= 500)
        assertEquals(key, exception.key)
    }

    @Test
    fun `람다 방식 - 충분한 대기 시간 후 성공`() {
        // given
        val key = "test:user:14"
        val windowSec = 2L
        val limit = 1

        // when - 첫 번째 요청
        val result1 = rateLimiter.acquire(key, windowSec, limit, maxWaitMillis = 1000) {
            "first"
        }
        assertEquals("first", result1)

        // then - 충분한 대기 시간을 주면 성공
        val startTime = System.currentTimeMillis()
        val result2 = rateLimiter.acquire(key, windowSec, limit, maxWaitMillis = 5000) {
            "second"
        }
        val elapsed = System.currentTimeMillis() - startTime

        assertEquals("second", result2)
        assertTrue(elapsed >= TimeUnit.SECONDS.toMillis(windowSec), "최소 windowSec 만큼 대기했어야 함")
    }

    @Test
    fun `람다 방식 - 예외 발생 시 전파`() {
        // given
        val key = "test:user:15"
        val windowSec = 10L
        val limit = 5

        // when & then - 람다 내부 예외가 전파되어야 함
        assertThrows<IllegalStateException> {
            rateLimiter.acquire(key, windowSec, limit, maxWaitMillis = 1000) {
                throw IllegalStateException("Test exception")
            }
        }
    }
}

