package com.example.llmragplatform.service

import com.example.llmragplatform.config.KnowledgeReindexJobProperties
import com.example.llmragplatform.domain.entity.KnowledgeReindexJobStatus
import com.example.llmragplatform.infrastructure.repository.KnowledgeReindexJobRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class KnowledgeReindexJobCleanupService(
    private val knowledgeReindexJobProperties: KnowledgeReindexJobProperties,
    private val knowledgeReindexJobRepository: KnowledgeReindexJobRepository,
    private val knowledgeReindexJobMetrics: KnowledgeReindexJobMetrics,
) {

    @Scheduled(fixedDelayString = "\${app.reindex-jobs.cleanup-interval-ms:3600000}")
    @Transactional
    fun purgeExpiredJobs() {
        if (!knowledgeReindexJobProperties.cleanupEnabled) {
            return
        }

        val cutoff = Instant.now().minus(knowledgeReindexJobProperties.retention)
        val deletedCount = knowledgeReindexJobRepository.deleteByStatusInAndCompletedAtBefore(
            statuses = listOf(KnowledgeReindexJobStatus.COMPLETED, KnowledgeReindexJobStatus.FAILED),
            completedAt = cutoff
        )
        knowledgeReindexJobMetrics.recordCleanupDeleted(deletedCount)
    }
}
