package com.example.llmragplatform.service

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Service
import java.time.Duration

@Service
/**
 * 再インデックス job に関する Micrometer メトリクス記録を担当するサービス。
 */
class KnowledgeReindexJobMetrics(
    private val meterRegistry: MeterRegistry,
) {

    /**
     * job 受付件数を記録する。
     *
     * @param scope all または document の実行スコープ。
     * @param trigger initial や retry などの受付契機。
     */
    fun recordAccepted(scope: String, trigger: String) {
        // 受付件数 counter を scope / trigger タグつきで増やす。
        counter(
            name = "knowledge.reindex.jobs.accepted",
            description = "Number of accepted knowledge reindex jobs",
            tags = arrayOf("scope", scope, "trigger", trigger)
        ).increment()
    }

    /**
     * job 再試行件数を記録する。
     *
     * @param scope all または document の実行スコープ。
     */
    fun recordRetried(scope: String) {
        // 再試行件数 counter を scope タグつきで増やす。
        counter(
            name = "knowledge.reindex.jobs.retried",
            description = "Number of retried knowledge reindex jobs",
            tags = arrayOf("scope", scope)
        ).increment()
    }

    /**
     * job 削除件数を記録する。
     *
     * @param scope all または document の実行スコープ。
     */
    fun recordDeleted(scope: String) {
        // 削除件数 counter を scope タグつきで増やす。
        counter(
            name = "knowledge.reindex.jobs.deleted",
            description = "Number of deleted knowledge reindex jobs",
            tags = arrayOf("scope", scope)
        ).increment()
    }

    /**
     * job 完了件数と実行時間を記録する。
     *
     * @param scope all または document の実行スコープ。
     * @param duration job 実行時間。
     */
    fun recordCompleted(scope: String, duration: Duration) {
        // 完了件数 counter を増やし、実行時間 timer へ記録する。
        counter(
            name = "knowledge.reindex.jobs.completed",
            description = "Number of completed knowledge reindex jobs",
            tags = arrayOf("scope", scope)
        ).increment()
        timer(scope, "completed").record(duration)
    }

    /**
     * job 失敗件数と実行時間を記録する。
     *
     * @param scope all または document の実行スコープ。
     * @param duration job 実行時間。
     */
    fun recordFailed(scope: String, duration: Duration) {
        // 失敗件数 counter を増やし、実行時間 timer へ記録する。
        counter(
            name = "knowledge.reindex.jobs.failed",
            description = "Number of failed knowledge reindex jobs",
            tags = arrayOf("scope", scope)
        ).increment()
        timer(scope, "failed").record(duration)
    }

    /**
     * 定期クリーンアップで削除した job 件数を記録する。
     *
     * @param count 削除した job 件数。
     */
    fun recordCleanupDeleted(count: Long) {
        if (count <= 0) {
            // 削除件数 0 のときはメトリクス更新を省略する。
            return
        }

        // クリーンアップ削除件数を counter へ加算する。
        counter(
            name = "knowledge.reindex.jobs.cleanup.deleted",
            description = "Number of cleaned up knowledge reindex jobs",
            tags = emptyArray()
        ).increment(count.toDouble())
    }

    /**
     * scope と status タグつきの job 実行時間 Timer を取得する。
     *
     * @param scope all または document の実行スコープ。
     * @param status completed または failed の実行結果。
     * @return 実行時間記録用 Timer。
     */
    private fun timer(scope: String, status: String): Timer {
        // scope / status タグつきの実行時間 timer を取得または登録する。
        return Timer.builder("knowledge.reindex.jobs.execution")
            .description("Execution time of knowledge reindex jobs")
            .tags("scope", scope, "status", status)
            .register(meterRegistry)
    }

    /**
     * 名前、説明、タグを指定して Counter を取得する。
     *
     * @param name Counter 名。
     * @param description Counter 説明。
     * @param tags Counter に付けるタグ配列。
     * @return Counter インスタンス。
     */
    private fun counter(name: String, description: String, tags: Array<String>): Counter {
        // 指定名・説明・タグで Counter を取得または登録する。
        return Counter.builder(name)
            .description(description)
            .tags(*tags)
            .register(meterRegistry)
    }
}
