package org.ghkdqhrbals.client.ai

enum class LlmClientType(val description: String) {
    OPENAI("OpenAI LLM Client"),
    OLLAMA("OLLAMA LLM Client"),
    NOOP("No Operation LLM Client")
}