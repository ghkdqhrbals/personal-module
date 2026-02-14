package org.ghkdqhrbals.client.domain.interview.dto

import java.time.LocalDateTime

/**
 * Interview session creation request
 */
data class CreateInterviewRequest(
    val cvText: String? = null,
    val candidateName: String? = null
)

/**
 * Interview session response
 */
data class InterviewSessionResponse(
    val id: Long,
    val status: InterviewStatus,
    val openingQuestion: String? = null,
    val createdAt: LocalDateTime
)

/**
 * User turn (message) in interview
 */
data class SendTurnRequest(
    val userMessage: String
)

/**
 * AI interviewer turn response
 */
data class InterviewTurnResponse(
    val sessionId: Long,
    val userMessage: String,
    val aiResponse: String,
    val turnNumber: Int,
    val timestamp: LocalDateTime
)

/**
 * Interview status
 */
enum class InterviewStatus {
    CREATED,
    IN_PROGRESS,
    COMPLETED,
    FAILED
}

/**
 * Health check response
 */
data class HealthCheckResponse(
    val status: String,
    val openai: ProviderHealth,
    val localLlm: ProviderHealth,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

data class ProviderHealth(
    val available: Boolean,
    val message: String?,
    val latencyMs: Long? = null
)

/**
 * CV upload response
 */
data class CvUploadResponse(
    val success: Boolean,
    val message: String,
    val extractedText: String? = null
)
