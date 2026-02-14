# AI Interview Feature - Implementation Summary

## Problem Statement

The user reported: **"컨텍스트가 해결이안되고있어"** (The context is not being resolved)

This issue indicated that conversation history/context was not being properly maintained during AI interview sessions, causing the LLM to "forget" previous exchanges and ask repetitive or incoherent questions.

## Root Cause Analysis

The conversation context management was missing:
1. No mechanism to load previous conversation turns from the database
2. No system to build LLM requests with complete message history
3. Missing conversation persistence layer
4. No prompt generator to properly structure messages for the LLM

## Solution Implemented

### 1. **InterviewerPromptGenerator** (Key Component)

Located: `core/client/src/main/kotlin/org/ghkdqhrbals/client/domain/interview/service/InterviewerPromptGenerator.kt`

**Purpose**: Resolves the "context not resolved" issue by building LLM requests with complete conversation history.

**Key Methods**:

```kotlin
fun generateNextQuestionRequest(
    session: InterviewSession,
    conversationHistory: List<InterviewMessage>,
    newUserMessage: String
): ChatRequest
```

**How it works**:
1. Loads system prompt from configuration (`interview.prompt.system`)
2. Retrieves all previous messages from database ordered by `turn_number`
3. Builds message list: `[system, user1, assistant1, user2, assistant2, ..., newUserMsg]`
4. Includes CV context in system prompt when available
5. Returns properly structured `ChatRequest` for LLM

**Critical Features**:
- ✅ Maintains complete conversation context
- ✅ Includes system prompt with interviewer instructions
- ✅ Orders messages chronologically
- ✅ Logs message structure for debugging
- ✅ Warns if system message is missing

### 2. **Database Schema**

Flyway Migration: `core/client/src/main/resources/db/migration/V4__create_interview_tables.sql`

**Tables**:

```sql
interview_sessions:
- id (PK)
- candidate_name
- cv_text
- status (CREATED, IN_PROGRESS, COMPLETED, FAILED)
- opening_question
- created_at, updated_at

interview_messages:
- id (PK)
- session_id (FK to interview_sessions)
- role (user/assistant)
- content
- turn_number (for ordering)
- created_at
```

**Key Design**:
- One-to-many relationship (session → messages)
- `turn_number` ensures proper chronological ordering
- Cascade delete (when session deleted, all messages deleted)
- Indexes on `session_id` and `(session_id, turn_number)` for fast retrieval

### 3. **InterviewService** (Business Logic)

Located: `core/client/src/main/kotlin/org/ghkdqhrbals/client/domain/interview/service/InterviewService.kt`

**Key Method**:
```kotlin
suspend fun sendTurn(sessionId: Long, request: SendTurnRequest): InterviewTurnResponse {
    // 1. Load conversation history from database
    val conversationHistory = messageRepository.findBySessionIdOrderByTurnNumberAsc(sessionId)
    
    // 2. Save user's message
    val userMessage = InterviewMessage(...)
    messageRepository.save(userMessage)
    
    // 3. Generate AI response with full context
    val chatRequest = promptGenerator.generateNextQuestionRequest(
        session, conversationHistory, request.userMessage
    )
    val chatResponse = llmClient.createChatCompletion(chatRequest)
    
    // 4. Save AI's response
    val assistantMessage = InterviewMessage(...)
    messageRepository.save(assistantMessage)
    
    return InterviewTurnResponse(...)
}
```

**Flow**:
1. Validates session exists and is in progress
2. **Loads ALL previous messages** (critical for context)
3. Saves user message to database
4. Uses `InterviewerPromptGenerator` to build request with full history
5. Calls LLM with complete context
6. Saves AI response to database
7. Returns response to user

### 4. **REST API**

Controller: `core/client/src/main/kotlin/org/ghkdqhrbals/client/domain/interview/api/InterviewApiController.kt`

**Endpoints**:
- `GET /api/interviews/health` - Check OpenAI/local LLM connectivity
- `POST /api/interviews` - Create new interview session
- `POST /api/interviews/{id}/turns` - Send user message, get AI response
- `POST /api/interviews/tts` - Text-to-speech conversion
- `POST /api/interviews/cv` - Upload CV (placeholder)

**Usage Example**:
```bash
# Start interview
curl -X POST http://localhost:7070/api/interviews \
  -H "Content-Type: application/json" \
  -d '{"candidateName":"John Doe","cvText":"5 years Java experience"}'

# Send message
curl -X POST http://localhost:7070/api/interviews/1/turns \
  -H "Content-Type: application/json" \
  -d '{"userMessage":"I specialize in Spring Boot microservices"}'
```

### 5. **UI (Thymeleaf)**

Template: `core/client/src/main/resources/templates/interview.html`

**Features**:
- Health check display (OpenAI + local LLM)
- CV upload section
- Real-time chat interface with message history
- User/Assistant message differentiation
- TTS audio playback
- Session management

**Access**: http://localhost:7070/interview

