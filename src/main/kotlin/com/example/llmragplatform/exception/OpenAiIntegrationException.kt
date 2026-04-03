package com.example.llmragplatform.exception

class OpenAiIntegrationException(
    message: String,
    val details: List<String> = emptyList(),
) : RuntimeException(message)
