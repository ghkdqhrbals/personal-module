package org.ghkdqhrbals.client.ai

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.stereotype.Component

@Component
@ConditionalOnMissingBean(LlmClient::class)
class NoopLlmClient : LlmClient {
    override fun createChatCompletion(request: ChatRequest): ChatResponse {
        return ChatResponse(
            id = null,
            choices = listOf(Choice(message = Message(role = "assistant", content = ""))),
            usage = null
        )
    }

    override fun summarizePaper(abstract: String, maxLength: Int): String {
        // OpenAI가 비활성화된 경우 요약은 빈 문자열 반환
        return ""
    }
}
