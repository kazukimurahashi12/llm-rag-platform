package knowledge

import (
	"context"
	"database/sql"
	"fmt"
	"strings"
	"time"

	"github.com/lib/pq"
)

// DocumentRecord は knowledge document 本体を表す。
type DocumentRecord struct {
	ID               int64
	Title            string
	Content          string
	AccessScope      string
	AllowedUsernames []string
	AceCategory      string
	CreatedAt        time.Time
	UpdatedAt        time.Time
}

// ChunkRecord は retrieval 用に必要な文書 chunk 情報を保持する。
type ChunkRecord struct {
	ChunkID          int64
	DocumentID       int64
	Title            string
	AccessScope      string
	AllowedUsernames []string
	AceCategory      string
	ChunkIndex       int
	Content          string
}

// VectorMatch は pgvector 検索で返す候補を保持する。
type VectorMatch struct {
	ChunkRecord
	DistanceScore   float64
	SimilarityScore float64
}

// Repository は knowledge read path を担当する。
type Repository struct {
	db *sql.DB
}

// NewRepository は knowledge repository を生成する。
func NewRepository(db *sql.DB) *Repository {
	return &Repository{db: db}
}

// ListDocuments は作成日時の降順で文書一覧を返す。
func (r *Repository) ListDocuments(ctx context.Context) ([]DocumentRecord, error) {
	rows, err := r.db.QueryContext(ctx, `
		select
			kd.id,
			kd.title,
			kd.content,
			kd.access_scope,
			kd.ace_category,
			kd.created_at,
			kd.updated_at,
			coalesce(array_remove(array_agg(distinct kdau.username), null), '{}') as allowed_usernames
		from knowledge_documents kd
		left join knowledge_document_allowed_usernames kdau
			on kdau.knowledge_document_id = kd.id
		group by kd.id, kd.title, kd.content, kd.access_scope, kd.ace_category, kd.created_at, kd.updated_at
		order by kd.created_at desc, kd.id desc
	`)
	if err != nil {
		return nil, err
	}
	defer func() {
		_ = rows.Close()
	}()

	documents := make([]DocumentRecord, 0)
	for rows.Next() {
		var document DocumentRecord
		var allowedUsernames pq.StringArray
		if err := rows.Scan(
			&document.ID,
			&document.Title,
			&document.Content,
			&document.AccessScope,
			&document.AceCategory,
			&document.CreatedAt,
			&document.UpdatedAt,
			&allowedUsernames,
		); err != nil {
			return nil, err
		}
		document.AllowedUsernames = []string(allowedUsernames)
		documents = append(documents, document)
	}

	return documents, rows.Err()
}

// FindDocumentByID は単一文書を返す。
func (r *Repository) FindDocumentByID(ctx context.Context, id int64) (*DocumentRecord, error) {
	row := r.db.QueryRowContext(ctx, `
		select
			kd.id,
			kd.title,
			kd.content,
			kd.access_scope,
			kd.ace_category,
			kd.created_at,
			kd.updated_at,
			coalesce(array_remove(array_agg(distinct kdau.username), null), '{}') as allowed_usernames
		from knowledge_documents kd
		left join knowledge_document_allowed_usernames kdau
			on kdau.knowledge_document_id = kd.id
		where kd.id = $1
		group by kd.id, kd.title, kd.content, kd.access_scope, kd.ace_category, kd.created_at, kd.updated_at
	`, id)

	var document DocumentRecord
	var allowedUsernames pq.StringArray
	if err := row.Scan(
		&document.ID,
		&document.Title,
		&document.Content,
		&document.AccessScope,
		&document.AceCategory,
		&document.CreatedAt,
		&document.UpdatedAt,
		&allowedUsernames,
	); err != nil {
		if err == sql.ErrNoRows {
			return nil, nil
		}
		return nil, err
	}
	document.AllowedUsernames = []string(allowedUsernames)
	return &document, nil
}

