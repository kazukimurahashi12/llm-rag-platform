package evaluation

import (
	"context"
	_ "embed"
	"encoding/json"
	"strings"

	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/api"
	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/knowledge"
)

//go:embed retrieval-cases.json
var defaultRetrievalCasesJSON []byte

// RetrievalEvaluationService は retrieval 品質をケース単位で集計する。
type RetrievalEvaluationService struct {
	retrievalService *knowledge.RetrievalService
}

// NewRetrievalEvaluationService は評価 service を生成する。
func NewRetrievalEvaluationService(retrievalService *knowledge.RetrievalService) *RetrievalEvaluationService {
	return &RetrievalEvaluationService{retrievalService: retrievalService}
}

// EvaluateDefaultCases は組み込みの標準ケースで評価する。
func (s *RetrievalEvaluationService) EvaluateDefaultCases(
	ctx context.Context,
	topK *int,
) (api.RetrievalEvaluationResponse, error) {
	request := api.RetrievalEvaluationRequest{}
	if err := json.Unmarshal(defaultRetrievalCasesJSON, &request); err != nil {
		return api.RetrievalEvaluationResponse{}, err
	}
	if topK != nil {
		request.TopK = topK
	}
	return s.Evaluate(ctx, request, knowledge.RetrievalOptions{}), nil
}

// Evaluate は指定ケース群を現在の retrieval 設定で実行する。
func (s *RetrievalEvaluationService) Evaluate(
	ctx context.Context,
	request api.RetrievalEvaluationRequest,
	options knowledge.RetrievalOptions,
) api.RetrievalEvaluationResponse {
	requestedTopK := 0
	if request.TopK != nil {
		requestedTopK = *request.TopK
	}
	if options.TopK == 0 {
		options.TopK = requestedTopK
	}

	caseResults := make([]api.RetrievalEvaluationCaseResult, 0, len(request.Cases))
	matchedCases := 0
	totalRetrievedCount := 0
	totalReciprocalRank := 0.0
	totalRecallAtK := 0.0
	totalPrecisionAtK := 0.0

	for _, requestCase := range request.Cases {
		caseResult := s.evaluateCase(ctx, requestCase, options)
		caseResults = append(caseResults, caseResult)
		if caseResult.Matched {
			matchedCases++
		}
		totalRetrievedCount += caseResult.RetrievedCount
		totalReciprocalRank += caseResult.ReciprocalRank
		totalRecallAtK += caseResult.RecallAtK
		totalPrecisionAtK += caseResult.PrecisionAtK
	}

	totalCases := len(caseResults)
	return api.RetrievalEvaluationResponse{
		AveragePrecisionAtK:   averageFloat(totalPrecisionAtK, totalCases),
		AverageRecallAtK:      averageFloat(totalRecallAtK, totalCases),
		AverageRetrievedCount: averageFloat(float64(totalRetrievedCount), totalCases),
		CaseResults:           caseResults,
		HitRate:               averageFloat(float64(matchedCases), totalCases),
		MatchedCases:          matchedCases,
		MeanReciprocalRank:    averageFloat(totalReciprocalRank, totalCases),
		TopK:                  requestedTopK,
		TotalCases:            totalCases,
	}
}

