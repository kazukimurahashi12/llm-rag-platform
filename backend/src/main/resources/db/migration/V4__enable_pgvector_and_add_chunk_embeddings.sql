create extension if not exists vector;

alter table knowledge_document_chunks
    add column if not exists embedding vector(1536);

create index if not exists idx_knowledge_document_chunks_embedding
    on knowledge_document_chunks
    using ivfflat (embedding vector_cosine_ops)
    with (lists = 100);
