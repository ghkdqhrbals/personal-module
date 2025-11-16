package org.ghkdqhrbals.client.ai

import org.ghkdqhrbals.client.config.logger
import org.springframework.web.client.RestTemplate
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Ollama 로컬 모델을 사용하는 LLM 클라이언트
 * gpt-oss:20b-cloud 모델 지원
 */
class OllamaClientImpl(
    private val ollamaUrl: String,
    private val modelName: String,
    private val restTemplate: RestTemplate
) : LlmClient {

    companion object {
        private const val MAX_CONCURRENT_REQUESTS = 10 // Ollama 동시 요청 제한
    }

    private val semaphore = Semaphore(MAX_CONCURRENT_REQUESTS)
    private val activeRequests = AtomicInteger(0)
    private val totalRequests = AtomicInteger(0)
    private val completedRequests = AtomicInteger(0)

    data class OllamaRequest(
        val model: String,
        val messages: List<OllamaMessage>,
        val temperature: Double = 0.3,
        val stream: Boolean = false
    )

    data class OllamaMessage(
        val role: String,
        val content: String
    )

    data class OllamaResponse(
        val message: OllamaResponseMessage
    )

    data class OllamaResponseMessage(
        val role: String,
        val content: String
    )

    override suspend fun createChatCompletion(request: ChatRequest): ChatResponse {
        val requestId = totalRequests.incrementAndGet()
        val waitingCount = MAX_CONCURRENT_REQUESTS - semaphore.availablePermits

        logger().info("🔵 Ollama 요청 #$requestId 대기 중 - [대기: $waitingCount, 활성: ${activeRequests.get()}, 완료: ${completedRequests.get()}]")

        return semaphore.withPermit {
            val active = activeRequests.incrementAndGet()
            val available = semaphore.availablePermits
            val inUse = MAX_CONCURRENT_REQUESTS - available

            logger().info("🟢 Ollama 요청 #$requestId 시작 - [활성: $active/$MAX_CONCURRENT_REQUESTS, 가용: $available, 사용중: $inUse]")

            try {
                val ollamaRequest = OllamaRequest(
                    model = modelName,
                    messages = request.messages.map { msg ->
                        OllamaMessage(
                            role = msg.role,
                            content = msg.content
                        )
                    },
                    temperature = request.temperature
                )

                val url = "$ollamaUrl/api/chat"
                val startTime = System.currentTimeMillis()

                val response = restTemplate.postForObject(url, ollamaRequest, OllamaResponse::class.java)
                    ?: throw IllegalStateException("Ollama returned null response")

                val elapsed = System.currentTimeMillis() - startTime
                val completed = completedRequests.incrementAndGet()
                val remaining = totalRequests.get() - completed

                logger().info("✅ Ollama 요청 #$requestId 완료 (${elapsed}ms) - [완료: $completed/${totalRequests.get()}, 남음: $remaining]")

                val choice = Choice(
                    message = Message(
                        role = "assistant",
                        content = response.message.content
                    ),
                    finishReason = "stop"
                )

                ChatResponse(
                    id = "ollama-$requestId",
                    choices = listOf(choice),
                    usage = Usage(
                        promptTokens = 0,
                        completionTokens = 0,
                        totalTokens = 0
                    )
                )
            } catch (e: Exception) {
                val completed = completedRequests.incrementAndGet()
                logger().error("❌ Ollama 요청 #$requestId 실패 - [완료: $completed/${totalRequests.get()}] ${e.message}")
                throw e
            } finally {
                val activeAfter = activeRequests.decrementAndGet()
                val availableAfter = semaphore.availablePermits
                logger().info("🔴 Ollama 요청 #$requestId 종료 - [활성: $activeAfter/$MAX_CONCURRENT_REQUESTS, 가용: ${availableAfter + 1}]")
            }
        }
    }
}

