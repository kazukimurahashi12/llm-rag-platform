package dashboard

import (
	"context"
	"database/sql"

	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/api"
	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/audit"
	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/knowledge"
	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/openai"
)

// Service は dashboard summary を集計する。
type Service struct {
	db               *sql.DB
	auditRepository  *audit.Repository
	reindexService   *knowledge.ReindexJobService
	retrievalService *knowledge.RetrievalService
	openAIClient     *openai.Client
}

// NewService は dashboard service を生成する。
func NewService(db *sql.DB, auditRepository *audit.Repository, reindexService *knowledge.ReindexJobService, retrievalService *knowledge.RetrievalService, openAIClient *openai.Client) *Service {
	return &Service{
		db:               db,
		auditRepository:  auditRepository,
		reindexService:   reindexService,
		retrievalService: retrievalService,
		openAIClient:     openAIClient,
	}
}

// GetSummary は dashboard summary を返す。
func (s *Service) GetSummary(ctx context.Context) (*api.DashboardSummaryResponse, error) {
	knowledgeStats := api.DashboardSummaryResponse{}
	if err := s.db.QueryRowContext(ctx, `
		select
			count(*),
			count(*) filter (where access_scope = 'SHARED' and not exists (
				select 1 from knowledge_document_allowed_usernames kdau where kdau.knowledge_document_id = knowledge_documents.id
			)),
			count(*) filter (where not (access_scope = 'SHARED' and not exists (
				select 1 from knowledge_document_allowed_usernames kdau where kdau.knowledge_document_id = knowledge_documents.id
			))),
			count(*) filter (where ace_category = 'ABILITY'),
			count(*) filter (where ace_category = 'CULTURE'),
			count(*) filter (where ace_category = 'EXPECTATION')
		from knowledge_documents
	`).Scan(
		&knowledgeStats.TotalKnowledgeDocuments,
		&knowledgeStats.SharedKnowledgeDocuments,
		&knowledgeStats.RestrictedKnowledgeDocuments,
		&knowledgeStats.AbilityKnowledgeDocuments,
		&knowledgeStats.CultureKnowledgeDocuments,
		&knowledgeStats.ExpectationKnowledgeDocuments,
	); err != nil {
		return nil, err
	}
	if err := s.db.QueryRowContext(ctx, `select count(*) from knowledge_document_chunks`).Scan(&knowledgeStats.TotalKnowledgeChunks); err != nil {
		return nil, err
	}

	reindexJobs := s.reindexService.ListJobs(1000, 0)
	reindexStats := api.DashboardSummaryResponse{
		TotalReindexJobs: int64(len(reindexJobs.Items)),
	}
	for _, item := range reindexJobs.Items {
		switch item.Status {
		case "QUEUED":
			reindexStats.QueuedReindexJobs++
		case "RUNNING":
			reindexStats.RunningReindexJobs++
		case "COMPLETED":
			reindexStats.CompletedReindexJobs++
		case "FAILED":
			reindexStats.FailedReindexJobs++
		}
	}
	finishedJobs := reindexStats.CompletedReindexJobs + reindexStats.FailedReindexJobs
	if finishedJobs > 0 {
		reindexStats.ReindexSuccessRate = float64(reindexStats.CompletedReindexJobs) / float64(finishedJobs)
	}

	summary, err := s.auditRepository.BuildDashboardSummary(ctx, reindexStats, knowledgeStats)
	if err != nil {
		return nil, err
	}
	if s.openAIClient != nil {
		metrics := s.openAIClient.MetricsSnapshot()
		summary.OpenAiRetryAttempts = metrics.RetryAttempts
		summary.OpenAiTimeouts = metrics.Timeouts
		summary.OpenAiFailFastCount = metrics.FailFastCount
		summary.OpenAiCircuitOpenTransitions = metrics.CircuitOpenTransitions
		summary.OpenAiCircuitState = metrics.CircuitState
	}
	if s.retrievalService != nil {
		metrics := s.retrievalService.MetricsSnapshot()
		summary.VectorAcceptedRetrievals = metrics.VectorAcceptedRetrievals
		summary.VectorThresholdFallbacks = metrics.VectorThresholdFallbacks
		summary.VectorThresholdFilteredChunks = metrics.VectorThresholdFilteredChunks
	}
	return summary, nil
}
