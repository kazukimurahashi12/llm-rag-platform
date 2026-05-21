package advice

import (
	"context"
	"fmt"
	"strings"

	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/api"
	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/config"
	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/openai"
	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/prompt"
)

// Service は Go 版 advice API の最小業務ロジックを担当する。
type Service struct {
	cfg          config.Config
	openAIClient *openai.Client
	promptLoader *prompt.TemplateLoader
}

// NewService は advice service を生成する。
func NewService(cfg config.Config, openAIClient *openai.Client, promptLoader *prompt.TemplateLoader) *Service {
	return &Service{
		cfg:          cfg,
		openAIClient: openAIClient,
		promptLoader: promptLoader,
	}
}

// GenerateAdvice は RAG なしの最小 advice を OpenAI から生成する。
func (s *Service) GenerateAdvice(ctx context.Context, request api.AdviceRequest) (*api.AdviceResponse, error) {
	tone := "empathetic"
	model := s.cfg.OpenAI.DefaultModel
	if request.Setting != nil && request.Setting.Tone != nil && strings.TrimSpace(*request.Setting.Tone) != "" {
		tone = strings.TrimSpace(*request.Setting.Tone)
	}
	if request.Setting != nil && request.Setting.Model != nil && strings.TrimSpace(*request.Setting.Model) != "" {
		model = strings.TrimSpace(*request.Setting.Model)
	}

	aceAnalysis := analyzeACE(request.MemberContext.Situation, request.MemberContext.TargetGoal)

	instructions := s.promptLoader.Render(map[string]string{
		"tone":             tone,
		"situation":        request.MemberContext.Situation,
		"goal":             request.MemberContext.TargetGoal,
		"aceCategory":      string(aceAnalysis.PrimaryCategory),
		"aceReason":        aceAnalysis.Reason,
		"knowledgeContext": "(Go 版では RAG 未移植のため未取得)",
	})
	userMessage := fmt.Sprintf("状況: %s\n達成したい目標: %s", request.MemberContext.Situation, request.MemberContext.TargetGoal)

	result, err := s.openAIClient.Chat(ctx, model, instructions, userMessage)
	if err != nil {
		return nil, err
	}

	return &api.AdviceResponse{
		Advice: result.Content,
		AceAnalysis: api.AceAnalysis{
			PrimaryCategory: aceAnalysis.PrimaryCategory,
			Reason:          aceAnalysis.Reason,
		},
		GroundednessEvaluation: api.GroundednessEvaluation{
			GroundednessScore: 0,
			Reason:            "Go 版では groundedness judge は未移植のため、暫定値です。",
			Status:            api.GroundednessEvaluationStatusNOEVIDENCE,
			FallbackApplied:   false,
		},
		Usage: api.UsageInfo{
			Model:            result.Model,
			PromptTokens:     result.PromptTokens,
			CompletionTokens: result.CompletionTokens,
			TotalTokens:      result.PromptTokens + result.CompletionTokens,
			EstimatedCostJpy: 0,
		},
		RetrievedDocuments: []api.RetrievedDocument{},
	}, nil
}
