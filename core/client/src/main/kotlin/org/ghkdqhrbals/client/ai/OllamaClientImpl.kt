package org.ghkdqhrbals.client.ai

import org.ghkdqhrbals.client.config.log.logger
import java.util.concurrent.atomic.AtomicInteger
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import org.ghkdqhrbals.client.config.log.setting
import org.ghkdqhrbals.model.domain.Jackson
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

/**
 * Ollama 로컬 모델을 사용하는 LLM 클라이언트 - WebClient 기반 + Circuit Breaker
 */
open class OllamaClientImpl(
    private val modelName: String,
    private val ollamaUrl: String,
    private val webClient: WebClient
) : LlmClient {

    override val name: LlmClientType = LlmClientType.OLLAMA
    init {
        logger().setting("ollamaUrl=$ollamaUrl, modelName=$modelName")
    }

    private val activeRequests = AtomicInteger(0)
    private val totalRequests = AtomicInteger(0)
    private val completedRequests = AtomicInteger(0)
    private val mapper = Jackson.getMapper()

    @JsonInclude(Include.NON_NULL)
    data class OllamaChatRequest(
        val model: String,
        val messages: List<OllamaChatMessage>,
        val temperature: Double? = null,
        val stream: Boolean = false
    )

    data class OllamaChatMessage(
        val role: String,
        val content: String
    )

    data class OllamaChatResponse(
        val model: String,
        val created_at: String,
        val message: OllamaChatMessage,
        val done: Boolean,
        val done_reason: String? = null,
        val total_duration: Long? = null,
        val load_duration: Long? = null,
        val prompt_eval_count: Int? = null,
        val prompt_eval_duration: Long? = null,
        val eval_count: Int? = null,
        val eval_duration: Long? = null
    )

    @CircuitBreaker(name = "ollama", fallbackMethod = "circuitBreakerFallback")
    override suspend fun createChatCompletion(request: ChatRequest): ChatResponse {
        logger().info("🔌 [Before] Ollama 요청 시작")
        return try {
            val response = executeOllamaRequest(request)
            logger().info("🔌 [After Success] Ollama 요청 성공")
            response
        } catch (e: io.github.resilience4j.circuitbreaker.CallNotPermittedException) {
            logger().error("🔌 [Circuit Open] 요청 차단됨 - Circuit이 OPEN 상태")
            throw e
        } catch (e: Exception) {
            logger().error("🔌 [After Error] Ollama 요청 실패: {}", e.message)
            throw e
        }
    }

    /**
     * Circuit Breaker fallback 메서드
     * Circuit이 OPEN된 경우 호출됨
     */
    open fun circuitBreakerFallback(request: ChatRequest, e: Exception): ChatResponse {
        logger().warn("🔌 [Fallback] Ollama 서비스 이용 불가 - Circuit이 OPEN됨")
        throw e
    }

    private fun executeOllamaRequest(request: ChatRequest): ChatResponse {
        val requestId = totalRequests.incrementAndGet()
        activeRequests.incrementAndGet()

        try {
            // OpenAI 형식 messages를 Ollama 형식으로 변환
            val ollamaMessages = request.messages.map { msg ->
                OllamaChatMessage(role = msg.role, content = msg.content)
            }

            val ollamaRequest = OllamaChatRequest(
                model = modelName,
                messages = ollamaMessages,
                temperature = request.temperature
            )

            val startTime = System.currentTimeMillis()

            // /api/chat 엔드포인트 사용 (role 지원)
            val responseBytes = webClient.post()
                .uri("$ollamaUrl/api/chat")
                .header("Content-Type", "application/json")
                .bodyValue(ollamaRequest)
                .retrieve()
                .bodyToMono(ByteArray::class.java)
                .block() ?: throw IllegalStateException("Ollama returned null response")

            val responseText = String(responseBytes)

            // NDJSON 형식: 여러 줄의 JSON이 '\n'으로 구분됨
            val lines = responseText.trim().lines().filter { it.isNotBlank() }
            val fullResponse = StringBuilder()
            var lastResponse: OllamaChatResponse? = null

            for (line in lines) {
                try {
                    val partial = mapper.readValue(line, OllamaChatResponse::class.java)
                    fullResponse.append(partial.message.content)
                    lastResponse = partial
                } catch (e: Exception) {
                    logger().warn("⚠️ Failed to parse Ollama response line: ${line.take(100)}")
                }
            }

            if (lastResponse == null || !lastResponse.done) {
                throw IllegalStateException("Ollama response incomplete - done=${lastResponse?.done}")
            }

            val elapsed = System.currentTimeMillis() - startTime
            completedRequests.incrementAndGet()

            logger().info("✅ Ollama 요청 #$requestId 완료 (${elapsed}ms, ${lastResponse.eval_count ?: 0} tokens)")

            val choice = Choice(
                message = Message(
                    role = "assistant",
                    content = fullResponse.toString()
                ),
                finishReason = lastResponse.done_reason ?: "stop"
            )

            return ChatResponse(
                id = "ollama-$requestId",
                choices = listOf(choice),
                usage = Usage(0, 0, 0)
            )

        } catch (e: Exception) {
            val completed = completedRequests.incrementAndGet()
            logger().error("❌ Ollama 요청 #$requestId 실패 - [완료: $completed/${totalRequests.get()}] ${e.message}")
            throw e

        } finally {
            val remaining = activeRequests.decrementAndGet()
            logger().info("🔴 Ollama 요청 #$requestId 종료 (남은 활성: $remaining)")
        }
    }
}

