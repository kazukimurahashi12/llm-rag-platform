package knowledge

import (
	"context"
	"database/sql"
	"errors"
	"time"

	"github.com/google/uuid"
	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/api"
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
}

// NewReindexJobService は job service を生成する。
func NewReindexJobService(managementService *ManagementService, db *sql.DB) *ReindexJobService {
	return &ReindexJobService{
		managementService: managementService,
		db:                db,
	}
}

// SubmitAllDocumentsJob は全文書対象の job を受け付ける。
func (s *ReindexJobService) SubmitAllDocumentsJob(ctx context.Context) (*api.KnowledgeReindexJobAcceptedResponse, error) {
	job, err := s.insertJob(ctx, nil)
	if err != nil {
		return nil, err
	}
	go s.executeJob(context.Background(), job.jobID, reindexJobScope{})
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

	job, err := s.insertJob(ctx, &documentID)
	if err != nil {
		return nil, err
	}
	go s.executeJob(context.Background(), job.jobID, reindexJobScope{documentID: &documentID})
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
	defer rows.Close()

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
	if job.documentID == nil {
		return s.SubmitAllDocumentsJob(ctx)
	}
	return s.SubmitSingleDocumentJob(ctx, *job.documentID)
}

func (s *ReindexJobService) insertJob(ctx context.Context, documentID *int64) (*reindexJobRecord, error) {
	job := &reindexJobRecord{
		jobID:      uuid.NewString(),
		status:     "QUEUED",
		acceptedAt: time.Now().UTC(),
		documentID: documentID,
	}
	_, err := s.db.ExecContext(ctx, `
		insert into knowledge_reindex_jobs (
			job_id, status, accepted_at, knowledge_document_id
		) values ($1, $2, $3, $4)
	`, job.jobID, job.status, job.acceptedAt, job.documentID)
	if err != nil {
		return nil, err
	}
	return job, nil
}

func (s *ReindexJobService) executeJob(ctx context.Context, jobID string, scope reindexJobScope) {
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
		return
	}
	_ = s.markCompleted(ctx, jobID, result)
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
