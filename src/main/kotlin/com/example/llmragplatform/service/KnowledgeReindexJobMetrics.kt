package com.example.llmragplatform.service

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class KnowledgeReindexJobMetrics(
    private val meterRegistry: MeterRegistry,
) {

    fun recordAccepted(scope: String, trigger: String) {
        counter(
            name = "knowledge.reindex.jobs.accepted",
            description = "Number of accepted knowledge reindex jobs",
            tags = arrayOf("scope", scope, "trigger", trigger)
        ).increment()
    }

    fun recordRetried(scope: String) {
        counter(
            name = "knowledge.reindex.jobs.retried",
            description = "Number of retried knowledge reindex jobs",
            tags = arrayOf("scope", scope)
        ).increment()
    }

    fun recordDeleted(scope: String) {
        counter(
            name = "knowledge.reindex.jobs.deleted",
            description = "Number of deleted knowledge reindex jobs",
            tags = arrayOf("scope", scope)
        ).increment()
    }

    fun recordCompleted(scope: String, duration: Duration) {
        counter(
            name = "knowledge.reindex.jobs.completed",
            description = "Number of completed knowledge reindex jobs",
            tags = arrayOf("scope", scope)
        ).increment()
        timer(scope, "completed").record(duration)
    }

    fun recordFailed(scope: String, duration: Duration) {
        counter(
            name = "knowledge.reindex.jobs.failed",
            description = "Number of failed knowledge reindex jobs",
            tags = arrayOf("scope", scope)
        ).increment()
        timer(scope, "failed").record(duration)
    }

    fun recordCleanupDeleted(count: Long) {
        if (count <= 0) {
            return
        }

        counter(
            name = "knowledge.reindex.jobs.cleanup.deleted",
            description = "Number of cleaned up knowledge reindex jobs",
            tags = emptyArray()
        ).increment(count.toDouble())
    }

    private fun timer(scope: String, status: String): Timer {
        return Timer.builder("knowledge.reindex.jobs.execution")
            .description("Execution time of knowledge reindex jobs")
            .tags("scope", scope, "status", status)
            .register(meterRegistry)
    }

    private fun counter(name: String, description: String, tags: Array<String>): Counter {
        return Counter.builder(name)
            .description(description)
            .tags(*tags)
            .register(meterRegistry)
    }
}
