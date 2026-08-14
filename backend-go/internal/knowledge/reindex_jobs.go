package knowledge

import (
	"context"
	"database/sql"
	"errors"
	"sync/atomic"
	"time"

	"github.com/google/uuid"
	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/api"
	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/config"
)

// ErrKnowledgeReindexJobNotFound は対象 job 不在を表す。
var ErrKnowledgeReindexJobNotFound = errors.New("knowledge reindex job not found")

type reindexJobScope struct {
	documentID *int64
}

type reindexJobRecord struct {
	jobID              string
	status             string
	acceptedAt         time.Time
	startedAt          *time.Time
	completedAt        *time.Time
	documentID         *int64
	documentsProcessed *int64
	chunksProcessed    *int64
	embeddingsUpdated  *int64
	vectorSearch       *bool
	errorMessage       *string
}

// ReindexJobService は再インデックス job を DB へ永続化して管理する。
type ReindexJobService struct {
	managementService *ManagementService
	db                *sql.DB
	cfg               config.ReindexJobConfig
	metrics           *ReindexJobMetrics
}

// NewReindexJobService は job service を生成する。
func NewReindexJobService(managementService *ManagementService, db *sql.DB, cfg config.ReindexJobConfig) *ReindexJobService {
	return &ReindexJobService{
		managementService: managementService,
		db:                db,
		cfg:               cfg,
		metrics:           &ReindexJobMetrics{},
	}
}

type ReindexJobMetricsSnapshot struct {
	AcceptedTotal         float64
	RetriedTotal          float64
	DeletedTotal          float64
	CompletedTotal        float64
	FailedTotal           float64
	CleanupDeletedTotal   float64
	ExecutionSecondsSum   float64
	ExecutionSecondsCount float64
}

// ReindexJobMetrics は Go 版 reindex job の Prometheus 用集計値を保持する。
type ReindexJobMetrics struct {
	acceptedTotal         atomic.Int64
	retriedTotal          atomic.Int64
	deletedTotal          atomic.Int64
	completedTotal        atomic.Int64
	failedTotal           atomic.Int64
	cleanupDeletedTotal   atomic.Int64
	executionMillisSum    atomic.Int64
	executionSecondsCount atomic.Int64
}

func (m *ReindexJobMetrics) Snapshot() ReindexJobMetricsSnapshot {
	return ReindexJobMetricsSnapshot{
		AcceptedTotal:         float64(m.acceptedTotal.Load()),
		RetriedTotal:          float64(m.retriedTotal.Load()),
		DeletedTotal:          float64(m.deletedTotal.Load()),
		CompletedTotal:        float64(m.completedTotal.Load()),
		FailedTotal:           float64(m.failedTotal.Load()),
		CleanupDeletedTotal:   float64(m.cleanupDeletedTotal.Load()),
		ExecutionSecondsSum:   float64(m.executionMillisSum.Load()) / 1000,
		ExecutionSecondsCount: float64(m.executionSecondsCount.Load()),
	}
}

// StartBackgroundMaintenance は起動時回復と定期 cleanup を開始する。
func (s *ReindexJobService) StartBackgroundMaintenance(ctx context.Context) {
	if s.cfg.RecoveryEnabled {
		_, _ = s.RecoverInterruptedJobs(ctx)
	}
	if !s.cfg.CleanupEnabled {
		return
	}

	interval := time.Duration(s.cfg.CleanupIntervalSeconds) * time.Second
	if interval <= 0 {
		interval = time.Hour
	}

	go func() {
		ticker := time.NewTicker(interval)
		defer ticker.Stop()
		for {
			select {
			case <-ctx.Done():
				return
			case <-ticker.C:
				_, _ = s.PurgeExpiredJobs(context.Background())
			}
		}
	}()
}

// SubmitAllDocumentsJob は全文書対象の job を受け付ける。
func (s *ReindexJobService) SubmitAllDocumentsJob(ctx context.Context) (*api.KnowledgeReindexJobAcceptedResponse, error) {
	job, created, err := s.insertJob(ctx, nil)
	if err != nil {
		return nil, err
	}
	if created {
		s.metrics.acceptedTotal.Add(1)
		go s.executeJob(context.Background(), job.jobID, reindexJobScope{})
	}
	return toAcceptedResponse(job), nil
}

