package org.ghkdqhrbals.client.domain.interview.service

import org.ghkdqhrbals.client.ai.ChatRequest
import org.ghkdqhrbals.client.ai.Message
import org.ghkdqhrbals.client.config.log.logger
import org.ghkdqhrbals.client.domain.interview.entity.InterviewMessage
import org.ghkdqhrbals.client.domain.interview.entity.InterviewSession
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * InterviewerPromptGenerator
 * 
 * This component is responsible for building LLM prompts with proper conversation context.
 * 
 * KEY FIX: Resolves "context not resolved" issue by:
 * 1. Injecting system prompt from configuration
 * 2. Loading full conversation history from database
 * 3. Building messages in correct order: [system, user1, assistant1, user2, assistant2, ...]
 * 4. Including CV context when available
 * 
 * This ensures that the LLM receives complete conversation context for coherent interview flow.
 */
@Component
class InterviewerPromptGenerator(
    @Value("\${interview.prompt.system:You are a professional interviewer. Conduct a thorough interview by asking relevant follow-up questions based on the candidate's responses. Be sharp and strict but professional.}")
    private val systemPromptTemplate: String,
    
    @Value("\${interview.prompt.model:gpt-4o-mini}")
    private val defaultModel: String,
    
    @Value("\${interview.prompt.temperature:0.7}")
    private val temperature: Double
) {
    
    /**
     * Generate opening question for a new interview session
     */
    fun generateOpeningQuestionRequest(session: InterviewSession): ChatRequest {
        val systemPrompt = buildSystemPrompt(session)
        
        val userPrompt = buildString {
            append("Generate an opening question to start the interview.")
            if (!session.cvText.isNullOrBlank()) {
                append("\n\nCandidate CV/Background:\n")
                append(session.cvText!!.take(2000)) // Limit CV text to avoid token limits
            }
            if (!session.candidateName.isNullOrBlank()) {
                append("\n\nCandidate Name: ${session.candidateName}")
            }
            append("\n\nProvide only the opening question, no additional explanation.")
        }
        
        val messages = listOf(
            Message(role = "system", content = systemPrompt),
            Message(role = "user", content = userPrompt)
        )
        
        logger().info("[InterviewerPromptGenerator] Opening question request - model: $defaultModel, systemPromptLen: ${systemPrompt.length}, userPromptLen: ${userPrompt.length}")
        
        return ChatRequest(
            model = defaultModel,
            messages = messages,
            temperature = temperature
        )
    }
    
    /**
     * Generate next question based on conversation history
     * 
     * KEY METHOD: This resolves the "context not resolved" issue by including
     * ALL previous messages in the conversation
     */
    fun generateNextQuestionRequest(
        session: InterviewSession,
        conversationHistory: List<InterviewMessage>,
        newUserMessage: String
    ): ChatRequest {
        val systemPrompt = buildSystemPrompt(session)
        
        // Build messages list with full context
        val messages = mutableListOf<Message>()
        
        // 1. Add system prompt
        messages.add(Message(role = "system", content = systemPrompt))
        
        // 2. Add all previous conversation turns in order
        for (message in conversationHistory) {
            messages.add(Message(role = message.role, content = message.content))
        }
        
        // 3. Add new user message
        messages.add(Message(role = "user", content = newUserMessage))
        
        logger().info("[InterviewerPromptGenerator] Next question request - model: $defaultModel, totalMessages: ${messages.size}, historyTurns: ${conversationHistory.size}, newMessageLen: ${newUserMessage.length}")
        logger().debug("[InterviewerPromptGenerator] Messages structure: ${messages.map { "${it.role}: ${it.content.take(50)}..." }}")
        
        // Verify system message exists
        if (messages.none { it.role == "system" }) {
            logger().warn("[InterviewerPromptGenerator] WARNING: System message missing from request!")
        }
        
        return ChatRequest(
            model = defaultModel,
            messages = messages,
            temperature = temperature
        )
    }
    
    /**
     * Build system prompt with CV context if available
     */
    private fun buildSystemPrompt(session: InterviewSession): String {
        val cvContext = if (!session.cvText.isNullOrBlank()) {
            "\n\nCandidate Background/CV:\n${session.cvText!!.take(1500)}"
        } else {
            ""
        }
        
        val candidateContext = if (!session.candidateName.isNullOrBlank()) {
            "\nCandidate Name: ${session.candidateName}"
        } else {
            ""
        }
        
        return buildString {
            append(systemPromptTemplate)
            append(candidateContext)
            append(cvContext)
            append("\n\nImportant: Maintain context throughout the conversation. Reference previous answers when asking follow-up questions.")
        }
    }
}
