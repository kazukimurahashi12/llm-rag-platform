alter table knowledge_documents
    add column access_scope varchar(32) not null default 'SHARED';
