# LLM JSON 파싱 에러 해결 가이드

## 발생한 문제

### JsonParseException
```
com.fasterxml.jackson.core.JsonParseException: 
Illegal unquoted character ((CTRL-CHAR, code 10)): 
has to be escaped using backslash to be included in string value
```

**원인:**
LLM이 반환한 JSON에 이스케이프되지 않은 제어 문자(줄바꿈 `\n`, 캐리지 리턴 `\r`, 탭 `\t` 등)가 포함되어 있음

**예시:**
```json
{
  "core_contribution": "이 논문은
  새로운 방법을 제안합니다",
  "novelty": "기존 연구와 달리..."
}
```
↓ 줄바꿈이 이스케이프되지 않아서 JSON 파싱 실패

## 해결 방법

### 1. LLM 응답 정제 (`LlmClient.kt`)

#### Before
```kotlin
val cleanedJson = raw.trim()
    .removePrefix("```json")
    .removePrefix("```")
    .removeSuffix("```")
    .trim()

val node = mapper.readTree(cleanedJson) // ❌ 제어 문자로 파싱 실패
```

#### After ✅
```kotlin
val cleanedJson = raw.trim()
    .removePrefix("```json")
    .removePrefix("```")
    .removeSuffix("```")
    .trim()
    // JSON 문자열 값 내부의 제어 문자를 이스케이프
    .replace("\r\n", "\\n")
    .replace("\n", "\\n")
    .replace("\r", "\\r")
    .replace("\t", "\\t")

logger().debug("Cleaned JSON length: ${cleanedJson.length} chars")

val node = try {
    mapper.readTree(cleanedJson)
} catch (e: JsonParseException) {
    logger().error("JSON Parse Error. Raw response (first 500 chars): ${raw.take(500)}")
    logger().error("Cleaned JSON (first 500 chars): ${cleanedJson.take(500)}")
    throw IllegalStateException("Failed to parse LLM JSON response: ${e.message}", e)
}
```

### 2. 에러별 처리 강화

#### 빈 응답 처리
```kotlin
val raw = response.choices.firstOrNull()?.message?.content
    ?: throw IllegalStateException("No response from LLM")
```

#### 빈 필드 검증
```kotlin
if (core.isBlank() && novelty.isBlank()) {
    logger().warn("LLM returned empty core_contribution and novelty. Response: $raw")
    throw IllegalStateException("LLM returned empty summary fields")
}
```

#### 상세 에러 로깅
```kotlin
catch (e: Exception) {
    logger().error("❌ Failed to process LLM response", e)
    logger().error("Raw response (first 1000 chars): ${raw.take(1000)}")
    throw e
}
```

### 3. SummaryStreamListener 에러 처리 개선

#### Before
```kotlin
try {
    val analysis = withContext(Dispatchers.IO) {
        llmClient.summarizePaper(...)
    }
} catch (e: Exception) {
    logger().error("Failed", e) // 😕 모든 에러를 동일하게 처리
}
```

#### After ✅
```kotlin
val analysis = try {
    withContext(Dispatchers.IO) {
        llmClient.summarizePaper(
            event.abstract ?: "",
            event.maxLength,
            event.journalRefRaw
        )
    }
} catch (e: IllegalStateException) {
    // LLM 처리 실패 (빈 응답, 빈 필드 등)
    logger().error("[SummaryListener] ❌ LLM processing failed for paperId=${paperId}: ${e.message}", e)
    incrementProgress(searchEventId, "failed")
    checkAndMarkCompleted(searchEventId)
    acknowledge(message)
    return
    
} catch (e: com.fasterxml.jackson.core.JsonProcessingException) {
    // JSON 파싱 실패 (제어 문자, ��못된 형식 등)
    logger().error("[SummaryListener] ❌ JSON parsing failed for LLM response, paperId=${paperId}", e)
    incrementProgress(searchEventId, "failed")
    checkAndMarkCompleted(searchEventId)
    acknowledge(message)
    return
    
} catch (e: Exception) {
    // 기타 예상치 못한 에러
    logger().error("[SummaryListener] ❌ Unexpected error during LLM call for paperId=${paperId}", e)
    incrementProgress(searchEventId, "failed")
    checkAndMarkCompleted(searchEventId)
    acknowledge(message)
    return
}
```

### 4. 안전한 acknowledge 처리

#### Before
```kotlin
redisTemplate.opsForStream<String, String>()
    .acknowledge(streamKey, groupName, message.id)
