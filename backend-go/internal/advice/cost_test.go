package advice

import (
	"math"
	"testing"

	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/config"
)

func TestCalculateCostJpy(t *testing.T) {
	cfg := config.OpenAIConfig{
		DefaultModel: "gpt-4o-mini",
		USDToJPY:     150.0,
		ModelPricing: map[string]config.OpenAIModelPricing{
			"gpt-4o": {
				InputUSDPer1MTokens:  2.50,
				OutputUSDPer1MTokens: 10.00,
			},
			"gpt-4o-mini": {
				InputUSDPer1MTokens:  0.15,
				OutputUSDPer1MTokens: 0.60,
			},
		},
	}

	got := calculateCostJpy(cfg, "gpt-4o-mini", 1000, 500)
	want := ((1000.0 / 1_000_000 * 0.15) + (500.0 / 1_000_000 * 0.60)) * 150.0
	if math.Abs(got-want) > 0.0000001 {
		t.Fatalf("calculateCostJpy() = %f, want %f", got, want)
	}
}

func TestCalculateCostJpyFallsBackToDefaultModelPricing(t *testing.T) {
	cfg := config.OpenAIConfig{
		DefaultModel: "gpt-4o-mini",
		USDToJPY:     150.0,
		ModelPricing: map[string]config.OpenAIModelPricing{
			"gpt-4o-mini": {
				InputUSDPer1MTokens:  0.15,
				OutputUSDPer1MTokens: 0.60,
			},
		},
	}

	got := calculateCostJpy(cfg, "unknown-model", 1000, 1000)
	want := ((1000.0 / 1_000_000 * 0.15) + (1000.0 / 1_000_000 * 0.60)) * 150.0
	if math.Abs(got-want) > 0.0000001 {
		t.Fatalf("calculateCostJpy() = %f, want %f", got, want)
	}
}
