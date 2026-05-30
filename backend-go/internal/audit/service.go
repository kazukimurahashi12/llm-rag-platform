package audit

import (
	"context"
	"errors"
	"time"

	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/api"
)

// ErrAuditLogNotFound は監査ログ未存在を表す。
var ErrAuditLogNotFound = errors.New("audit log not found")

// Service は audit log の保存・参照・集計を担当する。
type Service struct {
	repository *Repository
}

// NewService は audit service を生成する。
func NewService(repository *Repository) *Service {
	return &Service{repository: repository}
}

// Save は監査ログを保存する。
func (s *Service) Save(ctx context.Context, record LogRecord) error {
	return s.repository.Save(ctx, record)
}

// GetLogs は一覧 API 用レスポンスを返す。
func (s *Service) GetLogs(ctx context.Context, limit int, offset int, model string, from *time.Time, to *time.Time) (*api.AuditLogListResponse, error) {
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

	items, totalCount, err := s.repository.List(ctx, safeLimit, safeOffset, model, from, to)
	if err != nil {
		return nil, err
	}
	responseItems := make([]api.AuditLogSummaryItem, 0, len(items))
	for _, item := range items {
		responseItems = append(responseItems, toSummaryItem(item))
	}
	return &api.AuditLogListResponse{
		Items:      responseItems,
		Limit:      safeLimit,
		Offset:     safeOffset,
		TotalCount: totalCount,
	}, nil
}

// GetLogDetail は詳細 API 用レスポンスを返す。
func (s *Service) GetLogDetail(ctx context.Context, id int64) (*api.AuditLogDetailResponse, error) {
	item, err := s.repository.FindByID(ctx, id)
	if err != nil {
		return nil, err
	}
	if item == nil {
		return nil, ErrAuditLogNotFound
	}
	return &api.AuditLogDetailResponse{
		CompletionTokens:       item.CompletionTokens,
		CostJpy:                item.CostJpy,
		CreatedAt:              item.CreatedAt.UTC(),
		GroundednessEvaluation: toGroundednessEvaluation(*item),
		Id:                     item.ID,
		LatencyMs:              item.LatencyMs,
		Model:                  item.Model,
		Prompt:                 item.Prompt,
		PromptTokens:           item.PromptTokens,
		Response:               item.Response,
		TotalTokens:            item.TotalTokens,
	}, nil
}

func toSummaryItem(item LogRecord) api.AuditLogSummaryItem {
	return api.AuditLogSummaryItem{
		CompletionTokens:       item.CompletionTokens,
		CostJpy:                item.CostJpy,
		CreatedAt:              item.CreatedAt.UTC(),
		GroundednessEvaluation: toGroundednessEvaluation(item),
		Id:                     item.ID,
		LatencyMs:              item.LatencyMs,
		Model:                  item.Model,
		PromptTokens:           item.PromptTokens,
		TotalTokens:            item.TotalTokens,
	}
}

func toGroundednessEvaluation(item LogRecord) api.GroundednessEvaluation {
	return api.GroundednessEvaluation{
		FallbackApplied:   item.GroundednessFallbackApplied,
		GroundednessScore: item.GroundednessScore,
		Reason:            item.GroundednessReason,
		Status:            api.GroundednessEvaluationStatus(item.GroundednessStatus),
	}
}
