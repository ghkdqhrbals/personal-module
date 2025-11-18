# 이벤트 아키텍처 단순화 마이그레이션

## 변경 개요

복잡한 이벤트 구조를 **2개의 핵심 이벤트로 단순화**하여 유지보수성과 명확성을 대폭 향상시켰습니다.

### 변경 전
- **7개의 복잡한 이벤트 타입**:
  - `PaperSearchRequested`, `PaperDiscovered`, `SummaryJobStarted`, `SummaryCompleted`, `SummaryFailed`, `PaperMetadataUpdated`, `BatchJobStarted`, `BatchJobCompleted`
- 복잡한 이벤트 체인 및 상태 관리
- Batch 개념의 불필요한 추상화
- 다수의 Stream Listener와 Handler

### 변경 후
- **2개의 명확한 이벤트**:
  1. `PaperSearchAndStoreEvent` - arXiv 검색 및 저장
  2. `SummaryEvent` (optional) - 논문 요약 생성

## 새로운 이벤트 흐름

```
사용자 요청
    ↓
ArxivService.searchAsync()
    ↓
PaperSearchAndStoreEvent 발행 → searchEventId 즉시 반환
    ↓
PaperSearchAndStoreStreamListener
    ├─ arXiv API 호출
    ├─ 검색 결과 저장
    ├─ Redis 진행상태 업데이트
    └─ (shouldSummarize=true인 경우)
        → SummaryEvent 발행 (각 논문마다)
            ↓
        SummaryStreamListener
            ├─ LLM 호출 (요약 생성)
            ├─ DB 업데이트
            ├─ Redis 진행상태 업데이트
            └─ 모든 요약 완료시 자동으로 COMPLETED 상태로 변경
```

## 주요 개선사항

### 1. 단순성
- 이벤트 타입이 7개 → 2개로 감소
- 불필요한 Batch 개념 제거
- 명확한 책임 분리

### 2. 즉시 응답
- API 호출 즉시 `searchEventId` 반환
- 백그라운드에서 비동기 처리
- 실시간 진행상태 추적 가능

### 3. 진행상태 추적
Redis 키: `search:{searchEventId}:progress`
```json
{
  "status": "IN_PROGRESS | COMPLETED | FAILED",
  "total": "검색된 논문 수",
  "completed": "요약 완료 수",
  "failed": "요약 실패 수",
  "error": "에러 메시지 (실패시)"
}
```

### 4. 자동 상태 관리
- 검색 시작: `IN_PROGRESS`
- 요약 없이 완료: `COMPLETED` (즉시)
- 요약 진행중: 각 요약 완료시마다 `completed` 증가
- 모든 요약 완료: 자동으로 `COMPLETED` 상태로 전환

## 변경된 파일

### 새로 생성
- `PaperSearchAndStoreStreamListener.kt` - 검색 및 저장 처리
- `SummaryStreamListener.kt` - 요약 생성 처리

### 수정
- `Event.kt` - 이벤트 정의 단순화
- `EventPublisher.kt` - 스트림 매핑 단순화
- `ArxivService.kt` - 이벤트 발행 및 상태 조회만 담당
- `application.yaml` - 스트림 설정 단순화

### 제거
- `PaperDiscoveredStreamListener.kt`
- `SummaryCompletedStreamListener.kt`
- `SummaryFailedStreamListener.kt`
- `BatchJobStartedStreamListener.kt`
- `BatchJobCompletedStreamListener.kt`
- `PaperMetadataUpdatedStreamListener.kt`
- `PaperDiscoveredHandler.kt`
- `PaperEventPublisher.kt`
- `SummaryQueueConsumer.kt` (백업으로 이동)
- `SummaryQueueProducer.kt`

## API 사용법

### 1. 검색 요청
```http
POST /api/papers/arxiv/search
Content-Type: application/json

{
  "query": "machine learning",
  "categories": ["cs.AI", "cs.LG"],
  "maxResults": 10,
  "summarize": true
}
```