// SubmitSingleDocumentJob は単一文書対象の job を受け付ける。
func (s *ReindexJobService) SubmitSingleDocumentJob(ctx context.Context, documentID int64) (*api.KnowledgeReindexJobAcceptedResponse, error) {
	document, err := s.managementService.repository.FindDocumentByID(ctx, documentID)
	if err != nil {
		return nil, err
	}
	if document == nil {
		return nil, ErrKnowledgeDocumentNotFound
	}

	job, created, err := s.insertJob(ctx, &documentID)
	if err != nil {
		return nil, err
	}
	if created {
		s.metrics.acceptedTotal.Add(1)
		go s.executeJob(context.Background(), job.jobID, reindexJobScope{documentID: &documentID})
	}
	return toAcceptedResponse(job), nil
}

// GetJob は単票を返す。
func (s *ReindexJobService) GetJob(jobID string) (*api.KnowledgeReindexJobStatusResponse, error) {
	job, err := s.findJob(context.Background(), jobID)
	if err != nil {
		return nil, err
	}
	response := toStatusResponse(job)
	return &response, nil
}

// ListJobs は job 一覧を返す。
func (s *ReindexJobService) ListJobs(limit int, offset int) *api.KnowledgeReindexJobListResponse {
	safeLimit := limit
	if safeLimit < 1 {
		safeLimit = 20
	}
	if safeLimit > 100 {
		safeLimit = 100
	}
	safeOffset := offset
	if safeOffset < 0 {
		safeOffset = 0
	}

	rows, err := s.db.Query(`
		select
			job_id, status, accepted_at, started_at, completed_at, knowledge_document_id,
			documents_processed, chunks_processed, embeddings_updated, vector_search_enabled, error_message
		from knowledge_reindex_jobs
		order by accepted_at desc
		limit $1 offset $2
	`, safeLimit, safeOffset)
	if err != nil {
		return &api.KnowledgeReindexJobListResponse{
			Items:      []api.KnowledgeReindexJobStatusResponse{},
			Limit:      safeLimit,
			Offset:     safeOffset,
			TotalCount: 0,
		}
	}
	defer func() {
		_ = rows.Close()
	}()

	items := make([]api.KnowledgeReindexJobStatusResponse, 0)
	for rows.Next() {
		record, scanErr := scanReindexJob(rows)
		if scanErr != nil {
			continue
		}
		items = append(items, toStatusResponse(record))
	}

	var totalCount int64
	if err := s.db.QueryRow(`select count(*) from knowledge_reindex_jobs`).Scan(&totalCount); err != nil {
		totalCount = int64(len(items))
	}

	return &api.KnowledgeReindexJobListResponse{
		Items:      items,
		Limit:      safeLimit,
		Offset:     safeOffset,
		TotalCount: totalCount,
	}
}

// DeleteJob は完了済み job を削除する。
func (s *ReindexJobService) DeleteJob(jobID string) error {
	job, err := s.findJob(context.Background(), jobID)
	if err != nil {
		return err
	}
	if job.status == "QUEUED" || job.status == "RUNNING" {
		return errors.New("knowledge reindex job cannot be deleted while active")
	}
	_, err = s.db.Exec(`delete from knowledge_reindex_jobs where job_id = $1`, jobID)
	if err == nil {
		s.metrics.deletedTotal.Add(1)
	}
	return err
}

// RetryJob は FAILED job を再投入する。
func (s *ReindexJobService) RetryJob(ctx context.Context, jobID string) (*api.KnowledgeReindexJobAcceptedResponse, error) {
	job, err := s.findJob(ctx, jobID)
	if err != nil {
		return nil, err
	}
	if job.status != "FAILED" {
		return nil, errors.New("only failed reindex jobs can be retried")
	}
	s.metrics.retriedTotal.Add(1)
	if job.documentID == nil {
		return s.SubmitAllDocumentsJob(ctx)
	}
	return s.SubmitSingleDocumentJob(ctx, *job.documentID)
}

