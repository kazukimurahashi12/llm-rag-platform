create table if not exists knowledge_documents (
    id bigserial primary key,
    title varchar(255) not null,
    content text not null,
    created_at timestamp with time zone not null default current_timestamp
);