**응답:**
```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "message": "Search initiated. Use GET /api/papers/arxiv/search/{eventId}/status to check progress."
}
```

### 2. 진행상태 조회
```http
GET /api/papers/arxiv/search/{eventId}/status
```

**응답:**
```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "IN_PROGRESS",
  "batch": {
    "totalPapers": 10,
    "category": null,
    "startedAt": null
  },
  "summary": {
    "total": 10,
    "completed": 3,
    "failed": 0,
    "processing": 7,
    "progressPercent": 30.0,
    "isDone": false
  },
  "papers": [...]
}
```

## Redis 스트림 구조

### 1. PaperSearchAndStore 스트림
- 키: `domain:events:paper-search-and-store`
- Consumer Group: `event-handlers`
- Consumer: `paper-search-and-store-consumer`

### 2. Summary 스트림
- 키: `domain:events:summary`
- Consumer Group: `event-handlers`
- Consumer: `summary-consumer`

## 설정

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

### 스트림 모니터링
```bash
# 스트림 길이 확인
redis-cli XLEN domain:events:paper-search-and-store
redis-cli XLEN domain:events:summary

# Consumer Group 정보
redis-cli XINFO GROUPS domain:events:paper-search-and-store
redis-cli XINFO GROUPS domain:events:summary

# 스트림 내용 확인
redis-cli XRANGE domain:events:paper-search-and-store - + COUNT 10
```

### 진행상태 모니터링
```bash
# 특정 검색의 진행상태 확인
redis-cli HGETALL search:{searchEventId}:progress
```

## 마이그레이션 체크리스트

- [x] 이벤트 도메인 단순화 (2개 이벤트만 유지)
- [x] EventPublisher 스트림 매핑 업데이트
- [x] application.yaml 스트림 설정 단순화
- [x] PaperSearchAndStoreStreamListener 구현
- [x] SummaryStreamListener 구현
- [x] ArxivService 단순화
- [x] 기존 복잡한 Listener 제거
- [x] 기존 Queue 기반 Consumer 제거
- [x] 빌드 성공 확인
- [ ] 통합 테스트 실행
- [ ] 기존 Redis 데이터 정리

## 배포 전 준비

### 1. Redis 데이터 정리
```bash
# 기존 스트림 삭제
redis-cli DEL domain:events:paper-discovered
redis-cli DEL domain:events:summary-job-started
redis-cli DEL domain:events:summary-completed
redis-cli DEL domain:events:summary-failed
redis-cli DEL domain:events:paper-metadata-updated
redis-cli DEL domain:events:batch-job-started
redis-cli DEL domain:events:batch-job-completed

# 기존 진행상태 키 정리 (필요시)
redis-cli KEYS "batch:*:progress" | xargs redis-cli DEL
```

### 2. 새로운 Consumer Group 생성
```bash
# 애플리케이션 시작시 자동으로 생성되지만, 수동으로도 가능
redis-cli XGROUP CREATE domain:events:paper-search-and-store event-handlers 0 MKSTREAM
redis-cli XGROUP CREATE domain:events:summary event-handlers 0 MKSTREAM
```

## 장점

### 1. 단순성
- 이벤트 타입 85% 감소 (7개 → 2개)
- 이해하기 쉬운 명확한 흐름
- 코드 복잡도 대폭 감소

### 2. 유지보수성
- 변경 지점 최소화
- 명확한 책임 분리
- 디버깅 용이

### 3. 성능
- 불필요한 이벤트 체인 제거
- 직접적인 처리 흐름
- Redis 스트림 효율적 사용

### 4. 확장성
- 새로운 기능 추가 시 명확한 확장 포인트
- 이벤트별 독립적인 스케일링 가능

## 롤백 계획

문제 발생 시:
1. `SummaryQueueConsumer.kt.backup` 파일 복원
2. 기존 코드 재배포
3. Redis 스트림 재설정


