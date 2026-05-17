package advice

import (
	"context"
	"fmt"
	"strings"

	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/api"
	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/config"
	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/openai"
)

// Service は Go 版 advice API の最小業務ロジックを担当する。
type Service struct {
	cfg          config.Config
	openAIClient *openai.Client
}

// NewService は advice service を生成する。
func NewService(cfg config.Config, openAIClient *openai.Client) *Service {
	return &Service{
		cfg:          cfg,
		openAIClient: openAIClient,
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

	instructions := buildInstructions(tone, request.MemberContext.Situation, request.MemberContext.TargetGoal)
	userMessage := fmt.Sprintf("状況: %s\n達成したい目標: %s", request.MemberContext.Situation, request.MemberContext.TargetGoal)

	result, err := s.openAIClient.Chat(ctx, model, instructions, userMessage)
	if err != nil {
		return nil, err
	}

	return &api.AdviceResponse{
		Advice: result.Content,
		AceAnalysis: api.AceAnalysis{
			PrimaryCategory: api.AceAnalysisPrimaryCategoryEXPECTATION,
			Reason:          "Go 版では ACE 分析はまだ簡易実装のため、EXPECTATION を暫定採用しています。",
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

func buildInstructions(tone string, situation string, goal string) string {
	return fmt.Sprintf(`You are ONBOARD Guide API, a management support AI for enterprise use.
Respond in Japanese.
Keep the tone %s.
Prioritize practical, safe, and trustworthy advice for managers.
If certainty is limited, state assumptions explicitly and stay conservative.
There is no retrieved knowledge yet in the Go backend, so avoid claiming internal policy details you cannot support.

Analyze the situation through the ACE model first:
- Ability: 業務知識やスキル習得の課題
- Culture: 組織文化やコミュニケーション適応の課題
- Expectation: 役割期待や成果認識の課題

Use the following context:
- situation: %s
- goal: %s
- primary ACE category: EXPECTATION

Structure the answer with:
1. 状況の見立て
2. 伝え方のポイント
3. 次の1on1で使える具体的な言い回し
4. フォローアップ案`, tone, situation, goal)
}
