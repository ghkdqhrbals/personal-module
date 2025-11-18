# 단순화된 이벤트 아키텍처

## 개요

논문 검색 및 요약 시스템을 **2개의 핵심 이벤트**로 단순화하여 명확하고 유지보수하기 쉬운 구조로 개선했습니다.

## 아키텍처

### 이벤트 흐름

```
사용자 → POST /api/papers/arxiv/search
          ↓
    searchEventId 즉시 반환
          ↓
PaperSearchAndStoreEvent 발행
          ↓
    [Redis Stream: paper-search-and-store]
          ↓
PaperSearchAndStoreStreamListener
    ├─ arXiv API 호출
    ├─ 논문 저장
    ├─ 진행상태 초기화
    └─ SummaryEvent 발행 (선택적)
          ↓
    [Redis Stream: summary]
          ↓
SummaryStreamListener
    ├─ LLM 요약 생성
    ├─ DB 업데이트
    └─ 진행상태 업데이트
          ↓
    자동 완료 처리
```

## 핵심 컴포넌트

### 1. 이벤트

#### PaperSearchAndStoreEvent
```kotlin
data class PaperSearchAndStoreEvent(
    val searchEventId: String,      // 추적 ID
    val query: String?,              // 검색어
    val categories: List<String>?,   // 카테고리
    val maxResults: Int = 10,        // 최대 결과 수
    val page: Int = 0,              // 페이지
    val fromDate: String? = null,   // 시작 날짜
    val shouldSummarize: Boolean = true  // 요약 여부
)
```

#### SummaryEvent
```kotlin
data class SummaryEvent(
    val searchEventId: String,  // 원본 검색 ID
    val paperId: String,        // 논문 ID
    val arxivId: String?,       // arXiv ID
    val title: String,          // 제목
    val abstract: String?,      // 초록
    val journalRefRaw: String? = null,
    val maxLength: Int = 120    // 요약 최대 길이
)
```

### 2. Stream Listeners

#### PaperSearchAndStoreStreamListener
**책임:**
- arXiv API 호출 및 XML 파싱
- 논문 DB 저장
- Redis 진행상태 초기화
- SummaryEvent 발행 (선택적)

**Redis 키:** `search:{searchEventId}:progress`
```json
{
  "status": "IN_PROGRESS",
  "total": "10",
  "completed": "0",
  "failed": "0"
}
```

#### SummaryStreamListener
**책임:**
- LLM 호출 (비동기)
- 논문 요약 생성
- DB 업데이트
- 진행상태 증가
- 완료 체크 및 상태 변경

**완료 조건:** `completed + failed >= total`

### 3. ArxivService

**단순화된 역할:**
- `searchAsync()`: PaperSearchAndStoreEvent 발행 → searchEventId 반환
- `getSearchStatus()`: **Redis에서 직접 진행상태 조회 (프로젝션 없음)**
  - 단일 Redis Hash를 조회하여 즉시 상태 반환
  - EventStore 200건 조회 → incrementSummary 200번 호출 같은 비효율 제거
  - O(1) 성능으로 실시간 상태 조회

```kotlin
fun searchAsync(
    query: String?,
    categories: List<String>?,
    maxResults: Int = 10,
    page: Int = 0,
    fromDate: String? = null,
    summarize: Boolean = true
): String {
    val searchEventId = UUID.randomUUID().toString()
    
    val event = PaperSearchAndStoreEvent(
        searchEventId = searchEventId,
        query = query,
        categories = categories,
        maxResults = maxResults,
        page = page,
        fromDate = fromDate,
        shouldSummarize = summarize
    )
    
    eventPublisher.publish(event)
    
    return searchEventId
}
```

## 진행상태 추적

### Redis 구조

**키:** `search:{searchEventId}:progress`

**필드:**
- `status`: `IN_PROGRESS` | `COMPLETED` | `FAILED`
- `total`: 검색된 논문 총 개수
- `completed`: 요약 완료 개수
- `failed`: 요약 실패 개수
- `error`: 에러 메시지 (FAILED 상태일 때)

**TTL:** 3600초 (1시간)

### 상태 전이

```
PENDING → IN_PROGRESS → COMPLETED
                     ↘ FAILED
```

1. **IN_PROGRESS**: PaperSearchAndStoreEvent 처리 시작
2. **COMPLETED**: 
   - `summarize=false`: 즉시 완료
   - `summarize=true`: 모든 요약 완료 시 (`completed + failed >= total`)
3. **FAILED**: 검색 또는 처리 중 에러 발생

## API 사용 예시

### 1. 검색 요청

```bash
curl -X POST http://localhost:8080/api/papers/arxiv/search \
  -H "Content-Type: application/json" \
  -d '{
    "query": "machine learning",
    "categories": ["cs.AI"],
    "maxResults": 5,
    "summarize": true
  }'
```

**응답:**
```json
{
  "eventId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "message": "Search initiated. Use GET /api/papers/arxiv/search/{eventId}/status to check progress."
}
```

### 2. 진행상태 조회

```bash
curl http://localhost:8080/api/papers/arxiv/search/a1b2c3d4-e5f6-7890-abcd-ef1234567890/status
```

