package com.example.llmragplatform.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "knowledge_reindex_jobs")
class KnowledgeReindexJob(
    @Id
    @Column(name = "job_id", nullable = false, length = 64)
    val jobId: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var status: KnowledgeReindexJobStatus,
    @Column(nullable = false)
    val acceptedAt: Instant,
    @Column(nullable = true)
    var startedAt: Instant? = null,
    @Column(nullable = true)
    var completedAt: Instant? = null,
    @Column(nullable = true)
    val knowledgeDocumentId: Long? = null,
    @Column(nullable = true)
    var documentsProcessed: Long? = null,
    @Column(nullable = true)
    var chunksProcessed: Long? = null,
    @Column(nullable = true)
    var embeddingsUpdated: Long? = null,
    @Column(nullable = true)
    var vectorSearchEnabled: Boolean? = null,
    @Column(columnDefinition = "TEXT", nullable = true)
    var errorMessage: String? = null,
)

enum class KnowledgeReindexJobStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
}
