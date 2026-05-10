create table if not exists knowledge_document_chunks (
    id bigserial primary key,
    knowledge_document_id bigint not null references knowledge_documents(id) on delete cascade,
    chunk_index integer not null,
    content text not null,
    created_at timestamp with time zone not null default current_timestamp
);

create index if not exists idx_knowledge_document_chunks_document_id
    on knowledge_document_chunks (knowledge_document_id);
