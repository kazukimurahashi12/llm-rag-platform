create table if not exists audit_logs (
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
);
