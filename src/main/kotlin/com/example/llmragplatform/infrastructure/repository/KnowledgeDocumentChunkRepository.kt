package com.example.llmragplatform.infrastructure.repository

import com.example.llmragplatform.domain.entity.KnowledgeDocument
import com.example.llmragplatform.domain.entity.KnowledgeDocumentAccessScope
import com.example.llmragplatform.domain.entity.KnowledgeDocumentChunk
import org.springframework.data.jpa.repository.JpaRepository

interface KnowledgeDocumentChunkRepository : JpaRepository<KnowledgeDocumentChunk, Long> {
    fun findAllByKnowledgeDocumentOrderByChunkIndexAsc(knowledgeDocument: KnowledgeDocument): List<KnowledgeDocumentChunk>
    fun findAllByKnowledgeDocumentAccessScopeIn(accessScopes: Collection<KnowledgeDocumentAccessScope>): List<KnowledgeDocumentChunk>
    fun findAllByIdInAndKnowledgeDocumentAccessScopeIn(ids: Collection<Long>, accessScopes: Collection<KnowledgeDocumentAccessScope>): List<KnowledgeDocumentChunk>
}
