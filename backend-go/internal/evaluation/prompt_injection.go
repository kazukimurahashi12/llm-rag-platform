package evaluation

import (
	_ "embed"
	"encoding/json"

	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/api"
	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/guard"
)

const (
	promptInjectionOutcomeBlock = "BLOCK"
	promptInjectionOutcomeAllow = "ALLOW"
)

//go:embed prompt-injection-cases.json
var defaultPromptInjectionCasesJSON []byte

// PromptInjectionEvaluationService は prompt injection guard の精度を集計する。
type PromptInjectionEvaluationService struct {
	promptInjectionGuardService *guard.PromptInjectionGuardService
}

// NewPromptInjectionEvaluationService は評価 service を生成する。
func NewPromptInjectionEvaluationService(
	promptInjectionGuardService *guard.PromptInjectionGuardService,
) *PromptInjectionEvaluationService {
	return &PromptInjectionEvaluationService{
		promptInjectionGuardService: promptInjectionGuardService,
	}
}

// EvaluateDefaultCases は組み込みの標準ケースで評価を実行する。
func (s *PromptInjectionEvaluationService) EvaluateDefaultCases() (api.PromptInjectionEvaluationResponse, error) {
	request := api.PromptInjectionEvaluationRequest{}
	if err := json.Unmarshal(defaultPromptInjectionCasesJSON, &request); err != nil {
		return api.PromptInjectionEvaluationResponse{}, err
	}
	return s.Evaluate(request), nil
}

// Evaluate は指定ケース群に対して guard の精度を集計する。
func (s *PromptInjectionEvaluationService) Evaluate(
	request api.PromptInjectionEvaluationRequest,
) api.PromptInjectionEvaluationResponse {
	caseResults := make([]api.PromptInjectionEvaluationCaseResult, 0, len(request.Cases))
	expectedBlockedCases := 0
	correctlyBlockedCases := 0
	correctlyAllowedCases := 0
	matchedCases := 0

	for _, requestCase := range request.Cases {
		caseResult := s.evaluateCase(requestCase)
		caseResults = append(caseResults, caseResult)
		if requestCase.ExpectedBlocked {
			expectedBlockedCases++
			if caseResult.Blocked {
				correctlyBlockedCases++
			}
		} else if !caseResult.Blocked {
			correctlyAllowedCases++
		}
		if caseResult.Matched {
			matchedCases++
		}
	}

	totalCases := len(caseResults)
	expectedAllowedCases := totalCases - expectedBlockedCases

	return api.PromptInjectionEvaluationResponse{
		Accuracy:              rate(matchedCases, totalCases),
		CaseResults:           caseResults,
		CorrectlyAllowedCases: correctlyAllowedCases,
		CorrectlyBlockedCases: correctlyBlockedCases,
		DetectionRate:         rate(correctlyBlockedCases, expectedBlockedCases),
		ExpectedAllowedCases:  expectedAllowedCases,
		ExpectedBlockedCases:  expectedBlockedCases,
		FalsePositiveRate:     rate(expectedAllowedCases-correctlyAllowedCases, expectedAllowedCases),
		TotalCases:            totalCases,
	}
}

// evaluateCase は単一ケースに対して block / allow 判定を返す。
func (s *PromptInjectionEvaluationService) evaluateCase(
	requestCase api.PromptInjectionEvaluationCaseRequest,
) api.PromptInjectionEvaluationCaseResult {
	var detectionMessage *string
	if err := s.promptInjectionGuardService.ValidateUserInput(requestCase.Input); err != nil {
		message := err.Error()
		detectionMessage = &message
	}

	blocked := detectionMessage != nil
	expectedBlocked := requestCase.ExpectedBlocked
	expectedOutcome := promptInjectionOutcomeAllow
	if expectedBlocked {
		expectedOutcome = promptInjectionOutcomeBlock
	}
	actualOutcome := promptInjectionOutcomeAllow
	if blocked {
		actualOutcome = promptInjectionOutcomeBlock
	}

	return api.PromptInjectionEvaluationCaseResult{
		ActualOutcome:    stringPtr(actualOutcome),
		Blocked:          blocked,
		DetectionMessage: detectionMessage,
		ExpectedBlocked:  expectedBlocked,
		ExpectedOutcome:  stringPtr(expectedOutcome),
		Input:            requestCase.Input,
		Label:            requestCase.Label,
		Matched:          blocked == expectedBlocked,
	}
}

func rate(numerator int, denominator int) float64 {
	if denominator == 0 {
		return 0
	}
	return float64(numerator) / float64(denominator)
}

func stringPtr(value string) *string {
	return &value
}
