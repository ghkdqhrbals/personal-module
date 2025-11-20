# RedisSlidingWindowRateLimiter 람다 방식 사용 가이드

## 개요
`acquire` 메서드에 람다를 전달하면 토큰 획득 후 자동으로 실행되며, 타임아웃 시 `LockTimeoutException`이 발생합니다.

## 두 가지 방식 비교

### 1. Boolean 반환 방식 (기존)
```kotlin
fun processRequest(userId: String): String {
    val acquired = rateLimiter.acquire(
        key = "user:$userId",
        windowSec = 60,
        limit = 100,
        maxWaitMillis = 5000
    )
    
    if (!acquired) {
        throw RuntimeException("Rate limit exceeded")
    }
    
    // 비즈니스 로직
    return performTask()
}
```

### 2. 람다 방식 (권장) ⭐
```kotlin
fun processRequest(userId: String): String {
    return rateLimiter.acquire(
        key = "user:$userId",
        windowSec = 60,
        limit = 100,
        maxWaitMillis = 5000
    ) {
        // 비즈니스 로직 - 토큰이 획득된 상태에서만 실행
        performTask()
    }
    // 타임아웃 시 자동으로 LockTimeoutException 발생
}
```

## 람다 방식의 장점

### 1. 간결한 코드
- 토큰 획득 실패 처리가 자동화
- 보일러플레이트 코드 제거

### 2. 타입 안정성
- 람다의 반환 타입이 메서드 반환 타입과 일치
- 컴파일 시점에 타입 체크

### 3. 예외 처리 일관성
- 타임아웃은 항상 `LockTimeoutException`
- 람다 내부 예외는 그대로 전파

## 사용 예제

### 기본 사용
```kotlin
@Service
class UserService(
    private val rateLimiter: RedisSlidingWindowRateLimiter
) {
    fun getUserData(userId: String): UserData {
        return rateLimiter.acquire(
            key = "api:user:$userId",
            windowSec = 60,
            limit = 100,
            maxWaitMillis = 3000
        ) {
            // 토큰 획득 성공 - DB 조회
            userRepository.findById(userId)
                ?: throw NotFoundException("User not found")
        }
    }
}
```

### 예외 처리
```kotlin
@RestController
class ApiController(
    private val rateLimiter: RedisSlidingWindowRateLimiter
) {
    @GetMapping("/api/data")
    fun getData(@RequestParam userId: String): ResponseEntity<String> {
        return try {
            val data = rateLimiter.acquire(
                key = "api:$userId",
                windowSec = 60,
                limit = 100,
                maxWaitMillis = 5000
            ) {
                fetchData(userId)
            }
            ResponseEntity.ok(data)
        } catch (e: LockTimeoutException) {
            // 레이트 리미트 초과
            logger.warn("Rate limit exceeded for user=$userId: ${e.message}")
            ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", "60")
                .body("Too many requests")
        } catch (e: NotFoundException) {
            // 비즈니스 예외
            ResponseEntity.notFound().build()
        }
    }
}
```

### 여러 단계 처리
```kotlin
@Service
class OrderService(
    private val rateLimiter: RedisSlidingWindowRateLimiter
) {
    fun createOrder(userId: String, items: List<Item>): Order {
        return rateLimiter.acquire(
            key = "order:user:$userId",
            windowSec = 300, // 5분
            limit = 10,      // 5분에 10개 주문
            maxWaitMillis = 10_000
        ) {
            // 1. 재고 확인
            checkInventory(items)
            
            // 2. 결제 처리
            val payment = processPayment(userId, items)
            
            // 3. 주문 생성
            val order = orderRepository.save(Order(userId, items, payment))
            
            // 4. 알림 발송
            sendNotification(userId, order)
            
            order
        }
    }
}
```

