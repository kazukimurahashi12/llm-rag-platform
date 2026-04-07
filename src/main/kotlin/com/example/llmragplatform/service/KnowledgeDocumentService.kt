package com.example.llmragplatform.service

import com.example.llmragplatform.domain.entity.KnowledgeDocument
import com.example.llmragplatform.generated.model.KnowledgeDocumentCreateRequest
import com.example.llmragplatform.generated.model.KnowledgeDocumentListResponse
import com.example.llmragplatform.generated.model.KnowledgeDocumentResponse
import com.example.llmragplatform.infrastructure.repository.KnowledgeDocumentRepository
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Service
class KnowledgeDocumentService(
    private val knowledgeDocumentRepository: KnowledgeDocumentRepository,
) {

    fun getDocuments(limit: Int, offset: Int): KnowledgeDocumentListResponse {
        val safeLimit = limit.coerceIn(1, 100)
        val safeOffset = offset.coerceAtLeast(0)
        val allDocuments = knowledgeDocumentRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
        val items = allDocuments.drop(safeOffset).take(safeLimit).map(::toResponse)

        return KnowledgeDocumentListResponse()
            .items(items)
            .totalCount(allDocuments.size.toLong())
            .limit(safeLimit)
            .offset(safeOffset)
    }

    fun createDocument(request: KnowledgeDocumentCreateRequest): KnowledgeDocumentResponse {
        val savedDocument = knowledgeDocumentRepository.save(
            KnowledgeDocument(
                title = request.title,
                content = request.content
            )
        )

        return toResponse(savedDocument)
    }

    private fun toResponse(document: KnowledgeDocument): KnowledgeDocumentResponse {
        return KnowledgeDocumentResponse()
            .id(document.id)
            .title(document.title)
            .content(document.content)
            .createdAt(OffsetDateTime.ofInstant(document.createdAt, ZoneOffset.UTC))
    }
}
