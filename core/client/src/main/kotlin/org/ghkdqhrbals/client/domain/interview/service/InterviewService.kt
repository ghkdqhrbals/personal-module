package org.ghkdqhrbals.client.domain.interview.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ghkdqhrbals.client.ai.LlmClient
import org.ghkdqhrbals.client.config.log.logger
import org.ghkdqhrbals.client.domain.interview.dto.*
import org.ghkdqhrbals.client.domain.interview.entity.InterviewMessage
import org.ghkdqhrbals.client.domain.interview.entity.InterviewSession
import org.ghkdqhrbals.client.domain.interview.repository.InterviewMessageRepository
import org.ghkdqhrbals.client.domain.interview.repository.InterviewSessionRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
@Transactional
class InterviewService(
    private val sessionRepository: InterviewSessionRepository,
    private val messageRepository: InterviewMessageRepository,
    private val llmClient: LlmClient,
    private val promptGenerator: InterviewerPromptGenerator
) {
    
    /**
     * Create a new interview session and generate opening question
     */
    suspend fun createSession(request: CreateInterviewRequest): InterviewSessionResponse {
        logger().info("[InterviewService] Creating new interview session")
        
        // Create session entity
        val session = InterviewSession(
            candidateName = request.candidateName,
            cvText = request.cvText,
            status = InterviewStatus.CREATED
        )
        
        val savedSession = sessionRepository.save(session)
        logger().info("[InterviewService] Session created with ID: ${savedSession.id}")
        
        // Generate opening question using LLM with context
        return try {
            val chatRequest = promptGenerator.generateOpeningQuestionRequest(savedSession)
            
            logger().info("[InterviewService] Requesting opening question from LLM")
            val chatResponse = llmClient.createChatCompletion(chatRequest)
            
            val openingQuestion = chatResponse.choices.firstOrNull()?.message?.content
                ?: throw IllegalStateException("No response from LLM")
            
            logger().info("[InterviewService] Opening question generated: ${openingQuestion.take(100)}...")
            
            // Save opening question to session
            savedSession.openingQuestion = openingQuestion
            savedSession.status = InterviewStatus.IN_PROGRESS
            sessionRepository.save(savedSession)
            
            // Store opening question as first assistant message
            val openingMessage = InterviewMessage(
                session = savedSession,
                role = "assistant",
                content = openingQuestion,
                turnNumber = 0
            )
            messageRepository.save(openingMessage)
            
            InterviewSessionResponse(
                id = savedSession.id!!,
                status = savedSession.status,
                openingQuestion = openingQuestion,
                createdAt = savedSession.createdAt
            )
        } catch (e: Exception) {
            logger().error("[InterviewService] Failed to generate opening question", e)
            savedSession.status = InterviewStatus.FAILED
            sessionRepository.save(savedSession)
            throw RuntimeException("Failed to start interview: ${e.message}", e)
        }
    }
    
    /**
     * Process user's answer and generate next question
     * 
     * KEY METHOD: Uses InterviewerPromptGenerator to include full conversation context
     */
    suspend fun sendTurn(sessionId: Long, request: SendTurnRequest): InterviewTurnResponse {
        logger().info("[InterviewService] Processing turn for session: $sessionId")
        
        // Load session
        val session = sessionRepository.findByIdOrNull(sessionId)
            ?: throw IllegalArgumentException("Session not found: $sessionId")
        
        if (session.status != InterviewStatus.IN_PROGRESS) {
            throw IllegalStateException("Session is not in progress: ${session.status}")
        }
        
        // Load conversation history - THIS IS CRITICAL FOR CONTEXT
        val conversationHistory = messageRepository.findBySessionIdOrderByTurnNumberAsc(sessionId)
        logger().info("[InterviewService] Loaded ${conversationHistory.size} previous messages for context")
        
        val nextTurnNumber = conversationHistory.maxOfOrNull { it.turnNumber }?.plus(1) ?: 1
        
        // Save user's message
        val userMessage = InterviewMessage(
            session = session,
            role = "user",
            content = request.userMessage,
            turnNumber = nextTurnNumber
        )
        messageRepository.save(userMessage)
        logger().info("[InterviewService] Saved user message, turn: $nextTurnNumber")
        
        // Generate next question using full context
        return try {
            val chatRequest = promptGenerator.generateNextQuestionRequest(
                session = session,
                conversationHistory = conversationHistory,
                newUserMessage = request.userMessage
            )
            
            logger().info("[InterviewService] Requesting next question from LLM with ${chatRequest.messages.size} messages in context")
            val chatResponse = llmClient.createChatCompletion(chatRequest)
            
            val aiResponse = chatResponse.choices.firstOrNull()?.message?.content
                ?: throw IllegalStateException("No response from LLM")
            
            logger().info("[InterviewService] AI response generated: ${aiResponse.take(100)}...")
            
            // Save AI's response
            val assistantMessage = InterviewMessage(
                session = session,
                role = "assistant",
                content = aiResponse,
                turnNumber = nextTurnNumber + 1
            )
            messageRepository.save(assistantMessage)
            
            InterviewTurnResponse(
                sessionId = sessionId,
                userMessage = request.userMessage,
                aiResponse = aiResponse,
                turnNumber = nextTurnNumber,
                timestamp = LocalDateTime.now()
            )
        } catch (e: Exception) {
            logger().error("[InterviewService] Failed to generate next question", e)
            throw RuntimeException("Failed to process turn: ${e.message}", e)
        }
    }
    
    /**
     * Get session details
     */
    fun getSession(sessionId: Long): InterviewSession {
        return sessionRepository.findByIdOrNull(sessionId)
            ?: throw IllegalArgumentException("Session not found: $sessionId")
    }
    
    /**
     * Get conversation history
     */
    fun getConversationHistory(sessionId: Long): List<InterviewMessage> {
        return messageRepository.findBySessionIdOrderByTurnNumberAsc(sessionId)
    }
}
