package org.ghkdqhrbals.model.event

import io.swagger.v3.oas.annotations.media.Schema

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
)

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
)

@Schema(description = "Saga 타입 정보")
data class SagaTypeInfo(
    @Schema(description = "Saga 타입 이름", example = "AI_PROCESS")
    val type: String
)