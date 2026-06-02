package audit

import (
	"context"
	"errors"
	"time"

	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/api"
	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/pii"
)

// ErrAuditLogNotFound は監査ログ未存在を表す。
var ErrAuditLogNotFound = errors.New("audit log not found")

// Service は audit log の保存・参照・集計を担当する。
type Service struct {
	repository     *Repository
	maskingService *pii.MaskingService
}

// NewService は audit service を生成する。
func NewService(repository *Repository, maskingService *pii.MaskingService) *Service {
	return &Service{repository: repository, maskingService: maskingService}
}

// Save は監査ログを保存する。
func (s *Service) Save(ctx context.Context, record LogRecord) error {
	if s.maskingService != nil {
		record.Prompt = s.maskingService.MaskText(record.Prompt)
		record.Response = s.maskingService.MaskText(record.Response)
	}
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
func (s *Service) GetLogDetail(ctx context.Context, id int64, includeSensitiveContent bool) (*api.AuditLogDetailResponse, error) {
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
		Prompt:                 s.toVisiblePrompt(item.Prompt, includeSensitiveContent),
		PromptTokens:           item.PromptTokens,
		Response:               s.toVisibleResponse(item.Response, includeSensitiveContent),
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

func (s *Service) toVisiblePrompt(value string, includeSensitiveContent bool) string {
	if includeSensitiveContent || s.maskingService == nil {
		return value
	}
	return abbreviateForOperator(value)
}

func (s *Service) toVisibleResponse(value string, includeSensitiveContent bool) string {
	if includeSensitiveContent || s.maskingService == nil {
		return value
	}
	return abbreviateForOperator(value)
}

func abbreviateForOperator(value string) string {
	const maxLength = 80
	runes := []rune(value)
	if len(runes) <= maxLength {
		return value
	}
	return string(runes[:maxLength]) + "... [REDACTED]"
}
