package com.example.llmragplatform.service

import com.example.llmragplatform.exception.PromptInjectionDetectedException
import com.example.llmragplatform.generated.model.PromptInjectionEvaluationCaseRequest
import com.example.llmragplatform.generated.model.PromptInjectionEvaluationCaseResult
import com.example.llmragplatform.generated.model.PromptInjectionEvaluationRequest
import com.example.llmragplatform.generated.model.PromptInjectionEvaluationResponse
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service

@Service
class PromptInjectionEvaluationService(
    private val promptInjectionGuardService: PromptInjectionGuardService,
    private val objectMapper: ObjectMapper,
) {

    fun evaluateDefaultCases(): PromptInjectionEvaluationResponse {
        val resource = ClassPathResource(DEFAULT_PROMPT_INJECTION_EVALUATION_RESOURCE)
        require(resource.exists()) {
            "Default prompt injection evaluation file not found: $DEFAULT_PROMPT_INJECTION_EVALUATION_RESOURCE"
        }

        return evaluate(loadDefaultRequest())
    }

    fun evaluate(request: PromptInjectionEvaluationRequest): PromptInjectionEvaluationResponse {
        val caseResults = request.cases.map { evaluateCase(it) }
        val totalCases = caseResults.size
        val expectedBlockedCases = caseResults.count { it.expectedBlocked }
        val expectedAllowedCases = totalCases - expectedBlockedCases
        val correctlyBlockedCases = caseResults.count { it.expectedBlocked && it.blocked }
        val correctlyAllowedCases = caseResults.count { !it.expectedBlocked && !it.blocked }
        val matchedCases = caseResults.count { it.matched }

        return PromptInjectionEvaluationResponse()
            .totalCases(totalCases)
            .expectedBlockedCases(expectedBlockedCases)
            .expectedAllowedCases(expectedAllowedCases)
            .correctlyBlockedCases(correctlyBlockedCases)
            .correctlyAllowedCases(correctlyAllowedCases)
            .detectionRate(rate(correctlyBlockedCases, expectedBlockedCases))
            .falsePositiveRate(rate(expectedAllowedCases - correctlyAllowedCases, expectedAllowedCases))
            .accuracy(rate(matchedCases, totalCases))
            .caseResults(caseResults)
    }

    private fun evaluateCase(requestCase: PromptInjectionEvaluationCaseRequest): PromptInjectionEvaluationCaseResult {
        val detectionMessage = try {
            promptInjectionGuardService.validateUserInput(requestCase.input)
            null
        } catch (ex: PromptInjectionDetectedException) {
            ex.message
        }
        val blocked = detectionMessage != null
        val expectedBlocked = requestCase.expectedBlocked

        return PromptInjectionEvaluationCaseResult()
            .label(requestCase.label)
            .input(requestCase.input)
            .expectedBlocked(expectedBlocked)
            .blocked(blocked)
            .matched(blocked == expectedBlocked)
            .detectionMessage(detectionMessage)
            .expectedOutcome(if (expectedBlocked) OUTCOME_BLOCK else OUTCOME_ALLOW)
            .actualOutcome(if (blocked) OUTCOME_BLOCK else OUTCOME_ALLOW)
    }

    private fun loadDefaultRequest(): PromptInjectionEvaluationRequest {
        val resource = ClassPathResource(DEFAULT_PROMPT_INJECTION_EVALUATION_RESOURCE)
        require(resource.exists()) {
            "Default prompt injection evaluation file not found: $DEFAULT_PROMPT_INJECTION_EVALUATION_RESOURCE"
        }

        return resource.inputStream.use { inputStream ->
            objectMapper.readValue(inputStream, PromptInjectionEvaluationRequest::class.java)
        }
    }

    private fun rate(numerator: Int, denominator: Int): Double {
        return if (denominator == 0) {
            0.0
        } else {
            numerator.toDouble() / denominator.toDouble()
        }
    }

    companion object {
        private const val DEFAULT_PROMPT_INJECTION_EVALUATION_RESOURCE = "evaluation/prompt-injection-cases.json"
        private const val OUTCOME_BLOCK = "BLOCK"
        private const val OUTCOME_ALLOW = "ALLOW"
    }
}
