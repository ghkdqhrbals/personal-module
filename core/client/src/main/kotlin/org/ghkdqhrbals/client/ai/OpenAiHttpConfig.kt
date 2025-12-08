package org.ghkdqhrbals.client.ai

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.api.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.util.*
import kotlinx.serialization.json.Json
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

@Configuration
class OpenAiHttpConfig {

    companion object {
        val rateLimitRemaining = AtomicInteger(-1)
        val rateLimitLimit = AtomicInteger(-1)
        val rateLimitResetTime = AtomicLong(-1)
    }

    @Bean
    fun openAiHttpClient(): HttpClient {
        return HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }

            install(Logging) {
                level = LogLevel.INFO
            }

            install(HttpRequestRetry) {
                maxRetries = 3
                retryOnServerErrors(maxRetries = 3)
                exponentialDelay()
            }

            // Response 인터셉터로 X-RateLimit 헤더 추출
            install(createClientPlugin("RateLimitExtractor") {
                onResponse { response ->
                    // X-RateLimit 헤더 파싱
                    response.headers["x-ratelimit-limit-requests"]?.toIntOrNull()?.let {
                        rateLimitLimit.set(it)
                    }
                    response.headers["x-ratelimit-remaining-requests"]?.toIntOrNull()?.let {
                        rateLimitRemaining.set(it)
                    }
                    response.headers["x-ratelimit-reset-requests"]?.let { resetStr ->
                        val seconds = parseResetTime(resetStr)
                        rateLimitResetTime.set(System.currentTimeMillis() + (seconds * 1000))
                    }
                }
            })

            defaultRequest {
                header("User-Agent", "ghkdqhrbals-openai-client/1.0")
            }
        }
    }

    private fun parseResetTime(resetStr: String): Long {
        var totalSeconds = 0L
        val regex = Regex("(\\d+)([smh])")
        regex.findAll(resetStr).forEach { match ->
            val value = match.groupValues[1].toLongOrNull() ?: 0
            val unit = match.groupValues[2]
            totalSeconds += when (unit) {
                "s" -> value
                "m" -> value * 60
                "h" -> value * 3600
                else -> 0
            }
        }
        return totalSeconds
    }
}

