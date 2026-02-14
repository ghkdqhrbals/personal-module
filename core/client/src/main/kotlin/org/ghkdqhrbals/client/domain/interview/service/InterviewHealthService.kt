package org.ghkdqhrbals.client.domain.interview.service

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.ghkdqhrbals.client.config.log.logger
import org.ghkdqhrbals.client.domain.interview.dto.HealthCheckResponse
import org.ghkdqhrbals.client.domain.interview.dto.ProviderHealth
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import kotlin.system.measureTimeMillis

/**
 * Health check service for OpenAI and local LLM
 */
@Service
class InterviewHealthService(
    @Value("\${openai.api.key}") private val openaiApiKey: String,
    @Value("\${ollama.url:http://127.0.0.1:11434}") private val ollamaUrl: String,
    private val restClient: RestClient
) {
    
    /**
     * Check health of both OpenAI and local LLM providers
     */
    suspend fun checkHealth(): HealthCheckResponse = coroutineScope {
        logger().info("[InterviewHealthService] Performing health check")
        
        val openaiHealth = async { checkOpenAiHealth() }
        val localLlmHealth = async { checkLocalLlmHealth() }
        
        val openai = openaiHealth.await()
        val localLlm = localLlmHealth.await()
        
        val overallStatus = if (openai.available || localLlm.available) "UP" else "DOWN"
        
        logger().info("[InterviewHealthService] Health check complete - status: $overallStatus, OpenAI: ${openai.available}, LocalLLM: ${localLlm.available}")
        
        HealthCheckResponse(
            status = overallStatus,
            openai = openai,
            localLlm = localLlm
        )
    }
    
    /**
     * Check OpenAI API connectivity
     */
    private fun checkOpenAiHealth(): ProviderHealth {
        if (openaiApiKey.isBlank()) {
            logger().warn("[InterviewHealthService] OpenAI API key not configured")
            return ProviderHealth(
                available = false,
                message = "API key not configured",
                latencyMs = null
            )
        }
        
        return try {
            val latency = measureTimeMillis {
                restClient.get()
                    .uri("https://api.openai.com/v1/models")
                    .header("Authorization", "Bearer $openaiApiKey")
                    .retrieve()
                    .body<Map<String, Any>>()
                    ?: throw IllegalStateException("No response from OpenAI")
            }
            
            logger().info("[InterviewHealthService] OpenAI health check passed - latency: ${latency}ms")
            ProviderHealth(
                available = true,
                message = "Connected",
                latencyMs = latency
            )
        } catch (e: Exception) {
            logger().error("[InterviewHealthService] OpenAI health check failed", e)
            ProviderHealth(
                available = false,
                message = "Failed: ${e.message?.take(100)}",
                latencyMs = null
            )
        }
    }
    
    /**
     * Check local LLM (Ollama) connectivity
     */
    private fun checkLocalLlmHealth(): ProviderHealth {
        return try {
            val latency = measureTimeMillis {
                restClient.get()
                    .uri("$ollamaUrl/api/tags")
                    .retrieve()
                    .body<Map<String, Any>>()
                    ?: throw IllegalStateException("No response from Ollama")
            }
            
            logger().info("[InterviewHealthService] Local LLM health check passed - latency: ${latency}ms")
            ProviderHealth(
                available = true,
                message = "Connected",
                latencyMs = latency
            )
        } catch (e: Exception) {
            logger().warn("[InterviewHealthService] Local LLM health check failed", e)
            ProviderHealth(
                available = false,
                message = "Not available: ${e.message?.take(100)}",
                latencyMs = null
            )
        }
    }
}
