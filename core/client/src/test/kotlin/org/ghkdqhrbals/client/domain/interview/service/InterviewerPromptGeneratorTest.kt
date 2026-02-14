package org.ghkdqhrbals.client.domain.interview.service

import org.ghkdqhrbals.client.domain.interview.dto.InterviewStatus
import org.ghkdqhrbals.client.domain.interview.entity.InterviewMessage
import org.ghkdqhrbals.client.domain.interview.entity.InterviewSession
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Unit tests for InterviewerPromptGenerator
 * 
 * Verifies that conversation context is properly built for LLM requests
 */
class InterviewerPromptGeneratorTest {

    private val promptGenerator = InterviewerPromptGenerator(
        systemPromptTemplate = "You are a professional interviewer.",
        defaultModel = "gpt-4o-mini",
        temperature = 0.7
    )

    @Test
    fun `generateOpeningQuestionRequest includes system prompt and CV context`() {
        // Given
        val session = InterviewSession(
            id = 1L,
            candidateName = "John Doe",
            cvText = "Experienced software engineer with 5 years in Kotlin and Spring Boot.",
            status = InterviewStatus.CREATED
        )

        // When
        val request = promptGenerator.generateOpeningQuestionRequest(session)

        // Then
        assertEquals("gpt-4o-mini", request.model)
        assertEquals(0.7, request.temperature)
        
        // Should have system message and user message
        assertEquals(2, request.messages.size)
        
        val systemMessage = request.messages[0]
        assertEquals("system", systemMessage.role)
        assertTrue(systemMessage.content.contains("professional interviewer"))
        assertTrue(systemMessage.content.contains("John Doe"))
        assertTrue(systemMessage.content.contains("Experienced software engineer"))
        
        val userMessage = request.messages[1]
        assertEquals("user", userMessage.role)
        assertTrue(userMessage.content.contains("opening question"))
    }

    @Test
    fun `generateNextQuestionRequest includes full conversation history`() {
        // Given
        val session = InterviewSession(
            id = 1L,
            candidateName = "Jane Smith",
            cvText = "Frontend developer",
            status = InterviewStatus.IN_PROGRESS
        )

        val conversationHistory = listOf(
            InterviewMessage(
                id = 1L,
                session = session,
                role = "assistant",
                content = "Tell me about your React experience.",
                turnNumber = 0
            ),
            InterviewMessage(
                id = 2L,
                session = session,
                role = "user",
                content = "I have 3 years of React experience.",
                turnNumber = 1
            ),
            InterviewMessage(
                id = 3L,
                session = session,
                role = "assistant",
                content = "What was your most challenging React project?",
                turnNumber = 2
            )
        )

        val newUserMessage = "I built a complex dashboard with real-time updates."

        // When
        val request = promptGenerator.generateNextQuestionRequest(
            session = session,
            conversationHistory = conversationHistory,
            newUserMessage = newUserMessage
        )

        // Then
        assertEquals("gpt-4o-mini", request.model)
        
        // Should have: 1 system + 3 history + 1 new = 5 messages
        assertEquals(5, request.messages.size)
        
        // Verify message order
        assertEquals("system", request.messages[0].role)
        assertTrue(request.messages[0].content.contains("professional interviewer"))
        
        assertEquals("assistant", request.messages[1].role)
        assertEquals("Tell me about your React experience.", request.messages[1].content)
        
        assertEquals("user", request.messages[2].role)
        assertEquals("I have 3 years of React experience.", request.messages[2].content)
        
        assertEquals("assistant", request.messages[3].role)
        assertEquals("What was your most challenging React project?", request.messages[3].content)
        
        assertEquals("user", request.messages[4].role)
        assertEquals(newUserMessage, request.messages[4].content)
    }

    @Test
    fun `generateNextQuestionRequest with empty history includes system and new message`() {
        // Given
        val session = InterviewSession(
            id = 1L,
            status = InterviewStatus.IN_PROGRESS
        )

        val conversationHistory = emptyList<InterviewMessage>()
        val newUserMessage = "I am ready to start."

        // When
        val request = promptGenerator.generateNextQuestionRequest(
            session = session,
            conversationHistory = conversationHistory,
            newUserMessage = newUserMessage
        )

        // Then
        // Should have: 1 system + 1 new = 2 messages
        assertEquals(2, request.messages.size)
        
        assertEquals("system", request.messages[0].role)
        assertEquals("user", request.messages[1].role)
        assertEquals(newUserMessage, request.messages[1].content)
    }

    @Test
    fun `system prompt includes context maintenance instruction`() {
        // Given
        val session = InterviewSession(id = 1L, status = InterviewStatus.CREATED)

        // When
        val request = promptGenerator.generateOpeningQuestionRequest(session)

        // Then
        val systemMessage = request.messages.first { it.role == "system" }
        assertTrue(systemMessage.content.contains("context"))
    }
}
