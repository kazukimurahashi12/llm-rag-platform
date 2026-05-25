package advice

import (
	"context"
	"encoding/json"
	"fmt"
	"strings"

	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/api"
	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/config"
	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/openai"
	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/prompt"
)

type groundednessJudgeResponse struct {
	Status string  `json:"status"`
	Score  float64 `json:"score"`
	Reason string  `json:"reason"`
}

func evaluateGroundedness(
	ctx context.Context,
	cfg config.RAGConfig,
	openAIClient *openai.Client,
	promptLoader *prompt.TemplateLoader,
	model string,
	situation string,
	targetGoal string,
	advice string,
	retrievedDocuments []api.RetrievedDocument,
) (api.GroundednessEvaluation, string) {
	if len(retrievedDocuments) == 0 {
		evaluation := api.GroundednessEvaluation{
			GroundednessScore: 0,
			Reason:            "根拠文書が取得できていないため、回答が根拠に沿っているかを確認できません。",
			Status:            api.GroundednessEvaluationStatusNOEVIDENCE,
			FallbackApplied:   cfg.GroundednessFallbackEnabled,
		}
		if cfg.GroundednessFallbackEnabled {
			return evaluation, fallbackAdviceText()
		}
		return evaluation, advice
	}

	instructions := promptLoader.Render(map[string]string{
		"situation":        situation,
		"goal":             targetGoal,
		"advice":           advice,
		"knowledgeContext": toGroundednessKnowledgeContext(retrievedDocuments),
	})

	result, err := openAIClient.Chat(ctx, model, instructions, "groundedness evaluation")
	if err != nil {
		return api.GroundednessEvaluation{
			GroundednessScore: 0,
			Reason:            fmt.Sprintf("groundedness judge の実行に失敗しました: %s", err.Error()),
			Status:            api.GroundednessEvaluationStatusJUDGEERROR,
			FallbackApplied:   false,
		}, advice
	}

	parsed, err := parseGroundednessJudgeResponse(result.Content)
	if err != nil {
		return api.GroundednessEvaluation{
			GroundednessScore: 0,
			Reason:            "groundedness judge の応答を解析できませんでした。",
			Status:            api.GroundednessEvaluationStatusPARSEFAILED,
			FallbackApplied:   false,
		}, advice
	}

	evaluation := api.GroundednessEvaluation{
		GroundednessScore: parsed.Score,
		Reason:            parsed.Reason,
		Status:            toGroundednessStatus(parsed.Status, parsed.Score, cfg.GroundednessThreshold),
		FallbackApplied:   false,
	}

	if cfg.GroundednessFallbackEnabled &&
		evaluation.Status == api.GroundednessEvaluationStatusLOWGROUNDEDNESS &&
		parsed.Score < cfg.GroundednessFallbackScoreThreshold {
		evaluation.FallbackApplied = true
		return evaluation, fallbackAdviceText()
	}

	return evaluation, advice
}

func parseGroundednessJudgeResponse(text string) (*groundednessJudgeResponse, error) {
	start := strings.Index(text, "{")
	end := strings.LastIndex(text, "}")
	if start < 0 || end <= start {
		return nil, fmt.Errorf("json body not found")
	}

	parsed := groundednessJudgeResponse{}
	if err := json.Unmarshal([]byte(text[start:end+1]), &parsed); err != nil {
		return nil, err
	}

	return &parsed, nil
}

func toGroundednessStatus(rawStatus string, score float64, threshold float64) api.GroundednessEvaluationStatus {
	normalizedStatus := strings.ToUpper(strings.TrimSpace(rawStatus))
	if normalizedStatus == string(api.GroundednessEvaluationStatusGROUNDED) && score >= threshold {
		return api.GroundednessEvaluationStatusGROUNDED
	}
	if normalizedStatus == string(api.GroundednessEvaluationStatusLOWGROUNDEDNESS) || score < threshold {
		return api.GroundednessEvaluationStatusLOWGROUNDEDNESS
	}
	return api.GroundednessEvaluationStatusLOWGROUNDEDNESS
}

func toGroundednessKnowledgeContext(documents []api.RetrievedDocument) string {
	lines := make([]string, 0, len(documents))
	for _, document := range documents {
		lines = append(lines, fmt.Sprintf("- %s: %s", document.Title, document.Excerpt))
	}
	return strings.Join(lines, "\n")
}

func fallbackAdviceText() string {
	return "取得できた根拠だけでは安全に助言を確定できませんでした。まずは状況の事実確認と背景整理を行い、必要に応じて社内の一次情報や管理者向けガイドを確認したうえで次の対応を決めてください。"
}
