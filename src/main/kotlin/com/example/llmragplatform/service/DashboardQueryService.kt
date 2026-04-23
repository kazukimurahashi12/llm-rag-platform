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
class DashboardQueryService(
    private val auditLogRepository: AuditLogRepository,
    private val knowledgeDocumentRepository: KnowledgeDocumentRepository,
    private val knowledgeDocumentChunkRepository: KnowledgeDocumentChunkRepository,
    private val knowledgeReindexJobRepository: KnowledgeReindexJobRepository,
    private val meterRegistry: MeterRegistry,
) {

    fun getSummary(): DashboardSummaryResponse {
        val totalAdviceRequests = auditLogRepository.count()
        val averageLatencyMs = auditLogRepository.findAverageLatencyMs() ?: 0.0
        val averageCostJpy = auditLogRepository.findAverageCostJpy() ?: 0.0
        val queuedReindexJobs = knowledgeReindexJobRepository.countByStatus(KnowledgeReindexJobStatus.QUEUED)
        val runningReindexJobs = knowledgeReindexJobRepository.countByStatus(KnowledgeReindexJobStatus.RUNNING)
        val completedReindexJobs = knowledgeReindexJobRepository.countByStatus(KnowledgeReindexJobStatus.COMPLETED)
        val failedReindexJobs = knowledgeReindexJobRepository.countByStatus(KnowledgeReindexJobStatus.FAILED)
        val finishedJobs = completedReindexJobs + failedReindexJobs
        val reindexSuccessRate = if (finishedJobs == 0L) {
            0.0
        } else {
            completedReindexJobs.toDouble() / finishedJobs.toDouble()
        }
        val totalKnowledgeDocuments = knowledgeDocumentRepository.count()
        val sharedKnowledgeDocuments = knowledgeDocumentRepository.countByAccessScopeIn(
            listOf(KnowledgeDocumentAccessScope.SHARED),
        )
        val restrictedKnowledgeDocuments = totalKnowledgeDocuments - sharedKnowledgeDocuments

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

    private fun counterValue(name: String): Double {
        return meterRegistry.find(name).counter()?.count() ?: 0.0
    }
}