// PurgeExpiredJobs は retention を過ぎた completed/failed job を削除する。
func (s *ReindexJobService) PurgeExpiredJobs(ctx context.Context) (int64, error) {
	retention := time.Duration(s.cfg.RetentionHours) * time.Hour
	if retention <= 0 {
		retention = 7 * 24 * time.Hour
	}
	cutoff := time.Now().UTC().Add(-retention)
	result, err := s.db.ExecContext(ctx, `
		delete from knowledge_reindex_jobs
		where status in ('COMPLETED', 'FAILED')
		  and completed_at is not null
		  and completed_at < $1
	`, cutoff)
	if err != nil {
		return 0, err
	}
	deleted, err := result.RowsAffected()
	if err != nil {
		return 0, nil
	}
	s.metrics.cleanupDeletedTotal.Add(deleted)
	return deleted, nil
}

// RecoverInterruptedJobs は再起動時に active job を FAILED へ回復する。
func (s *ReindexJobService) RecoverInterruptedJobs(ctx context.Context) (int64, error) {
	now := time.Now().UTC()
	result, err := s.db.ExecContext(ctx, `
		update knowledge_reindex_jobs
		set
			status = 'FAILED',
			completed_at = $1,
			error_message = $2
		where status in ('QUEUED', 'RUNNING')
		  and completed_at is null
	`, now, "reindex job interrupted by process restart")
	if err != nil {
		return 0, err
	}
	updated, err := result.RowsAffected()
	if err != nil {
		return 0, nil
	}
	return updated, nil
}

func (s *ReindexJobService) insertJob(ctx context.Context, documentID *int64) (*reindexJobRecord, bool, error) {
	active, err := s.findActiveJob(ctx, documentID)
	if err != nil {
		return nil, false, err
	}
	if active != nil {
		return active, false, nil
	}

	job := &reindexJobRecord{
		jobID:      uuid.NewString(),
		status:     "QUEUED",
		acceptedAt: time.Now().UTC(),
		documentID: documentID,
	}
	_, err = s.db.ExecContext(ctx, `
		insert into knowledge_reindex_jobs (
			job_id, status, accepted_at, knowledge_document_id
		) values ($1, $2, $3, $4)
	`, job.jobID, job.status, job.acceptedAt, job.documentID)
	if err != nil {
		return nil, false, err
	}
	return job, true, nil
}

func (s *ReindexJobService) executeJob(ctx context.Context, jobID string, scope reindexJobScope) {
	startedAt := time.Now()
	_ = s.markRunning(ctx, jobID)

	var (
		result *api.KnowledgeReindexResponse
		err    error
	)
	if scope.documentID == nil {
		result, err = s.managementService.ReindexAllDocuments(ctx)
	} else {
		result, err = s.managementService.ReindexDocument(ctx, *scope.documentID)
	}

	if err != nil {
		_ = s.markFailed(ctx, jobID, err.Error())
		s.metrics.failedTotal.Add(1)
		s.recordExecutionDuration(startedAt)
		return
	}
	_ = s.markCompleted(ctx, jobID, result)
	s.metrics.completedTotal.Add(1)
	s.recordExecutionDuration(startedAt)
}

func (s *ReindexJobService) recordExecutionDuration(startedAt time.Time) {
	s.metrics.executionMillisSum.Add(time.Since(startedAt).Milliseconds())
	s.metrics.executionSecondsCount.Add(1)
}

func (s *ReindexJobService) markRunning(ctx context.Context, jobID string) error {
	now := time.Now().UTC()
	_, err := s.db.ExecContext(ctx, `
		update knowledge_reindex_jobs
		set status = 'RUNNING', started_at = $2
		where job_id = $1
	`, jobID, now)
	return err
}

func (s *ReindexJobService) markCompleted(ctx context.Context, jobID string, result *api.KnowledgeReindexResponse) error {
	now := time.Now().UTC()
	_, err := s.db.ExecContext(ctx, `
		update knowledge_reindex_jobs
		set
			status = 'COMPLETED',
			completed_at = $2,
			documents_processed = $3,
			chunks_processed = $4,
			embeddings_updated = $5,
			vector_search_enabled = $6,
			error_message = null
		where job_id = $1
	`, jobID, now, result.DocumentsProcessed, result.ChunksProcessed, result.EmbeddingsUpdated, result.VectorSearchEnabled)
	return err
}

func (s *ReindexJobService) markFailed(ctx context.Context, jobID string, message string) error {
	now := time.Now().UTC()
	_, err := s.db.ExecContext(ctx, `
		update knowledge_reindex_jobs
		set
			status = 'FAILED',
			completed_at = $2,
			error_message = $3
		where job_id = $1
	`, jobID, now, message)
	return err
}

