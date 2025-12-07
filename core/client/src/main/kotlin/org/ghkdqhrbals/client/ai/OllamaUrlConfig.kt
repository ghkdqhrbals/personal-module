package org.ghkdqhrbals.client.ai

import okhttp3.OkHttpClient
import okhttp3.Request
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.TimeUnit

@Configuration
class OllamaUrlConfig {
    private val log = LoggerFactory.getLogger(this::class.java)

    @Bean(name = ["resolvedOllamaUrl"])
    fun resolvedOllamaUrl(
        @Value("\${ollama.url:}") configured: String,
    ): String {
        if (configured.isNotBlank()) {
            log.info("[OllamaUrl] Using configured ollama.url=$configured")
            return configured
        }

        val candidates = listOf(
            "http://[::1]:11434",
            "http://127.0.0.1:11434",
            "http://localhost:11434",
            "http://host.docker.internal:11434",
        )
        val client = OkHttpClient.Builder()
            .connectTimeout(1500, TimeUnit.MILLISECONDS)
            .readTimeout(1500, TimeUnit.MILLISECONDS)
            .build()

        for (base in candidates) {
            try {
                val req = Request.Builder()
                    .url("$base/api/tags")
                    .get()
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        log.info("[OllamaUrl] Resolved base URL -> $base (status=${resp.code})")
                        return base
                    } else {
                        log.warn("[OllamaUrl] Probe failed for $base (status=${resp.code})")
                    }
                }
            } catch (e: Exception) {
                log.warn("[OllamaUrl] Probe error for $base: ${e.message}")
            }
        }
        // 마지막 수단: 기본값 (IPv6 우선)
        val fallback = "http://[::1]:11434"
        log.warn("[OllamaUrl] All probes failed. Falling back to $fallback")
        return fallback
    }
}

