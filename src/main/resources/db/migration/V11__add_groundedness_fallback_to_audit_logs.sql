alter table audit_logs
    add column if not exists groundedness_fallback_applied boolean not null default false;
