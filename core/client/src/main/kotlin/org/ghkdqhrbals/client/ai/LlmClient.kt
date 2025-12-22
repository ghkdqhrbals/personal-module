package org.ghkdqhrbals.client.ai

import org.ghkdqhrbals.client.config.log.logger
import org.ghkdqhrbals.client.controller.paper.dto.PaperAnalysisResponse
import org.ghkdqhrbals.model.domain.Jackson

interface LlmClient {
    suspend fun createChatCompletion(request: ChatRequest): ChatResponse

    /**
     * 논문 초록과 journal_ref를 요약하고 저널 정보를 추출합니다
     */
    suspend fun summarizePaper(
        abstract: String,
        maxLength: Int = 150,
        journalRef: String? = null
    ): PaperAnalysisResponse {
        val journalPrompt = if (!journalRef.isNullOrBlank()) {
            """
            
            추가로, 다음 journal reference에서 저널명과 발표 연도, 해당 연도의 impact factor(또는 가장 최신의 공신력 있는 근사치)를 추정하여 기입하세요.
            Journal reference: "$journalRef"
            
            저널명은 볼륨, 페이지 번호를 제외한 순수 저널 이름만 추출하세요.
            예: "Nature 646, 818-824 (2025)" → journal_name: "Nature", year: 2025
            impact factor가 출처에 따라 다른 경우, 가장 공신력 있는 수치를 하나만 제시하고, 연도(impact_factor_year)를 함께 적으세요.
            """
        } else ""

        val request = chatRequest(journalPrompt, maxLength, abstract)

        val response = createChatCompletion(request)
        val raw = response.choices.firstOrNull()?.message?.content
            ?: throw IllegalStateException("No response from LLM")

        val codeBlockRegex = Regex("```[a-zA-Z]*")
        fun removeCodeBlocks(input: String): String =
            input.replace(codeBlockRegex, "").trim()
        fun ensureJsonClosed(json: String): String {
            val open = json.count { it == '{' }
            val close = json.count { it == '}' }
            return if (open > close) json + "}".repeat(open - close) else json
        }
        fun removeControlChars(s: String): String =
            s.replace(Regex("[\\u0000-\\u001F]"), "")
        fun normalizeQuotes(s: String): String =
            s.replace("“", "\"")
                .replace("”", "\"")
                .replace("‘", "'")
                .replace("’", "'")
        try {
            var cleanedJson = raw.trim()

            // 1. 코드블록 제거
            cleanedJson = removeCodeBlocks(cleanedJson)
            // 2. 개행 제거
            cleanedJson = cleanedJson.replace("\r\n", "")
                .replace("\n", "")
                .replace("\r", "")
            // 3. 제어문자 제거
            cleanedJson = removeControlChars(cleanedJson)
            // 4. 잘린 JSON 자동 복구
            cleanedJson = ensureJsonClosed(cleanedJson)
            // 5. 따옴표 정규화
            cleanedJson = normalizeQuotes(cleanedJson)

            logger().debug("Cleaned JSON length: ${cleanedJson.length} chars")

            Jackson.getMapper()
            val mapper = com.fasterxml.jackson.databind.ObjectMapper()
            val node = try {
                mapper.readTree(cleanedJson)
            } catch (e: com.fasterxml.jackson.core.JsonParseException) {
                logger().error("JSON Parse Error. Raw response (first 500 chars): ${raw.take(500)}")
                logger().error("Cleaned JSON (first 500 chars): ${cleanedJson.take(500)}")
                throw IllegalStateException("Failed to parse LLM JSON response: ${e.message}", e)
            }

            val core = node["core_contribution"]?.asText()?.trim() ?: ""
            val novelty = node["novelty_against_previous_works"]?.asText()?.trim() ?: ""
            val journal = node["journal_name"]?.asText()?.takeIf { it != "null" && it.isNotBlank() }
            val year = node["year"]?.asInt()
            val ifv = node["impact_factor"]?.asDouble()
            val ifYear = node["impact_factor_year"]?.asInt()

            if (core.isBlank() && novelty.isBlank()) {
                logger().warn("LLM returned empty core_contribution and novelty. Response: $raw")
                throw IllegalStateException("LLM returned empty summary fields")
            }

            val result = PaperAnalysisResponse(
                coreContribution = core,
                noveltyAgainstPreviousWorks = novelty,
                journalName = journal,
                year = year,
                impactFactor = ifv,
                impactFactorYear = ifYear
            )
            logger().info("✅ Summarized successfully - core: ${core.take(50)}..., novelty: ${novelty.take(50)}...")
            return result

        } catch (e: Exception) {
            logger().error("❌ Failed to process LLM response", e)
            logger().error("Raw response (first 1000 chars): ${raw.take(1000)}")
            throw e
        }
    }

    fun chatRequest(
        journalPrompt: String,
        maxLength: Int,
        abstract: String
    ) = ChatRequest(
        model = "gpt-4o-mini",  // OpenAI용, Ollama 사용 시에는 ovarride됨
        messages = listOf(
            Message(
                role = "system",
                content =
                """
                        당신은 전문 학술 의사소통 전문가입니다.
                        
                        목표:
                        주어진 논문 초록에서 '논문의 고유한 기여(독창성)'와 '기존 연구 대비 차별점'을 한국어로 정확하게 추출합니다.
                        $journalPrompt
                        
                        출력 전 수행 규칙:
                        - 내부적으로만 간단한 체크리스트를 점검하고, 출력에는 포함하지 않습니다.
                        - 출력은 반드시 JSON 객체 형태만 사용합니다.
                        - 절대로 코드블록(```` ````, ```json```, 등)으로 감싸지 않습니다.
                        - JSON 외의 추가 텍스트(설명, 접두사, 접미사)를 출력하지 않습니다.
                        
                        요약 규칙:
                        - 반드시 한국어 존댓말로 작성합니다.
                        - 논문의 핵심 기여만 간결하게 요약합니다.
                        - 기존 연구와의 차별성을 명확히 기술합니다.
                        - 불필요한 배경 설명은 제거합니다.
                        - 전체 분량은 공백 포함 $maxLength 자 이내로 유지합니다.
                        - 문체는 자연스럽고 읽기 쉽게 유지합니다.
                        - "이 논문은", "저자들은" 등 학술적 서론 문장은 제외합니다.
                        
                        ### 출력 형식(JSON only):
                        {
                            "core_contribution": "(논문의 고유한 기여 요약, $maxLength 자 이하)",
                            "novelty_against_previous_works": "(기존 연구와 비교한 차별점 한 문장)",
                            "journal_name": "저널명 (journal_ref가 주어진 경우만, 없으면 null)",
                            "year": 발표연도숫자 (journal_ref가 주어진 경우만, 없으면 null),
                            "impact_factor": 수치 또는 null,
                            "impact_factor_year": 연도 또는 null
                        }
                        """.trimIndent()
            ),
            Message(
                role = "user",
                content = abstract
            )
        ),
        temperature = 0.3
    )
}