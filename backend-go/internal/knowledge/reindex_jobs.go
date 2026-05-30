package knowledge

import (
	"context"
	"errors"
	"sort"
	"sync"
	"time"

	"github.com/google/uuid"
	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/api"
)

// ErrKnowledgeReindexJobNotFound は対象 job 不在を表す。
var ErrKnowledgeReindexJobNotFound = errors.New("knowledge reindex job not found")

type reindexJobScope struct {
	documentID *int64
}

type reindexJob struct {
	jobID        string
	status       string
	acceptedAt   time.Time
	startedAt    *time.Time
	completedAt  *time.Time
	documentID   *int64
	result       *api.KnowledgeReindexResponse
	errorMessage *string
}

// ReindexJobService は再インデックス job をメモリで管理する。
type ReindexJobService struct {
	managementService *ManagementService
	mu                sync.RWMutex
	jobs              map[string]*reindexJob
}

// NewReindexJobService は job service を生成する。
func NewReindexJobService(managementService *ManagementService) *ReindexJobService {
	return &ReindexJobService{
		managementService: managementService,
		jobs:              make(map[string]*reindexJob),
	}
}

// SubmitAllDocumentsJob は全文書対象の job を受け付ける。
func (s *ReindexJobService) SubmitAllDocumentsJob(ctx context.Context) (*api.KnowledgeReindexJobAcceptedResponse, error) {
	job := s.createJob(nil)
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
	job := s.createJob(&documentID)
	go s.executeJob(context.Background(), job.jobID, reindexJobScope{documentID: &documentID})
	return toAcceptedResponse(job), nil
}

// GetJob は単票を返す。
func (s *ReindexJobService) GetJob(jobID string) (*api.KnowledgeReindexJobStatusResponse, error) {
	s.mu.RLock()
	job, ok := s.jobs[jobID]
	s.mu.RUnlock()
	if !ok {
		return nil, ErrKnowledgeReindexJobNotFound
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

	s.mu.RLock()
	items := make([]*reindexJob, 0, len(s.jobs))
	for _, job := range s.jobs {
		items = append(items, cloneJob(job))
	}
	s.mu.RUnlock()

	sort.SliceStable(items, func(i, j int) bool {
		return items[i].acceptedAt.After(items[j].acceptedAt)
	})

	totalCount := int64(len(items))
	start := safeOffset
	if start > len(items) {
		start = len(items)
	}
	end := start + safeLimit
	if end > len(items) {
		end = len(items)
	}

	responses := make([]api.KnowledgeReindexJobStatusResponse, 0, end-start)
	for _, job := range items[start:end] {
		responses = append(responses, toStatusResponse(job))
	}

	return &api.KnowledgeReindexJobListResponse{
		Items:      responses,
		Limit:      safeLimit,
		Offset:     safeOffset,
		TotalCount: totalCount,
	}
}

// DeleteJob は完了済み job を削除する。
func (s *ReindexJobService) DeleteJob(jobID string) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	job, ok := s.jobs[jobID]
	if !ok {
		return ErrKnowledgeReindexJobNotFound
	}
	if job.status == "QUEUED" || job.status == "RUNNING" {
		return errors.New("knowledge reindex job cannot be deleted while active")
	}
	delete(s.jobs, jobID)
	return nil
}

// RetryJob は FAILED job を再投入する。
func (s *ReindexJobService) RetryJob(ctx context.Context, jobID string) (*api.KnowledgeReindexJobAcceptedResponse, error) {
	s.mu.RLock()
	job, ok := s.jobs[jobID]
	s.mu.RUnlock()
	if !ok {
		return nil, ErrKnowledgeReindexJobNotFound
	}
	if job.status != "FAILED" {
		return nil, errors.New("only failed reindex jobs can be retried")
	}
	if job.documentID == nil {
		return s.SubmitAllDocumentsJob(ctx)
	}
	return s.SubmitSingleDocumentJob(ctx, *job.documentID)
}

func (s *ReindexJobService) createJob(documentID *int64) *reindexJob {
	job := &reindexJob{
		jobID:      uuid.NewString(),
		status:     "QUEUED",
		acceptedAt: time.Now().UTC(),
		documentID: documentID,
	}
	s.mu.Lock()
	s.jobs[job.jobID] = job
	s.mu.Unlock()
	return cloneJob(job)
}

func (s *ReindexJobService) executeJob(ctx context.Context, jobID string, scope reindexJobScope) {
	s.markRunning(jobID)

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
		s.markFailed(jobID, err.Error())
		return
	}
	s.markCompleted(jobID, result)
}

func (s *ReindexJobService) markRunning(jobID string) {
	s.mu.Lock()
	defer s.mu.Unlock()
	job, ok := s.jobs[jobID]
	if !ok {
		return
	}
	now := time.Now().UTC()
	job.status = "RUNNING"
	job.startedAt = &now
}

func (s *ReindexJobService) markCompleted(jobID string, result *api.KnowledgeReindexResponse) {
	s.mu.Lock()
	defer s.mu.Unlock()
	job, ok := s.jobs[jobID]
	if !ok {
		return
	}
	now := time.Now().UTC()
	job.status = "COMPLETED"
	job.completedAt = &now
	job.result = result
	job.errorMessage = nil
}

func (s *ReindexJobService) markFailed(jobID string, message string) {
	s.mu.Lock()
	defer s.mu.Unlock()
	job, ok := s.jobs[jobID]
	if !ok {
		return
	}
	now := time.Now().UTC()
	job.status = "FAILED"
	job.completedAt = &now
	job.errorMessage = &message
}

func toAcceptedResponse(job *reindexJob) *api.KnowledgeReindexJobAcceptedResponse {
	return &api.KnowledgeReindexJobAcceptedResponse{
		AcceptedAt: job.acceptedAt,
		JobId:      job.jobID,
		Status:     job.status,
	}
}

func toStatusResponse(job *reindexJob) api.KnowledgeReindexJobStatusResponse {
	return api.KnowledgeReindexJobStatusResponse{
		AcceptedAt:          job.acceptedAt,
		CompletedAt:         job.completedAt,
		ErrorMessage:        job.errorMessage,
		JobId:               job.jobID,
		KnowledgeDocumentId: job.documentID,
		Result:              job.result,
		StartedAt:           job.startedAt,
		Status:              job.status,
	}
}

func cloneJob(job *reindexJob) *reindexJob {
	if job == nil {
		return nil
	}
	cloned := *job
	if job.result != nil {
		resultCopy := *job.result
		cloned.result = &resultCopy
	}
	return &cloned
}
