package org.ghkdqhrbals.client.ai

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.slf4j.LoggerFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URI
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.TimeUnit
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.http.HttpStatus

@RestController
class OllamaAbTestController {
    private val log = LoggerFactory.getLogger(this::class.java)

    data class VariantResult(
        val url: String,
        val client: String,
        val success: Boolean,
        val status: Int?,
        val durationMs: Long,
        val error: String? = null,
        val bodyPreview: String? = null,
        val resolvedIp: String? = null,
        val tcpConnectOk: Boolean? = null
    )

    data class AbSuiteResult(
        val prompt: String,
        val model: String,
        val results: List<VariantResult>
    )

    @GetMapping("/api/chat/ollama-ab-test")
    fun abTest(
        @RequestParam(defaultValue = "Say hello in one word") prompt: String,
        @RequestParam(defaultValue = "gemma3") model: String,
        @RequestParam(required = false) customUrl: String?
    ): ResponseEntity<AbSuiteResult> {
        val defaultUrls = listOf(
//            "http://127.0.0.1:11434",
//            "http://localhost:11434",
//            "http://host.docker.internal:11434",
            "http://[::1]:11434"
        )
        val urls = if (customUrl.isNullOrBlank()) defaultUrls else listOf(customUrl)

        val bodyJson = """{"model":"$model","prompt":"$prompt","stream":false}"""
        val results = mutableListOf<VariantResult>()

        urls.forEach { base ->
            val (resolved, tcpOk) = diagnose(base)

            // OkHttp
            results += runCatching { callOkHttp(base, bodyJson, resolved, tcpOk) }
                .getOrElse { e -> VariantResult(base, "OkHttp", false, null, 0, error = fullError(e), resolvedIp = resolved, tcpConnectOk = tcpOk) }

            // Java HttpClient
            results += runCatching { callJavaHttp(base, bodyJson, resolved, tcpOk) }
                .getOrElse { e -> VariantResult(base, "JavaHttp", false, null, 0, error = fullError(e), resolvedIp = resolved, tcpConnectOk = tcpOk) }

            // WebClient
            results += runCatching { callWebClient(base, bodyJson, resolved, tcpOk) }
                .getOrElse { e -> VariantResult(base, "WebClient", false, null, 0, error = fullError(e), resolvedIp = resolved, tcpConnectOk = tcpOk) }
        }

        return ResponseEntity.ok(AbSuiteResult(prompt, model, results))
    }

    private fun fullError(e: Throwable): String = buildString {
        append("${e.javaClass.simpleName}: ${e.message}")
        var cause = e.cause
        var depth = 0
        while (cause != null && depth < 3) {
            append(" | caused by ${cause.javaClass.simpleName}: ${cause.message}")
            cause = cause.cause
            depth++
        }
    }

    private fun diagnose(base: String): Pair<String?, Boolean?> {
        return try {
            val uri = URI(base)
            val host = uri.host
            val ipList = InetAddress.getAllByName(host).joinToString(",") { it.hostAddress }
            val ip = ipList.split(',').firstOrNull()
            val tcpOk = try {
                Socket().use { s ->
                    s.connect(InetSocketAddress(host, uri.port.takeIf { it > 0 } ?: 11434), 1500)
                    true
                }
            } catch (_: Exception) { false }
            log.info("[AB][DIAG] $base -> host=$host, resolved=$ipList, tcp=$tcpOk")
            ip to tcpOk
        } catch (e: Exception) {
            log.warn("[AB][DIAG] $base DNS/Socket failed: ${e.message}")
            null to null
        }
    }

    private fun callOkHttp(base: String, json: String, resolved: String?, tcpOk: Boolean?): VariantResult {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
        val media = "application/json; charset=utf-8".toMediaType()
        val req = Request.Builder()
            .url("$base/api/generate")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .post(json.toRequestBody(media))
            .build()
        val start = System.currentTimeMillis()
        client.newCall(req).execute().use { resp ->
            val dur = System.currentTimeMillis() - start
            val body = resp.body?.string()
            val preview = body?.take(120)
            log.info("[AB][OkHttp] $base -> status=${resp.code}, ${dur}ms, bodyPreview=$preview")
            return VariantResult(base, "OkHttp", resp.isSuccessful, resp.code, dur, bodyPreview = preview, resolvedIp = resolved, tcpConnectOk = tcpOk)
        }
    }

    private fun callJavaHttp(base: String, json: String, resolved: String?, tcpOk: Boolean?): VariantResult {
        val client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build()
        val req = HttpRequest.newBuilder()
            .uri(URI.create("$base/api/generate"))
            .timeout(Duration.ofSeconds(60))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build()
        val start = System.currentTimeMillis()
        val resp = client.send(req, HttpResponse.BodyHandlers.ofString())
        val dur = System.currentTimeMillis() - start
        val preview = resp.body()?.take(120)
        log.info("[AB][JavaHttp] $base -> status=${resp.statusCode()}, ${dur}ms, bodyPreview=$preview")
        return VariantResult(base, "JavaHttp", resp.statusCode() in 200..299, resp.statusCode(), dur, bodyPreview = preview, resolvedIp = resolved, tcpConnectOk = tcpOk)
    }

    private fun callWebClient(base: String, json: String, resolved: String?, tcpOk: Boolean?): VariantResult {
        val client = WebClient.builder()
            .baseUrl(base)
            .build()
        val start = System.currentTimeMillis()
        val pair = client.post()
            .uri("/api/generate")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .bodyValue(json)
            .exchangeToMono { r ->
                r.bodyToMono(String::class.java)
                    .defaultIfEmpty("")
                    .map { body -> r.statusCode() to body }
            }
            .block()
        val dur = System.currentTimeMillis() - start
        val status = pair?.first ?: HttpStatus.INTERNAL_SERVER_ERROR
        val body = pair?.second
        val preview = body?.take(120)
        log.info("[AB][WebClient] $base -> status=${status.value()}, ${dur}ms, bodyPreview=$preview")
        return VariantResult(base, "WebClient", status.is2xxSuccessful, status.value(), dur, bodyPreview = preview, resolvedIp = resolved, tcpConnectOk = tcpOk)
    }
}