// CreateDocument は文書本体を保存する。
func (r *Repository) CreateDocument(
	ctx context.Context,
	title string,
	content string,
	accessScope string,
	aceCategory string,
	allowedUsernames []string,
) (*DocumentRecord, error) {
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return nil, err
	}
	defer rollbackSilently(tx)

	var document DocumentRecord
	err = tx.QueryRowContext(ctx, `
		insert into knowledge_documents (title, content, access_scope, ace_category, updated_at)
		values ($1, $2, $3, $4, current_timestamp)
		returning id, title, content, access_scope, ace_category, created_at, updated_at
	`, title, content, accessScope, aceCategory).Scan(
		&document.ID,
		&document.Title,
		&document.Content,
		&document.AccessScope,
		&document.AceCategory,
		&document.CreatedAt,
		&document.UpdatedAt,
	)
	if err != nil {
		return nil, err
	}

	cleanAllowed := normalizeAllowedUsernames(allowedUsernames)
	if err := replaceAllowedUsernames(ctx, tx, document.ID, cleanAllowed); err != nil {
		return nil, err
	}
	document.AllowedUsernames = cleanAllowed

	if err := tx.Commit(); err != nil {
		return nil, err
	}
	return &document, nil
}

// UpdateDocument は文書本体と ACL を全置換する。
func (r *Repository) UpdateDocument(
	ctx context.Context,
	id int64,
	title string,
	content string,
	accessScope string,
	aceCategory string,
	allowedUsernames []string,
) (*DocumentRecord, error) {
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return nil, err
	}
	defer rollbackSilently(tx)

	var document DocumentRecord
	err = tx.QueryRowContext(ctx, `
		update knowledge_documents
		set title = $2,
		    content = $3,
		    access_scope = $4,
		    ace_category = $5,
		    updated_at = current_timestamp
		where id = $1
		returning id, title, content, access_scope, ace_category, created_at, updated_at
	`, id, title, content, accessScope, aceCategory).Scan(
		&document.ID,
		&document.Title,
		&document.Content,
		&document.AccessScope,
		&document.AceCategory,
		&document.CreatedAt,
		&document.UpdatedAt,
	)
	if err != nil {
		if err == sql.ErrNoRows {
			return nil, nil
		}
		return nil, err
	}

	cleanAllowed := normalizeAllowedUsernames(allowedUsernames)
	if err := replaceAllowedUsernames(ctx, tx, document.ID, cleanAllowed); err != nil {
		return nil, err
	}
	document.AllowedUsernames = cleanAllowed

	if err := tx.Commit(); err != nil {
		return nil, err
	}
	return &document, nil
}

// ReplaceChunks は対象文書の chunk を全削除して再生成する。
func (r *Repository) ReplaceChunks(ctx context.Context, documentID int64, chunks []string) ([]int64, error) {
	tx, err := r.db.BeginTx(ctx, nil)
	if err != nil {
		return nil, err
	}
	defer rollbackSilently(tx)

	if _, err := tx.ExecContext(ctx, `delete from knowledge_document_chunks where knowledge_document_id = $1`, documentID); err != nil {
		return nil, err
	}

	chunkIDs := make([]int64, 0, len(chunks))
	for index, chunk := range chunks {
		var chunkID int64
		if err := tx.QueryRowContext(ctx, `
			insert into knowledge_document_chunks (knowledge_document_id, chunk_index, content)
			values ($1, $2, $3)
			returning id
		`, documentID, index, chunk).Scan(&chunkID); err != nil {
			return nil, err
		}
		chunkIDs = append(chunkIDs, chunkID)
	}

	if err := tx.Commit(); err != nil {
		return nil, err
	}
	return chunkIDs, nil
}

// UpdateChunkEmbedding は chunk の embedding を更新する。
func (r *Repository) UpdateChunkEmbedding(ctx context.Context, chunkID int64, vectorLiteral string) error {
	result, err := r.db.ExecContext(ctx, `
		update knowledge_document_chunks
		set embedding = cast($2 as vector)
		where id = $1
	`, chunkID, vectorLiteral)
	if err != nil {
		return err
	}
	rowsAffected, err := result.RowsAffected()
	if err != nil {
		return err
	}
	if rowsAffected == 0 {
		return fmt.Errorf("knowledge chunk not found: %d", chunkID)
	}
	return nil
}

