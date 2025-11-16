package org.ghkdqhrbals.client.ai

import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.http.Timeout
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import io.ktor.client.*
import io.ktor.client.plugins.api.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.ghkdqhrbals.client.config.logger
import kotlin.time.Duration.Companion.seconds
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max

class OpenAiClientImpl(
    private val apiKey: String
) : LlmClient {

    private val httpClientConfig: HttpClientConfig<*>.() -> Unit = {
        install(createClientPlugin("RateLimitExtractor") {
            onResponse { response ->
                response.headers["x-ratelimit-limit-requests"]?.toIntOrNull()?.let {
                    logger().info("OpenAI Rate Limit Info - Limit: $it")
                    OpenAiHttpConfig.rateLimitLimit.set(it)
                }
                response.headers["x-ratelimit-remaining-requests"]?.toIntOrNull()?.let {
                    logger().info("OpenAI Rate Limit Info - Remaining: $it")
                    OpenAiHttpConfig.rateLimitRemaining.set(it)
                }
                response.headers["x-ratelimit-reset-requests"]?.let { resetStr ->
                    logger().info("OpenAI Rate Limit Info - Reset: $resetStr")
                    val seconds = parseResetTime(resetStr)
                    OpenAiHttpConfig.rateLimitResetTime.set(System.currentTimeMillis() + (seconds * 1000))
                }
            }
        })
    }

    private val openai = OpenAI(
        token = apiKey,
        timeout = Timeout(socket = 60.seconds),
        httpClientConfig = httpClientConfig
    )

    private val currentMaxPermits = AtomicInteger(3)
    private val semaphore = Semaphore(6)

    override suspend fun createChatCompletion(request: ChatRequest): ChatResponse {
        adjustSemaphoreFromRateLimit()

        return semaphore.withPermit {
            val remaining = OpenAiHttpConfig.rateLimitRemaining.get()
            val limit = OpenAiHttpConfig.rateLimitLimit.get()
            val availablePermits = semaphore.availablePermits
            val totalPermits = currentMaxPermits.get()
            val activeRequests = minOf(100 - availablePermits, totalPermits)

            logger().info("OpenAI request START - active: $activeRequests/$totalPermits, available: ${minOf(availablePermits, totalPermits)}, API: $remaining/$limit")

            val chatRequest = ChatCompletionRequest(
                model = ModelId(request.model),
                messages = request.messages.map {
                    ChatMessage(role = ChatRole(it.role), content = it.content)
                },
                temperature = request.temperature
            )

            val completion = openai.chatCompletion(chatRequest)

            logger().info("OpenAI request END - available: ${minOf(semaphore.availablePermits, currentMaxPermits.get())}/${currentMaxPermits.get()}")

            ChatResponse(
                id = completion.id,
                choices = completion.choices.map { choice ->
                    Choice(
                        message = Message(role = choice.message.role.role, content = choice.message.content ?: ""),
                        finishReason = choice.finishReason?.value
                    )
                },
                usage = completion.usage?.let { usage ->
                    Usage(promptTokens = usage.promptTokens, completionTokens = usage.completionTokens, totalTokens = usage.totalTokens)
                }
            )
        }
    }

    private fun parseResetTime(resetStr: String): Long {
        var totalSeconds = 0L
        Regex("(\\d+)([smh])").findAll(resetStr).forEach { match ->
            val value = match.groupValues[1].toLongOrNull() ?: 0
            totalSeconds += when (match.groupValues[2]) {
                "s" -> value
                "m" -> value * 60
                "h" -> value * 3600
                else -> 0
            }
        }
        return totalSeconds
    }

    private fun adjustSemaphoreFromRateLimit() {
        val remaining = OpenAiHttpConfig.rateLimitRemaining.get()
        val limit = OpenAiHttpConfig.rateLimitLimit.get()
        if (remaining < 0 || limit < 0) return

        val currentMax = currentMaxPermits.get()
        val remainingRatio = remaining.toDouble() / limit
        val targetPermits = when {
            remainingRatio > 0.8 -> max(10, limit / 1000)
            remainingRatio > 0.5 -> max(5, limit / 2000)
            remainingRatio > 0.2 -> max(3, limit / 5000)
            remainingRatio > 0.05 -> 2
            else -> 1
        }.coerceIn(1, 100)

        if (currentMax != targetPermits) {
            currentMaxPermits.set(targetPermits)
            val direction = if (targetPermits > currentMax) "INCREASED" else "DECREASED"
            val diff = targetPermits - currentMax
            logger().info("Semaphore $direction: $currentMax -> $targetPermits (${if (diff > 0) "+" else ""}$diff), API: $remaining/$limit (${String.format("%.2f", remainingRatio * 100)}%)")
        }
    }
}


