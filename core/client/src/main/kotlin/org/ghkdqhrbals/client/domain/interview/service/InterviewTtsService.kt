package org.ghkdqhrbals.client.domain.interview.service

import org.ghkdqhrbals.client.config.log.logger
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

/**
 * Text-to-Speech service using OpenAI TTS API
 */
@Service
@ConditionalOnProperty(prefix = "interview.tts", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class InterviewTtsService(
    @Value("\${openai.api.key}") private val apiKey: String,
    @Value("\${interview.tts.model:tts-1}") private val ttsModel: String,
    @Value("\${interview.tts.voice:alloy}") private val voice: String,
    private val restClient: RestClient
) {
    
    /**
     * Convert text to speech using OpenAI TTS
     */
    fun synthesizeSpeech(text: String): ByteArray {
        logger().info("[InterviewTtsService] Synthesizing speech - model: $ttsModel, voice: $voice, textLen: ${text.length}")
        
        try {
            val requestBody = mapOf(
                "model" to ttsModel,
                "input" to text.take(4096), // OpenAI TTS limit
                "voice" to voice
            )
            
            logger().debug("[InterviewTtsService] Request body: $requestBody")
            
            val audioBytes = restClient.post()
                .uri("https://api.openai.com/v1/audio/speech")
                .header("Authorization", "Bearer $apiKey")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body<ByteArray>()
                ?: throw IllegalStateException("No audio data received from OpenAI TTS")
            
            logger().info("[InterviewTtsService] Speech synthesized successfully, size: ${audioBytes.size} bytes")
            return audioBytes
            
        } catch (e: Exception) {
            logger().error("[InterviewTtsService] TTS request failed", e)
            throw RuntimeException("Failed to synthesize speech: ${e.message}", e)
        }
    }
}
