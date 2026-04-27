package com.example.llmragplatform.service

import com.example.llmragplatform.domain.entity.KnowledgeReindexJobStatus
import com.example.llmragplatform.domain.entity.KnowledgeDocumentAccessScope
import com.example.llmragplatform.generated.model.DashboardSummaryResponse
import com.example.llmragplatform.infrastructure.repository.AuditLogRepository
import com.example.llmragplatform.infrastructure.repository.KnowledgeDocumentChunkRepository
import com.example.llmragplatform.infrastructure.repository.KnowledgeDocumentRepository
import com.example.llmragplatform.infrastructure.repository.KnowledgeReindexJobRepository
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Service

@Service
/**
 * ダッシュボード画面に表示する KPI を集計して返すサービス。
 */
class DashboardQueryService(
    private val auditLogRepository: AuditLogRepository,
    private val knowledgeDocumentRepository: KnowledgeDocumentRepository,
    private val knowledgeDocumentChunkRepository: KnowledgeDocumentChunkRepository,
    private val knowledgeReindexJobRepository: KnowledgeReindexJobRepository,
    private val meterRegistry: MeterRegistry,
) {

    /**
     * 監査ログ、ナレッジ、reindex job、メトリクスからダッシュボード集計値を生成する。
     *
     * @return ダッシュボード表示用の集計レスポンス。
     */
    fun getSummary(): DashboardSummaryResponse {
        // 監査ログから利用件数と平均レイテンシ・平均コストを集計する。
        val totalAdviceRequests = auditLogRepository.count()
        val averageLatencyMs = auditLogRepository.findAverageLatencyMs() ?: 0.0
        val averageCostJpy = auditLogRepository.findAverageCostJpy() ?: 0.0
        // reindex job を状態別に集計する。
        val queuedReindexJobs = knowledgeReindexJobRepository.countByStatus(KnowledgeReindexJobStatus.QUEUED)
        val runningReindexJobs = knowledgeReindexJobRepository.countByStatus(KnowledgeReindexJobStatus.RUNNING)
        val completedReindexJobs = knowledgeReindexJobRepository.countByStatus(KnowledgeReindexJobStatus.COMPLETED)
        val failedReindexJobs = knowledgeReindexJobRepository.countByStatus(KnowledgeReindexJobStatus.FAILED)
        // 完了済み job のうち成功した割合を成功率として計算する。
        val finishedJobs = completedReindexJobs + failedReindexJobs
        val reindexSuccessRate = if (finishedJobs == 0L) {
            0.0
        } else {
            completedReindexJobs.toDouble() / finishedJobs.toDouble()
        }
        // ナレッジ文書数と ACL ごとの内訳を集計する。
        val totalKnowledgeDocuments = knowledgeDocumentRepository.count()
        val sharedKnowledgeDocuments = knowledgeDocumentRepository.countByAccessScopeIn(
            listOf(KnowledgeDocumentAccessScope.SHARED),
        )
        val restrictedKnowledgeDocuments = totalKnowledgeDocuments - sharedKnowledgeDocuments

        // DB 集計値とメトリクス値を 1 つのダッシュボードレスポンスへまとめる。
        return DashboardSummaryResponse()
            .totalAdviceRequests(totalAdviceRequests)
            .averageLatencyMs(averageLatencyMs)
            .averageCostJpy(averageCostJpy)
            .reindexSuccessRate(reindexSuccessRate)
            .completedReindexJobs(completedReindexJobs)
            .failedReindexJobs(failedReindexJobs)
            .queuedReindexJobs(queuedReindexJobs)
            .runningReindexJobs(runningReindexJobs)
            .totalReindexJobs(knowledgeReindexJobRepository.count())
            .totalKnowledgeDocuments(totalKnowledgeDocuments)
            .totalKnowledgeChunks(knowledgeDocumentChunkRepository.count())
            .sharedKnowledgeDocuments(sharedKnowledgeDocuments)
            .restrictedKnowledgeDocuments(restrictedKnowledgeDocuments)
            .vectorAcceptedRetrievals(counterValue("knowledge.retrieval.vector.accepted"))
            .vectorThresholdFallbacks(counterValue("knowledge.retrieval.vector.threshold.fallback"))
            .vectorThresholdFilteredChunks(counterValue("knowledge.retrieval.vector.threshold.filtered"))
    }

    /**
     * MeterRegistry から counter 値を取得し、存在しない場合は 0.0 を返す。
     *
     * @param name 取得したい counter 名。
     * @return counter の現在値。
     */
    private fun counterValue(name: String): Double {
        // 指定名の counter が存在しない場合は 0.0 を返す。
        return meterRegistry.find(name).counter()?.count() ?: 0.0
    }
}
