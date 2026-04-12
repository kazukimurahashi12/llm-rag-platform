package com.example.llmragplatform.service

import com.example.llmragplatform.config.RagProperties
import com.example.llmragplatform.domain.entity.KnowledgeDocument
import com.example.llmragplatform.domain.entity.KnowledgeDocumentChunk
import com.example.llmragplatform.exception.ResourceNotFoundException
import com.example.llmragplatform.generated.model.KnowledgeDocumentCreateRequest
import com.example.llmragplatform.generated.model.KnowledgeDocumentListResponse
import com.example.llmragplatform.generated.model.KnowledgeDocumentResponse
import com.example.llmragplatform.generated.model.KnowledgeReindexResponse
import com.example.llmragplatform.infrastructure.repository.KnowledgeDocumentChunkRepository
import com.example.llmragplatform.infrastructure.repository.KnowledgeDocumentRepository
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Service
class KnowledgeDocumentService(
    private val ragProperties: RagProperties,
    private val knowledgeDocumentRepository: KnowledgeDocumentRepository,
    private val knowledgeDocumentChunkRepository: KnowledgeDocumentChunkRepository,
    private val knowledgeChunkingService: KnowledgeChunkingService,
    private val knowledgeEmbeddingService: KnowledgeEmbeddingService,
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

    @Transactional
    fun createDocument(request: KnowledgeDocumentCreateRequest): KnowledgeDocumentResponse {
        val savedDocument = knowledgeDocumentRepository.save(
            KnowledgeDocument(
                title = request.title,
                content = request.content
            )
        )
        val chunks = knowledgeChunkingService.chunk(savedDocument.content)
            .mapIndexed { index, chunk ->
                KnowledgeDocumentChunk(
                    knowledgeDocument = savedDocument,
                    chunkIndex = index,
                    content = chunk
                )
            }
        val savedChunks = knowledgeDocumentChunkRepository.saveAll(chunks)
        knowledgeEmbeddingService.enrichChunks(savedChunks)

        return toResponse(savedDocument)
    }

    fun reindexDocuments(): KnowledgeReindexResponse {
        val allChunks = knowledgeDocumentChunkRepository.findAll()
        val embeddingsUpdated = knowledgeEmbeddingService.enrichChunks(allChunks)

        return KnowledgeReindexResponse()
            .documentsProcessed(knowledgeDocumentRepository.count())
            .chunksProcessed(allChunks.size.toLong())
            .embeddingsUpdated(embeddingsUpdated.toLong())
            .vectorSearchEnabled(ragProperties.vectorSearchEnabled)
    }

    fun reindexDocument(documentId: Long): KnowledgeReindexResponse {
        val document = knowledgeDocumentRepository.findById(documentId)
            .orElseThrow { ResourceNotFoundException("Knowledge document not found: $documentId") }
        val chunks = knowledgeDocumentChunkRepository.findAllByKnowledgeDocumentOrderByChunkIndexAsc(document)
        val embeddingsUpdated = knowledgeEmbeddingService.enrichChunks(chunks)

        return KnowledgeReindexResponse()
            .documentsProcessed(1)
            .chunksProcessed(chunks.size.toLong())
            .embeddingsUpdated(embeddingsUpdated.toLong())
            .vectorSearchEnabled(ragProperties.vectorSearchEnabled)
    }

    private fun toResponse(document: KnowledgeDocument): KnowledgeDocumentResponse {
        return KnowledgeDocumentResponse()
            .id(document.id)
            .title(document.title)
            .content(document.content)
            .createdAt(OffsetDateTime.ofInstant(document.createdAt, ZoneOffset.UTC))
    }
}
