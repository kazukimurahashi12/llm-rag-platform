package com.example.llmragplatform.controller

import com.example.llmragplatform.config.SecurityProperties
import com.example.llmragplatform.generated.api.KnowledgeApi
import com.example.llmragplatform.generated.model.KnowledgeDocumentCreateRequest
import com.example.llmragplatform.generated.model.KnowledgeDocumentListResponse
import com.example.llmragplatform.generated.model.KnowledgeDocumentResponse
import com.example.llmragplatform.generated.model.KnowledgeReindexJobAcceptedResponse
import com.example.llmragplatform.generated.model.KnowledgeReindexJobListResponse
import com.example.llmragplatform.generated.model.KnowledgeReindexJobStatusResponse
import com.example.llmragplatform.service.KnowledgeDocumentService
import com.example.llmragplatform.service.KnowledgeReindexJobService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.OffsetDateTime

@RestController
class KnowledgeDocumentController(
    private val securityProperties: SecurityProperties,
    private val knowledgeDocumentService: KnowledgeDocumentService,
    private val knowledgeReindexJobService: KnowledgeReindexJobService,
) : KnowledgeApi {

    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    override fun getKnowledgeDocuments(limit: Int?, offset: Int?): ResponseEntity<KnowledgeDocumentListResponse> {
        return ResponseEntity.ok(
            knowledgeDocumentService.getDocuments(
                limit = limit ?: 20,
                offset = offset ?: 0
            )
        )
    }

    @PreAuthorize("hasRole('ADMIN')")
    override fun createKnowledgeDocument(
        knowledgeDocumentCreateRequest: KnowledgeDocumentCreateRequest,
    ): ResponseEntity<KnowledgeDocumentResponse> {
        ensureConfiguredAdmin()
        return ResponseEntity.status(201).body(
            knowledgeDocumentService.createDocument(knowledgeDocumentCreateRequest)
        )
    }

    @PreAuthorize("hasRole('ADMIN')")
    override fun reindexKnowledgeDocuments(): ResponseEntity<KnowledgeReindexJobAcceptedResponse> {
        ensureConfiguredAdmin()
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(
            knowledgeReindexJobService.submitAllDocumentsJob()
        )
    }

    @PreAuthorize("hasRole('ADMIN')")
    override fun reindexKnowledgeDocument(knowledgeDocumentId: Long): ResponseEntity<KnowledgeReindexJobAcceptedResponse> {
        ensureConfiguredAdmin()
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(
            knowledgeReindexJobService.submitSingleDocumentJob(knowledgeDocumentId)
        )
    }

    @PreAuthorize("hasRole('ADMIN')")
    override fun getKnowledgeReindexJob(jobId: String): ResponseEntity<KnowledgeReindexJobStatusResponse> {
        ensureConfiguredAdmin()
        return ResponseEntity.ok(knowledgeReindexJobService.getJob(jobId))
    }

    @PreAuthorize("hasRole('ADMIN')")
    override fun deleteKnowledgeReindexJob(jobId: String): ResponseEntity<Void> {
        ensureConfiguredAdmin()
        knowledgeReindexJobService.deleteJob(jobId)
        return ResponseEntity.noContent().build()
    }

    @PreAuthorize("hasRole('ADMIN')")
    override fun retryKnowledgeReindexJob(jobId: String): ResponseEntity<KnowledgeReindexJobAcceptedResponse> {
        ensureConfiguredAdmin()
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(
            knowledgeReindexJobService.retryJob(jobId)
        )
    }

    @PreAuthorize("hasRole('ADMIN')")
    override fun getKnowledgeReindexJobs(
        limit: Int?,
        offset: Int?,
        status: String?,
        knowledgeDocumentId: Long?,
        acceptedFrom: OffsetDateTime?,
        acceptedTo: OffsetDateTime?,
        completedFrom: OffsetDateTime?,
        completedTo: OffsetDateTime?,
        sortBy: String?,
        sortDirection: String?,
    ): ResponseEntity<KnowledgeReindexJobListResponse> {
        ensureConfiguredAdmin()
        return ResponseEntity.ok(
            knowledgeReindexJobService.getJobs(
                limit = limit ?: 20,
                offset = offset ?: 0,
                status = status,
                knowledgeDocumentId = knowledgeDocumentId,
                acceptedFrom = acceptedFrom,
                acceptedTo = acceptedTo,
                completedFrom = completedFrom,
                completedTo = completedTo,
                sortBy = sortBy,
                sortDirection = sortDirection
            )
        )
    }

    private fun ensureConfiguredAdmin() {
        val authentication = SecurityContextHolder.getContext().authentication
        if (authentication.name != securityProperties.admin.username) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access is required for knowledge document write")
        }
    }
}
