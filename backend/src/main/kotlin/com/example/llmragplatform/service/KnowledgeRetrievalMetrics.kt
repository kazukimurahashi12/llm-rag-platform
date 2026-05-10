package com.example.llmragplatform.service

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Service

@Service
/**
 * retrieval の vector 採用状況や threshold fallback を記録するメトリクスサービス。
 */
class KnowledgeRetrievalMetrics(
    private val meterRegistry: MeterRegistry,
) {

    /**
     * vector 検索結果が最終採用された回数を記録する。
     */
    fun recordVectorAccepted() {
        // vector 検索結果が採用された件数 counter を増やす。
        counter(
            name = "knowledge.retrieval.vector.accepted",
            description = "Number of retrievals that used vector search results",
        ).increment()
    }

    /**
     * similarity threshold により除外された候補件数を記録する。
     *
     * @param count 除外した候補件数。
     */
    fun recordThresholdFiltered(count: Int) {
        if (count <= 0) {
            // 除外件数 0 の場合はメトリクス更新を省略する。
            return
        }

        // similarity threshold により除外した候補件数を加算する。
        counter(
            name = "knowledge.retrieval.vector.threshold.filtered",
            description = "Number of vector matches filtered out by similarity threshold",
        ).increment(count.toDouble())
    }

    /**
     * threshold 除外後に keyword fallback した回数を記録する。
     */
    fun recordThresholdFallback() {
        // threshold 除外後に keyword fallback した回数を増やす。
        counter(
            name = "knowledge.retrieval.vector.threshold.fallback",
            description = "Number of retrievals that fell back to keyword search after threshold filtering",
        ).increment()
    }

    /**
     * 指定名の Counter を取得する。
     *
     * @param name Counter 名。
     * @param description Counter 説明。
     * @return Counter インスタンス。
     */
    private fun counter(name: String, description: String): Counter {
        // 指定名の Counter を取得または登録して返す。
        return Counter.builder(name)
            .description(description)
            .register(meterRegistry)
    }
}
