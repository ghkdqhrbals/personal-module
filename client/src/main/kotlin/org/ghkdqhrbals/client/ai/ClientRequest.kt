package org.ghkdqhrbals.client.ai

data class ChatRequest(
    val model: String = "gpt-4o", // default ChatGPT-4o 로 모델 사용
    val messages: List<Message>,
    val temperature: Double = 0.7
)

data class Message(
    val role: String,
    val content: String
)

data class ChatResponse(
    val id: String? = null,
    val choices: List<Choice>,
    val usage: Usage? = null
)

data class Choice(
    val message: Message,
    val finishReason: String? = null
)

data class Usage(
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null
)