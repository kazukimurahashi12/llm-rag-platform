package com.example.llmragplatform.service

import com.example.llmragplatform.config.OpenAiProperties
import org.springframework.stereotype.Component

@Component
class CostCalculator(
    private val openAiProperties: OpenAiProperties,
) {

    fun calculateCostJpy(model: String, promptTokens: Int, completionTokens: Int): Double {
        val modelRate = openAiProperties.pricing.models[model]
            ?: openAiProperties.pricing.models[openAiProperties.defaultModel]
            ?: error("No pricing configured for model: $model")

        val inputCostUsd = promptTokens.toDouble() / 1_000_000 * modelRate.inputUsdPer1mTokens
        val outputCostUsd = completionTokens.toDouble() / 1_000_000 * modelRate.outputUsdPer1mTokens
        return (inputCostUsd + outputCostUsd) * openAiProperties.pricing.usdToJpy
    }
}