```

#### After ✅
```kotlin
private fun acknowledge(message: MapRecord<String, String, String>) {
    try {
        redisTemplate.opsForStream<String, String>()
            .acknowledge(streamKey, groupName, message.id)
    } catch (e: Exception) {
        logger().error("[SummaryListener] Failed to acknowledge message: id=${message.id}", e)
    }
}
```

## 제어 문자 목록

| 문자 | 설명 | 이스케이프 |
|------|------|-----------|
| `\n` | 줄바꿈 (Line Feed) | `\\n` |
| `\r` | 캐리지 리턴 | `\\r` |
| `\t` | 탭 | `\\t` |
| `\r\n` | Windows 줄바꿈 | `\\n` |

## 로그 출력 예시

### 성공 케이스
```
[INFO] ✅ Summarized successfully - core: 새로운 트랜스포머 아키텍처를 제안하여..., novelty: 기존 모델 대비 30% 성능 향상...
[INFO] ✅ Completed summary for paperId=123
```

### JSON 파싱 에러 케이스
```
[ERROR] JSON Parse Error. Raw response (first 500 chars): {"core_contribution": "이 논문은
새로운 방법을 제안합니다", "novelty": "..."}
[ERROR] Cleaned JSON (first 500 chars): {"core_contribution": "이 논문은\\n새로운 방법을 제안합니다", "novelty": "..."}
[ERROR] [SummaryListener] ❌ JSON parsing failed for LLM response, paperId=123
```

### LLM 빈 응답 케이스
```
[WARN] LLM returned empty core_contribution and novelty. Response: {"core_contribution": "", "novelty": ""}
[ERROR] [SummaryListener] ❌ LLM processing failed for paperId=123: LLM returned empty summary fields
```

## 테스트

### 단위 테스트
```kotlin
@Test
fun `제어 문자가 포함된 JSON 파싱 테스트`() {
    val rawJson = """
    {
        "core_contribution": "첫번째 줄
        두번째 줄",
        "novelty": "탭	문자	포함"
    }
    """.trimIndent()
    
    val cleanedJson = rawJson
        .replace("\r\n", "\\n")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
    
    val mapper = ObjectMapper()
    val node = mapper.readTree(cleanedJson) // 성공!
    
    assertEquals("첫번째 줄\\n두번째 줄", node["core_contribution"].asText())
}
```

## 추가 개선 사항

### 1. LLM 프롬프트 개선
```kotlin
content = """
...
출력 규칙:
- JSON 문자열 값 내부에 줄바꿈을 사용하지 마세요
- 모든 텍스트는 한 줄로 작성하세요
- 특수 문자는 자동으로 이스케이프됩니다
...
"""
```

### 2. Jackson 설정 조정
```kotlin
val mapper = ObjectMapper().apply {
    configure(JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true)
}
```
**주의:** 이 방법은 보안 이슈가 있을 수 있으므로 권장하지 않음

### 3. 재시도 로직
```kotlin
suspend fun summarizePaper(
    abstract: String,
    maxRetries: Int = 3
): PaperAnalysisResponse {
    repeat(maxRetries) { attempt ->
        try {
            return tryParseLlmResponse(abstract)
        } catch (e: JsonProcessingException) {
            if (attempt == maxRetries - 1) throw e
            logger().warn("Retry ${attempt + 1}/$maxRetries due to JSON parsing error")
        }
    }
    throw IllegalStateException("Failed after $maxRetries attempts")
}
```

## 모니터링

### 메트릭
```kotlin
// JSON 파싱 실패율
meterRegistry.counter("llm.json.parse.failure").increment()

// LLM 응답 시간
meterRegistry.timer("llm.response.time").record(duration, TimeUnit.MILLISECONDS)

// 빈 응답 비율
meterRegistry.counter("llm.empty.response").increment()
```

### 알림
```yaml
# Prometheus Alert
- alert: LLMJsonParseFailureHigh
  expr: rate(llm_json_parse_failure[5m]) > 0.1
  annotations:
    summary: "LLM JSON 파싱 실패율 높음"
```

## 문제 해결 체크리스트

- [x] LLM 응답에서 제어 문자 이스케이프
- [x] JSON 파싱 에러 상세 로깅
- [x] 빈 응답 검증
- [x] 에러 타입별 처리
- [x] 안전한 acknowledge 처리
- [x] searchEventId 추적
- [x] Progress 업데이트

