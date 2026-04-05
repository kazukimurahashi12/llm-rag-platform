package com.example.llmragplatform.service

import com.example.llmragplatform.config.OpenAiProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CostCalculatorTest {

    @Test
    fun `calculateCostJpy uses configured pricing`() {
        val calculator = CostCalculator(
            OpenAiProperties(
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
        )

        val cost = calculator.calculateCostJpy(
            model = "gpt-4o-mini",
            promptTokens = 1000,
            completionTokens = 500
        )

        assertEquals(0.0675, cost, 0.0000001)
    }
}
