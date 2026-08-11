package advice

import "github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/config"

func calculateCostJpy(cfg config.OpenAIConfig, model string, promptTokens int, completionTokens int) float64 {
	pricing, ok := cfg.ModelPricing[model]
	if !ok {
		pricing = cfg.ModelPricing[cfg.DefaultModel]
	}

	inputCostUSD := float64(promptTokens) / 1_000_000 * pricing.InputUSDPer1MTokens
	outputCostUSD := float64(completionTokens) / 1_000_000 * pricing.OutputUSDPer1MTokens
	return (inputCostUSD + outputCostUSD) * cfg.USDToJPY
}
