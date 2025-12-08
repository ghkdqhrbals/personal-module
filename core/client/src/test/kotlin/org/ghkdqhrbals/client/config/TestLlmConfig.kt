package org.ghkdqhrbals.client.config

import org.ghkdqhrbals.client.ai.ChatRequest
import org.ghkdqhrbals.client.ai.ChatResponse
import org.ghkdqhrbals.client.ai.Choice
import org.ghkdqhrbals.client.ai.LlmClient
import org.ghkdqhrbals.client.ai.LlmClientType
import org.ghkdqhrbals.client.ai.Message
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

@TestConfiguration
class TestLlmConfig {

    @Bean
    @Primary
    fun testLlmClient(): LlmClient {
        return object : LlmClient {
            override val name: LlmClientType = LlmClientType.NOOP

            override suspend fun createChatCompletion(request: ChatRequest): ChatResponse {
                return ChatResponse(
                    id = "test-id",
                    choices = listOf(
                        Choice(
                            message = Message(
                                role = "assistant",
                                content = "Test response"
                            ),
                            finishReason = "stop"
                        )
                    ),
                    usage = null
                )
            }
        }
    }
}

