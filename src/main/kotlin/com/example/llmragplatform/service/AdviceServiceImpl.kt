package com.example.llmragplatform.service

import com.example.llmragplatform.domain.LlmClient
import com.example.llmragplatform.generated.model.AdviceRequest
import com.example.llmragplatform.generated.model.AdviceResponse
import com.example.llmragplatform.generated.model.UsageInfo
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class AdviceServiceImpl(
    private val llmClient: LlmClient,
    private val promptManager: PromptManager,
    private val costCalculator: CostCalculator,
    private val auditLogAsyncService: AuditLogAsyncService,
    @Value("\${openai.default-model}") private val defaultModel: String,
) : AdviceService {

    override fun generateAdvice(request: AdviceRequest): AdviceResponse {
        val memberContext = request.memberContext
        val model = request.setting?.model?.takeIf { it.isNotBlank() } ?: defaultModel
        val tone = request.setting?.tone?.takeIf { it.isNotBlank() } ?: "empathetic"
        val startTime = System.currentTimeMillis()

        val systemPrompt = promptManager.buildPrompt(
            templateName = "management-coach-v1.0",
            variables = mapOf(
                "situation" to memberContext.situation,
                "goal" to memberContext.targetGoal,
                "tone" to tone
            )
        )

        val userMessage = """
            状況: ${memberContext.situation}
            達成したい目標: ${memberContext.targetGoal}
        """.trimIndent()

        val llmResponse = llmClient.chat(model, systemPrompt, userMessage)
        val latencyMs = System.currentTimeMillis() - startTime
        val totalTokens = llmResponse.promptTokens + llmResponse.completionTokens
        val costJpy = costCalculator.calculateCostJpy(
            model = model,
            promptTokens = llmResponse.promptTokens,
            completionTokens = llmResponse.completionTokens
        )

        auditLogAsyncService.save(
            model = model,
            prompt = "$systemPrompt\n---\n$userMessage",
            response = llmResponse.content,
            promptTokens = llmResponse.promptTokens,
            completionTokens = llmResponse.completionTokens,
            totalTokens = totalTokens,
            costJpy = costJpy,
            latencyMs = latencyMs
        )

        return AdviceResponse()
            .advice(llmResponse.content)
            .usage(
                UsageInfo()
                    .model(model)
                    .promptTokens(llmResponse.promptTokens)
                    .completionTokens(llmResponse.completionTokens)
                    .totalTokens(totalTokens)
                    .estimatedCostJpy(costJpy)
            )
    }
}
