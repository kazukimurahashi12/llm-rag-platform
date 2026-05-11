package com.example.llmragplatform.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "audit_logs")
class AuditLog(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(nullable = false)
    val model: String,
    @Column(columnDefinition = "TEXT", nullable = false)
    val prompt: String,
    @Column(columnDefinition = "TEXT", nullable = false)
    val response: String,
    @Column(nullable = false)
    val promptTokens: Int,
    @Column(nullable = false)
    val completionTokens: Int,
    @Column(nullable = false)
    val totalTokens: Int,
    @Column(nullable = false)
    val costJpy: Double,
    @Column(nullable = false)
    val latencyMs: Long,
    @Column(nullable = false)
    val groundednessScore: Double,
    @Column(nullable = false)
    val groundednessStatus: String,
    @Column(columnDefinition = "TEXT", nullable = false)
    val groundednessReason: String,
    @Column(nullable = false)
    val groundednessFallbackApplied: Boolean,
    @Column(nullable = false)
    val createdAt: Instant = Instant.now(),
)
