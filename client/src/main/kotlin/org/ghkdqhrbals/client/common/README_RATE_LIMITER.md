# RedisSlidingWindowRateLimiter 사용 가이드

## 개요
Redis 기반의 슬라이딩 윈도우 레이트 리미터로, 분산 환경에서 안전하게 요청 제한을 관리합니다.

## 주요 기능

### 1. 슬라이딩 윈도우 방식
- 고정 윈도우와 달리 시간이 흐르면서 윈도우가 이동
- 더 정확하고 부드러운 레이트 리미팅 제공

### 2. 지능적인 대기 메커니즘
```kotlin
// 토큰을 즉시 획득할 수 없을 때 자동으로 대기
val acquired = rateLimiter.acquire(
    key = "user:123",
    windowSec = 3,        // 3초 윈도우
    limit = 5,            // 최대 5개 요청
    maxWaitMillis = 10_000 // 최대 10초 대기
)
```

**대기 로직:**
- Redis에서 가장 오래된 요청의 타임스탬프를 조회
- 해당 요청이 윈도우 밖으로 나가는 정확한 시간을 계산
- 불필요한 폴링 없이 필요한 시간만큼만 대기
- `maxWaitMillis` 이내에 토큰 획득 실패 시 `false` 반환

### 3. 원자성 보장
- Lua 스크립트로 Redis에서 원자적으로 실행
- 분산 환경에서도 경쟁 조건(race condition) 없이 안전

### 4. 자동 정리
- 오래된 요청은 자동으로 제거
- Redis 키에 TTL 설정으로 메모리 누수 방지

## 사용 예제

### 기본 사용법 (Boolean 반환)
```kotlin
@Service
class MyService(
    private val rateLimiter: RedisSlidingWindowRateLimiter
) {
    fun processRequest(userId: String) {
        val key = "api:user:$userId"
        
        // 1분에 최대 10개 요청 허용
        val acquired = rateLimiter.acquire(
            key = key,
            windowSec = 60,
            limit = 10,
            maxWaitMillis = 5000 // 최대 5초 대기
        )
        
        if (!acquired) {
            throw RateLimitExceededException("Too many requests. Please try again later.")
        }
        
        // 비즈니스 로직 실행
        // ...
    }
}
```

### 람다 방식 (자동 예외 처리) ⭐ 권장
```kotlin
@Service
class MyService(
    private val rateLimiter: RedisSlidingWindowRateLimiter
) {
    fun processRequest(userId: String): String {
        val key = "api:user:$userId"
        
        // 토큰 획득 후 자동으로 람다 실행, 타임아웃 시 LockTimeoutException 발생
        return rateLimiter.acquire(
            key = key,
            windowSec = 60,
            limit = 10,
            maxWaitMillis = 5000
        ) {
            // 비즈니스 로직 - 토큰이 획득된 상태에서만 실행됨
            performBusinessLogic(userId)
        }
    }
    
    private fun performBusinessLogic(userId: String): String {
        // 실제 처리
        return "Success for $userId"
    }
}
```

### 람다 방식 예외 처리
```kotlin
@RestController
class ApiController(
    private val rateLimiter: RedisSlidingWindowRateLimiter
) {
    @PostMapping("/api/data")
    fun getData(@RequestHeader("X-User-ID") userId: String): ResponseEntity<String> {
        return try {
            val result = rateLimiter.acquire(
                key = "api:user:$userId",
                windowSec = 60,
                limit = 100,
                maxWaitMillis = 3000
            ) {
                // API 로직
                fetchData()
            }
            ResponseEntity.ok(result)
        } catch (e: LockTimeoutException) {
            ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body("Rate limit exceeded: ${e.message}")
        }
    }
    
    private fun fetchData(): String = "data"
}
```

### ArXiv API 호출 예제
```kotlin
@Component
class ArxivHttpClient(
    private val rateLimiter: RedisSlidingWindowRateLimiter
) {
    companion object {
        private const val RATE_LIMIT_KEY = "rate:arxiv:global"
    }
    
    // Boolean 방식
    fun fetchData(url: String): ByteArray? {
        // arXiv API는 3초당 1개 요청 제한
        val acquired = rateLimiter.acquire(
            key = RATE_LIMIT_KEY,
            windowSec = 3,
            limit = 1,
            maxWaitMillis = 15_000
        )
        
        if (!acquired) {
            logger().warn("Rate limit timeout; skipping request")
            return null
        }
        
        // API 호출
        return restClient.get()
            .uri(url)
            .retrieve()
            .body(ByteArray::class.java)
    }
    
    // 람다 방식 (권장)
    fun fetchDataWithLambda(url: String): ByteArray {
        return rateLimiter.acquire(
            key = RATE_LIMIT_KEY,
            windowSec = 3,
            limit = 1,
            maxWaitMillis = 15_000
        ) {
            // 토큰 획득 후 API 호출
            restClient.get()
                .uri(url)
                .retrieve()
                .body(ByteArray::class.java)
                ?: throw IllegalStateException("Empty response")
        }
    }
}
```

### 다양한 제한 레벨 적용
```kotlin
@Service
class ApiRateLimiter(
    private val rateLimiter: RedisSlidingWindowRateLimiter
) {
    // 사용자별 제한
    fun checkUserLimit(userId: String): Boolean {
        return rateLimiter.acquire(
            key = "user:$userId",
            windowSec = 60,
            limit = 100,  // 1분에 100개
            maxWaitMillis = 1000
        )
    }
    
    // IP별 제한
    fun checkIpLimit(ip: String): Boolean {
        return rateLimiter.acquire(
            key = "ip:$ip",
            windowSec = 60,
            limit = 1000, // 1분에 1000개
            maxWaitMillis = 1000
        )
    }
    
    // 엔드포인트별 글로벌 제한
    fun checkGlobalLimit(endpoint: String): Boolean {
        return rateLimiter.acquire(
            key = "global:$endpoint",
            windowSec = 1,
            limit = 10000, // 초당 10000개
            maxWaitMillis = 100
        )
    }
}
```

