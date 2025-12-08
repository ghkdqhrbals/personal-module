package org.ghkdqhrbals.orchestrator.saga.config

import org.ghkdqhrbals.orchestrator.saga.orchestrator.SagaOrchestrator
import org.ghkdqhrbals.message.saga.definition.SagaDefinitionFactory
import org.ghkdqhrbals.message.saga.definition.SagaType
import org.ghkdqhrbals.message.saga.definition.SagaTopics
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.EventListener

/**
 * Saga 정의 설정
 * message 모듈의 SagaDefinitionFactory를 사용하여 타입 안전한 Saga 정의
 */
@Configuration
class SagaDefinitionConfig(
    private val sagaOrchestrator: SagaOrchestrator
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    @Value("\${saga.response-topic:${SagaTopics.SAGA_RESPONSE}}")
    private lateinit var responseTopic: String

    @EventListener(ApplicationReadyEvent::class)
    fun registerSagaDefinitions() {
        // 모든 Saga Definition 등록
        SagaType.entries.forEach { sagaType ->
            val definition = SagaDefinitionFactory.create(sagaType, responseTopic)
            sagaOrchestrator.registerSagaDefinition(definition)
        }

        log.info("메모리에 Steps 미리 로드. Types: {}, Response topic: {}",
            SagaType.entries.map { it.name },
            responseTopic
        )
    }
}