// CompareDefaultCases は標準ケースに対して複数条件の比較を返す。
func (s *RetrievalEvaluationService) CompareDefaultCases(
	ctx context.Context,
	request api.RetrievalEvaluationComparisonRequest,
) (api.RetrievalEvaluationComparisonResponse, error) {
	baseRequest := api.RetrievalEvaluationRequest{}
	if err := json.Unmarshal(defaultRetrievalCasesJSON, &baseRequest); err != nil {
		return api.RetrievalEvaluationComparisonResponse{}, err
	}

	variantResults := make([]api.RetrievalEvaluationVariantResult, 0, len(request.Variants))
	for _, variant := range request.Variants {
		evaluationRequest := baseRequest
		if variant.TopK != nil {
			evaluationRequest.TopK = variant.TopK
		}
		evaluation := s.Evaluate(ctx, evaluationRequest, knowledge.RetrievalOptions{
			TopK:               intValue(variant.TopK),
			MinSimilarityScore: variant.MinSimilarityScore,
			RerankEnabled:      variant.RerankEnabled,
		})
		variantResults = append(variantResults, api.RetrievalEvaluationVariantResult{
			AveragePrecisionAtK:   evaluation.AveragePrecisionAtK,
			AverageRecallAtK:      evaluation.AverageRecallAtK,
			AverageRetrievedCount: evaluation.AverageRetrievedCount,
			HitRate:               evaluation.HitRate,
			Label:                 variant.Label,
			MatchedCases:          evaluation.MatchedCases,
			MeanReciprocalRank:    evaluation.MeanReciprocalRank,
			MinSimilarityScore:    variant.MinSimilarityScore,
			RerankEnabled:         variant.RerankEnabled,
			TopK:                  evaluation.TopK,
			TotalCases:            evaluation.TotalCases,
		})
	}
	return api.RetrievalEvaluationComparisonResponse{VariantResults: variantResults}, nil
}

func (s *RetrievalEvaluationService) evaluateCase(
	ctx context.Context,
	requestCase api.RetrievalEvaluationCaseRequest,
	options knowledge.RetrievalOptions,
) api.RetrievalEvaluationCaseResult {
	retrievedKnowledge, err := s.retrievalService.RetrieveWithOptions(
		ctx,
		requestCase.Query,
		"",
		true,
		"",
		options,
	)
	retrievedTitles := []string{}
	if err == nil && retrievedKnowledge != nil {
		for _, document := range retrievedKnowledge.Documents {
			retrievedTitles = append(retrievedTitles, document.Title)
		}
	}

	expectedLookup := map[string]string{}
	for _, title := range requestCase.ExpectedDocumentTitles {
		expectedLookup[normalize(title)] = title
	}

	matchedTitles := make([]string, 0)
	seenMatched := map[string]struct{}{}
	var firstRelevantRank *int
	for index, title := range retrievedTitles {
		if expectedTitle, ok := expectedLookup[normalize(title)]; ok {
			if firstRelevantRank == nil {
				rank := index + 1
				firstRelevantRank = &rank
			}
			if _, seen := seenMatched[expectedTitle]; !seen {
				seenMatched[expectedTitle] = struct{}{}
				matchedTitles = append(matchedTitles, expectedTitle)
			}
		}
	}

	reciprocalRank := 0.0
	if firstRelevantRank != nil {
		reciprocalRank = 1.0 / float64(*firstRelevantRank)
	}

	recallAtK := 0.0
	expectedDistinct := distinctNormalizedCount(requestCase.ExpectedDocumentTitles)
	if expectedDistinct > 0 {
		recallAtK = float64(len(matchedTitles)) / float64(expectedDistinct)
	}

	precisionAtK := 0.0
	if len(retrievedTitles) > 0 {
		precisionAtK = float64(len(matchedTitles)) / float64(len(retrievedTitles))
	}

	return api.RetrievalEvaluationCaseResult{
		ExpectedDocumentTitles:  requestCase.ExpectedDocumentTitles,
		FirstRelevantRank:       firstRelevantRank,
		Label:                   requestCase.Label,
		Matched:                 len(matchedTitles) > 0,
		MatchedDocumentTitles:   matchedTitles,
		PrecisionAtK:            precisionAtK,
		Query:                   requestCase.Query,
		RecallAtK:               recallAtK,
		ReciprocalRank:          reciprocalRank,
		RetrievedCount:          len(retrievedTitles),
		RetrievedDocumentTitles: retrievedTitles,
	}
}

func normalize(value string) string {
	return strings.ToLower(strings.TrimSpace(value))
}

func distinctNormalizedCount(values []string) int {
	seen := map[string]struct{}{}
	for _, value := range values {
		seen[normalize(value)] = struct{}{}
	}
	return len(seen)
}

func averageFloat(total float64, count int) float64 {
	if count == 0 {
		return 0
	}
	return total / float64(count)
}

func intValue(value *int) int {
	if value == nil {
		return 0
	}
	return *value
}
