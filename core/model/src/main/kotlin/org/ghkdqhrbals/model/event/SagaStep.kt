package org.ghkdqhrbals.model.event

/**
 * Saga Step Domain Model
 */
data class SagaStep(
    val name: String,
    val index: Int,
    val commandTopic: String,
    val hasCompensation: Boolean = true,
    val compensationTopic: String? = null,
    val timeoutSeconds: Long = 30
) {
    fun canCompensate(): Boolean = hasCompensation && compensationTopic != null
}