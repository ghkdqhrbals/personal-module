package org.ghkdqhrbals.model.event


/**
 * Saga Definition Domain Model
 */
data class SagaDefinition(
    val sagaType: String,
    val steps: List<SagaStep>,
    val responseTopic: String
) {
    init {
        require(steps.isNotEmpty()) { "Saga must have at least one step" }
    }

    fun getStep(index: Int): SagaStep? = steps.getOrNull(index)
    fun getTotalSteps(): Int = steps.size
    fun isLastStep(index: Int): Boolean = index == steps.size - 1
}
