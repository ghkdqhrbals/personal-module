package org.ghkdqhrbals.client.ai

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class LlmClientConfig {

    @Bean
    @ConditionalOnProperty(prefix = "ai", name = ["provider"], havingValue = "openai")
    fun openAiLlmClient(
        @Value("\${openai.api.key}") apiKey: String
    ): LlmClient = OpenAiClientImpl(apiKey)

    @Bean
    @ConditionalOnProperty(prefix = "ai", name = ["provider"], havingValue = "ollama")
    fun ollamaLlmClient(
        @Qualifier("resolvedOllamaUrl") ollamaUrl: String,
        @Value("\${ollama.model}") modelName: String,
        webClient: WebClient
    ): LlmClient = OllamaClientImpl(modelName, ollamaUrl, webClient)

    @Bean
    @ConditionalOnMissingBean(LlmClient::class)
    fun noopLlmClient(): LlmClient = NoopLlmClient()
}
