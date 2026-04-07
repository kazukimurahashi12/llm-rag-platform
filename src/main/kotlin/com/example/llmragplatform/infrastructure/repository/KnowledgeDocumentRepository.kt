package com.example.llmragplatform.infrastructure.repository

import com.example.llmragplatform.domain.entity.KnowledgeDocument
import org.springframework.data.jpa.repository.JpaRepository

interface KnowledgeDocumentRepository : JpaRepository<KnowledgeDocument, Long>