**응답 (진행 중):**
```json
{
  "eventId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "status": "IN_PROGRESS",
  "summary": {
    "total": 5,
    "completed": 2,
    "failed": 0,
    "processing": 3,
    "progressPercent": 40.0,
    "isDone": false
  },
  "papers": [...]
}
```

**응답 (완료):**
```json
{
  "eventId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "status": "COMPLETED",
  "summary": {
    "total": 5,
    "completed": 5,
    "failed": 0,
    "processing": 0,
    "progressPercent": 100.0,
    "isDone": true
  },
  "papers": [...]
}
```

## ���정

### application.yaml

```yaml
redis:
  host: localhost
  port: 6379
  stream:
    events:
      paper-search-and-store: domain:events:paper-search-and-store
      summary: domain:events:summary
      group: event-handlers
```

## 모니터링

### Redis CLI

```bash
# 스트림 모니터링
redis-cli XLEN domain:events:paper-search-and-store
redis-cli XLEN domain:events:summary

# 진행상태 확인
redis-cli HGETALL search:{searchEventId}:progress

# Consumer Group 정보
redis-cli XINFO GROUPS domain:events:paper-search-and-store
redis-cli XINFO CONSUMERS domain:events:summary event-handlers

# Pending 메시지 확인
redis-cli XPENDING domain:events:summary event-handlers
```

### 로그

```bash
# PaperSearchAndStoreListener 로그
[PaperSearchAndStoreListener] Processing searchEventId=xxx
[PaperSearchAndStoreListener] Found 5 papers from arXiv
[PaperSearchAndStoreListener] Saved 3 new papers
[PaperSearchAndStoreListener] Published 3 SummaryEvents

# SummaryListener 로그
[SummaryListener] Processing summary for paperId=xxx
[SummaryListener] LLM completed in 2500ms
[SummaryListener] Updated paper: arxivId=xxx
[SummaryListener] 🎉 All summaries completed for searchEventId=xxx
```

## 개발 가이드

### 새로운 기능 추가

#### 1. 새로운 이벤트 타입 추가 (필요시)

```kotlin
// Event.kt
data class NewFeatureEvent(
    val searchEventId: String,
    val data: String,
    ...
) : PaperEvent(...)
```

#### 2. Listener 구현

```kotlin
@Component
class NewFeatureStreamListener(
    private val redisTemplate: StringRedisTemplate,
    ...
) : StreamListener<...> {
    // 구현
}
```

#### 3. Stream 설정 추가

```yaml
redis:
  stream:
    events:
      new-feature: domain:events:new-feature
```

### 테스트

```kotlin
@SpringBootTest
class EventFlowTest {
    @Test
    fun `검색 및 요약 전체 흐름 테스트`() {
        // Given
        val searchEventId = arxivService.searchAsync(
            query = "test",
            summarize = true
        )
        
        // When
        Thread.sleep(5000) // 처리 대기
        
        // Then
        val status = arxivService.getSearchStatus(searchEventId)
        assertEquals(SearchStatus.COMPLETED, status.status)
        assertTrue(status.summary.isDone)
    }
}
```

## 트러블슈팅

### 1. 진행상태가 업데이트되지 않음

**확인:**
```bash
redis-cli HGETALL search:{searchEventId}:progress
```

**해결:**
- Listener가 정상 작동하는지 로그 확인
- Redis 연결 상태 확인
- Consumer Group이 생성되었는지 확인

### 2. 요약이 진행되지 않음

**확인:**
```bash
redis-cli XINFO CONSUMERS domain:events:summary event-handlers
redis-cli XPENDING domain:events:summary event-handlers
```

**해결:**
- SummaryStreamListener 로그 확인
- LLM 서비스 상태 확인
- Pending 메시지가 있는지 확인

### 3. 메모리 누수

**확인:**
```bash
redis-cli KEYS "search:*:progress" | wc -l
```

**해결:**
- TTL이 설정되어 있는지 확인 (기본 3600초)
- 필요시 TTL 조정 또는 수동 정리

## 성능 고려사항

### 1. Redis 직접 조회
- **프로젝션 제거**: EventStore에서 200건 조회 후 루프 돌며 incrementSummary 200번 호출하는 비효율 제거
- **단일 Hash 조회**: `HGETALL search:{searchEventId}:progress` 한 번으로 모든 상태 조회
- **O(1) 성능**: 이벤트 개수와 무관하게 일정한 조회 시간
- **실시간 반영**: Listener가 업데이트하는 즉시 조회 가능

### 2. 동시성
- SummaryStreamListener는 코루틴을 사용하여 비동기 처리
- 여러 요약 작업을 동시에 처리 가능

### 3. LLM 호출 최적화
- 각 요약 작업은 독립적으로 처리
- 실패한 작업은 다른 작업에 영향을 주지 않음

### 4. Redis 최적화
- 진행상태 키에 적절한 TTL 설정
- Pub/Sub 대신 Stream 사용으로 메시지 유실 방지

## 참고

- [REDIS_STREAM_MIGRATION.md](./REDIS_STREAM_MIGRATION.md) - 마이그레이션 가이드
- Redis Streams 문서: https://redis.io/docs/data-types/streams/

