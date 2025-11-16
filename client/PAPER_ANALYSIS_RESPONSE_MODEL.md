# 논문 분석 응답 매핑 모델

## 구현 완료 ✅

논문의 핵심 기여도와 참신성을 매핑하는 응답 모델을 구현했습니다.

## 응답 모델 구조

### 1. PaperAnalysisResponse
논문 분석 결과를 담는 DTO

```kotlin
data class PaperAnalysisResponse(
    val coreContribution: String,              // 핵심 기여도
    val noveltyAgainstPreviousWorks: String,   // 기존 연구 대비 참신성
    val methodology: String? = null,            // 방법론 (선택)
    val keyFindings: List<String>? = null,      // 주요 발견 (선택)
    val limitations: String? = null,            // 한계점 (선택)
    val futureWork: String? = null              // 향후 연구 (선택)
)
```

### 2. PaperResponse
논문 정보 + 분석 결과

```kotlin
data class PaperResponse(
    val title: String,
    val authors: List<String>,
    val journal: String?,
    val publicationDate: String?,
    val doi: String?,
    val abstract: String?,
    val url: String?,
    val citations: Int?,
    val impactFactor: Double?,
    val impactFactorYear: Int?,
    val summary: String?,
    val analysis: PaperAnalysisResponse? = null  // 분석 결과
)
```

## 사용 예시

### 응답 JSON 예시

```json
{
  "papers": [
    {
      "title": "Joint analysis of NOvA and T2K neutrino data",
      "authors": ["NOvA Collaboration", "T2K Collaboration"],
      "journal": "Nature",
      "publicationDate": "2025-03-15",
      "doi": "10.1038/s41586-025-xxxxx",
      "abstract": "We present the first joint analysis...",
      "url": "https://arxiv.org/abs/2503.12345",
      "citations": 45,
      "impactFactor": 64.8,
      "impactFactorYear": 2025,
      "summary": "NOvA와 T2K 실험의 최초 공동 분석으로 중성미자 매개변수에 대한 정밀도를 향상시켰습니다.",
      "analysis": {
        "coreContribution": "NOvA와 T2K 데이터를 최초로 공동 분석하여 중성미자 섹터의 여러 매개변수에 대한 새로운 제약을 설정했습니다.",
        "noveltyAgainstPreviousWorks": "기존 연구와 달리 두 실험의 상호 보완적 설계를 활용하여 새로운 정밀도를 달성했습니다.",
        "methodology": "베이지안 통계 분석을 사용하여 두 실험의 데이터를 결합했습니다.",
        "keyFindings": [
          "CP 위반 각도 δCP에 대한 제약 개선",
          "질량 순서에 대한 95% CL 제약",
          "θ23 혼합각의 정밀도 향상"
        ],
        "limitations": "장기선 실험의 체계적 불확실성이 여전히 존재합니다.",
        "futureWork": "DUNE과 Hyper-Kamiokande 실험과의 결합 분석을 계획하고 있습니다."
      }
    }
  ],
  "count": 1,
  "source": "arXiv",
  "pagination": {
    "page": 0,
    "size": 10,
    "totalResults": 152,
    "totalPages": 16
  }
}
```

### Mapper 사용법

#### 1. 기본 변환
```kotlin
@Service
class PaperService(
    private val mapper: PaperResponseMapper
) {
    fun searchPapers(): PaperSearchResponseDTO {
        val domainResponse = arxivService.search(...)
        return mapper.toResponseDTO(domainResponse)
    }
}
```

#### 2. 분석 정보 포함
```kotlin
val paper = Paper(
    title = "Joint analysis of NOvA and T2K",
    // ...other fields...
)

val analysis = PaperAnalysisResponse(
    coreContribution = "NOvA와 T2K 데이터를 최초로 공동 분석...",
    noveltyAgainstPreviousWorks = "기존 연구와 달리 두 실험의 상호 보완적 설계를 활용..."
)

val response = mapper.toResponse(
    paper = paper,
    analysis = analysis
)
```

