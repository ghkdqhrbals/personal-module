package org.ghkdqhrbals.message.saga.step

/**
 * Saga 스텝 정의
 */
data class SagaStep(
    val name: String,
    val index: Int,
    val commandTopic: String,
    val hasCompensation: Boolean = true,
    val compensationTopic: String? = null,
    val timeoutSeconds: Long = 30
)

/**
 * Saga 정의 - 순서대로 실행될 스텝들을 정의
 */
data class SagaDefinition(
    val sagaType: String,
    val steps: List<SagaStep>,
    val responseTopic: String  // 모든 응답을 받을 단일 토픽
) {
    init {
        require(steps.isNotEmpty()) { "Saga must have at least one step" }
        // 인덱스 검증
        steps.forEachIndexed { index, step ->
            require(step.index == index) { "Step index mismatch at position $index" }
        }
    }

    fun getStep(index: Int): SagaStep? = steps.getOrNull(index)

    fun getTotalSteps(): Int = steps.size

    fun getCompensationSteps(fromIndex: Int): List<SagaStep> {
        return steps.take(fromIndex + 1)
            .filter { it.hasCompensation }
            .reversed()
    }
}

/**
 * Saga Definition Builder
 */
class SagaDefinitionBuilder(private val sagaType: String) {
    private val steps = mutableListOf<SagaStep>()
    private var responseTopic: String = ""

    fun step(
        name: String,
        commandTopic: String,
        hasCompensation: Boolean = true,
        compensationTopic: String? = null,
        timeoutSeconds: Long = 30
    ): SagaDefinitionBuilder {
        steps.add(
            SagaStep(
                name = name,
                index = steps.size,
                commandTopic = commandTopic,
                hasCompensation = hasCompensation,
                compensationTopic = compensationTopic,
                timeoutSeconds = timeoutSeconds
            )
        )
        return this
    }

    fun responseTopic(topic: String): SagaDefinitionBuilder {
        this.responseTopic = topic
        return this
    }

    fun build(): SagaDefinition {
        require(responseTopic.isNotBlank()) { "Response topic must be specified" }
        return SagaDefinition(
            sagaType = sagaType,
            steps = steps.toList(),
            responseTopic = responseTopic
        )
    }
}

/**
 * DSL 함수
 */
fun sagaDefinition(sagaType: String, block: SagaDefinitionBuilder.() -> Unit): SagaDefinition {
    return SagaDefinitionBuilder(sagaType).apply(block).build()
}

