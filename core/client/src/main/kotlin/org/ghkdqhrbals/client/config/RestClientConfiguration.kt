package org.ghkdqhrbals.client.config

import io.netty.handler.logging.LogLevel
import org.ghkdqhrbals.client.config.interceptor.ApiCallInterceptor
import org.ghkdqhrbals.client.config.interceptor.WebClientLoggingFilter
import org.ghkdqhrbals.model.domain.Jackson
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.http.client.BufferingClientHttpRequestFactory
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.http.converter.ByteArrayHttpMessageConverter
import org.springframework.http.converter.StringHttpMessageConverter
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.web.client.RestClient
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.*
import reactor.core.publisher.Mono
import reactor.netty.http.client.HttpClient
import reactor.netty.resources.ConnectionProvider
import reactor.netty.resources.LoopResources
import reactor.netty.transport.logging.AdvancedByteBufFormat
import java.nio.charset.StandardCharsets
import java.time.Duration

@Configuration
class RestClientConfiguration(
    private val consoleLogApiCallInterceptor: ApiCallInterceptor,
) {
    companion object {
        val customJacksonMessageConverter =
            MappingJackson2HttpMessageConverter().also { it.objectMapper = Jackson.getMapper() }
    }
    @Bean
    fun webClient(
        loggingFilter: WebClientLoggingFilter,
    ): WebClient {

        val httpClient = HttpClient.create()
            .wiretap(
                "reactor.netty.http.client",
                LogLevel.INFO,
                AdvancedByteBufFormat.TEXTUAL   // ★ 실제 전송되는 바디까지 출력
            )

        return WebClient.builder()
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .filter(loggingFilter)  // response body 를 출력 및 복원
            .build()
    }

    @Primary
    @Bean(name = ["restClient"])
    fun restClient(): RestClient {
        val requestFactory = SimpleClientHttpRequestFactory()
        requestFactory.setConnectTimeout(2 * 1000)
        requestFactory.setReadTimeout(15 * 1000)

        return RestClient.builder()
            .requestFactory(requestFactory)
            .messageConverters { converters ->
                converters.add(0, stringXmlMessageConverter()) // 가장 먼저 매칭되도록
                converters.removeIf { it is MappingJackson2HttpMessageConverter }
                converters.add(1, customJacksonMessageConverter)
            }
            .build()
    }

    @Primary
    @Bean(name = ["ollamaRestClient"])
    fun orestClient(
        @Value("\${ollama.url}") ollamaUrl: String
    ): RestClient {
        val requestFactory = SimpleClientHttpRequestFactory()
        requestFactory.setConnectTimeout(5 * 1000) // 5초
        requestFactory.setReadTimeout(120 * 1000) // 120초 - LLM 응답 대기 시간

        return RestClient.builder()
            .baseUrl(ollamaUrl)
            .requestFactory(requestFactory)
            .messageConverters { converters ->
                converters.add(customJacksonMessageConverter)
                // ByteArray 컨버터 추가 - NDJSON 응답 처리용
                converters.add(ByteArrayHttpMessageConverter())
            }
            .build()
    }


    @Bean(name = ["plainClient"])
    fun plainClient(): RestClient {
        val requestFactory = BufferingClientHttpRequestFactory(SimpleClientHttpRequestFactory())

        return RestClient.builder()
            .requestFactory(requestFactory)
            // 인터셉터 제거: 응답 body를 읽어버려서 ArxivService에서 null 반환되는 문제 방지
            .messageConverters { it.add(ByteArrayHttpMessageConverter()) }
            .build()
    }

    @Bean(name = ["ollamaWebClient"])
    fun ollamaWebClient(
        @Value("\${ollama.url:http://localhost:11434}") ollamaUrl: String
    ): WebClient {

        val provider = ConnectionProvider.builder("ollama-pool")
            .maxConnections(200)
            .pendingAcquireMaxCount(1000)
            .build()

        val loop = LoopResources.create("ollama-loops", 50, true)   // ★ event-loop 50개

        val httpClient = HttpClient.create(provider)
            .runOn(loop)                                             // ★ multi-loop 적용
            .compress(false)
            .wiretap(false)
            .responseTimeout(Duration.ofSeconds(300))

        return WebClient.builder()
            .baseUrl(ollamaUrl)
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .build()
    }

    @Bean
    fun stringXmlMessageConverter(): StringHttpMessageConverter {
        val converter = StringHttpMessageConverter(StandardCharsets.UTF_8)
        converter.supportedMediaTypes = listOf(
            MediaType.APPLICATION_XML,
            MediaType.TEXT_XML,
            MediaType.APPLICATION_ATOM_XML,
            MediaType.APPLICATION_OCTET_STREAM,
            MediaType.ALL
        )
        return converter
    }
}
