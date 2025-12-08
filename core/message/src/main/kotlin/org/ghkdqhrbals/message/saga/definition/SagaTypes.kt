package org.ghkdqhrbals.message.saga.definition

/**
 * Saga 타입 정의
 * 새로운 Saga 타입을 추가할 때 여기에 추가
 */
enum class SagaType(val description: String) {
    AI_PROCESS("AI 프로세스 Saga - 입력 검증, 전처리, 추론, 후처리, 저장"),
    AI_BATCH_INFERENCE("AI 배치 추론 Saga - 데이터 로드, 배치 추론, 결과 집계, 내보내기");

    companion object {
        fun fromString(value: String): SagaType? {
            return entries.find { it.name == value }
        }
    }
}

/**
 * AI Process Saga의 스텝 정의
 */
enum class AiProcessStep(
    val stepName: String,
    val commandTopic: String,
    val compensationTopic: String?,
    val hasCompensation: Boolean = true
) {
//    PREPROCESS_DATA(
//        stepName = "PREPROCESS_DATA",
//        commandTopic = SagaTopics.AiProcess.PREPROCESSING_COMMAND,
//        compensationTopic = SagaTopics.AiProcess.PREPROCESSING_COMPENSATION
//    ),
    RUN_AI_MODEL(
        stepName = "RUN_AI_MODEL",
        commandTopic = SagaTopics.AiProcess.INFERENCE_COMMAND,
        compensationTopic = SagaTopics.AiProcess.INFERENCE_COMPENSATION
    );
//    POSTPROCESS_RESULT(
//        stepName = "POSTPROCESS_RESULT",
//        commandTopic =  SagaTopics.AiProcess.POSTPROCESSING_COMMAND,
//        compensationTopic = SagaTopics.AiProcess.POSTPROCESSING_COMPENSATION
//    );

    companion object {
        fun fromStepName(name: String): AiProcessStep? {
            return entries.find { it.stepName == name }
        }

        fun orderedSteps(): List<AiProcessStep> = entries.toList()
    }
}

/**
 * AI Batch Inference Saga의 스텝 정의
 */
enum class AiBatchInferenceStep(
    val stepName: String,
    val commandTopic: String,
    val compensationTopic: String?,
    val hasCompensation: Boolean = true
) {
    LOAD_BATCH_DATA(
        stepName = "LOAD_BATCH_DATA",
        commandTopic = "ai-batch-loader-command",
        compensationTopic = "ai-batch-loader-compensation"
    ),
    BATCH_INFERENCE(
        stepName = "BATCH_INFERENCE",
        commandTopic = "ai-batch-inference-command",
        compensationTopic = "ai-batch-inference-compensation"
    ),
    AGGREGATE_RESULTS(
        stepName = "AGGREGATE_RESULTS",
        commandTopic = "ai-aggregation-command",
        compensationTopic = "ai-aggregation-compensation"
    ),
    EXPORT_RESULTS(
        stepName = "EXPORT_RESULTS",
        commandTopic = "ai-export-command",
        compensationTopic = null,
        hasCompensation = false
    );

    companion object {
        fun fromStepName(name: String): AiBatchInferenceStep? {
            return entries.find { it.stepName == name }
        }

        fun orderedSteps(): List<AiBatchInferenceStep> = entries.toList()
    }
}

