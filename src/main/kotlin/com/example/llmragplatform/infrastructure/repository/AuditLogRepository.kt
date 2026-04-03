package com.example.llmragplatform.infrastructure.repository

import com.example.llmragplatform.domain.entity.AuditLog
import org.springframework.data.jpa.repository.JpaRepository

interface AuditLogRepository : JpaRepository<AuditLog, Long>
