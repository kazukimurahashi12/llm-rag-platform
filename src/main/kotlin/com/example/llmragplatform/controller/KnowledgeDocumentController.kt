package com.example.llmragplatform.controller

import com.example.llmragplatform.generated.api.KnowledgeApi
import com.example.llmragplatform.generated.model.KnowledgeDocumentCreateRequest
import com.example.llmragplatform.generated.model.KnowledgeDocumentListResponse
import com.example.llmragplatform.generated.model.KnowledgeDocumentResponse
import com.example.llmragplatform.service.KnowledgeDocumentService
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.RestController

@RestController
class KnowledgeDocumentController(
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
        return ResponseEntity.status(201).body(
            knowledgeDocumentService.createDocument(knowledgeDocumentCreateRequest)
        )
    }
}
