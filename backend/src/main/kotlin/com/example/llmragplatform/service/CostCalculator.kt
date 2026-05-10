package com.example.llmragplatform.service

import com.example.llmragplatform.config.OpenAiProperties
import org.springframework.stereotype.Component

@Component
/**
 * token 数とモデル単価から円換算コストを計算するサービス。
 */
class CostCalculator(
    private val openAiProperties: OpenAiProperties,
) {

    /**
     * モデル単価設定を使って prompt/completion の概算コストを計算する。
     *
     * @param model コスト計算対象のモデル名。
     * @param promptTokens 入力 token 数。
     * @param completionTokens 出力 token 数。
     * @return 円換算した概算コスト。
     */
    fun calculateCostJpy(model: String, promptTokens: Int, completionTokens: Int): Double {
        val modelRate = openAiProperties.pricing.models[model]
            ?: openAiProperties.pricing.models[openAiProperties.defaultModel]
            ?: error("No pricing configured for model: $model")

        val inputCostUsd = promptTokens.toDouble() / 1_000_000 * modelRate.inputUsdPer1mTokens
        val outputCostUsd = completionTokens.toDouble() / 1_000_000 * modelRate.outputUsdPer1mTokens
        return (inputCostUsd + outputCostUsd) * openAiProperties.pricing.usdToJpy
    }
}
