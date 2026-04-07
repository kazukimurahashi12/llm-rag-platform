package com.example.llmragplatform.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
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
    @Column(nullable = false)
    val createdAt: Instant = Instant.now(),
)
