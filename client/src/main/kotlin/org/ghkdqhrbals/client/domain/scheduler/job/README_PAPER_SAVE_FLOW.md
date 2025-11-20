# Subscribe Batch - Paper 저장 및 Summary 처리 흐름

## 전체 흐름

```
┌─────────────────────────────────────────────────────────────┐
│           1. Subscribe Batch Job                            │
│  SubscribePaperChunkProcessor                               │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Subscribe 처리:                                            │
│  ├─ ArXiv API 호출 (페이지네이션)                          │
│  ├─ ArxivService.search(event)                             │
│  │   ├─ 신규 논문 필터링 (DB에 없는 것만)                  │
│  │   ├─ ✅ Paper DB 저장 (paperRepository.saveAll)        │
│  │   └─ SummaryEvent 발행 (EventPublisher)                │
│  └─ 다음 페이지 반복                                       │
│                                                             │
└─────────────────────────────────────────────────────────────┘
                            ↓
                    Redis Stream
                            ↓
┌─────────────────────────────────────────────────────────────┐
│           2. Summary Stream Listener                        │
│  SummaryStreamListener (Consumer)                           │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Summary 처리:                                              │
│  ├─ SummaryEvent 수신                                      │
│  ├─ LLM 요약 생성 (llmClient.summarizePaper)               │
│  ├─ arxivId로 Paper 조회                                   │
│  │   ├─ 있으면: Summary 업데이트 ✅                        │
│  │   └─ 없으면: 로그 남기고 스킵 ⚠️                       │
│  └─ Progress 업데이트                                      │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

## 1. Batch에서 Paper 저장

### ArxivService.search()

**역할:** 신규 논문을 DB에 저장하고 Summary 이벤트 발행

```kotlin
fun search(event: PaperSearchAndStoreEvent): List<SummaryEvent> {
    val response = arxivHttpClient.search(event)
    
    // 1. ArXiv 응답에서 arxivId 수집
    val incomingIds = response.papers.mapNotNull { it.arxivId }
    if (incomingIds.isEmpty()) return emptyList()
    
    // 2. DB에 이미 존재하는 논문 조회
    val existingIds = paperRepository.findAllByArxivIdIn(incomingIds)
        .map { it.arxivId }
        .toSet()
    
    // 3. 신규 논문만 필터링
    val newPapers = response.papers.filter { p ->
        val id = p.arxivId
        id != null && id !in existingIds
    }
    
    if (newPapers.isEmpty()) {
        logger().info("신규 논문 없음. totalResponse=${incomingIds.size}")
        return emptyList()
    }
    
    logger().info("📄 신규 논문 ${newPapers.size}건 발견")
    
    // 4. ✅ 신규 논문을 DB에 저장
    try {
        paperRepository.saveAll(newPapers)
        logger().info("✅ 신규 논문 ${newPapers.size}건 DB 저장 완료")
    } catch (e: Exception) {
        logger().error("❌ 논문 저장 실패: ${e.message}", e)
        return emptyList()
    }
    
    // 5. Summary 이벤트 발행
    return newPapers.map { it.toSummaryEvent() }.toList()
}
```

**주요 변경사항:**
- ✅ `paperRepository.saveAll(newPapers)` 추가
- ✅ 저장 성공 시에만 SummaryEvent 발행
- ✅ 저장 실패 시 빈 리스트 반환 (Summary 이벤트 발행 안 함)

## 2. Summary에서 Paper 업데이트

### SummaryStreamListener.handleSummaryEvent()

**역할:** arxivId로 Paper를 찾아서 요약만 업데이트

```kotlin
// LLM 요약 생성
val analysis = llmClient.summarizePaper(
    event.abstract ?: "",
    event.maxLength,
    event.journalRefRaw
)

// arxivId 확인
val arxivId = event.arxivId
if (arxivId.isNullOrBlank()) {
    logger().warn("⚠️ No arxivId in event, skipping")
    incrementProgress(searchEventId, "failed")
    acknowledge(message)
    return
}

// ✅ arxivId로 Paper 조회
val paper = paperRepository.findByArxivId(arxivId)

if (paper == null) {
    logger().warn("⚠️ Paper not found for arxivId=$arxivId, skipping")
    incrementProgress(searchEventId, "failed")
    acknowledge(message)
    return
}

// ✅ Paper 업데이트 (요약만)
val updated = paper.copy(
    summary = analysis.coreContribution,
    novelty = analysis.noveltyAgainstPreviousWorks,
    summarizedAt = OffsetDateTime.now(),
    journal = analysis.journalName ?: paper.journal,
    impactFactor = analysis.impactFactor ?: paper.impactFactor
)

paperRepository.save(updated)

logger().title(LogTitle.PAPER, "✅ Updated paper summary: arxivId=$arxivId")
incrementProgress(searchEventId, "completed")
```

**주요 변경사항:**
- ✅ Paper가 없으면 경고 로그만 남기고 스킵
- ✅ `incrementProgress(searchEventId, "failed")` 호출로 실패 카운트
- ✅ 명확한 로그 메시지

## 데이터 흐름

### 시나리오 1: 정상 처리

```
1. Batch: Subscribe "Transformer" 처리
   └─ ArXiv 검색: 10개 논문 발견

2. ArxivService.search()
   ├─ DB 조회: 기존 논문 3개
   ├─ 신규 논문 필터링: 7개
   ├─ ✅ paperRepository.saveAll(7개)
   └─ SummaryEvent 7개 발행

3. Redis Stream
   └─ 7개 SummaryEvent 저장

