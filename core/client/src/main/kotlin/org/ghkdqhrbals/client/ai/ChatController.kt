package org.ghkdqhrbals.client.ai

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.ghkdqhrbals.message.service.EventStoreService
import org.ghkdqhrbals.model.event.BaseSagaEvent
import org.ghkdqhrbals.model.event.SagaEvent
import org.ghkdqhrbals.model.event.SagaEventType
import java.time.Instant
import kotlin.system.measureTimeMillis
import java.util.concurrent.ConcurrentHashMap

@RestController
@RequestMapping("/api/chat")
@Tag(name = "Chat", description = "OpenAI 텍스트/채팅 API")
class ChatController(
    private val ollamaClientImpl: LlmClient,
    private val eventStoreService: EventStoreService,
) {

    @PostMapping("/completions")
    @Operation(summary = "Chat Completions", description = "메시지 배열을 기반으로 OpenAI 채팅 응답을 생성합니다")
    suspend fun createChatCompletion(
        @RequestBody request: ChatRequest
    ): ResponseEntity<ChatResponse> {
        val response = ollamaClientImpl.createChatCompletion(request)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/send-event")
    fun send(): SagaEvent {
        val sendEvent = eventStoreService.sendEvent(
            "test-topic",
            BaseSagaEvent(
                eventType = SagaEventType.SAGA_STARTED,
                timestamp = Instant.now()
            )
        )
        return sendEvent
    }

    @PostMapping("/send")
    @Operation(summary = "간단 메시지 전송", description = "단일 텍스트로 모델에 질문하고 답변을 받습니다")
    suspend fun sendMessage(
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

        val response = ollamaClientImpl.createChatCompletion(chatRequest)
        val reply = response.choices.firstOrNull()?.message?.content ?: "No response"

        return ResponseEntity.ok(
            TextResponse(
                message = reply,
                usage = response.usage
            )
        )
    }

    @GetMapping("/test-ollama-parallel")
    @Operation(summary = "Ollama 동시 요청 테스트")
    fun testOllamaParallel(): ResponseEntity<OllamaParallelTestResponse> {

        val results = ConcurrentHashMap<Int, OllamaTestResult>()
        val logger = org.slf4j.LoggerFactory.getLogger(this::class.java)

        logger.info("🎬 [Controller] testOllamaParallel 메서드 시작")
        logger.info("🎬 [Controller] ollamaClientImpl 타입: ${ollamaClientImpl.javaClass.name}")
        logger.info("🎬 [Controller] OllamaClientImpl 인스턴스인가? ${ollamaClientImpl is OllamaClientImpl}")

        // Virtual Thread 풀 생성
        val executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()

        val totalTime = measureTimeMillis {
            logger.info("🚀 병렬 테스트 시작 - 50개 요청 준비")

            val futures = (1..50).map { index ->
                executor.submit<OllamaTestResult> {
                    val requestTime = System.currentTimeMillis()
                    val threadName = Thread.currentThread().name
                    logger.info("📤 [Task-$index] Virtual Thread 시작: $threadName")

                    try {
                        val request = ChatRequest(
                            model = "gemma3",
                            messages = listOf(
                                Message("user", "간단한 테스트 요청 #$index 입니다. 짧게 응답해주세요.")
                            ),
                        )

                        logger.info("📤 [Task-$index] ChatRequest 생성 완료, OllamaClientImpl 캐스팅 시도")

                        // Virtual Thread에서 직접 호출
                        val response = if (ollamaClientImpl is OllamaClientImpl) {
                            logger.info("📤 [Task-$index] OllamaClientImpl 캐스팅 성공, createChatCompletionBlocking 호출")
                            ollamaClientImpl.createChatCompletionBlocking(request)
                        } else {
                            logger.error("❌ [Task-$index] OllamaClientImpl이 아닙니다: ${ollamaClientImpl.javaClass.name}")
                            throw IllegalStateException("OllamaClientImpl이 아닙니다: ${ollamaClientImpl.javaClass.name}")
                        }

                        val reply = response.choices.firstOrNull()?.message?.content ?: "No response"
                        val duration = System.currentTimeMillis() - requestTime

                        logger.info("📥 [Task-$index] 요청 완료 (${duration}ms)")
                        OllamaTestResult(index, true, reply, null, duration)

                    } catch (e: Exception) {
                        val duration = System.currentTimeMillis() - requestTime
                        logger.error("❌ [Task-$index] 요청 실패 (${duration}ms): ${e.javaClass.name} - ${e.message}", e)
                        OllamaTestResult(index, false, null, "${e.javaClass.name}: ${e.message}", duration)
                    }
                }
            }

            logger.info("⏳ [Controller] 모든 Future 제출 완료, 결과 수집 시작")

            futures.forEach { future ->
                val result = future.get()
                results[result.requestId] = result
            }

            logger.info("🎉 모든 요청 완료!")
        }

        executor.shutdown()

        val resultList = results.values.sortedBy { it.requestId }

        logger.info("📊 [Controller] 테스트 완료 - 성공: ${resultList.count { it.success }}, 실패: ${resultList.count { !it.success }}")

        return ResponseEntity.ok(
            OllamaParallelTestResponse(
                totalRequests = 50,
                successCount = resultList.count { it.success },
                failureCount = resultList.count { !it.success },
                totalTimeMs = totalTime,
                averageTimeMs = if (resultList.isNotEmpty()) resultList.map { it.durationMs }.average() else 0.0,
                results = resultList
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

data class OllamaTestResult(
    val requestId: Int,
    val success: Boolean,
    val response: String?,
    val error: String?,
    val durationMs: Long
)

data class OllamaParallelTestResponse(
    val totalRequests: Int,
    val successCount: Int,
    val failureCount: Int,
    val totalTimeMs: Long,
    val averageTimeMs: Double,
    val results: List<OllamaTestResult>
)

