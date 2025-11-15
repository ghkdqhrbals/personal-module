package org.ghkdqhrbals.client.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springdoc.core.models.GroupedOpenApi
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Bean
    fun apiGroup(): GroupedOpenApi = GroupedOpenApi.builder()
        .group("client-api")
        .pathsToMatch("/api/**")
        .build()

    @Bean
    fun customOpenAPI(
        @Value("\${spring.application.name:client}") appName: String,
    ): OpenAPI = OpenAPI().info(
        Info()
            .title("$appName API")
            .version("v1")
            .description("OpenAI 텍스트 전송 API 문서")
    )
}

