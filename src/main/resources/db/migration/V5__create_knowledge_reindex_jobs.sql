create table if not exists knowledge_reindex_jobs (
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
);
