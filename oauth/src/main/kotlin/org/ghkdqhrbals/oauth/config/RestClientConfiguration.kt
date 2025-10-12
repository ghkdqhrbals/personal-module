package org.ghkdqhrbals.oauth.config

import org.ghkdqhrbals.oauth.config.log
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.BufferingClientHttpRequestFactory
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.io.ByteArrayInputStream
import java.io.InputStream

@Configuration
class RestClientConfiguration {

    @Bean
    fun restClient(): RestClient {
        val base = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(2_000)
            setReadTimeout(10_000)
        }
        val buffering = BufferingClientHttpRequestFactory(base)

        return RestClient.builder()
            .requestFactory(buffering)
            .requestInterceptors { it.add(loggingInterceptor()) }
//            .defaultStatusHandler({ true }) { _, _ -> }
            .build()
    }

    private fun loggingInterceptor(): ClientHttpRequestInterceptor =
        ClientHttpRequestInterceptor { req, body, exec ->
            val start = System.currentTimeMillis()
            val res = exec.execute(req, body)
            val elapsed = System.currentTimeMillis() - start
            val reqBody = body.toString(Charsets.UTF_8)
            val resBody = res.body.use { String(it.readAllBytes()) }

            log().info("[HTTP] {} {} | {}ms | status={} | req={} | res={}",
                req.method, req.uri, elapsed, res.statusCode.value(), reqBody.ifBlank { "-" }, resBody.ifBlank { "-" })

            object : ClientHttpResponse by res {
                override fun getBody(): InputStream = ByteArrayInputStream(resBody.toByteArray())
            }
        }
}