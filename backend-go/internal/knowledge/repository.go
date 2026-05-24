package knowledge

import (
	"context"
	"database/sql"
	"strings"

	"github.com/lib/pq"
)

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
	defer rows.Close()

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
		if strings.Contains(strings.ToLower(err.Error()), "ivfflat") {
			// probes 設定が不要な環境でも検索は継続できるため、通常クエリ結果だけ返す。
		}
		return nil, err
	}
	defer rows.Close()

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