### Spring MVC Interceptor와 통합
```kotlin
@Component
class RateLimitInterceptor(
    private val rateLimiter: RedisSlidingWindowRateLimiter
) : HandlerInterceptor {
    
    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any
    ): Boolean {
        val userId = request.getHeader("X-User-ID") ?: "anonymous"
        val key = "api:user:$userId"
        
        val acquired = rateLimiter.acquire(
            key = key,
            windowSec = 60,
            limit = 100,
            maxWaitMillis = 0 // 즉시 반환
        )
        
        if (!acquired) {
            response.status = HttpStatus.TOO_MANY_REQUESTS.value()
            response.writer.write("Rate limit exceeded")
            return false
        }
        
        return true
    }
}
```

## 동작 원리

### 내부 구조
```
Redis Sorted Set (ZSET)
Key: rate:user:123
┌─────────────────────────────────────┐
│ Score (timestamp)  │  Member (UUID) │
├─────────────────────────────────────┤
│ 1700000000000      │  uuid-1        │
│ 1700000001000      │  uuid-2        │
│ 1700000002000      │  uuid-3        │
└─────────────────────────────────────┘

Window: 3초 (3000ms)
Limit: 3개
```

### 획득 과정
1. **현재 시간 기준으로 오래된 요청 제거**
   - `ZREMRANGEBYSCORE key -inf (now - windowMillis)`
   
2. **현재 카운트 확인**
   - `ZCARD key`
   
3. **Limit 이하면 추가**
   - `ZADD key now uuid`
   - `EXPIRE key (windowSec * 2)`
   
4. **Limit 초과면 대기**
   - 가장 오래된 요청의 타임스탬프 조회
   - 윈도우 밖으로 나가는 시간 계산
   - 해당 시간만큼 대기 후 재시도

## 성능 특성

### 시간 복잡도
- `acquire()`: O(log N + M)
  - N: ZSET의 크기
  - M: 제거할 오래된 항목 수
  - 대부분의 경우 O(log N)

### 공간 복잡도
- O(L): L은 limit 값
- 자동 정리로 메모리 효율적

### 처리량
- Redis 성능에 의존
- 단일 Redis: ~50,000 ops/sec
- Lua 스크립트로 네트워크 왕복 최소화

## 모범 사례

### 1. 적절한 키 설계
```kotlin
// Good: 계층적 키 구조
"rate:service:user:${userId}"
"rate:api:endpoint:${endpoint}:ip:${ip}"

// Bad: 평탄한 키 구조
"user_${userId}_rate"
```

### 2. maxWaitMillis 설정
```kotlin
// 동기 API: 짧은 대기 시간
rateLimiter.acquire(key, 1, 100, maxWaitMillis = 100)

// 백그라운드 작업: 긴 대기 시간
rateLimiter.acquire(key, 60, 10, maxWaitMillis = 60_000)

// 즉시 확인만: 0
rateLimiter.acquire(key, 60, 100, maxWaitMillis = 0)
```

### 3. 다층 제한
```kotlin
fun processRequest(userId: String, ip: String) {
    // 1. 글로벌 제한
    if (!rateLimiter.acquire("global", 1, 10000, 0)) {
        throw GlobalRateLimitException()
    }
    
    // 2. IP 제한
    if (!rateLimiter.acquire("ip:$ip", 60, 1000, 0)) {
        throw IpRateLimitException()
    }
    
    // 3. 사용자 제한 (대기 허용)
    if (!rateLimiter.acquire("user:$userId", 60, 100, 5000)) {
        throw UserRateLimitException()
    }
    
    // 처리
}
```

### 4. 로깅 및 모니터링
```kotlin
fun acquire(key: String): Boolean {
    val startTime = System.currentTimeMillis()
    val acquired = rateLimiter.acquire(key, 60, 100, 5000)
    val elapsed = System.currentTimeMillis() - startTime
    
    if (elapsed > 1000) {
        logger().warn("Rate limiter waited ${elapsed}ms for key=$key")
    }
    
    // 메트릭 전송
    metrics.recordRateLimiterWait(key, elapsed)
    metrics.recordRateLimiterResult(key, acquired)
    
    return acquired
}
```

## 테스트

테스트 코드는 `RedisSlidingWindowRateLimiterTest.kt`를 참고하세요.
- Testcontainers로 실제 Redis 환경에서 테스트
- 동시성, 슬라이딩 윈도우, 대기 기능 검증

```bash
cd /Users/ghkdqhrbals/personal/mod/client
./gradlew test --tests "RedisSlidingWindowRateLimiterTest"
```

## 문제 해결

### Q: 대기 시간이 너무 길어요
A: `maxWaitMillis`를 줄이거나, `windowSec`과 `limit`를 조정하세요.

### Q: 분산 환경에서 정확도가 떨어져요
A: Redis 클러스터 사용 시 같은 키가 같은 샤드에 있는지 확인하세요.

### Q: 메모리 사용량이 계속 증가해요
A: Redis 키에 TTL이 제대로 설정되는지 확인하세요. `EXPIRE` 명령어 로그 확인.

### Q: 성능이 느려요
A: 
- Redis 연결 풀 크기 확인
- `windowSec`이 너무 크지 않은지 확인
- Lua 스크립트 최적화 (이미 최적화됨)

