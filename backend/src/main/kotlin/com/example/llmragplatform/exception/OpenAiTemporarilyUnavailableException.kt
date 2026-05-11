package com.example.llmragplatform.exception

class OpenAiTemporarilyUnavailableException(
    message: String,
    val details: List<String> = emptyList(),
) : RuntimeException(message)
