package org.ghkdqhrbals.client.ai

import org.ghkdqhrbals.client.config.log.logger
import org.springframework.web.client.RestClient
import java.util.concurrent.atomic.AtomicInteger
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include
import org.ghkdqhrbals.client.config.log.setting
import org.ghkdqhrbals.model.config.Jackson

/**
 * Ollama 로컬 모델을 사용하는 LLM 클라이언트 - RestClient 기반
 */
class OllamaClientImpl(
    private val modelName: String,
    private val ollamaUrl: String,
    private val restClient: RestClient,
) : LlmClient {

    init {
        logger().setting("ollamaUrl=$ollamaUrl, modelName=$modelName")
    }

    private val activeRequests = AtomicInteger(0)
    private val totalRequests = AtomicInteger(0)
    private val completedRequests = AtomicInteger(0)
    private val mapper = Jackson.getMapper()

    @JsonInclude(Include.NON_NULL)
    data class OllamaRequest(
        val model: String,
        val prompt: String,
        val temperature: Double? = null,
        val stream: Boolean = false
    )

    data class OllamaResponse(
        val model: String,
        val created_at: String,
        val response: String,
        val done: Boolean,
        val done_reason: String? = null,
        val context: List<Int>? = null,
        val total_duration: Long? = null,
        val load_duration: Long? = null,
        val prompt_eval_count: Int? = null,
        val prompt_eval_duration: Long? = null,
        val eval_count: Int? = null,
        val eval_duration: Long? = null
    )

    override suspend fun createChatCompletion(request: ChatRequest): ChatResponse {
        return createChatCompletionBlocking(request)
    }

    fun createChatCompletionBlocking(request: ChatRequest): ChatResponse {
        val requestId = totalRequests.incrementAndGet()
        activeRequests.incrementAndGet()

        try {
            val promptText = request.messages.joinToString("\n") { msg ->
                msg.content
            }

            val ollamaRequest = OllamaRequest(
                model = modelName,
                prompt = promptText,
                temperature = request.temperature
            )

            val startTime = System.currentTimeMillis()

            // RestClient로 동기 호출 - Virtual Thread에서 병렬 처리
            // Ollama는 NDJSON 형식으로 응답하므로 bytes로 받아서 처리
            val responseBytes = restClient.post()
                .uri("$ollamaUrl/api/generate")
                .header("Content-Type", "application/json")
                .body(ollamaRequest)
                .retrieve()
                .body(ByteArray::class.java) ?: throw IllegalStateException("Ollama returned null response")

            val responseText = String(responseBytes)

            // NDJSON 형식: 여러 줄의 JSON이 '\n'으로 구분됨
            // 모든 줄의 response 필드를 합쳐서 완전한 응답 생성
            val lines = responseText.trim().lines().filter { it.isNotBlank() }
            val fullResponse = StringBuilder()
            var lastResponse: OllamaResponse? = null

            for (line in lines) {
                try {
                    val partial = mapper.readValue(line, OllamaResponse::class.java)
                    fullResponse.append(partial.response)
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

