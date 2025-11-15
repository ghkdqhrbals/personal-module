package org.ghkdqhrbals.client.ai

import org.ghkdqhrbals.client.config.logger

interface LlmClient {
    fun createChatCompletion(request: ChatRequest): ChatResponse

    /**
     * 논문 초록을 요약합니다
     */
    fun summarizePaper(abstract: String, maxLength: Int = 150): String {
        val request = ChatRequest(
            model = "gpt-4o",
            messages = listOf(
                Message(
                    role = "system",
                    content =
                    """
                    당신은 전문 학술 의사소통 전문가입니다.
                    
                    목표:
                    주어진 논문 초록에서 '논문의 고유한 기여(독창성)'와 '기존 연구 대비 차별점'을 한국어로 정확하게 추출합니다.
                    
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
                    - reasoning_effort=medium 수준으로 핵심만 구조적으로 도출합니다.
                    
                    ### 출력 형식(JSON only):
                    {
                        “core_contribution”: “(논문의 고유한 기여 요약, $maxLength 자 이하)”,
                        “novelty_against_previous_works”: “(기존 연구와 비교한 차별점 한 문장)”
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

        val response = createChatCompletion(request)
        val raw = response.choices.firstOrNull()?.message?.content
        if (raw.isNullOrBlank()) {
            return "Unable to generate summary"
        }
        try {
            val mapper = com.fasterxml.jackson.databind.ObjectMapper()
            val node = mapper.readTree(raw)
            val core = node["core_contribution"]?.asText()?.trim() ?: ""
            val novelty = node["novelty_against_previous_works"]?.asText()?.trim() ?: ""
            if (core.isNotBlank() || novelty.isNotBlank()) {
                val resultMap = mapOf(
                    "core_contribution" to core,
                    "novelty_against_previous_works" to novelty
                )
                return mapper.writeValueAsString(resultMap)
            }
        } catch (e: Exception) {
            // parsing 실패 시 원본 응답을 반환
        }

        logger().info("result : $raw")
        return raw
    }
}