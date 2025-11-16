package org.ghkdqhrbals.client.ai

import org.ghkdqhrbals.client.paper.dto.PaperAnalysisResponse

class NoopLlmClient : LlmClient {
    override suspend fun createChatCompletion(request: ChatRequest): ChatResponse {
        return ChatResponse(
            id = null,
            choices = listOf(Choice(message = Message(role = "assistant", content = ""))),
            usage = null
        )
    }

    override fun summarizePaper(abstract: String, maxLength: Int, journalRef: String?): PaperAnalysisResponse {
        // OpenAI가 비활성화된 경우 요약은 빈 문자열 반환
        return PaperAnalysisResponse()
    }
}
