package com.example.llmragplatform.service

import com.example.llmragplatform.domain.entity.AuditLog
import com.example.llmragplatform.infrastructure.repository.AuditLogRepository
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
class AuditLogAsyncService(
    private val auditLogRepository: AuditLogRepository,
) {

    @Async
    fun save(
        model: String,
        prompt: String,
        response: String,
        promptTokens: Int,
        completionTokens: Int,
        totalTokens: Int,
        costJpy: Double,
        latencyMs: Long,
    ) {
        auditLogRepository.save(
            AuditLog(
                model = model,
                prompt = prompt,
                response = response,
                promptTokens = promptTokens,
                completionTokens = completionTokens,
                totalTokens = totalTokens,
                costJpy = costJpy,
                latencyMs = latencyMs
            )
        )
    }
}
