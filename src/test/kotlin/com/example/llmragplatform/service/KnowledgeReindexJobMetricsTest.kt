package com.example.llmragplatform.service

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration

class KnowledgeReindexJobMetricsTest {

    private val meterRegistry = SimpleMeterRegistry()
    private val metrics = KnowledgeReindexJobMetrics(meterRegistry)

    @Test
    fun `records accepted retried and deleted counters`() {
        metrics.recordAccepted(scope = "all", trigger = "initial")
        metrics.recordAccepted(scope = "document", trigger = "retry")
        metrics.recordRetried(scope = "document")
        metrics.recordDeleted(scope = "all")

        assertEquals(
            1.0,
            meterRegistry.get("knowledge.reindex.jobs.accepted")
                .tag("scope", "all")
                .tag("trigger", "initial")
                .counter()
                .count()
        )
        assertEquals(
            1.0,
            meterRegistry.get("knowledge.reindex.jobs.accepted")
                .tag("scope", "document")
                .tag("trigger", "retry")
                .counter()
                .count()
        )
        assertEquals(
            1.0,
            meterRegistry.get("knowledge.reindex.jobs.retried")
                .tag("scope", "document")
                .counter()
                .count()
        )
        assertEquals(
            1.0,
            meterRegistry.get("knowledge.reindex.jobs.deleted")
                .tag("scope", "all")
                .counter()
                .count()
        )
    }

    @Test
    fun `records completed and failed execution metrics`() {
        metrics.recordCompleted(scope = "all", duration = Duration.ofMillis(120))
        metrics.recordFailed(scope = "document", duration = Duration.ofMillis(250))

        assertEquals(
            1.0,
            meterRegistry.get("knowledge.reindex.jobs.completed")
                .tag("scope", "all")
                .counter()
                .count()
        )
        assertEquals(
            1.0,
            meterRegistry.get("knowledge.reindex.jobs.failed")
                .tag("scope", "document")
                .counter()
                .count()
        )
        assertEquals(
            1L,
            meterRegistry.get("knowledge.reindex.jobs.execution")
                .tag("scope", "all")
                .tag("status", "completed")
                .timer()
                .count()
        )
        assertEquals(
            1L,
            meterRegistry.get("knowledge.reindex.jobs.execution")
                .tag("scope", "document")
                .tag("status", "failed")
                .timer()
                .count()
        )
    }

    @Test
    fun `records cleanup deleted counter only when positive`() {
        metrics.recordCleanupDeleted(0)
        metrics.recordCleanupDeleted(3)

        assertEquals(
            3.0,
            meterRegistry.get("knowledge.reindex.jobs.cleanup.deleted")
                .counter()
                .count()
        )
    }
}
