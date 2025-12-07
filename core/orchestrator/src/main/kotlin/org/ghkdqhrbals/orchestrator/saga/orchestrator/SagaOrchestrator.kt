package org.ghkdqhrbals.orchestrator.saga.orchestrator

import com.fasterxml.jackson.databind.ObjectMapper
import org.ghkdqhrbals.message.kafka.sender.KafkaMessageSender
import org.ghkdqhrbals.model.event.*
import org.ghkdqhrbals.message.service.EventStoreService

import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Saga Orchestrator - Saga 실행을 관리하고 이벤트를 순서대로 전송
 */
@Component
class SagaOrchestrator(
    private val sender: KafkaMessageSender,
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val eventStoreService: EventStoreService,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(SagaOrchestrator::class.java)

    // 등록된 Saga 정의들
    private val sagaDefinitions = mutableMapOf<String, SagaDefinition>()

    /**
     * Saga 정의 등록
     */
    fun registerSagaDefinition(definition: SagaDefinition) {
        sagaDefinitions[definition.sagaType] = definition
        log.info("Saga definition registered: type={}, steps={}",
            definition.sagaType, definition.getTotalSteps())
    }

    /**
     * Saga 정의 조회
     */
    fun getSagaDefinition(sagaType: String): SagaDefinition? = sagaDefinitions[sagaType]

    /**
     * 새로운 Saga 시작
     */
    @Transactional
    fun startSaga(
        sagaType: String,
        sagaData: Map<String, Any>
    ): String {
        val definition = sagaDefinitions[sagaType]
            ?: throw IllegalArgumentException("Unknown saga type: $sagaType")

        val sagaId = UUID.randomUUID().toString()

        // Saga 상태 생성
        eventStoreService.createSagaState(
            sagaId = sagaId,
            sagaType = sagaType,
            totalSteps = definition.getTotalSteps(),
            sagaData = sagaData
        )

        // SAGA_STARTED 이벤트 저장
        val startEvent = BaseSagaEvent(
            sagaId = sagaId,
            eventType = SagaEventType.SAGA_STARTED,
            payload = sagaData
        )
        eventStoreService.appendEvent(startEvent)

        log.info("Saga started: sagaId={}, type={}", sagaId, sagaType)

        // 첫 번째 스텝 실행
        executeStep(sagaId, definition, 0, sagaData)

        return sagaId
    }

    /**
     * 특정 스텝 실행
     */
    @Transactional
    fun executeStep(
        sagaId: String,
        definition: SagaDefinition,
        stepIndex: Int,
        sagaData: Map<String, Any>
    ) {
        val step = definition.getStep(stepIndex) ?: return

        // 상태 업데이트
        eventStoreService.updateSagaState(
            sagaId = sagaId,
            status = SagaStatus.IN_PROGRESS,
            currentStepIndex = stepIndex
        )

        // 명령 이벤트 생성
        val commandEvent = SagaCommandEvent(
            sagaId = sagaId,
            eventType = SagaEventType.SAGA_STEP_STARTED,
            stepName = step.name,
            stepIndex = stepIndex,
            commandTopic = step.commandTopic,
            responseTopic = definition.responseTopic,
            payload = sagaData + mapOf(
                "sagaId" to sagaId,
                "stepName" to step.name,
                "stepIndex" to stepIndex,
                "responseTopic" to definition.responseTopic
            )
        )

        // Event Store에 저장
        eventStoreService.appendEvent(commandEvent)

        // Kafka로 명령 전송
        sender.send(step.commandTopic, sagaId, objectMapper.writeValueAsString(commandEvent))

        log.info("Step executed: sagaId={}, step={}",
            sagaId, step.name)
    }

    /**
     * 응답 처리 - 단일 응답 토픽에서 모든 응답 처리
     */
    @Transactional
    fun handleResponse(responseEvent: SagaResponseEvent) {
        val sagaState = eventStoreService.getSagaState(responseEvent.sagaId)
            ?: throw IllegalStateException("Saga not found: ${responseEvent.sagaId}")

        val definition = sagaDefinitions[sagaState.sagaType]
            ?: throw IllegalStateException("Unknown saga type: ${sagaState.sagaType}")

        // 응답 이벤트 저장
        eventStoreService.appendEvent(responseEvent)

        if (responseEvent.success) {
            handleSuccessResponse(sagaState.sagaId, definition, responseEvent)
        } else {
            handleFailureResponse(sagaState.sagaId, definition, responseEvent)
        }
    }

    /**
     * 성공 응답 처리
     */
    private fun handleSuccessResponse(
        sagaId: String,
        definition: SagaDefinition,
        responseEvent: SagaResponseEvent
    ) {
        val sagaState = eventStoreService.getSagaState(sagaId)!!
        val nextStepIndex = responseEvent.stepIndex + 1

        // 완료 이벤트 저장
        val completedEvent = BaseSagaEvent(
            sagaId = sagaId,
            eventType = SagaEventType.SAGA_STEP_COMPLETED,
            payload = responseEvent.payload + mapOf(
                "stepName" to responseEvent.stepName,
                "stepIndex" to responseEvent.stepIndex
            )
        )
        eventStoreService.appendEvent(completedEvent)

        // 다음 스텝이 있으면 실행
        if (nextStepIndex < definition.getTotalSteps()) {
            val sagaData = eventStoreService.parseSagaData(sagaState.sagaData) + responseEvent.payload
            eventStoreService.updateSagaState(sagaId, sagaData = sagaData)
            executeStep(sagaId, definition, nextStepIndex, sagaData)
        } else {
            // Saga 완료
            completeSaga(sagaId)
        }
    }

    /**
     * 실패 응답 처리 - 보상 트랜잭션 시작
     */
    private fun handleFailureResponse(
        sagaId: String,
        definition: SagaDefinition,
        responseEvent: SagaResponseEvent
    ) {
        // 실패 이벤트 저장
        val failedEvent = BaseSagaEvent(
            sagaId = sagaId,
            eventType = SagaEventType.SAGA_STEP_FAILED,
            payload = mapOf(
                "stepName" to responseEvent.stepName,
                "stepIndex" to responseEvent.stepIndex,
                "errorMessage" to (responseEvent.errorMessage ?: "Unknown error")
            )
        )
        eventStoreService.appendEvent(failedEvent)

        log.warn("Step failed: sagaId={}, step={}, error={}",
            sagaId, responseEvent.stepName, responseEvent.errorMessage)

        // 보상 트랜잭션 시작
        startCompensation(sagaId, definition, responseEvent.stepIndex - 1)
    }

    /**
     * 보상 트랜잭션 시작
     */
    @Transactional
    fun startCompensation(sagaId: String, definition: SagaDefinition, fromStepIndex: Int) {
        if (fromStepIndex < 0) {
            // 보상할 스텝이 없음
            failSaga(sagaId)
            return
        }

        // 상태 업데이트
        eventStoreService.updateSagaState(
            sagaId = sagaId,
            status = SagaStatus.COMPENSATING
        )

        // 보상 시작 이벤트 저장
        val compensatingEvent = BaseSagaEvent(
            sagaId = sagaId,
            eventType = SagaEventType.SAGA_COMPENSATING,
            payload = mapOf("fromStepIndex" to fromStepIndex)
        )
        eventStoreService.appendEvent(compensatingEvent)

        log.info("Starting compensation: sagaId={}, fromStep={}", sagaId, fromStepIndex)

        // 보상 스텝 실행
        executeCompensationStep(sagaId, definition, fromStepIndex)
    }

    /**
     * 보상 스텝 실행
     */
    private fun executeCompensationStep(
        sagaId: String,
        definition: SagaDefinition,
        stepIndex: Int
    ) {
        if (stepIndex < 0) {
            // 모든 보상 완료
            completeCompensation(sagaId)
            return
        }

        val step = definition.getStep(stepIndex) ?: return

        if (!step.hasCompensation || step.compensationTopic == null) {
            // 보상이 없는 스텝은 건너뜀
            executeCompensationStep(sagaId, definition, stepIndex - 1)
            return
        }

        val sagaState = eventStoreService.getSagaState(sagaId)!!
        val sagaData = eventStoreService.parseSagaData(sagaState.sagaData)

        // 보상 명령 이벤트 생성
        val compensationEvent = SagaCommandEvent(
            sagaId = sagaId,
            eventType = SagaEventType.SAGA_COMPENSATING,
            stepName = "${step.name}_COMPENSATION",
            stepIndex = stepIndex,
            commandTopic = step.compensationTopic!!,
            responseTopic = definition.responseTopic,
            payload = sagaData + mapOf(
                "sagaId" to sagaId,
                "stepName" to "${step.name}_COMPENSATION",
                "stepIndex" to stepIndex,
                "responseTopic" to definition.responseTopic,
                "isCompensation" to true
            )
        )

        eventStoreService.appendEvent(compensationEvent)
        sender.send(step.compensationTopic!!, sagaId, objectMapper.writeValueAsString(compensationEvent))

        log.info("Compensation step executed: sagaId={}, step={}", sagaId, step.name)
    }

    /**
     * 보상 응답 처리
     */
    @Transactional
    fun handleCompensationResponse(responseEvent: SagaResponseEvent) {
        val sagaState = eventStoreService.getSagaState(responseEvent.sagaId)
            ?: throw IllegalStateException("Saga not found: ${responseEvent.sagaId}")

        val definition = sagaDefinitions[sagaState.sagaType]
            ?: throw IllegalStateException("Unknown saga type: ${sagaState.sagaType}")

        eventStoreService.appendEvent(responseEvent)

        if (responseEvent.success) {
            // 다음 보상 스텝 실행
            executeCompensationStep(responseEvent.sagaId, definition, responseEvent.stepIndex - 1)
        } else {
            // 보상 실패 - 수동 개입 필요
            log.error("Compensation failed: sagaId={}, step={}, error={}",
                responseEvent.sagaId, responseEvent.stepName, responseEvent.errorMessage)
            failSaga(responseEvent.sagaId)
        }
    }

    /**
     * Saga 완료
     */
    private fun completeSaga(sagaId: String) {
        eventStoreService.updateSagaState(sagaId, status = SagaStatus.COMPLETED)

        val completedEvent = BaseSagaEvent(
            sagaId = sagaId,
            eventType = SagaEventType.SAGA_COMPLETED
        )
        eventStoreService.appendEvent(completedEvent)

        log.info("Saga completed: sagaId={}", sagaId)
    }

    /**
     * 보상 완료
     */
    private fun completeCompensation(sagaId: String) {
        eventStoreService.updateSagaState(sagaId, status = SagaStatus.COMPENSATION_COMPLETED)

        val completedEvent = BaseSagaEvent(
            sagaId = sagaId,
            eventType = SagaEventType.SAGA_COMPENSATION_COMPLETED
        )
        eventStoreService.appendEvent(completedEvent)

        log.info("Compensation completed: sagaId={}", sagaId)
    }

    /**
     * Saga 실패
     */
    private fun failSaga(sagaId: String) {
        eventStoreService.updateSagaState(sagaId, status = SagaStatus.FAILED)

        val failedEvent = BaseSagaEvent(
            sagaId = sagaId,
            eventType = SagaEventType.SAGA_FAILED
        )
        eventStoreService.appendEvent(failedEvent)

        log.error("Saga failed: sagaId={}", sagaId)
    }
}

