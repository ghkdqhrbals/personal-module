# RedisSlidingWindowRateLimiter 테스트 가이드

## 개요
`RedisSlidingWindowRateLimiter`를 테스트하는 통합 테스트 코드입니다.
Redis Testcontainers를 사용하여 실제 Redis 인스턴스를 Docker 컨테이너로 실행하고 테스트합니다.

## 주요 기능

### 지능적인 대기 메커니즘
`acquire` 메서드는 토큰을 즉시 획득할 수 없을 때 효율적으로 대기합니다:

1. **가장 오래된 요청의 만료 시간 계산**: Redis에서 가장 오래된 요청이 윈도우 밖으로 나가는 정확한 시간을 계산
2. **정확한 대기 시간**: 불필요한 폴링 없이 필요한 시간만큼만 대기
3. **타임아웃 처리**: `maxWaitMillis` 이내에 토큰을 획득하지 못하면 `false` 반환
4. **로깅**: 대기 상황을 디버그 로그로 추적 가능

```kotlin
// 예시: 3초 윈도우에 1개 제한
rateLimiter.acquire(key, windowSec = 3, limit = 1, maxWaitMillis = 10_000)
// -> 토큰이 없으면 최대 3초 대기 후 자동으로 획득
```

## 필수 요구사항
- Docker가 설치되어 있고 실행 중이어야 합니다
- JDK 21

## 테스트 실행 방법

### 전체 테스트 실행
```bash
cd /Users/ghkdqhrbals/personal/mod/client
./gradlew test --tests "RedisSlidingWindowRateLimiterTest"
```

### 개별 테스트 실행
```bash
./gradlew test --tests "RedisSlidingWindowRateLimiterTest.토큰 획득 성공 - limit 이내"
./gradlew test --tests "RedisSlidingWindowRateLimiterTest.병렬 요청 - 정확히 limit 만큼만 허용"
```

## 테스트 케이스 설명

### 1. `토큰 획득 성공 - limit 이내`
- **목적**: 제한 이내의 요청은 모두 성공하는지 확인
- **검증**: limit=5일 때, 5번의 요청이 모두 성공

### 2. `토큰 획득 실패 - limit 초과 시 대기 후 타임아웃`
- **목적**: limit를 초과한 요청은 타임아웃으로 실패하는지 확인
- **검증**: limit=3일 때, 4번째 요청은 maxWaitMillis 후 실패

### 3. `슬라이딩 윈도우 - 시간 경과 후 새 토큰 획득 가능`
- **목적**: 슬라이딩 윈도우가 제대로 동작하는지 확인
- **검증**: 윈도우 시간 경과 후 새로운 요청 가능

### 4. `병렬 요청 - 정확히 limit 만큼만 허용`
- **목적**: 동시 요청 환경에서도 정확히 limit만큼만 허용되는지 확인
- **검증**: 20개의 동시 요청 중 limit=5개만 성공

### 5. `다른 키는 독립적으로 동작`
- **목적**: 각 키별로 독립적인 레이트 리미팅이 적용되는지 확인
- **검증**: key1과 key2가 서로 영향을 주지 않음

### 6. `Redis 키 만료 확인`
- **목적**: Redis 키에 TTL이 올바르게 설정되는지 확인
- **검증**: 키가 존재하고 windowSec * 2 이하의 TTL이 설정됨

### 7. `정확한 슬라이딩 윈도우 동작 검증`
- **목적**: 슬라이딩 윈도우의 정확한 시간 계산 검증
- **검증**: 
  - 0초: 3개 요청 성공, 4번째 실패
  - 1.5초 후: 여전히 실패 (윈도우 내)
  - 3.5초 후: 성공 (윈도우 밖)

### 8. `대기 기능 - 윈도우 경과 후 자동으로 토큰 획득` ⭐ NEW
- **목적**: 충분한 대기 시간을 주면 자동으로 토큰을 획득하는지 확인
- **검증**: limit 초과 후에도 maxWaitMillis 이내에 토큰 획득 성공

### 9. `대기 기능 - 효율적인 대기 시간 계산` ⭐ NEW
- **목적**: 불필요한 폴링 없이 정확히 필요한 시간만큼만 대기하는지 확인
- **검증**: 윈도우 시간(3초) ± 여유시간(500ms) 이내에 획득

### 10. `대기 기능 - 여러 요청이 순차적으로 대기하며 획득` ⭐ NEW
- **목적**: 여러 요청이 순차적으로 대기하며 모두 성공하는지 확인
- **검증**: 3개 요청이 각각 윈도우 시간만큼 대기하며 모두 성공

### 11. `람다 방식 - 토큰 획득 후 람다 실행` ⭐ NEW
- **목적**: 람다를 받는 acquire 메서드가 정상 동작하는지 확인
- **검증**: 다양한 타입의 반환값이 제대로 전달됨

### 12. `람다 방식 - 타임아웃 시 LockTimeoutException 발생` ⭐ NEW
- **목적**: 토큰 획득 실패 시 LockTimeoutException이 발생하는지 확인
- **검증**: 예외 메시지와 속성(key, attemptCount, elapsedMillis) 확인

### 13. `람다 방식 - 충분한 대기 시간 후 성공` ⭐ NEW
- **목적**: 충분한 대기 시간을 주면 람다가 정상 실행되는지 확인
- **검증**: 윈도우 시간 이후 성공적으로 람다 실행

### 14. `람다 방식 - 예외 발생 시 전파` ⭐ NEW
- **목적**: 람다 내부에서 발생한 예외가 올바르게 전파되는지 확인
- **검증**: IllegalStateException이 그대로 전파됨

## 추가된 의존성

### build.gradle.kts
```kotlin
// testcontainers
testImplementation("org.testcontainers:testcontainers:1.19.3")
testImplementation("org.testcontainers:junit-jupiter:1.19.3")
testImplementation("com.redis.testcontainers:testcontainers-redis-junit:1.6.4")
```

## 주요 특징

1. **실제 Redis 사용**: Testcontainers를 통해 실제 Redis를 Docker 컨테이너로 실행
2. **자동 라이프사이클 관리**: 테스트 시작/종료 시 컨테이너 자동 시작/종료
3. **격리된 테스트**: 각 테스트 메서드마다 Redis 데이터 초기화
4. **동시성 테스트**: 멀티스레드 환경에서의 정확성 검증
5. **슬라이딩 윈도우 검증**: 시간 기반 윈도우의 정확한 동작 확인

## 문제 해결

### Docker가 실행되지 않는 경우
```
Could not find a valid Docker environment.
```
→ Docker Desktop을 실행하세요.

### 포트 충돌
Testcontainers는 동적 포트를 사용하므로 포트 충돌 문제가 거의 없습니다.

### 테스트 타임아웃
일부 테스트는 슬라이딩 윈도우를 검증하기 위해 대기 시간이 필요합니다.
`정확한 슬라이딩 윈도우 동작 검증` 테스트는 약 4초 정도 소요됩니다.

