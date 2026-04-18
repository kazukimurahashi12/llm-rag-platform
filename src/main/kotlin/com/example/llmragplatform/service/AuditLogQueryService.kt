package com.example.llmragplatform.service

import com.example.llmragplatform.domain.entity.AuditLog
import com.example.llmragplatform.exception.ResourceNotFoundException
import com.example.llmragplatform.generated.model.AuditLogDetailResponse
import com.example.llmragplatform.generated.model.AuditLogListResponse
import com.example.llmragplatform.generated.model.AuditLogSummaryItem
import com.example.llmragplatform.infrastructure.repository.AuditLogRepository
import jakarta.persistence.criteria.Predicate
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Service
class AuditLogQueryService(
    private val auditLogRepository: AuditLogRepository,
    private val piiMaskingService: PiiMaskingService,
) {

    fun getAuditLogs(
        limit: Int,
        offset: Int,
        model: String?,
        from: OffsetDateTime?,
        to: OffsetDateTime?,
    ): AuditLogListResponse {
        val safeLimit = limit.coerceIn(1, 100)
        val safeOffset = offset.coerceAtLeast(0)
        val specification = buildSpecification(model, from?.toInstant(), to?.toInstant())
        val allMatchedLogs = auditLogRepository.findAll(specification, Sort.by(Sort.Direction.DESC, "createdAt"))
        val items = allMatchedLogs.drop(safeOffset).take(safeLimit).map(::toSummaryItem)

        return AuditLogListResponse()
            .items(items)
            .totalCount(allMatchedLogs.size.toLong())
            .limit(safeLimit)
            .offset(safeOffset)
    }

    fun getAuditLogDetail(auditLogId: Long, includeSensitiveContent: Boolean): AuditLogDetailResponse {
        val log = auditLogRepository.findById(auditLogId)
            .orElseThrow { ResourceNotFoundException("Audit log not found: $auditLogId") }

        return AuditLogDetailResponse()
            .id(log.id)
            .model(log.model)
            .prompt(toVisiblePrompt(log, includeSensitiveContent))
            .response(toVisibleResponse(log, includeSensitiveContent))
            .promptTokens(log.promptTokens)
            .completionTokens(log.completionTokens)
            .totalTokens(log.totalTokens)
            .costJpy(log.costJpy)
            .latencyMs(log.latencyMs)
            .createdAt(OffsetDateTime.ofInstant(log.createdAt, ZoneOffset.UTC))
    }

    private fun toSummaryItem(log: AuditLog): AuditLogSummaryItem {
        return AuditLogSummaryItem()
            .id(log.id)
            .model(log.model)
            .promptTokens(log.promptTokens)
            .completionTokens(log.completionTokens)
            .totalTokens(log.totalTokens)
            .costJpy(log.costJpy)
            .latencyMs(log.latencyMs)
            .createdAt(OffsetDateTime.ofInstant(log.createdAt, ZoneOffset.UTC))
    }

    private fun buildSpecification(
        model: String?,
        from: Instant?,
        to: Instant?,
    ): Specification<AuditLog> {
        return Specification { root, _, criteriaBuilder ->
            val predicates = mutableListOf<Predicate>()

            model?.takeIf { it.isNotBlank() }?.let {
                predicates += criteriaBuilder.equal(root.get<String>("model"), it)
            }
            from?.let {
                predicates += criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), it)
            }
            to?.let {
                predicates += criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), it)
            }

            criteriaBuilder.and(*predicates.toTypedArray())
        }
    }

    private fun toVisiblePrompt(log: AuditLog, includeSensitiveContent: Boolean): String {
        if (includeSensitiveContent) {
            return log.prompt
        }
        return abbreviateForOperator(piiMaskingService.maskText(log.prompt))
    }

    private fun toVisibleResponse(log: AuditLog, includeSensitiveContent: Boolean): String {
        if (includeSensitiveContent) {
            return log.response
        }
        return abbreviateForOperator(piiMaskingService.maskText(log.response))
    }

    private fun abbreviateForOperator(value: String, maxLength: Int = 80): String {
        return if (value.length <= maxLength) {
            value
        } else {
            value.take(maxLength) + "... [REDACTED]"
        }
    }
}
