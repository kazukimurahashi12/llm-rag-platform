package com.example.llmragplatform.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "openai")
data class OpenAiProperties(
    val apiKey: String,
    val defaultModel: String,
    val pricing: Pricing = Pricing(),
) {
    data class Pricing(
        val usdToJpy: Double = 150.0,
        val models: Map<String, ModelRate> = defaultRates(),
    )

    data class ModelRate(
        val inputUsdPer1mTokens: Double,
        val outputUsdPer1mTokens: Double,
    )
}

private fun defaultRates(): Map<String, OpenAiProperties.ModelRate> {
    return mapOf(
        "gpt-4o" to OpenAiProperties.ModelRate(
            inputUsdPer1mTokens = 2.50,
            outputUsdPer1mTokens = 10.00
        ),
        "gpt-4o-mini" to OpenAiProperties.ModelRate(
            inputUsdPer1mTokens = 0.15,
            outputUsdPer1mTokens = 0.60
        )
    )
}
