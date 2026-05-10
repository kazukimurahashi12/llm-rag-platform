package com.example.llmragplatform.exception

class ResourceNotFoundException(
    override val message: String,
) : RuntimeException(message)
