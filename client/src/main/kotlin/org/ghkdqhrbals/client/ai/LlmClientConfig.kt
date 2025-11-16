package org.ghkdqhrbals.client.ai

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestTemplate

@Configuration
class LlmClientConfig {

    /**
     * OpenAI 클라이언트 빈 생성
     * ai.provider=openai 일 때만 활성화
     */
    @Bean
    @ConditionalOnProperty(
        prefix = "ai",
        name = ["provider"],
        havingValue = "openai",
        matchIfMissing = false
    )
    fun openAiLlmClient(
        @Value("\${openai.api.key}") apiKey: String
    ): LlmClient {
        return OpenAiClientImpl(apiKey)
    }

    /**
     * Ollama 클라이언트 빈 생성
     * ai.provider=ollama 일 때만 활성화
     */
    @Bean
    @ConditionalOnProperty(
        prefix = "ai",
        name = ["provider"],
        havingValue = "ollama",
        matchIfMissing = false
    )
    fun ollamaLlmClient(
        @Value("\${ollama.url}") ollamaUrl: String,
        @Value("\${ollama.model}") modelName: String,
        restTemplate: RestTemplate
    ): LlmClient {
        return OllamaClientImpl(ollamaUrl, modelName, restTemplate)
    }

    /**
     * Noop 클라이언트 빈 생성 (기본값)
     * ai.provider가 설정되지 않았거나 none일 때 활성화
     */
    @Bean
    @ConditionalOnProperty(
        prefix = "ai",
        name = ["provider"],
        havingValue = "none",
        matchIfMissing = true
    )
    fun noopLlmClient(): LlmClient {
        return NoopLlmClient()
    }
}

