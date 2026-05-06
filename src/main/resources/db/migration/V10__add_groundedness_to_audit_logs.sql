alter table audit_logs
    add column if not exists groundedness_score double precision not null default 0.0;

alter table audit_logs
    add column if not exists groundedness_status varchar(64) not null default 'LOW_GROUNDEDNESS';

alter table audit_logs
    add column if not exists groundedness_reason text not null default '';
