package com.example.llmragplatform.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.CollectionTable
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "knowledge_documents")
class KnowledgeDocument(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(nullable = false)
    val title: String,
    @Column(columnDefinition = "TEXT", nullable = false)
    val content: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    val accessScope: KnowledgeDocumentAccessScope = KnowledgeDocumentAccessScope.SHARED,
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "knowledge_document_allowed_usernames",
        joinColumns = [JoinColumn(name = "knowledge_document_id")]
    )
    @Column(name = "username", nullable = false, length = 255)
    val allowedUsernames: Set<String> = emptySet(),
    @Column(nullable = false)
    val createdAt: Instant = Instant.now(),
)
