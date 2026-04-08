package com.example.llmragplatform.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "knowledge_document_chunks")
class KnowledgeDocumentChunk(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "knowledge_document_id", nullable = false)
    val knowledgeDocument: KnowledgeDocument,
    @Column(nullable = false)
    val chunkIndex: Int,
    @Column(columnDefinition = "TEXT", nullable = false)
    val content: String,
    @Column(nullable = false)
    val createdAt: Instant = Instant.now(),
)
