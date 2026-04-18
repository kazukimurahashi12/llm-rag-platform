package com.example.llmragplatform.infrastructure.repository

import com.example.llmragplatform.domain.entity.KnowledgeDocument
import com.example.llmragplatform.domain.entity.KnowledgeDocumentAccessScope
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.domain.Sort

interface KnowledgeDocumentRepository : JpaRepository<KnowledgeDocument, Long> {
    fun findAllByAccessScopeIn(accessScopes: Collection<KnowledgeDocumentAccessScope>, sort: Sort): List<KnowledgeDocument>
    fun countByAccessScopeIn(accessScopes: Collection<KnowledgeDocumentAccessScope>): Long
}
