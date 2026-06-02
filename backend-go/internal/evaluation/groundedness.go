package evaluation

import (
	"context"
	_ "embed"
	"encoding/json"

	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/advice"
	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/api"
	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/config"
	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/openai"
	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/prompt"
)

//go:embed groundedness-cases.json
var defaultGroundednessCasesJSON []byte

// GroundednessCaseEvaluationService は groundedness judge と fallback 方針を集計する。
type GroundednessCaseEvaluationService struct {
	ragConfig                config.RAGConfig
	openAIClient             *openai.Client
	groundednessPromptLoader *prompt.TemplateLoader
	model                    string
}

// NewGroundednessCaseEvaluationService は groundedness 評価 service を生成する。
func NewGroundednessCaseEvaluationService(
	ragConfig config.RAGConfig,
	openAIClient *openai.Client,
	groundednessPromptLoader *prompt.TemplateLoader,
	model string,
) *GroundednessCaseEvaluationService {
	return &GroundednessCaseEvaluationService{
		ragConfig:                ragConfig,
		openAIClient:             openAIClient,
		groundednessPromptLoader: groundednessPromptLoader,
		model:                    model,
	}
}

// EvaluateDefaultCases は組み込みケースで groundedness 評価を実行する。
func (s *GroundednessCaseEvaluationService) EvaluateDefaultCases(
	ctx context.Context,
) (api.GroundednessCaseEvaluationResponse, error) {
	request := api.GroundednessCaseEvaluationRequest{}
	if err := json.Unmarshal(defaultGroundednessCasesJSON, &request); err != nil {
		return api.GroundednessCaseEvaluationResponse{}, err
	}
	return s.Evaluate(ctx, request), nil
}

// Evaluate は任意ケース群に対して groundedness 評価を実行する。
func (s *GroundednessCaseEvaluationService) Evaluate(
	ctx context.Context,
	request api.GroundednessCaseEvaluationRequest,
) api.GroundednessCaseEvaluationResponse {
	caseResults := make([]api.GroundednessCaseEvaluationCaseResult, 0, len(request.Cases))
	matchedCases := 0
	groundedCases := 0
	lowGroundednessCases := 0
	noEvidenceCases := 0
	parseFailedCases := 0
	judgeErrorCases := 0
	fallbackAppliedCases := 0
	scoreTotal := 0.0

	for _, requestCase := range request.Cases {
		caseResult := s.evaluateCase(ctx, requestCase)
		caseResults = append(caseResults, caseResult)
		scoreTotal += caseResult.GroundednessScore
		if caseResult.Matched {
			matchedCases++
		}
		switch caseResult.ActualStatus {
		case api.GroundednessCaseEvaluationCaseResultActualStatusGROUNDED:
			groundedCases++
		case api.GroundednessCaseEvaluationCaseResultActualStatusLOWGROUNDEDNESS:
			lowGroundednessCases++
		case api.GroundednessCaseEvaluationCaseResultActualStatusNOEVIDENCE:
			noEvidenceCases++
		case api.GroundednessCaseEvaluationCaseResultActualStatusPARSEFAILED:
			parseFailedCases++
		case api.GroundednessCaseEvaluationCaseResultActualStatusJUDGEERROR:
			judgeErrorCases++
		}
		if caseResult.FallbackApplied {
			fallbackAppliedCases++
		}
	}

	totalCases := len(caseResults)
	averageGroundednessScore := 0.0
	accuracy := 0.0
	if totalCases > 0 {
		averageGroundednessScore = scoreTotal / float64(totalCases)
		accuracy = float64(matchedCases) / float64(totalCases)
	}

	return api.GroundednessCaseEvaluationResponse{
		Accuracy:                 accuracy,
		AverageGroundednessScore: averageGroundednessScore,
		CaseResults:              caseResults,
		FallbackAppliedCases:     fallbackAppliedCases,
		GroundedCases:            groundedCases,
		JudgeErrorCases:          judgeErrorCases,
		LowGroundednessCases:     lowGroundednessCases,
		MatchedCases:             matchedCases,
		NoEvidenceCases:          noEvidenceCases,
		ParseFailedCases:         parseFailedCases,
		TotalCases:               totalCases,
	}
}

func (s *GroundednessCaseEvaluationService) evaluateCase(
	ctx context.Context,
	requestCase api.GroundednessCaseEvaluationCaseRequest,
) api.GroundednessCaseEvaluationCaseResult {
	evaluation, _ := advice.EvaluateGroundedness(
		ctx,
		s.ragConfig,
		s.openAIClient,
		s.groundednessPromptLoader,
		s.model,
		requestCase.Situation,
		requestCase.TargetGoal,
		requestCase.Advice,
		toRetrievedDocuments(requestCase.RetrievedDocuments),
	)

	expectedFallbackApplied := false
	if requestCase.ExpectedFallbackApplied != nil {
		expectedFallbackApplied = *requestCase.ExpectedFallbackApplied
	}
	actualStatus := api.GroundednessCaseEvaluationCaseResultActualStatus(evaluation.Status)
	expectedStatus := api.GroundednessCaseEvaluationCaseResultExpectedStatus(requestCase.ExpectedStatus)
	matched := string(actualStatus) == string(expectedStatus) &&
		evaluation.FallbackApplied == expectedFallbackApplied

	return api.GroundednessCaseEvaluationCaseResult{
		ActualStatus:            actualStatus,
		Advice:                  requestCase.Advice,
		ExpectedFallbackApplied: expectedFallbackApplied,
		ExpectedStatus:          expectedStatus,
		FallbackApplied:         evaluation.FallbackApplied,
		GroundednessScore:       evaluation.GroundednessScore,
		Label:                   requestCase.Label,
		Matched:                 matched,
		Reason:                  evaluation.Reason,
		Situation:               requestCase.Situation,
		TargetGoal:              requestCase.TargetGoal,
	}
}

func toRetrievedDocuments(evidenceItems []api.GroundednessCaseEvaluationEvidence) []api.RetrievedDocument {
	documents := make([]api.RetrievedDocument, 0, len(evidenceItems))
	for _, evidence := range evidenceItems {
		aceCategory := api.RetrievedDocumentAceCategory(evidence.AceCategory)
		documents = append(documents, api.RetrievedDocument{
			AceCategory: &aceCategory,
			Excerpt:     evidence.Excerpt,
			Title:       evidence.Title,
		})
	}
	return documents
}
