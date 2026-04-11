package com.example.llmragplatform.controller

import com.example.llmragplatform.config.SecurityProperties
import com.example.llmragplatform.generated.api.KnowledgeApi
import com.example.llmragplatform.generated.model.KnowledgeDocumentCreateRequest
import com.example.llmragplatform.generated.model.KnowledgeDocumentListResponse
import com.example.llmragplatform.generated.model.KnowledgeDocumentResponse
import com.example.llmragplatform.generated.model.KnowledgeReindexResponse
import com.example.llmragplatform.service.KnowledgeDocumentService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
class KnowledgeDocumentController(
    private val securityProperties: SecurityProperties,
    private val knowledgeDocumentService: KnowledgeDocumentService,
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
    override fun reindexKnowledgeDocuments(): ResponseEntity<KnowledgeReindexResponse> {
        ensureConfiguredAdmin()
        return ResponseEntity.ok(knowledgeDocumentService.reindexDocuments())
    }

    @PreAuthorize("hasRole('ADMIN')")
    override fun reindexKnowledgeDocument(knowledgeDocumentId: Long): ResponseEntity<KnowledgeReindexResponse> {
        ensureConfiguredAdmin()
        return ResponseEntity.ok(knowledgeDocumentService.reindexDocument(knowledgeDocumentId))
    }

    private fun ensureConfiguredAdmin() {
        val authentication = SecurityContextHolder.getContext().authentication
        if (authentication.name != securityProperties.admin.username) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access is required for knowledge document write")
        }
    }
}