### 외부 API 호출
```kotlin
@Component
class ExternalApiClient(
    private val rateLimiter: RedisSlidingWindowRateLimiter,
    private val restClient: RestClient
) {
    fun fetchUserInfo(userId: String): UserInfo {
        return rateLimiter.acquire(
            key = "external-api:global",
            windowSec = 1,   // 1초
            limit = 100,     // 초당 100개
            maxWaitMillis = 500
        ) {
            restClient.get()
                .uri("/api/users/$userId")
                .retrieve()
                .body(UserInfo::class.java)
                ?: throw IllegalStateException("Empty response")
        }
    }
}
```

### 배치 처리
```kotlin
@Service
class BatchProcessor(
    private val rateLimiter: RedisSlidingWindowRateLimiter
) {
    fun processItems(items: List<String>) {
        items.forEach { item ->
            try {
                rateLimiter.acquire(
                    key = "batch:global",
                    windowSec = 1,
                    limit = 10,
                    maxWaitMillis = 60_000 // 배치는 긴 대기 시간 허용
                ) {
                    processItem(item)
                }
            } catch (e: LockTimeoutException) {
                logger.error("Failed to process item=$item: timeout after ${e.elapsedMillis}ms")
                // 실패 항목 기록
                failedItems.add(item)
            }
        }
    }
}
```

## LockTimeoutException 정보

### 예외 속성
```kotlin
class LockTimeoutException(
    message: String,        // 에러 메���지
    val key: String,        // Redis 키
    val attemptCount: Int,  // 시도 횟수
    val elapsedMillis: Long // 경과 시간 (밀리초)
) : RuntimeException(message)
```

### 예외 처리 예제
```kotlin
try {
    rateLimiter.acquire("key", 60, 100, 5000) {
        doSomething()
    }
} catch (e: LockTimeoutException) {
    logger.warn("""
        Rate limit timeout:
        - key: ${e.key}
        - attempts: ${e.attemptCount}
        - elapsed: ${e.elapsedMillis}ms
        - message: ${e.message}
    """.trimIndent())
    
    // 메트릭 기록
    metrics.recordRateLimitTimeout(e.key, e.attemptCount, e.elapsedMillis)
}
```

## 모범 사례

### 1. 짧은 대기 시간 - 사용자 대면 API
```kotlin
// 사용자는 짧은 응답 시간을 기대
rateLimiter.acquire(key, 60, 100, maxWaitMillis = 1000) {
    handleUserRequest()
}
```

### 2. 긴 대기 시간 - 백그라운드 작업
```kotlin
// 백그라운드는 대기 가능
rateLimiter.acquire(key, 60, 10, maxWaitMillis = 60_000) {
    processBackgroundTask()
}
```

### 3. 대기 없음 - 즉시 확인만
```kotlin
// 즉시 실패하고 싶은 경우
try {
    rateLimiter.acquire(key, 60, 100, maxWaitMillis = 0) {
        criticalOperation()
    }
} catch (e: LockTimeoutException) {
    // 즉시 실패 처리
    returnCached()
}
```

### 4. 재시도 로직과 결합
```kotlin
fun fetchWithRetry(userId: String, retries: Int = 3): Data {
    repeat(retries) { attempt ->
        try {
            return rateLimiter.acquire(
                key = "api:$userId",
                windowSec = 60,
                limit = 100,
                maxWaitMillis = 5000
            ) {
                fetchData(userId)
            }
        } catch (e: LockTimeoutException) {
            if (attempt == retries - 1) throw e
            logger.info("Retry $attempt after rate limit timeout")
            Thread.sleep(1000 * (attempt + 1)) // 지수 백오프
        }
    }
    throw IllegalStateException("Unreachable")
}
```

## Boolean 방식은 언제 사용?

람다 방식이 권장되지만, 다음 경우에는 Boolean 방식을 고려하세요:

```kotlin
// 1. 조건부 실행
if (rateLimiter.acquire(key, 60, 100, 0)) {
    // 가능하면 실행
    doOptionalTask()
} else {
    // 불가능하면 스킵
    logger.info("Skipping optional task due to rate limit")
}

// 2. 여러 리소스 체크
val canProceed = rateLimiter.acquire(key1, 60, 100, 0) &&
                 rateLimiter.acquire(key2, 60, 100, 0)

if (canProceed) {
    performTask()
}
```