#### 3. JSON 파싱
```kotlin
val json = """
{
  "core_contribution": "NOvA와 T2K 데이터를 최초로 공동 분석하여 중성미자 섹터의 여러 매개변수에 대한 새로운 제약을 설정했습니다.",
  "novelty_against_previous_works": "기존 연구와 달리 두 실험의 상호 보완적 설계를 활용하여 새로운 정밀도를 달성했습니다."
}
"""

val analysis = mapper.parseAnalysisJson(json)
```

## Controller 통합 예시

```kotlin
@RestController
@RequestMapping("/api/papers/arxiv")
class ArxivController(
    private val arxivService: ArxivService,
    private val mapper: PaperResponseMapper
) {
    
    @PostMapping("/search")
    fun search(@RequestBody req: ArxivSearchRequest): ResponseEntity<PaperSearchResponseDTO> {
        val result = arxivService.search(
            query = req.query,
            categories = req.categories,
            maxResults = req.maxResults,
            page = req.page,
            summarize = req.summarize ?: true
        )
        
        val dto = mapper.toResponseDTO(result)
        return ResponseEntity.ok(dto)
    }
}
```

## JSON 필드 매핑

### Snake Case → Camel Case
LLM 응답이 snake_case로 오는 경우 자동 매핑:

| JSON 필드 (snake_case) | DTO 필드 (camelCase) |
|------------------------|----------------------|
| `core_contribution` | `coreContribution` |
| `novelty_against_previous_works` | `noveltyAgainstPreviousWorks` |
| `methodology` | `methodology` |
| `key_findings` | `keyFindings` |
| `limitations` | `limitations` |
| `future_work` | `futureWork` |

### Jackson 설정
```yaml
# application.yaml
spring:
  jackson:
    property-naming-strategy: SNAKE_CASE  # snake_case 자동 변환
    default-property-inclusion: non_null  # null 필드 제외
```

## LLM 프롬프트 예시

논문 분석을 위한 LLM 프롬프트:

```kotlin
fun analyzePaper(abstract: String): PaperAnalysisResponse? {
    val prompt = """
        다음 논문 초록을 분석하여 JSON 형식으로 응답하세요:
        
        $abstract
        
        응답 형식:
        {
          "core_contribution": "핵심 기여도를 한 문장으로",
          "novelty_against_previous_works": "기존 연구 대비 참신성을 한 문장으로",
          "methodology": "사용된 방법론 (선택사항)",
          "key_findings": ["주요 발견 1", "주요 발견 2"],
          "limitations": "연구의 한계점 (선택사항)",
          "future_work": "향후 연구 방향 (선택사항)"
        }
    """.trimIndent()
    
    val response = llmClient.createChatCompletion(ChatRequest(
        model = "gpt-4o",
        messages = listOf(Message("user", prompt))
    ))
    
    val json = response.choices.firstOrNull()?.message?.content ?: return null
    return mapper.parseAnalysisJson(json)
}
```

## 응답 예시 (실제 사용)

### 최소 응답 (분석 없음)
```json
{
  "title": "Deep Learning for Stroke",
  "authors": ["John Doe"],
  "journal": "Nature",
  "impactFactor": 64.8
}
```
→ `@JsonInclude(NON_NULL)`로 null 필드 자동 제외

### 완전 응답 (분석 포함)
```json
{
  "title": "Deep Learning for Stroke",
  "authors": ["John Doe"],
  "journal": "Nature",
  "impactFactor": 64.8,
  "impactFactorYear": 2025,
  "analysis": {
    "coreContribution": "딥러닝으로 뇌졸중 예측 정확도 95% 달성",
    "noveltyAgainstPreviousWorks": "기존 모델 대비 20% 정확도 향상"
  }
}
```

---

## 완료! 🎉

논문 분석 결과를 체계적으로 매핑하는 응답 모델이 완성되었습니다.

### 주요 기능
- ✅ 핵심 기여도(core_contribution) 매핑
- ✅ 참신성(novelty_against_previous_works) 매핑
- ✅ 추가 분석 필드 (methodology, key_findings, limitations, future_work)
- ✅ JSON snake_case 자동 파싱
- ✅ null 필드 자동 제외
- ✅ 페이지네이션 정보 포함
- ✅ Impact Factor 연도 정보 포함

