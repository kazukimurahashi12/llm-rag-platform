package com.example.llmragplatform.service

import com.example.llmragplatform.config.OpenAiProperties
import com.example.llmragplatform.domain.LlmClient
import com.example.llmragplatform.domain.LlmResponse
import com.example.llmragplatform.generated.model.AdviceRequest
import com.example.llmragplatform.generated.model.AdviceSetting
import com.example.llmragplatform.generated.model.MemberContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class AdviceServiceImplTest {

    @Test
    fun `generateAdvice builds prompt calls llm and maps usage`() {
        val llmClient = RecordingLlmClient()
        val promptManager = PromptManager()
        val costCalculator = CostCalculator(testOpenAiProperties())
        val auditLogAsyncService = mock<AuditLogAsyncService>()
        val service = AdviceServiceImpl(
            llmClient = llmClient,
            promptManager = promptManager,
            costCalculator = costCalculator,
            auditLogAsyncService = auditLogAsyncService,
            defaultModel = "gpt-4o-mini"
        )

        val request = AdviceRequest(
            MemberContext("週報提出が遅れている", "重要性を理解してほしい")
        ).setting(
            AdviceSetting().tone("empathetic").model("gpt-4o-mini")
        )

        val response = service.generateAdvice(request)

        assertEquals("具体的なフィードバック案です。", response.advice)
        assertEquals("gpt-4o-mini", response.usage.model)
        assertEquals(120, response.usage.promptTokens)
        assertEquals(80, response.usage.completionTokens)
        assertEquals(200, response.usage.totalTokens)
        assertEquals(0.0099, response.usage.estimatedCostJpy, 0.0000001)
        assertEquals("gpt-4o-mini", llmClient.capturedModel)
        assertEquals(true, llmClient.capturedSystemPrompt.contains("週報提出が遅れている"))
        assertEquals(true, llmClient.capturedUserMessage.contains("重要性を理解してほしい"))

        val promptCaptor = argumentCaptor<String>()
        verify(auditLogAsyncService).save(
            model = org.mockito.kotlin.eq("gpt-4o-mini"),
            prompt = promptCaptor.capture(),
            response = org.mockito.kotlin.eq("具体的なフィードバック案です。"),
            promptTokens = org.mockito.kotlin.eq(120),
            completionTokens = org.mockito.kotlin.eq(80),
            totalTokens = org.mockito.kotlin.eq(200),
            costJpy = org.mockito.kotlin.eq(0.0099),
            latencyMs = org.mockito.kotlin.any()
        )
        assertEquals(true, promptCaptor.firstValue.contains("management support AI"))
    }

    private fun testOpenAiProperties(): OpenAiProperties {
        return OpenAiProperties(
            apiKey = "test-key",
            defaultModel = "gpt-4o-mini",
            pricing = OpenAiProperties.Pricing(
                usdToJpy = 150.0,
                models = mapOf(
                    "gpt-4o-mini" to OpenAiProperties.ModelRate(
                        inputUsdPer1mTokens = 0.15,
                        outputUsdPer1mTokens = 0.60
                    )
                )
            )
        )
    }

    private class RecordingLlmClient : LlmClient {
        var capturedModel: String? = null
        var capturedSystemPrompt: String = ""
        var capturedUserMessage: String = ""

        override fun chat(model: String, systemPrompt: String, userMessage: String): LlmResponse {
            capturedModel = model
            capturedSystemPrompt = systemPrompt
            capturedUserMessage = userMessage
            return LlmResponse(
                content = "具体的なフィードバック案です。",
                model = model,
                promptTokens = 120,
                completionTokens = 80
            )
        }
    }
}
