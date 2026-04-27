package com.example.llmragplatform.service

import com.example.llmragplatform.config.KnowledgeReindexJobProperties
import com.example.llmragplatform.domain.entity.KnowledgeReindexJobStatus
import com.example.llmragplatform.infrastructure.repository.KnowledgeReindexJobRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
/**
 * 保持期限を過ぎた再インデックス job を定期削除するサービス。
 */
class KnowledgeReindexJobCleanupService(
    private val knowledgeReindexJobProperties: KnowledgeReindexJobProperties,
    private val knowledgeReindexJobRepository: KnowledgeReindexJobRepository,
    private val knowledgeReindexJobMetrics: KnowledgeReindexJobMetrics,
) {

    @Scheduled(fixedDelayString = "\${app.reindex-jobs.cleanup-interval-ms:3600000}")
    @Transactional
    /**
     * COMPLETED / FAILED の古い job を削除し、削除件数メトリクスを記録する。
     *
     * @return なし。
     */
    fun purgeExpiredJobs() {
        if (!knowledgeReindexJobProperties.cleanupEnabled) {
            // クリーンアップ無効時は何もしない。
            return
        }

        // 保持期限より古い completed / failed job の削除境界を作る。
        val cutoff = Instant.now().minus(knowledgeReindexJobProperties.retention)
        // 期限切れ job をまとめて削除する。
        val deletedCount = knowledgeReindexJobRepository.deleteByStatusInAndCompletedAtBefore(
            statuses = listOf(KnowledgeReindexJobStatus.COMPLETED, KnowledgeReindexJobStatus.FAILED),
            completedAt = cutoff
        )
        // 削除件数をメトリクスへ反映する。
        knowledgeReindexJobMetrics.recordCleanupDeleted(deletedCount)
    }
}