func (s *ReindexJobService) MetricsSnapshot() ReindexJobMetricsSnapshot {
	if s == nil || s.metrics == nil {
		return ReindexJobMetricsSnapshot{}
	}
	return s.metrics.Snapshot()
}

func (s *ReindexJobService) findJob(ctx context.Context, jobID string) (*reindexJobRecord, error) {
	row := s.db.QueryRowContext(ctx, `
		select
			job_id, status, accepted_at, started_at, completed_at, knowledge_document_id,
			documents_processed, chunks_processed, embeddings_updated, vector_search_enabled, error_message
		from knowledge_reindex_jobs
		where job_id = $1
	`, jobID)

	record, err := scanSingleReindexJob(row)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, ErrKnowledgeReindexJobNotFound
		}
		return nil, err
	}
	return record, nil
}

func (s *ReindexJobService) findActiveJob(ctx context.Context, documentID *int64) (*reindexJobRecord, error) {
	query := `
		select
			job_id, status, accepted_at, started_at, completed_at, knowledge_document_id,
			documents_processed, chunks_processed, embeddings_updated, vector_search_enabled, error_message
		from knowledge_reindex_jobs
		where status in ('QUEUED', 'RUNNING')
	`
	var row *sql.Row
	if documentID == nil {
		row = s.db.QueryRowContext(ctx, query+` and knowledge_document_id is null order by accepted_at desc limit 1`)
	} else {
		row = s.db.QueryRowContext(ctx, query+` and knowledge_document_id = $1 order by accepted_at desc limit 1`, *documentID)
	}

	record, err := scanSingleReindexJob(row)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, nil
		}
		return nil, err
	}
	return record, nil
}

func toAcceptedResponse(job *reindexJobRecord) *api.KnowledgeReindexJobAcceptedResponse {
	return &api.KnowledgeReindexJobAcceptedResponse{
		AcceptedAt: job.acceptedAt,
		JobId:      job.jobID,
		Status:     job.status,
	}
}

func toStatusResponse(job *reindexJobRecord) api.KnowledgeReindexJobStatusResponse {
	var result *api.KnowledgeReindexResponse
	if job.documentsProcessed != nil || job.chunksProcessed != nil || job.embeddingsUpdated != nil || job.vectorSearch != nil {
		result = &api.KnowledgeReindexResponse{
			DocumentsProcessed:  valueOrZero(job.documentsProcessed),
			ChunksProcessed:     valueOrZero(job.chunksProcessed),
			EmbeddingsUpdated:   valueOrZero(job.embeddingsUpdated),
			VectorSearchEnabled: valueOrFalse(job.vectorSearch),
		}
	}
	return api.KnowledgeReindexJobStatusResponse{
		AcceptedAt:          job.acceptedAt,
		CompletedAt:         job.completedAt,
		ErrorMessage:        job.errorMessage,
		JobId:               job.jobID,
		KnowledgeDocumentId: job.documentID,
		Result:              result,
		StartedAt:           job.startedAt,
		Status:              job.status,
	}
}

func scanReindexJob(rows *sql.Rows) (*reindexJobRecord, error) {
	record := &reindexJobRecord{}
	err := rows.Scan(
		&record.jobID,
		&record.status,
		&record.acceptedAt,
		&record.startedAt,
		&record.completedAt,
		&record.documentID,
		&record.documentsProcessed,
		&record.chunksProcessed,
		&record.embeddingsUpdated,
		&record.vectorSearch,
		&record.errorMessage,
	)
	if err != nil {
		return nil, err
	}
	return record, nil
}

func scanSingleReindexJob(row *sql.Row) (*reindexJobRecord, error) {
	record := &reindexJobRecord{}
	err := row.Scan(
		&record.jobID,
		&record.status,
		&record.acceptedAt,
		&record.startedAt,
		&record.completedAt,
		&record.documentID,
		&record.documentsProcessed,
		&record.chunksProcessed,
		&record.embeddingsUpdated,
		&record.vectorSearch,
		&record.errorMessage,
	)
	if err != nil {
		return nil, err
	}
	return record, nil
}

func valueOrZero(value *int64) int64 {
	if value == nil {
		return 0
	}
	return *value
}

func valueOrFalse(value *bool) bool {
	if value == nil {
		return false
	}
	return *value
}