// FindAllChunks は retrieval 用に全 chunk を読み出す。
func (r *Repository) FindAllChunks(ctx context.Context) ([]ChunkRecord, error) {
	rows, err := r.db.QueryContext(ctx, `
		select
			kdc.id,
			kd.id,
			kd.title,
			kd.access_scope,
			kd.ace_category,
			kdc.chunk_index,
			kdc.content,
			coalesce(array_remove(array_agg(distinct kdau.username), null), '{}') as allowed_usernames
		from knowledge_document_chunks kdc
		join knowledge_documents kd
			on kd.id = kdc.knowledge_document_id
		left join knowledge_document_allowed_usernames kdau
			on kdau.knowledge_document_id = kd.id
		group by kdc.id, kd.id, kd.title, kd.access_scope, kd.ace_category, kdc.chunk_index, kdc.content
		order by kdc.id
	`)
	if err != nil {
		return nil, err
	}
	defer func() {
		_ = rows.Close()
	}()

	chunks := make([]ChunkRecord, 0)
	for rows.Next() {
		var chunkID int64
		var chunk ChunkRecord
		var allowedUsernames pq.StringArray
		if err := rows.Scan(
			&chunkID,
			&chunk.DocumentID,
			&chunk.Title,
			&chunk.AccessScope,
			&chunk.AceCategory,
			&chunk.ChunkIndex,
			&chunk.Content,
			&allowedUsernames,
		); err != nil {
			return nil, err
		}
		chunk.ChunkID = chunkID
		chunk.AllowedUsernames = []string(allowedUsernames)
		chunks = append(chunks, chunk)
	}

	return chunks, rows.Err()
}

// FindNearestChunks は pgvector を使って近傍検索を行う。
func (r *Repository) FindNearestChunks(ctx context.Context, vectorLiteral string, limit int) ([]VectorMatch, error) {
	rows, err := r.db.QueryContext(ctx, `
		select
			kdc.id,
			kd.id,
			kd.title,
			kd.access_scope,
			kd.ace_category,
			kdc.chunk_index,
			kdc.content,
			coalesce(array_remove(array_agg(distinct kdau.username), null), '{}') as allowed_usernames,
			kdc.embedding <=> cast($1 as vector) as distance_score
		from knowledge_document_chunks kdc
		join knowledge_documents kd
			on kd.id = kdc.knowledge_document_id
		left join knowledge_document_allowed_usernames kdau
			on kdau.knowledge_document_id = kd.id
		where kdc.embedding is not null
		group by kdc.id, kd.id, kd.title, kd.access_scope, kd.ace_category, kdc.chunk_index, kdc.content, kdc.embedding
		order by kdc.embedding <=> cast($1 as vector)
		limit $2
	`, vectorLiteral, limit)
	if err != nil {
		return nil, err
	}
	defer func() {
		_ = rows.Close()
	}()

	matches := make([]VectorMatch, 0)
	for rows.Next() {
		var match VectorMatch
		var allowedUsernames pq.StringArray
		if err := rows.Scan(
			&match.ChunkID,
			&match.DocumentID,
			&match.Title,
			&match.AccessScope,
			&match.AceCategory,
			&match.ChunkIndex,
			&match.Content,
			&allowedUsernames,
			&match.DistanceScore,
		); err != nil {
			return nil, err
		}
		match.AllowedUsernames = []string(allowedUsernames)
		match.SimilarityScore = 1.0 - match.DistanceScore
		if match.SimilarityScore < 0 {
			match.SimilarityScore = 0
		}
		if match.SimilarityScore > 1 {
			match.SimilarityScore = 1
		}
		matches = append(matches, match)
	}

	return matches, rows.Err()
}

func replaceAllowedUsernames(ctx context.Context, tx *sql.Tx, documentID int64, allowedUsernames []string) error {
	if _, err := tx.ExecContext(ctx, `delete from knowledge_document_allowed_usernames where knowledge_document_id = $1`, documentID); err != nil {
		return err
	}
	for _, username := range allowedUsernames {
		if _, err := tx.ExecContext(ctx, `
			insert into knowledge_document_allowed_usernames (knowledge_document_id, username)
			values ($1, $2)
		`, documentID, username); err != nil {
			return err
		}
	}
	return nil
}

func normalizeAllowedUsernames(values []string) []string {
	seen := map[string]struct{}{}
	normalized := make([]string, 0, len(values))
	for _, value := range values {
		trimmed := strings.TrimSpace(value)
		if trimmed == "" {
			continue
		}
		if _, exists := seen[trimmed]; exists {
			continue
		}
		seen[trimmed] = struct{}{}
		normalized = append(normalized, trimmed)
	}
	return normalized
}

func rollbackSilently(tx *sql.Tx) {
	_ = tx.Rollback()
}