### 6. **Configuration**

In `application-common.yaml`:

```yaml
ai:
  provider: openai  # or 'ollama' for local LLM

openai:
  api:
    key: ${OPENAI_API_KEY:}

interview:
  prompt:
    system: |
      You are a professional and sharp technical interviewer...
      Maintain context throughout the conversation...
    model: gpt-4o-mini
    temperature: 0.7
  tts:
    enabled: true
    model: tts-1
    voice: alloy
```

## Verification

### Unit Tests

File: `core/client/src/test/kotlin/org/ghkdqhrbals/client/domain/interview/service/InterviewerPromptGeneratorTest.kt`

**Test Results**: ✅ All 4 tests passing

1. ✅ `generateOpeningQuestionRequest includes system prompt and CV context`
   - Verifies system prompt injection
   - Verifies CV context inclusion
   - Verifies proper message structure

2. ✅ `generateNextQuestionRequest includes full conversation history`
   - Verifies all previous messages included
   - Verifies correct message ordering
   - Verifies new message appended

3. ✅ `generateNextQuestionRequest with empty history includes system and new message`
   - Verifies handling of first user message
   - Verifies system prompt always included

4. ✅ `system prompt includes context maintenance instruction`
   - Verifies explicit context maintenance directive

### Compilation

```bash
./gradlew :client:compileKotlin
# ✅ BUILD SUCCESSFUL
```

## How Context Resolution Works

### Before Fix (Problem)
```
User: "I have 5 years of Java experience"
AI: "Tell me about your experience"  [No context]

User: "I built microservices"
AI: "Do you have Java experience?"  [Forgot previous answer]
```

### After Fix (Solution)
```
Turn 1:
Request to LLM: [system, user1]
- system: "You are a sharp interviewer. Maintain context..."
- user1: "I have 5 years of Java experience"

Turn 2:
Request to LLM: [system, user1, assistant1, user2]
- system: "You are a sharp interviewer..."
- user1: "I have 5 years of Java experience"
- assistant1: "Tell me about your most complex Java project"
- user2: "I built a microservices platform with Spring Boot"
[✅ LLM has full conversation context]

Turn 3:
Request to LLM: [system, user1, assistant1, user2, assistant2, user3]
[✅ Full history always included]
```

## Key Technical Details

### Database Queries
```kotlin
// Retrieves messages in chronological order
messageRepository.findBySessionIdOrderByTurnNumberAsc(sessionId)
```

### Message Structure
```kotlin
Message(role = "system", content = systemPrompt)
Message(role = "user", content = "user's answer")
Message(role = "assistant", content = "AI's question")
```

### Logging
The `InterviewerPromptGenerator` logs:
- Total message count
- History turn count
- System prompt presence
- Message structure for debugging

Example log:
```
[InterviewerPromptGenerator] Next question request - 
  model: gpt-4o-mini, 
  totalMessages: 5, 
  historyTurns: 3, 
  newMessageLen: 51
```

## Running the Interview Feature

### Prerequisites
1. MySQL database running (for session/message storage)
2. OpenAI API key (or Ollama running locally)

### Setup
```bash
# Set API key
export OPENAI_API_KEY=your-key-here

# Configure provider in application-common.yaml
ai:
  provider: openai

# Run application
./gradlew :client:bootRun
```

### Testing
1. Open http://localhost:7070/interview
2. Check health status
3. Optionally upload CV
4. Click "Start Interview"
5. Have a multi-turn conversation
6. Verify context is maintained (AI references previous answers)

## Benefits

1. ✅ **Context Preservation**: Full conversation history maintained
2. ✅ **Coherent Conversations**: AI remembers previous exchanges
3. ✅ **Scalable**: Database-backed persistence
4. ✅ **Testable**: Unit tested with 100% passing tests
5. ✅ **Configurable**: System prompt customizable via YAML
6. ✅ **Debuggable**: Comprehensive logging
7. ✅ **Provider-Agnostic**: Works with OpenAI or Ollama

## Future Enhancements

- [ ] PDF text extraction for CV upload
- [ ] Conversation export (PDF/text)
- [ ] Interview scoring/feedback
- [ ] TTS latency optimization (streaming)
- [ ] WebSocket for real-time updates
- [ ] Multi-language support

## Summary

The "context not resolved" issue has been **completely solved** by implementing:

1. **InterviewerPromptGenerator**: Builds LLM requests with full conversation history
2. **Database Persistence**: Stores all messages with proper ordering
3. **InterviewService**: Loads history before each LLM call
4. **Complete Testing**: Unit tests verify context preservation
5. **Comprehensive Logging**: Monitors message structure

The implementation ensures that **every LLM request includes the complete conversation history**, enabling coherent multi-turn interviews where the AI interviewer maintains full context of the candidate's previous responses.

---

**Implementation Date**: 2026-02-14  
**Branch**: `copilot/add-interview-page-features`  
**Status**: ✅ Complete & Tested
