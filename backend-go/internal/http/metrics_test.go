package http

import (
	"strings"
	"testing"

	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/knowledge"
	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/openai"
)

func TestFormatPrometheusMetrics(t *testing.T) {
	body := formatPrometheusMetrics(
		openai.MetricsSnapshot{
			RetryAttempts:          2,
			Timeouts:               1,
			FailFastCount:          3,
			CircuitOpenTransitions: 4,
			CircuitState:           "closed",
		},
		knowledge.RetrievalMetricsSnapshot{
			VectorAcceptedRetrievals:      5,
			VectorThresholdFallbacks:      6,
			VectorThresholdFilteredChunks: 7,
		},
		knowledge.ReindexJobMetricsSnapshot{
			AcceptedTotal:         8,
			RetriedTotal:          9,
			DeletedTotal:          10,
			CompletedTotal:        11,
			FailedTotal:           12,
			CleanupDeletedTotal:   13,
			ExecutionSecondsSum:   14,
			ExecutionSecondsCount: 15,
		},
	)

	for _, want := range []string{
		"openai_retry_attempts_total 2",
		"openai_circuit_state{state=\"closed\"} 1",
		"knowledge_retrieval_vector_accepted_total 5",
		"knowledge_retrieval_vector_threshold_filtered_chunks_total 7",
		"knowledge_reindex_jobs_accepted_total 8",
		"knowledge_reindex_jobs_execution_seconds_count 15",
	} {
		if !strings.Contains(body, want) {
			t.Fatalf("metrics body did not contain %q:\n%s", want, body)
		}
	}
}
