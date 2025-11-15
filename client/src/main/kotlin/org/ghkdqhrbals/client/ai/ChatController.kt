package org.ghkdqhrbals.client.ai

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.beans.factory.annotation.Qualifier

@RestController
@RequestMapping("/api/chat")
@Tag(name = "Chat", description = "OpenAI 텍스트/채팅 API")
class ChatController(
    private val openAiClientImpl: LlmClient
) {

    @PostMapping("/completions")
    @Operation(summary = "Chat Completions", description = "메시지 배열을 기반으로 OpenAI 채팅 응답을 생성합니다")
    fun createChatCompletion(
        @RequestBody request: ChatRequest
    ): ResponseEntity<ChatResponse> {
        val response = openAiClientImpl.createChatCompletion(request)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/send")
    @Operation(summary = "간단 메시지 전송", description = "단일 텍스트로 모델에 질문하고 답변을 받습니다")
    fun sendMessage(
        @RequestBody textRequest: TextRequest
    ): ResponseEntity<TextResponse> {
        val chatRequest = ChatRequest(
            model = textRequest.model ?: "gpt-3.5-turbo",
            messages = listOf(
                Message(
                    role = "user",
                    content = textRequest.message
                )
            ),
            temperature = textRequest.temperature ?: 0.7
        )

        val response = openAiClientImpl.createChatCompletion(chatRequest)
        val reply = response.choices.firstOrNull()?.message?.content ?: "No response"

        return ResponseEntity.ok(
            TextResponse(
                message = reply,
                usage = response.usage
            )
        )
    }
}

data class TextRequest(
    val message: String,
    val model: String? = null,
    val temperature: Double? = null
)

data class TextResponse(
    val message: String,
    val usage: Usage? = null
)
