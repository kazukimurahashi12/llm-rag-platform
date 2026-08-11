package db

import "context"

var migrationStatements = []string{
	`create table if not exists audit_logs (
		id bigserial primary key,
		model varchar(255) not null,
		prompt text not null,
		response text not null,
		prompt_tokens integer not null,
		completion_tokens integer not null,
		total_tokens integer not null,
		cost_jpy double precision not null,
		latency_ms bigint not null,
		created_at timestamp with time zone not null default current_timestamp
	)`,
	`create table if not exists knowledge_documents (
		id bigserial primary key,
		title varchar(255) not null,
		content text not null,
		created_at timestamp with time zone not null default current_timestamp
	)`,
	`create table if not exists knowledge_document_chunks (
		id bigserial primary key,
		knowledge_document_id bigint not null references knowledge_documents(id) on delete cascade,
		chunk_index integer not null,
		content text not null,
		created_at timestamp with time zone not null default current_timestamp
	)`,
	`create index if not exists idx_knowledge_document_chunks_document_id
		on knowledge_document_chunks (knowledge_document_id)`,
	`create extension if not exists vector`,
	`alter table knowledge_document_chunks
		add column if not exists embedding vector(1536)`,
	`create index if not exists idx_knowledge_document_chunks_embedding
		on knowledge_document_chunks
		using ivfflat (embedding vector_cosine_ops)
		with (lists = 100)`,
	`create table if not exists knowledge_reindex_jobs (
		job_id varchar(64) primary key,
		status varchar(32) not null,
		accepted_at timestamp with time zone not null,
		started_at timestamp with time zone,
		completed_at timestamp with time zone,
		knowledge_document_id bigint,
		documents_processed bigint,
		chunks_processed bigint,
		embeddings_updated bigint,
		vector_search_enabled boolean,
		error_message text
	)`,
	`alter table knowledge_documents
		add column if not exists access_scope varchar(32) not null default 'SHARED'`,
	`create table if not exists knowledge_document_allowed_usernames (
		knowledge_document_id bigint not null,
		username varchar(255) not null,
		primary key (knowledge_document_id, username),
		constraint fk_knowledge_document_allowed_usernames_document
			foreign key (knowledge_document_id)
				references knowledge_documents (id)
				on delete cascade
	)`,
	`alter table knowledge_documents
		add column if not exists ace_category varchar(32) not null default 'EXPECTATION'`,
	`alter table knowledge_documents
		add column if not exists updated_at timestamp with time zone not null default current_timestamp`,
	`alter table audit_logs
		add column if not exists groundedness_score double precision not null default 0.0`,
	`alter table audit_logs
		add column if not exists groundedness_status varchar(64) not null default 'LOW_GROUNDEDNESS'`,
	`alter table audit_logs
		add column if not exists groundedness_reason text not null default ''`,
	`alter table audit_logs
		add column if not exists groundedness_fallback_applied boolean not null default false`,
}

// Migrate applies the idempotent schema needed by the Go backend.
func (p *Postgres) Migrate(ctx context.Context) error {
	for _, statement := range migrationStatements {
		if _, err := p.sqlDB.ExecContext(ctx, statement); err != nil {
			return err
		}
	}
	return nil
}
