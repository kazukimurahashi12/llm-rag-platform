package com.example.llmragplatform.service

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Service

@Service
class KnowledgeRetrievalMetrics(
    private val meterRegistry: MeterRegistry,
) {

    fun recordVectorAccepted() {
        counter(
            name = "knowledge.retrieval.vector.accepted",
            description = "Number of retrievals that used vector search results",
        ).increment()
    }

    fun recordThresholdFiltered(count: Int) {
        if (count <= 0) {
            return
        }

        counter(
            name = "knowledge.retrieval.vector.threshold.filtered",
            description = "Number of vector matches filtered out by similarity threshold",
        ).increment(count.toDouble())
    }

    fun recordThresholdFallback() {
        counter(
            name = "knowledge.retrieval.vector.threshold.fallback",
            description = "Number of retrievals that fell back to keyword search after threshold filtering",
        ).increment()
    }

    private fun counter(name: String, description: String): Counter {
        return Counter.builder(name)
            .description(description)
            .register(meterRegistry)
    }
}
