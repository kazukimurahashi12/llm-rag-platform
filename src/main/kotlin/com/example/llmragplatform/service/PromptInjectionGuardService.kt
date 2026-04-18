package com.example.llmragplatform.service

import com.example.llmragplatform.exception.PromptInjectionDetectedException
import org.springframework.stereotype.Service
import java.util.Locale

@Service
class PromptInjectionGuardService {

    fun validateUserInput(vararg values: String) {
        val normalizedText = values.joinToString("\n").lowercase(Locale.getDefault())
        if (suspiciousPatterns.any { pattern -> pattern in normalizedText }) {
            throw PromptInjectionDetectedException("Prompt injection risk detected in user input")
        }
    }

    companion object {
        private val suspiciousPatterns = listOf(
            "ignore previous instructions",
            "ignore all previous instructions",
            "system prompt",
            "developer message",
            "reveal the prompt",
            "show me the hidden prompt",
            "disregard the above",
            "jailbreak",
            "override your instructions",
        )
    }
}
