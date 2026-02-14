package org.ghkdqhrbals.client.domain.interview.api

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.ghkdqhrbals.client.config.log.logger
import org.ghkdqhrbals.client.domain.interview.dto.*
import org.ghkdqhrbals.client.domain.interview.service.InterviewHealthService
import org.ghkdqhrbals.client.domain.interview.service.InterviewService
import org.ghkdqhrbals.client.domain.interview.service.InterviewTtsService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

/**
 * REST API controller for AI Interview functionality
 */
@RestController
@RequestMapping("/api/interviews")
@Tag(name = "AI Interview", description = "AI-powered interview endpoints")
class InterviewApiController(
    private val interviewService: InterviewService,
    private val ttsService: InterviewTtsService,
    private val healthService: InterviewHealthService
) {
    
    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    @Operation(summary = "Check OpenAI and Local LLM health status")
    suspend fun healthCheck(): ResponseEntity<HealthCheckResponse> {
        logger().info("[InterviewApiController] Health check requested")
        val health = healthService.checkHealth()
        return ResponseEntity.ok(health)
    }
    
    /**
     * Create a new interview session
     */
    @PostMapping
    @Operation(summary = "Create new interview session")
    suspend fun createSession(
        @RequestBody request: CreateInterviewRequest
    ): ResponseEntity<InterviewSessionResponse> {
        logger().info("[InterviewApiController] Creating interview session")
        val response = interviewService.createSession(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }
    
    /**
     * Send user's answer and get next question
     */
    @PostMapping("/{sessionId}/turns")
    @Operation(summary = "Send user message and get AI response")
    suspend fun sendTurn(
        @PathVariable sessionId: Long,
        @RequestBody request: SendTurnRequest
    ): ResponseEntity<InterviewTurnResponse> {
        logger().info("[InterviewApiController] Processing turn for session: $sessionId")
        val response = interviewService.sendTurn(sessionId, request)
        return ResponseEntity.ok(response)
    }
    
    /**
     * Get TTS audio for text
     */
    @PostMapping("/tts")
    @Operation(summary = "Convert text to speech")
    fun synthesizeSpeech(@RequestBody text: String): ResponseEntity<ByteArray> {
        logger().info("[InterviewApiController] TTS requested, textLen: ${text.length}")
        val audioBytes = ttsService.synthesizeSpeech(text)
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("audio/mpeg"))
            .body(audioBytes)
    }
    
    /**
     * Upload CV (PDF)
     */
    @PostMapping("/cv")
    @Operation(summary = "Upload CV PDF and extract text")
    fun uploadCv(@RequestParam("file") file: MultipartFile): ResponseEntity<CvUploadResponse> {
        logger().info("[InterviewApiController] CV upload requested: ${file.originalFilename}, size: ${file.size}")
        
        // TODO: Implement PDF text extraction
        // For now, return a placeholder response
        return ResponseEntity.ok(
            CvUploadResponse(
                success = true,
                message = "CV upload endpoint - PDF extraction not yet implemented",
                extractedText = "PDF extraction placeholder"
            )
        )
    }
}
