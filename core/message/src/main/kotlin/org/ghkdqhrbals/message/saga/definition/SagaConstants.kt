package org.ghkdqhrbals.message.saga.definition

/**
 * Kafka Topic 상수 정의
 * 모든 Saga 관련 토픽을 중앙에서 관리
 */
object SagaTopics {

    // Response Topic (공통)
    const val SAGA_RESPONSE = "saga-response"

    // AI Process Topics
    object AiProcess {

//        const val PREPROCESSING_COMMAND = "ai-preprocessing-command"
//        const val PREPROCESSING_COMPENSATION = "ai-preprocessing-compensation"

        const val INFERENCE_COMMAND = "ai-inference-command"
        const val INFERENCE_COMPENSATION = "ai-inference-compensation"

        const val POSTPROCESSING_COMMAND = "ai-postprocessing-command"
        const val POSTPROCESSING_COMPENSATION = "ai-postprocessing-compensation"

        const val STORAGE_COMMAND = "ai-storage-command"
    }
    
    // AI Batch Inference Topics
    object AiBatchInference {
        const val BATCH_LOADER_COMMAND = "ai-batch-loader-command"
        const val BATCH_LOADER_COMPENSATION = "ai-batch-loader-compensation"

        const val BATCH_INFERENCE_COMMAND = "ai-batch-inference-command"
        const val BATCH_INFERENCE_COMPENSATION = "ai-batch-inference-compensation"

        const val AGGREGATION_COMMAND = "ai-aggregation-command"
        const val AGGREGATION_COMPENSATION = "ai-aggregation-compensation"

        const val EXPORT_COMMAND = "ai-export-command"
    }

    /**
     * 모든 Command Topic 목록
     */
    fun getAllCommandTopics(): List<String> {
        return listOf(
            // AI Process
//            AiProcess.PREPROCESSING_COMMAND,
            AiProcess.INFERENCE_COMMAND,
//            AiProcess.POSTPROCESSING_COMMAND,
//            AiProcess.STORAGE_COMMAND,
            // AI Batch Inference
            AiBatchInference.BATCH_LOADER_COMMAND,
            AiBatchInference.BATCH_INFERENCE_COMMAND,
            AiBatchInference.AGGREGATION_COMMAND,
            AiBatchInference.EXPORT_COMMAND
        )
    }

    /**
     * 모든 Compensation Topic 목록
     */
    fun getAllCompensationTopics(): List<String> {
        return listOf(
            // AI Process
//            AiProcess.PREPROCESSING_COMPENSATION,
            AiProcess.INFERENCE_COMPENSATION,
            AiProcess.POSTPROCESSING_COMPENSATION,
            // AI Batch Inference
            AiBatchInference.BATCH_LOADER_COMPENSATION,
            AiBatchInference.BATCH_INFERENCE_COMPENSATION,
            AiBatchInference.AGGREGATION_COMPENSATION
        )
    }
}

/**
 * Service 이름 상수 정의
 */
object SagaServices {

    // AI Process Services
    const val AI_VALIDATION_SERVICE = "ai-validation-service"
    const val AI_PREPROCESSING_SERVICE = "ai-preprocessing-service"
    const val AI_INFERENCE_SERVICE = "ai-inference-service"
    const val AI_POSTPROCESSING_SERVICE = "ai-postprocessing-service"
    const val AI_STORAGE_SERVICE = "ai-storage-service"

    // AI Training Services
    const val AI_DATA_PREPARATION_SERVICE = "ai-data-preparation-service"
    const val AI_RESOURCE_SERVICE = "ai-resource-service"
    const val AI_TRAINING_SERVICE = "ai-training-service"
    const val AI_DEPLOYMENT_SERVICE = "ai-deployment-service"

    // AI Batch Inference Services
    const val AI_BATCH_LOADER_SERVICE = "ai-batch-loader-service"
    const val AI_BATCH_INFERENCE_SERVICE = "ai-batch-inference-service"
    const val AI_AGGREGATION_SERVICE = "ai-aggregation-service"
    const val AI_EXPORT_SERVICE = "ai-export-service"
}

