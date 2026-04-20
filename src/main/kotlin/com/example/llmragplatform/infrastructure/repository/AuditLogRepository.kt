package com.example.llmragplatform.infrastructure.repository

import com.example.llmragplatform.domain.entity.AuditLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query

interface AuditLogRepository : JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {
    @Query("select avg(a.latencyMs) from AuditLog a")
    fun findAverageLatencyMs(): Double?

    @Query("select avg(a.costJpy) from AuditLog a")
    fun findAverageCostJpy(): Double?
}
