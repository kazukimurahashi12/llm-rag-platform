package com.example.llmragplatform.service

import com.example.llmragplatform.domain.entity.KnowledgeReindexJobStatus
import com.example.llmragplatform.generated.model.DashboardSummaryResponse
import com.example.llmragplatform.infrastructure.repository.AuditLogRepository
import com.example.llmragplatform.infrastructure.repository.KnowledgeReindexJobRepository
import org.springframework.stereotype.Service

@Service
class DashboardQueryService(
    private val auditLogRepository: AuditLogRepository,
    private val knowledgeReindexJobRepository: KnowledgeReindexJobRepository,
) {

    fun getSummary(): DashboardSummaryResponse {
        val totalAdviceRequests = auditLogRepository.count()
        val averageLatencyMs = auditLogRepository.findAverageLatencyMs() ?: 0.0
        val averageCostJpy = auditLogRepository.findAverageCostJpy() ?: 0.0
        val completedReindexJobs = knowledgeReindexJobRepository.countByStatus(KnowledgeReindexJobStatus.COMPLETED)
        val failedReindexJobs = knowledgeReindexJobRepository.countByStatus(KnowledgeReindexJobStatus.FAILED)
        val finishedJobs = completedReindexJobs + failedReindexJobs
        val reindexSuccessRate = if (finishedJobs == 0L) {
            0.0
        } else {
            completedReindexJobs.toDouble() / finishedJobs.toDouble()
        }

        return DashboardSummaryResponse()
            .totalAdviceRequests(totalAdviceRequests)
            .averageLatencyMs(averageLatencyMs)
            .averageCostJpy(averageCostJpy)
            .reindexSuccessRate(reindexSuccessRate)
            .completedReindexJobs(completedReindexJobs)
            .failedReindexJobs(failedReindexJobs)
    }
}
