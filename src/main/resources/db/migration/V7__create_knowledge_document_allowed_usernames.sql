create table knowledge_document_allowed_usernames (
    knowledge_document_id bigint not null,
    username varchar(255) not null,
    primary key (knowledge_document_id, username),
    constraint fk_knowledge_document_allowed_usernames_document
        foreign key (knowledge_document_id)
            references knowledge_documents (id)
            on delete cascade
);
