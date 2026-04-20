package com.example.llmragplatform.infrastructure.repository

import com.example.llmragplatform.domain.entity.KnowledgeReindexJob
import com.example.llmragplatform.domain.entity.KnowledgeReindexJobStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

interface KnowledgeReindexJobRepository : JpaRepository<KnowledgeReindexJob, String>, KnowledgeReindexJobRepositoryCustom {
    fun deleteByStatusInAndCompletedAtBefore(statuses: Collection<KnowledgeReindexJobStatus>, completedAt: Instant): Long
    fun countByStatus(status: KnowledgeReindexJobStatus): Long
}
