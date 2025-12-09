package org.ghkdqhrbals.orchestrator.saga.api

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.ghkdqhrbals.model.event.SagaEventType
import org.ghkdqhrbals.message.saga.definition.SagaType
import org.ghkdqhrbals.orchestrator.saga.orchestrator.SagaOrchestrator
import org.ghkdqhrbals.orchestrator.saga.service.SagaEventStreamService
import org.ghkdqhrbals.message.service.EventStoreService
import org.ghkdqhrbals.infra.event.SagaStateEntity
import org.ghkdqhrbals.infra.event.EventStoreEntity
import org.springframework.http.ResponseEntity
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

/**
 * Saga 관리 REST API
 */
@Tag(name = "Saga Management", description = "Saga 오케스트레이션 관리 API")
@RestController
@RequestMapping("/api/user")
class UserController(
    private val sagaOrchestrator: SagaOrchestrator,
    private val eventStoreService: EventStoreService,
    private val sagaEventStreamService: SagaEventStreamService
) {
    fun request() {

        // 비 관심사 분리
        // 1. arxiv req & res + save result

        // 2. ollama req & res + summary save

        // 3. push & email send event
        // 4. user 별 관심사 종합 insight 제공(~~주제에 관심이 있고 현재까지 ~~ 논문이 요약 및 저장되어 있습니다.)
        // 그래서 유저 별 추천 논문 제공할 수 있도록.

    }
}

