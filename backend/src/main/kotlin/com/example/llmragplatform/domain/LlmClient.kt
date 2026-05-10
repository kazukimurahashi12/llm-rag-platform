package com.example.llmragplatform.domain

interface LlmClient {
    fun chat(model: String, systemPrompt: String, userMessage: String): LlmResponse
}
