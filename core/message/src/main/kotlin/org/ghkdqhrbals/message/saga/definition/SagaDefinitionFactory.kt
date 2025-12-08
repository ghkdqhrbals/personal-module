package org.ghkdqhrbals.message.saga.definition

import org.ghkdqhrbals.model.event.SagaDefinition
import org.ghkdqhrbals.model.event.SagaStep

/**
 * Saga Definition Factory 미리 이걸로 메모리에 로드
 */
object SagaDefinitionFactory {

    /**
     * SagaType에 해당하는 SagaDefinition 생성
     */
    fun create(sagaType: SagaType, responseTopic: String): SagaDefinition {
        return when (sagaType) {
            SagaType.AI_PROCESS -> createAiProcessSaga(responseTopic)
            SagaType.AI_BATCH_INFERENCE -> createAiBatchInferenceSaga(responseTopic)
        }
    }

    /**
     * AI Process Saga Definition 생성
     */
    fun createAiProcessSaga(responseTopic: String): SagaDefinition {
        val steps = AiProcessStep.orderedSteps().mapIndexed { index, step ->
            SagaStep(
                name = step.stepName,
                index = index,
                commandTopic = step.commandTopic,
                hasCompensation = step.hasCompensation,
                compensationTopic = step.compensationTopic
            )
        }

        return SagaDefinition(
            sagaType = SagaType.AI_PROCESS.name,
            steps = steps,
            responseTopic = responseTopic
        )
    }

    /**
     * AI Batch Inference Saga Definition 생성
     */
    fun createAiBatchInferenceSaga(responseTopic: String): SagaDefinition {
        val steps = AiBatchInferenceStep.orderedSteps().mapIndexed { index, step ->
            SagaStep(
                name = step.stepName,
                index = index,
                commandTopic = step.commandTopic,
                hasCompensation = step.hasCompensation,
                compensationTopic = step.compensationTopic
            )
        }

        return SagaDefinition(
            sagaType = SagaType.AI_BATCH_INFERENCE.name,
            steps = steps,
            responseTopic = responseTopic
        )
    }

    /**
     * 모든 Saga Definition 생성
     */
    fun createAll(responseTopic: String): List<SagaDefinition> {
        return SagaType.entries.map { create(it, responseTopic) }
    }
}

