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
import org.ghkdqhrbals.repository.event.SagaStateEntity
import org.ghkdqhrbals.repository.event.EventStoreEntity
import org.springframework.http.ResponseEntity
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

/**
 * Saga 관리 REST API
 */
@Tag(name = "Saga Management", description = "Saga 오케스트레이션 관리 API")
@RestController
@RequestMapping("/api/saga")
class SagaController(
    private val sagaOrchestrator: SagaOrchestrator,
    private val eventStoreService: EventStoreService,
    private val sagaEventStreamService: SagaEventStreamService
) {

    /**
     * 새로운 Saga 시작
     */
    @Operation(
        summary = "새로운 Saga 시작",
        description = "지정된 타입의 Saga를 시작하고 sagaId를 반환합니다."
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Saga 시작 성공",
                content = [Content(schema = Schema(implementation = StartSagaResponse::class))]
            ),
            ApiResponse(responseCode = "400", description = "잘못된 요청"),
            ApiResponse(responseCode = "500", description = "서버 오류")
        ]
    )
    @PostMapping("/start")
    fun startSaga(@RequestBody request: StartSagaRequest): ResponseEntity<StartSagaResponse> {
        val sagaId = sagaOrchestrator.startSaga(request.sagaType, request.data)
        return ResponseEntity.ok(StartSagaResponse(sagaId))
    }

    /**
     * Saga 상태 조회
     */
    @Operation(
        summary = "Saga 상태 조회",
        description = "특정 Saga의 현재 상태를 조회합니다."
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "조회 성공",
                content = [Content(schema = Schema(implementation = SagaStateResponse::class))]
            ),
            ApiResponse(responseCode = "404", description = "Saga를 찾을 수 없음")
        ]
    )
    @GetMapping("/{sagaId}")
    fun getSagaState(
        @Parameter(description = "Saga ID", required = true)
        @PathVariable sagaId: String
    ): ResponseEntity<SagaStateResponse> {
        val state = eventStoreService.getSagaState(sagaId)
            ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(SagaStateResponse.from(state))
    }

    /**
     * Saga 이벤트 히스토리 조회
     */
    @Operation(
        summary = "Saga 이벤트 히스토리 조회",
        description = "특정 Saga의 모든 이벤트를 순서대로 조회합니다."
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "조회 성공"
            )
        ]
    )
    @GetMapping("/{sagaId}/events")
    fun getSagaEvents(
        @Parameter(description = "Saga ID", required = true)
        @PathVariable sagaId: String
    ): ResponseEntity<List<EventResponse>> {
        val events = eventStoreService.getEventsBySagaId(sagaId)
        return ResponseEntity.ok(events.map { EventResponse.from(it) })
    }

    /**
     * Saga 이벤트 소싱 전체 데이터 조회 (이벤트 + 최종 상태)
     */
    @Operation(
        summary = "Saga 이벤트 소싱 전체 조회",
        description = "특정 Saga의 모든 이벤트와 최종 상태를 함께 조회합니다. Event Sourcing 패턴으로 저장된 모든 데이터를 반환합니다."
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "조회 성공"
            ),
            ApiResponse(responseCode = "404", description = "Saga를 찾을 수 없음")
        ]
    )
    @GetMapping("/{sagaId}/event-sourcing")
    fun getSagaEventSourcing(
        @Parameter(description = "Saga ID", required = true)
        @PathVariable sagaId: String
    ): ResponseEntity<Any> {
        val eventSourcing = eventStoreService.getSagaEventSourcing(sagaId)
            ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(eventSourcing)
    }

    /**
     * 활성 Saga 목록 조회
     */
    @Operation(
        summary = "활성 Saga 목록 조회",
        description = "현재 실행 중이거나 보상 중인 모든 Saga를 조회합니다."
    )
    @GetMapping("/active")
    fun getActiveSagas(): ResponseEntity<List<SagaStateResponse>> {
        val sagas = eventStoreService.getActiveSagas()
        return ResponseEntity.ok(sagas.map { SagaStateResponse.from(it) })
    }

    /**
     * 등록된 Saga 타입 목록 조회
     */
    @Operation(
        summary = "등록된 Saga 타입 목록",
        description = "시스템에 등록된 모든 Saga 타입을 조회합니다."
    )
    @GetMapping("/types")
    fun getSagaTypes(): ResponseEntity<List<SagaTypeInfo>> {
        // SagaType enum에서 모든 타입 가져옴
        val types = SagaType.entries.map { SagaTypeInfo(it.name, it.description) }
        return ResponseEntity.ok(types)
    }

    /**
     * SSE를 통한 Saga 이벤트 실시간 스트리밍
     */
    @Operation(
        summary = "Saga 이벤트 실시간 스트리밍 (SSE)",
        description = """
            Server-Sent Events(SSE)를 통해 특정 Saga의 이벤트를 실시간으로 수신합니다.
            
            연결 후 다음 이벤트들을 수신할 수 있습니다:
            - connected: 초기 연결 확인
            - saga-event: Saga 상태 변경 이벤트
            
            이벤트 데이터에는 현재 Saga 상태, 스텝 정보, 성공/실패 여부 등이 포함됩니다.
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "SSE 스트림 연결 성공"
            ),
            ApiResponse(responseCode = "404", description = "Saga를 찾을 수 없음")
        ]
    )
    @GetMapping("/{sagaId}/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun streamSagaEvents(
        @Parameter(description = "Saga ID", required = true)
        @PathVariable sagaId: String
    ): SseEmitter {
        // Saga 존재 여부 확인
        val state = eventStoreService.getSagaState(sagaId)
            ?: throw IllegalArgumentException("Saga not found: $sagaId")
        if (state.isCompleted()) {
            sagaEventStreamService.publishEvent(sagaId, "Saga already completed")
        }


        return sagaEventStreamService.createEmitter(sagaId)
    }
}

// Request/Response DTOs
@Schema(description = "Saga 시작 요청")
data class StartSagaRequest(
    @Schema(description = "Saga 타입 (예: AI_PROCESS, AI_TRAINING, AI_BATCH_INFERENCE)", example = "AI_PROCESS")
    val sagaType: String,

    @Schema(description = "Saga 실행에 필요한 데이터", example = "{\"userId\": \"user-123\", \"inputData\": \"sample-data\", \"modelId\": \"model-v1\"}")
    val data: Map<String, Any>
)

@Schema(description = "Saga 시작 응답")
data class StartSagaResponse(
    @Schema(description = "생성된 Saga ID", example = "550e8400-e29b-41d4-a716-446655440000")
    val sagaId: String
)

@Schema(description = "Saga 상태 정보")
data class SagaStateResponse(
    @Schema(description = "Saga ID", example = "550e8400-e29b-41d4-a716-446655440000")
    val sagaId: String,

    @Schema(description = "Saga 타입", example = "AI_PROCESS")
    val sagaType: String,

    @Schema(description = "현재 상태", example = "IN_PROGRESS", allowableValues = ["STARTED", "IN_PROGRESS", "COMPENSATING", "COMPLETED", "FAILED", "COMPENSATION_COMPLETED"])
    val status: String,

    @Schema(description = "현재 스텝 인덱스", example = "2")
    val currentStepIndex: Int,

    @Schema(description = "전체 스텝 수", example = "5")
    val totalSteps: Int,

    @Schema(description = "생성 시간", example = "2025-12-07T10:30:00.000Z")
    val createdAt: String,

    @Schema(description = "수정 시간", example = "2025-12-07T10:35:00.000Z")
    val updatedAt: String
) {
    companion object {
        fun from(entity: SagaStateEntity) = SagaStateResponse(
            sagaId = entity.sagaId,
            sagaType = entity.sagaType,
            status = entity.status.name,
            currentStepIndex = entity.currentStepIndex,
            totalSteps = entity.totalSteps,
            createdAt = entity.createdAt.toString(),
            updatedAt = entity.updatedAt.toString()
        )
    }
}

@Schema(description = "Saga 이벤트 정보")
data class EventResponse(
    @Schema(description = "이벤트 ID", example = "evt-123")
    val eventId: String,

    @Schema(description = "Saga ID", example = "550e8400-e29b-41d4-a716-446655440000")
    val sagaId: String,

    @Schema(description = "이벤트 시퀀스 번호", example = "5")
    val sequenceNumber: Long,

    @Schema(description = "이벤트 타입", example = "SAGA_STEP_COMPLETED")
    val eventType: SagaEventType,

    @Schema(description = "발생 시간", example = "2025-12-07T10:32:00.000Z")
    val timestamp: String,

    @Schema(description = "스텝 이름", example = "RUN_AI_MODEL")
    val stepName: String?,

    @Schema(description = "스텝 인덱스", example = "2")
    val stepIndex: Int?,

    @Schema(description = "성공 여부", example = "true")
    val success: Boolean,

    @Schema(description = "에러 메시지", example = "Model inference failed")
    val errorMessage: String?
) {
    companion object {
        fun from(entity: EventStoreEntity) = EventResponse(
            eventId = entity.eventId,
            sagaId = entity.sagaId,
            sequenceNumber = entity.sequenceNumber,
            eventType = entity.eventType,
            timestamp = entity.timestamp.toString(),
            stepName = entity.stepName,
            stepIndex = entity.stepIndex,
            success = entity.success,
            errorMessage = entity.errorMessage
        )
    }
}

@Schema(description = "Saga 타입 정보")
data class SagaTypeInfo(
    @Schema(description = "Saga 타입 이름", example = "AI_PROCESS")
    val type: String,

    @Schema(description = "Saga 타입 설명", example = "AI 프로세스 Saga - 입력 검증, 전처리, 추론, 후처리, 저장")
    val description: String
)

