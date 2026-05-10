package com.example.llmragplatform.domain

data class LlmResponse(
    val content: String,
    val model: String,
    val promptTokens: Int,
    val completionTokens: Int,
)
