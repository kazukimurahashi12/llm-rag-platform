package http

import (
	"fmt"
	"net/http"
	"strings"

	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/knowledge"
	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/openai"
	"github.com/labstack/echo/v4"
)

// RegisterMetricsRoutes は Prometheus scrape 用の最小 metrics endpoint を登録する。
func RegisterMetricsRoutes(
	e *echo.Echo,
	openAIClient *openai.Client,
	retrievalService *knowledge.RetrievalService,
	reindexJobService *knowledge.ReindexJobService,
) {
	e.GET("/metrics", func(c echo.Context) error {
		openAISnapshot := openai.MetricsSnapshot{}
		if openAIClient != nil {
			openAISnapshot = openAIClient.MetricsSnapshot()
		}

		retrievalSnapshot := knowledge.RetrievalMetricsSnapshot{}
		if retrievalService != nil {
			retrievalSnapshot = retrievalService.MetricsSnapshot()
		}

		reindexSnapshot := knowledge.ReindexJobMetricsSnapshot{}
		if reindexJobService != nil {
			reindexSnapshot = reindexJobService.MetricsSnapshot()
		}

		return c.String(http.StatusOK, formatPrometheusMetrics(openAISnapshot, retrievalSnapshot, reindexSnapshot))
	})
}

func formatPrometheusMetrics(
	openAI openai.MetricsSnapshot,
	retrieval knowledge.RetrievalMetricsSnapshot,
	reindex knowledge.ReindexJobMetricsSnapshot,
) string {
	lines := []string{
		"# TYPE openai_retry_attempts_total counter",
		fmt.Sprintf("openai_retry_attempts_total %s", formatMetricValue(openAI.RetryAttempts)),
		"# TYPE openai_timeouts_total counter",
		fmt.Sprintf("openai_timeouts_total %s", formatMetricValue(openAI.Timeouts)),
		"# TYPE openai_fail_fast_total counter",
		fmt.Sprintf("openai_fail_fast_total %s", formatMetricValue(openAI.FailFastCount)),
		"# TYPE openai_circuit_open_transitions_total counter",
		fmt.Sprintf("openai_circuit_open_transitions_total %s", formatMetricValue(openAI.CircuitOpenTransitions)),
		"# TYPE openai_circuit_state gauge",
		fmt.Sprintf("openai_circuit_state{state=%q} 1", openAI.CircuitState),
		"# TYPE knowledge_retrieval_vector_accepted_total counter",
		fmt.Sprintf("knowledge_retrieval_vector_accepted_total %s", formatMetricValue(retrieval.VectorAcceptedRetrievals)),
		"# TYPE knowledge_retrieval_vector_threshold_fallback_total counter",
		fmt.Sprintf("knowledge_retrieval_vector_threshold_fallback_total %s", formatMetricValue(retrieval.VectorThresholdFallbacks)),
		"# TYPE knowledge_retrieval_vector_threshold_filtered_chunks_total counter",
		fmt.Sprintf("knowledge_retrieval_vector_threshold_filtered_chunks_total %s", formatMetricValue(retrieval.VectorThresholdFilteredChunks)),
		"# TYPE knowledge_reindex_jobs_accepted_total counter",
		fmt.Sprintf("knowledge_reindex_jobs_accepted_total %s", formatMetricValue(reindex.AcceptedTotal)),
		"# TYPE knowledge_reindex_jobs_retried_total counter",
		fmt.Sprintf("knowledge_reindex_jobs_retried_total %s", formatMetricValue(reindex.RetriedTotal)),
		"# TYPE knowledge_reindex_jobs_deleted_total counter",
		fmt.Sprintf("knowledge_reindex_jobs_deleted_total %s", formatMetricValue(reindex.DeletedTotal)),
		"# TYPE knowledge_reindex_jobs_completed_total counter",
		fmt.Sprintf("knowledge_reindex_jobs_completed_total %s", formatMetricValue(reindex.CompletedTotal)),
		"# TYPE knowledge_reindex_jobs_failed_total counter",
		fmt.Sprintf("knowledge_reindex_jobs_failed_total %s", formatMetricValue(reindex.FailedTotal)),
		"# TYPE knowledge_reindex_jobs_cleanup_deleted_total counter",
		fmt.Sprintf("knowledge_reindex_jobs_cleanup_deleted_total %s", formatMetricValue(reindex.CleanupDeletedTotal)),
		"# TYPE knowledge_reindex_jobs_execution_seconds summary",
		fmt.Sprintf("knowledge_reindex_jobs_execution_seconds_sum %s", formatMetricValue(reindex.ExecutionSecondsSum)),
		fmt.Sprintf("knowledge_reindex_jobs_execution_seconds_count %s", formatMetricValue(reindex.ExecutionSecondsCount)),
	}
	return strings.Join(lines, "\n") + "\n"
}

func formatMetricValue(value float64) string {
	return fmt.Sprintf("%.0f", value)
}
