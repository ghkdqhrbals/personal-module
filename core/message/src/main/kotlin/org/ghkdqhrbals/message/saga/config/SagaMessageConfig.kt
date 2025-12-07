package org.ghkdqhrbals.message.saga.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

/**
 * Saga 메시지 설정
 * message 모듈의 모든 컴포넌트를 스캔
 */
@Configuration
@ComponentScan(basePackages = ["org.ghkdqhrbals.message"])
class SagaMessageConfig {

    @Bean
    @ConditionalOnMissingBean(ObjectMapper::class)
    fun messageObjectMapper(): ObjectMapper {
        return jacksonObjectMapper().apply {
            registerModule(JavaTimeModule())
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
        }
    }
}

