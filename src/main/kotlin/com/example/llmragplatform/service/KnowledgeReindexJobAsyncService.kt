package com.example.llmragplatform.service

import com.example.llmragplatform.domain.entity.KnowledgeReindexJobStatus
import com.example.llmragplatform.infrastructure.repository.KnowledgeReindexJobRepository
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class KnowledgeReindexJobAsyncService(
    private val knowledgeDocumentService: KnowledgeDocumentService,
    private val knowledgeReindexJobRepository: KnowledgeReindexJobRepository,
    private val knowledgeReindexJobMetrics: KnowledgeReindexJobMetrics,
) {

    @Async
    fun executeAllDocumentsJob(jobId: String) {
        runJob(jobId, scope = "all") {
            knowledgeDocumentService.reindexDocuments()
        }
    }

    @Async
    fun executeSingleDocumentJob(jobId: String, documentId: Long) {
        runJob(jobId, scope = "document") {
            knowledgeDocumentService.reindexDocument(documentId)
        }
    }

    private fun runJob(
        jobId: String,
        scope: String,
        block: () -> com.example.llmragplatform.generated.model.KnowledgeReindexResponse,
    ) {
        val job = knowledgeReindexJobRepository.findById(jobId).orElse(null) ?: return
        val startedAt = Instant.now()
        job.status = KnowledgeReindexJobStatus.RUNNING
        job.startedAt = startedAt
        knowledgeReindexJobRepository.save(job)

        try {
            val result = block()
            val completedAt = Instant.now()
            job.status = KnowledgeReindexJobStatus.COMPLETED
            job.completedAt = completedAt
            job.documentsProcessed = result.documentsProcessed
            job.chunksProcessed = result.chunksProcessed
            job.embeddingsUpdated = result.embeddingsUpdated
            job.vectorSearchEnabled = result.vectorSearchEnabled
            job.errorMessage = null
            knowledgeReindexJobRepository.save(job)
            knowledgeReindexJobMetrics.recordCompleted(scope, java.time.Duration.between(startedAt, completedAt))
        } catch (ex: Exception) {
            val completedAt = Instant.now()
            job.status = KnowledgeReindexJobStatus.FAILED
            job.completedAt = completedAt
            job.errorMessage = ex.message ?: "reindex job failed"
            knowledgeReindexJobRepository.save(job)
            knowledgeReindexJobMetrics.recordFailed(scope, java.time.Duration.between(startedAt, completedAt))
        }
    }
}
