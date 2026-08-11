package evaluation

import (
	"context"
	"testing"

	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/config"
	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/knowledge"
)

func TestDefaultRetrievalSeedMatchesBundledCases(t *testing.T) {
	repository := &seedRetrievalRepository{
		chunks: buildSeedChunksForTest(),
	}
	retrievalService := knowledge.NewRetrievalService(
		repository,
		config.RAGConfig{
			TopK:                               3,
			VectorSearchEnabled:                false,
			RerankEnabled:                      true,
			RerankCandidateMultiplier:          3,
			GroundednessThreshold:              0.7,
			GroundednessFallbackEnabled:        true,
			GroundednessFallbackScoreThreshold: 0.3,
		},
		nil,
		knowledge.NewRetrievalMetrics(),
	)

	result, err := NewRetrievalEvaluationService(retrievalService).EvaluateDefaultCases(context.Background(), nil)
	if err != nil {
		t.Fatalf("EvaluateDefaultCases returned error: %v", err)
	}

	if result.TotalCases != 12 {
		t.Fatalf("TotalCases = %d, want 12", result.TotalCases)
	}
	if result.HitRate < 0.8 {
		t.Fatalf("HitRate = %f, want at least 0.8; case results: %+v", result.HitRate, result.CaseResults)
	}
	if result.MeanReciprocalRank < 0.6 {
		t.Fatalf("MeanReciprocalRank = %f, want at least 0.6", result.MeanReciprocalRank)
	}
}

type seedRetrievalRepository struct {
	chunks []knowledge.ChunkRecord
}

func (r *seedRetrievalRepository) FindAllChunks(context.Context) ([]knowledge.ChunkRecord, error) {
	return r.chunks, nil
}

func (r *seedRetrievalRepository) FindNearestChunks(context.Context, string, int) ([]knowledge.VectorMatch, error) {
	return []knowledge.VectorMatch{}, nil
}

func buildSeedChunksForTest() []knowledge.ChunkRecord {
	chunks := make([]knowledge.ChunkRecord, 0)
	var documentID int64 = 1
	var chunkID int64 = 1
	for _, document := range defaultRetrievalSeedDocuments {
		for index, content := range seedChunks(document.Content) {
			chunks = append(chunks, knowledge.ChunkRecord{
				ChunkID:     chunkID,
				DocumentID:  documentID,
				Title:       document.Title,
				AccessScope: "SHARED",
				AceCategory: document.AceCategory,
				ChunkIndex:  index,
				Content:     content,
			})
			chunkID++
		}
		documentID++
	}
	return chunks
}
