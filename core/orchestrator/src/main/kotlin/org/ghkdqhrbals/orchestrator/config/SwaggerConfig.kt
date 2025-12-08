package org.ghkdqhrbals.orchestrator.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.servers.Server
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SwaggerConfig {

    @Value("\${server.port:9090}")
    private val serverPort: Int = 9090

    @Bean
    fun openAPI(): OpenAPI {
        return OpenAPI()
            .info(
                Info()
                    .title("Saga Orchestrator API")
                    .description("""
                        ## Saga 패턴 기반 분산 트랜잭션 관리 API
                        
                        이 API는 마이크로서비스 환경에서 분산 트랜잭션을 관리하기 위한 Saga 패턴 오케스트레이터입니다.
                        
                        ### 주요 기능
                        - **Event Store**: 모든 Saga 이벤트를 순서대로 저장
                        - **단일 응답 토픽**: `saga-response` 토픽 하나에서 모든 서비스 응답 처리
                        - **자동 보상 트랜잭션**: 실패 시 역순으로 보상 스텝 실행
                        - **상태 추적**: 실시간 Saga 상태 및 이벤트 히스토리 조회
                        
                        ### Saga 시작 방법
                        1. `/api/saga/start` 엔드포인트로 Saga 시작
                        2. 각 서비스는 명령을 받아 처리 후 `saga-response` 토픽에 응답
                        3. 오케스트레이터가 자동으로 다음 스텝 실행 또는 보상 처리
                    """.trimIndent())
                    .version("1.0.0")
                    .contact(
                        Contact()
                            .name("Saga Orchestrator Team")
                            .email("support@example.com")
                    )
            )
            .servers(
                listOf(
                    Server()
                        .url("http://localhost:$serverPort")
                        .description("로컬 개발 서버")
                )
            )
    }
}

