package com.example.llmragplatform.service

import com.example.llmragplatform.domain.entity.KnowledgeReindexJob
import com.example.llmragplatform.domain.entity.KnowledgeReindexJobStatus
import com.example.llmragplatform.exception.ResourceNotFoundException
import com.example.llmragplatform.generated.model.KnowledgeReindexJobAcceptedResponse
import com.example.llmragplatform.generated.model.KnowledgeReindexJobListResponse
import com.example.llmragplatform.generated.model.KnowledgeReindexJobStatusResponse
import com.example.llmragplatform.generated.model.KnowledgeReindexResponse
import com.example.llmragplatform.infrastructure.repository.KnowledgeDocumentRepository
import com.example.llmragplatform.infrastructure.repository.KnowledgeReindexJobRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Service
class KnowledgeReindexJobService(
    private val knowledgeDocumentRepository: KnowledgeDocumentRepository,
    private val knowledgeReindexJobRepository: KnowledgeReindexJobRepository,
    private val knowledgeReindexJobAsyncService: KnowledgeReindexJobAsyncService,
    private val knowledgeReindexJobMetrics: KnowledgeReindexJobMetrics,
) {

    fun submitAllDocumentsJob(): KnowledgeReindexJobAcceptedResponse {
        val job = knowledgeReindexJobRepository.save(
            KnowledgeReindexJob(
                jobId = UUID.randomUUID().toString(),
                status = KnowledgeReindexJobStatus.QUEUED,
                acceptedAt = Instant.now(),
                knowledgeDocumentId = null
            )
        )
        knowledgeReindexJobMetrics.recordAccepted(scope = "all", trigger = "initial")
        knowledgeReindexJobAsyncService.executeAllDocumentsJob(job.jobId)
        return toAcceptedResponse(job)
    }

    fun submitSingleDocumentJob(documentId: Long): KnowledgeReindexJobAcceptedResponse {
        if (!knowledgeDocumentRepository.existsById(documentId)) {
            throw ResourceNotFoundException("Knowledge document not found: $documentId")
        }

        val job = knowledgeReindexJobRepository.save(
            KnowledgeReindexJob(
                jobId = UUID.randomUUID().toString(),
                status = KnowledgeReindexJobStatus.QUEUED,
                acceptedAt = Instant.now(),
                knowledgeDocumentId = documentId
            )
        )
        knowledgeReindexJobMetrics.recordAccepted(scope = "document", trigger = "initial")
        knowledgeReindexJobAsyncService.executeSingleDocumentJob(job.jobId, documentId)
        return toAcceptedResponse(job)
    }

    fun getJob(jobId: String): KnowledgeReindexJobStatusResponse {
        val job = knowledgeReindexJobRepository.findById(jobId)
            .orElseThrow { ResourceNotFoundException("Knowledge reindex job not found: $jobId") }
        return toStatusResponse(job)
    }

    fun getJobs(
        limit: Int,
        offset: Int,
        status: String?,
        knowledgeDocumentId: Long?,
        acceptedFrom: OffsetDateTime?,
        acceptedTo: OffsetDateTime?,
        completedFrom: OffsetDateTime?,
        completedTo: OffsetDateTime?,
    ): KnowledgeReindexJobListResponse {
        val safeLimit = limit.coerceIn(1, 100)
        val safeOffset = offset.coerceAtLeast(0)
        val normalizedStatus = status?.takeIf { it.isNotBlank() }?.let { statusValue ->
            runCatching { KnowledgeReindexJobStatus.valueOf(statusValue) }
                .getOrElse { throw IllegalArgumentException("Invalid reindex job status: $statusValue") }
        }
        val acceptedFromInstant = acceptedFrom?.toInstant()
        val acceptedToInstant = acceptedTo?.toInstant()
        val completedFromInstant = completedFrom?.toInstant()
        val completedToInstant = completedTo?.toInstant()

        if (acceptedFromInstant != null && acceptedToInstant != null && acceptedFromInstant.isAfter(acceptedToInstant)) {
            throw IllegalArgumentException("acceptedFrom must be earlier than or equal to acceptedTo")
        }
        if (completedFromInstant != null && completedToInstant != null && completedFromInstant.isAfter(completedToInstant)) {
            throw IllegalArgumentException("completedFrom must be earlier than or equal to completedTo")
        }

        val filteredJobs = knowledgeReindexJobRepository.findAllByOrderByAcceptedAtDesc()
            .filter { job ->
                (normalizedStatus == null || job.status == normalizedStatus) &&
                    (knowledgeDocumentId == null || job.knowledgeDocumentId == knowledgeDocumentId) &&
                    (acceptedFromInstant == null || !job.acceptedAt.isBefore(acceptedFromInstant)) &&
                    (acceptedToInstant == null || !job.acceptedAt.isAfter(acceptedToInstant)) &&
                    (
                        completedFromInstant == null ||
                            (job.completedAt != null && !job.completedAt!!.isBefore(completedFromInstant))
                        ) &&
                    (
                        completedToInstant == null ||
                            (job.completedAt != null && !job.completedAt!!.isAfter(completedToInstant))
                        )
            }
        val items = filteredJobs.drop(safeOffset).take(safeLimit).map(::toStatusResponse)

        return KnowledgeReindexJobListResponse()
            .items(items)
            .totalCount(filteredJobs.size.toLong())
            .limit(safeLimit)
            .offset(safeOffset)
    }

    @Transactional
    fun deleteJob(jobId: String) {
        val job = knowledgeReindexJobRepository.findById(jobId)
            .orElseThrow { ResourceNotFoundException("Knowledge reindex job not found: $jobId") }

        if (job.status == KnowledgeReindexJobStatus.QUEUED || job.status == KnowledgeReindexJobStatus.RUNNING) {
            throw IllegalArgumentException("Knowledge reindex job cannot be deleted while active: $jobId")
        }

        knowledgeReindexJobRepository.delete(job)
        knowledgeReindexJobMetrics.recordDeleted(scope = job.scope())
    }

    fun retryJob(jobId: String): KnowledgeReindexJobAcceptedResponse {
        val job = knowledgeReindexJobRepository.findById(jobId)
            .orElseThrow { ResourceNotFoundException("Knowledge reindex job not found: $jobId") }

        if (job.status != KnowledgeReindexJobStatus.FAILED) {
            throw IllegalArgumentException("Only failed reindex jobs can be retried: $jobId")
        }

        knowledgeReindexJobMetrics.recordRetried(scope = job.scope())
        return if (job.knowledgeDocumentId == null) {
            submitAllDocumentsJob(trigger = "retry")
        } else {
            submitSingleDocumentJob(job.knowledgeDocumentId, trigger = "retry")
        }
    }

    private fun submitAllDocumentsJob(trigger: String): KnowledgeReindexJobAcceptedResponse {
        val job = knowledgeReindexJobRepository.save(
            KnowledgeReindexJob(
                jobId = UUID.randomUUID().toString(),
                status = KnowledgeReindexJobStatus.QUEUED,
                acceptedAt = Instant.now(),
                knowledgeDocumentId = null
            )
        )
        knowledgeReindexJobMetrics.recordAccepted(scope = "all", trigger = trigger)
        knowledgeReindexJobAsyncService.executeAllDocumentsJob(job.jobId)
        return toAcceptedResponse(job)
    }

    private fun submitSingleDocumentJob(documentId: Long, trigger: String): KnowledgeReindexJobAcceptedResponse {
        if (!knowledgeDocumentRepository.existsById(documentId)) {
            throw ResourceNotFoundException("Knowledge document not found: $documentId")
        }

        val job = knowledgeReindexJobRepository.save(
            KnowledgeReindexJob(
                jobId = UUID.randomUUID().toString(),
                status = KnowledgeReindexJobStatus.QUEUED,
                acceptedAt = Instant.now(),
                knowledgeDocumentId = documentId
            )
        )
        knowledgeReindexJobMetrics.recordAccepted(scope = "document", trigger = trigger)
        knowledgeReindexJobAsyncService.executeSingleDocumentJob(job.jobId, documentId)
        return toAcceptedResponse(job)
    }

    private fun toAcceptedResponse(job: KnowledgeReindexJob): KnowledgeReindexJobAcceptedResponse {
        return KnowledgeReindexJobAcceptedResponse()
            .jobId(job.jobId)
            .status(job.status.name)
            .acceptedAt(job.acceptedAt.atOffset(ZoneOffset.UTC))
    }

    private fun toStatusResponse(job: KnowledgeReindexJob): KnowledgeReindexJobStatusResponse {
        return KnowledgeReindexJobStatusResponse()
            .jobId(job.jobId)
            .status(job.status.name)
            .acceptedAt(job.acceptedAt.atOffset(ZoneOffset.UTC))
            .startedAt(job.startedAt?.atOffset(ZoneOffset.UTC))
            .completedAt(job.completedAt?.atOffset(ZoneOffset.UTC))
            .knowledgeDocumentId(job.knowledgeDocumentId)
            .result(
                if (job.documentsProcessed == null && job.chunksProcessed == null && job.embeddingsUpdated == null && job.vectorSearchEnabled == null) {
                    null
                } else {
                    KnowledgeReindexResponse()
                        .documentsProcessed(job.documentsProcessed)
                        .chunksProcessed(job.chunksProcessed)
                        .embeddingsUpdated(job.embeddingsUpdated)
                        .vectorSearchEnabled(job.vectorSearchEnabled)
                }
            )
            .errorMessage(job.errorMessage)
    }

    private fun KnowledgeReindexJob.scope(): String {
        return if (knowledgeDocumentId == null) "all" else "document"
    }
}
