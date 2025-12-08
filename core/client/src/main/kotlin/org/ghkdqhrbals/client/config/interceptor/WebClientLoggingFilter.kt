package org.ghkdqhrbals.client.config.interceptor

import org.ghkdqhrbals.client.config.log.logger
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.ExchangeFunction
import reactor.core.publisher.Flux
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import reactor.core.publisher.Mono

@Component
class WebClientLoggingFilter : ExchangeFilterFunction {

    private val bufferFactory = DefaultDataBufferFactory()

    override fun filter(request: ClientRequest, next: ExchangeFunction): Mono<ClientResponse> {

        println("REQUEST → ${request.method()} ${request.url()}")

        return next.exchange(request).flatMap { response ->

            response.bodyToMono(ByteArray::class.java)
                .defaultIfEmpty(ByteArray(0))
                .flatMap { bytes ->

                    val bodyString = String(bytes, Charsets.UTF_8)
                    println("RESPONSE → ${response.statusCode()}")
                    println("BODY → $bodyString")

                    // ★ Mono → Flux 로 변환
                    val bodyFlux: Flux<DataBuffer> =
                        Flux.just(bufferFactory.wrap(bytes))

                    // ★ mutate().body(Flux<DataBuffer>) 정확히 매칭됨
                    val newResponse = response.mutate()
                        .body(bodyFlux)
                        .build()

                    Mono.just(newResponse)
                }
        }
    }
}