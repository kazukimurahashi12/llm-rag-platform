package com.example.llmragplatform.controller

import com.example.llmragplatform.generated.api.DashboardApi
import com.example.llmragplatform.generated.model.DashboardSummaryResponse
import com.example.llmragplatform.service.DashboardQueryService
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.RestController

@RestController
class DashboardController(
    private val dashboardQueryService: DashboardQueryService,
) : DashboardApi {

    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    override fun getDashboardSummary(): ResponseEntity<DashboardSummaryResponse> {
        return ResponseEntity.ok(dashboardQueryService.getSummary())
    }
}
