package audit

import (
	"context"
	"database/sql"
	"time"

	"github.com/kazukimurahashi12/llm-rag-platform/backend-go/internal/api"
)

// LogRecord は audit_logs の 1 件分を表す。
type LogRecord struct {
	ID                          int64
	Model                       string
	Prompt                      string
	Response                    string
	PromptTokens                int
	CompletionTokens            int
	TotalTokens                 int
	CostJpy                     float64
	LatencyMs                   int64
	GroundednessScore           float64
	GroundednessStatus          string
	GroundednessReason          string
	GroundednessFallbackApplied bool
	CreatedAt                   time.Time
}

// Repository は audit log の永続化と集計を担当する。
type Repository struct {
	db *sql.DB
}

// NewRepository は audit repository を生成する。
func NewRepository(db *sql.DB) *Repository {
	return &Repository{db: db}
}

// Save は audit log を保存する。
func (r *Repository) Save(ctx context.Context, record LogRecord) error {
	_, err := r.db.ExecContext(ctx, `
		insert into audit_logs (
			model,
			prompt,
			response,
			prompt_tokens,
			completion_tokens,
			total_tokens,
			cost_jpy,
			latency_ms,
			groundedness_score,
			groundedness_status,
			groundedness_reason,
			groundedness_fallback_applied
		) values ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12)
	`, record.Model, record.Prompt, record.Response, record.PromptTokens, record.CompletionTokens, record.TotalTokens,
		record.CostJpy, record.LatencyMs, record.GroundednessScore, record.GroundednessStatus, record.GroundednessReason,
		record.GroundednessFallbackApplied)
	return err
}

// List は条件付きで監査ログ一覧を返す。
func (r *Repository) List(ctx context.Context, limit int, offset int, model string, from *time.Time, to *time.Time) ([]LogRecord, int64, error) {
	query := `
		select
			id, model, prompt, response, prompt_tokens, completion_tokens, total_tokens,
			cost_jpy, latency_ms, groundedness_score, groundedness_status, groundedness_reason,
			groundedness_fallback_applied, created_at
		from audit_logs
		where ($1 = '' or model = $1)
		  and ($2::timestamptz is null or created_at >= $2)
		  and ($3::timestamptz is null or created_at <= $3)
		order by created_at desc
		limit $4 offset $5
	`
	rows, err := r.db.QueryContext(ctx, query, model, from, to, limit, offset)
	if err != nil {
		return nil, 0, err
	}
	defer func() {
		_ = rows.Close()
	}()

	items := make([]LogRecord, 0)
	for rows.Next() {
		var item LogRecord
		if err := rows.Scan(
			&item.ID, &item.Model, &item.Prompt, &item.Response, &item.PromptTokens, &item.CompletionTokens, &item.TotalTokens,
			&item.CostJpy, &item.LatencyMs, &item.GroundednessScore, &item.GroundednessStatus, &item.GroundednessReason,
			&item.GroundednessFallbackApplied, &item.CreatedAt,
		); err != nil {
			return nil, 0, err
		}
		items = append(items, item)
	}
	if err := rows.Err(); err != nil {
		return nil, 0, err
	}

	var totalCount int64
	if err := r.db.QueryRowContext(ctx, `
		select count(*)
		from audit_logs
		where ($1 = '' or model = $1)
		  and ($2::timestamptz is null or created_at >= $2)
		  and ($3::timestamptz is null or created_at <= $3)
	`, model, from, to).Scan(&totalCount); err != nil {
		return nil, 0, err
	}

	return items, totalCount, nil
}

// FindByID は監査ログ単票を返す。
func (r *Repository) FindByID(ctx context.Context, id int64) (*LogRecord, error) {
	row := r.db.QueryRowContext(ctx, `
		select
			id, model, prompt, response, prompt_tokens, completion_tokens, total_tokens,
			cost_jpy, latency_ms, groundedness_score, groundedness_status, groundedness_reason,
			groundedness_fallback_applied, created_at
		from audit_logs
		where id = $1
	`, id)

	var item LogRecord
	if err := row.Scan(
		&item.ID, &item.Model, &item.Prompt, &item.Response, &item.PromptTokens, &item.CompletionTokens, &item.TotalTokens,
		&item.CostJpy, &item.LatencyMs, &item.GroundednessScore, &item.GroundednessStatus, &item.GroundednessReason,
		&item.GroundednessFallbackApplied, &item.CreatedAt,
	); err != nil {
		if err == sql.ErrNoRows {
			return nil, nil
		}
		return nil, err
	}
	return &item, nil
}

// BuildDashboardSummary は DB 集計値と reindex 件数から summary を返す。
func (r *Repository) BuildDashboardSummary(
	ctx context.Context,
	reindexStats api.DashboardSummaryResponse,
	knowledgeStats api.DashboardSummaryResponse,
) (*api.DashboardSummaryResponse, error) {
	summary := knowledgeStats
	summary.CompletedReindexJobs = reindexStats.CompletedReindexJobs
	summary.FailedReindexJobs = reindexStats.FailedReindexJobs
	summary.QueuedReindexJobs = reindexStats.QueuedReindexJobs
	summary.RunningReindexJobs = reindexStats.RunningReindexJobs
	summary.TotalReindexJobs = reindexStats.TotalReindexJobs
	summary.ReindexSuccessRate = reindexStats.ReindexSuccessRate

	if err := r.db.QueryRowContext(ctx, `
		select
			count(*),
			coalesce(avg(latency_ms), 0),
			coalesce(avg(cost_jpy), 0),
			coalesce(avg(groundedness_score), 0),
			count(*) filter (where groundedness_status = 'GROUNDED'),
			count(*) filter (where groundedness_status = 'LOW_GROUNDEDNESS'),
			count(*) filter (where groundedness_fallback_applied = true)
		from audit_logs
	`).Scan(
		&summary.TotalAdviceRequests,
		&summary.AverageLatencyMs,
		&summary.AverageCostJpy,
		&summary.AverageGroundednessScore,
		&summary.GroundedResponses,
		&summary.LowGroundednessResponses,
		&summary.GroundednessFallbackResponses,
	); err != nil {
		return nil, err
	}

	return &summary, nil
}
