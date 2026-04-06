package com.example.llmragplatform.controller

import com.example.llmragplatform.generated.api.AuditApi
import com.example.llmragplatform.generated.model.AuditLogDetailResponse
import com.example.llmragplatform.generated.model.AuditLogListResponse
import com.example.llmragplatform.service.AuditLogQueryService
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime

@RestController
class AuditLogController(
    private val auditLogQueryService: AuditLogQueryService,
) : AuditApi {

    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    override fun getAuditLogs(
        limit: Int?,
        offset: Int?,
        model: String?,
        from: OffsetDateTime?,
        to: OffsetDateTime?,
    ): ResponseEntity<AuditLogListResponse> {
        return ResponseEntity.ok(
            auditLogQueryService.getAuditLogs(
                limit = limit ?: 20,
                offset = offset ?: 0,
                model = model,
                from = from,
                to = to
            )
        )
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    override fun getAuditLogDetail(auditLogId: Long): ResponseEntity<AuditLogDetailResponse> {
        val authentication = SecurityContextHolder.getContext().authentication
        val isAdmin = authentication.authorities.any { it.authority == "ROLE_ADMIN" }
        return ResponseEntity.ok(auditLogQueryService.getAuditLogDetail(auditLogId, isAdmin))
    }
}
