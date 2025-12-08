package org.ghkdqhrbals.client.ai

/**
 * No-op LLM 클라이언트 - 아무 동작도 하지 않음
 */
class NoopLlmClient : LlmClient {
    override val name: LlmClientType = LlmClientType.NOOP

    override suspend fun createChatCompletion(request: ChatRequest): ChatResponse {
        throw UnsupportedOperationException("NoopLlmClient does not support createChatCompletion")
    }
}