4. SummaryStreamListener (20 consumers)
   ├─ Event 1 처리:
   │   ├─ LLM 요약 생성
   │   ├─ arxivId로 Paper 조회 ✅ (있음)
   │   └─ Summary 업데이트 ✅
   ├─ Event 2 처리: ...
   └─ Event 7 처리: ...

5. 결과
   ├─ Paper 저장: 7개
   └─ Summary 업데이트: 7개
```

### 시나리오 2: Paper 없는 경우

```
1. SummaryEvent 수신
   └─ arxivId: "2501.12345"

2. LLM 요약 생성 ✅
   └─ analysis.coreContribution: "새로운 방법 제안..."

3. Paper 조회
   └─ paperRepository.findByArxivId("2501.12345") → null ❌

4. 처리
   ├─ ⚠️ 로그: "Paper not found for arxivId=2501.12345, skipping"
   ├─ incrementProgress(searchEventId, "failed")
   └─ acknowledge(message) ✅

5. 결과
   └─ Summary 저장 안 됨, 메시지는 ACK됨
```

## 로그 예시

### Batch - Paper 저장
```
╔═══════════════════════════════════════════════════════════════════
║ 📚 구독 처리 시작
║ ├─ 구독 ID: 1
║ ├─ 구독 이름: Transformer
║ └─ 구독 타입: KEYWORD
╚═══════════════════════════════════════════════════════════════════

🔍 [Subscribe#1] 'Transformer' (KEYWORD) Page#0 - ArXiv 검색 시작...
   ├─ ArXiv Query: 'all:Transformer'
   └─ 검색 결과: 10개 논문 발견

[INFO] 📄 신규 논문 7건 발견. totalResponse=10
[INFO] ✅ 신규 논문 7건 DB 저장 완료

✅ [Subscribe#1] 'Transformer' (KEYWORD) Page#0 - 성공: 7개 논문 처리 | 누적: 7개
```

### Summary - Paper 업데이트 성공
```
[SUMMARY] Processing summary for paperId=2501.12345, searchEventId=abc-123
[SUMMARY] LLM completed in 2341ms for paperId=2501.12345
[PAPER] ✅ Updated paper summary: arxivId=2501.12345
   ├─ Core: 새로운 트랜스포머 아키텍처를 제안하여...
   ├─ Novelty: 기존 모델 대비 30% 성능 향상...
   └─ Journal: Nature (IF: 42.778)
[SUMMARY] ✅ Completed summary for paperId=2501.12345
```

### Summary - Paper 없음
```
[SUMMARY] Processing summary for paperId=2501.99999, searchEventId=def-456
[SUMMARY] LLM completed in 1923ms for paperId=2501.99999
[WARN] [SummaryListener] ⚠️ Paper not found for arxivId=2501.99999, skipping (may not be saved yet)
```

## 에러 처리

### 1. Batch에서 저장 실패
```kotlin
try {
    paperRepository.saveAll(newPapers)
    logger().info("✅ 신규 논문 ${newPapers.size}건 DB 저장 완료")
} catch (e: Exception) {
    logger().error("❌ 논문 저장 실패: ${e.message}", e)
    return emptyList()  // Summary 이벤트 발행 안 함
}
```

**결과:**
- Summary 이벤트가 발행되지 않음
- 해당 페이지는 실패로 기록
- 다음 페이지는 계속 진행

### 2. Summary에서 Paper 없음
```kotlin
val paper = paperRepository.findByArxivId(arxivId)
if (paper == null) {
    logger().warn("⚠️ Paper not found for arxivId=$arxivId, skipping")
    incrementProgress(searchEventId, "failed")
    acknowledge(message)
    return
}
```

**결과:**
- failed 카운트 증가
- 메시지는 ACK (재처리 안 됨)
- 다음 메시지 처리 계속

## 성능 고려사항

### Batch 저장
```kotlin
// ✅ Good: Bulk insert
paperRepository.saveAll(newPapers)  // 한 번에 여러 건

// ❌ Bad: 개별 insert
newPapers.forEach { paperRepository.save(it) }  // N번 호출
```

### Summary 조회
```kotlin
// ✅ Good: 단일 조회
val paper = paperRepository.findByArxivId(arxivId)

// 인덱스 필요:
CREATE INDEX idx_paper_arxiv_id ON paper(arxiv_id);
```

## 모니터링

### 메트릭
```kotlin
// Batch
meterRegistry.counter("batch.papers.saved").increment(newPapers.size)
meterRegistry.counter("batch.papers.skipped").increment(existingIds.size)

// Summary
meterRegistry.counter("summary.papers.updated").increment()
meterRegistry.counter("summary.papers.not_found").increment()
```

### 알림
```yaml
# Paper 저장 실패율
- alert: PaperSaveFailureHigh
  expr: rate(batch_papers_save_failure[5m]) > 0.1

# Paper not found 비율
- alert: SummaryPaperNotFoundHigh
  expr: rate(summary_papers_not_found[5m]) > 0.1
```

## 문제 해결

### Q: Summary에서 Paper를 찾을 수 없어요
A: 
1. Batch 로그 확인 → Paper 저장이 성공했는지 확인
2. arxivId가 정확한지 확인
3. 트랜잭션 커밋 확인

### Q: 중복 Paper가 저장되어요
A: 
- `arxiv_id`에 UNIQUE 제약조건 추가
- `findAllByArxivIdIn()` 쿼리 확인

### Q: 저장은 됐는데 Summary가 안 업데이트되어요
A:
- Redis Stream 로그 확인
- SummaryEvent가 제대로 발행되었는지 확인
- Consumer 개수 확인 (20개 작동 중인지)

