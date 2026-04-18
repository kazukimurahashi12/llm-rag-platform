package com.example.llmragplatform.exception

class InvalidCredentialsException(
    message: String = "Invalid username or password",
) : RuntimeException(message)
