package org.ghkdqhrbals.model.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.ghkdqhrbals.model.event.parser.EventParser
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

/**
 * Model 모듈 자동 설정
 * 이 모듈을 import하는 프로젝트에서 자동으로 Bean이 등록됩니다.
 */
@Configuration
@ComponentScan(basePackages = ["org.ghkdqhrbals.model"])
class ModelAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ObjectMapper::class)
    fun modelObjectMapper(): ObjectMapper {
        return jacksonObjectMapper().apply {
            registerModule(JavaTimeModule())
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        }
    }

    @Bean
    @ConditionalOnMissingBean(EventParser::class)
    fun eventParser(objectMapper: ObjectMapper): EventParser {
        return EventParser(objectMapper)
    }
}

