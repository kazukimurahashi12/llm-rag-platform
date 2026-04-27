package com.example.llmragplatform.service

import com.example.llmragplatform.domain.entity.KnowledgeReindexJobStatus
import com.example.llmragplatform.infrastructure.repository.KnowledgeReindexJobRepository
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.time.Instant

@Service
/**
 * 再インデックス job の実処理を非同期実行し、状態遷移を記録するサービス。
 */
class KnowledgeReindexJobAsyncService(
    private val knowledgeDocumentService: KnowledgeDocumentService,
    private val knowledgeReindexJobRepository: KnowledgeReindexJobRepository,
    private val knowledgeReindexJobMetrics: KnowledgeReindexJobMetrics,
) {

    @Async
    /**
     * 全文書対象の再インデックス job を非同期で実行する。
     *
     * @param jobId 実行対象の job ID。
     */
    fun executeAllDocumentsJob(jobId: String) {
        // 全文書再インデックス処理を共通の job 実行ラッパーへ渡す。
        runJob(jobId, scope = "all") {
            knowledgeDocumentService.reindexDocuments()
        }
    }

    @Async
    /**
     * 単一文書対象の再インデックス job を非同期で実行する。
     *
     * @param jobId 実行対象の job ID。
     * @param documentId 再インデックス対象の文書 ID。
     */
    fun executeSingleDocumentJob(jobId: String, documentId: Long) {
        // 単一文書再インデックス処理を共通の job 実行ラッパーへ渡す。
        runJob(jobId, scope = "document") {
            knowledgeDocumentService.reindexDocument(documentId)
        }
    }

    /**
     * job の状態を RUNNING / COMPLETED / FAILED へ更新しながら処理本体を実行する。
     *
     * @param jobId 実行対象の job ID。
     * @param scope メトリクスタグ用の実行スコープ。
     * @param block 実際の再インデックス処理本体。
     */
    private fun runJob(
        jobId: String,
        scope: String,
        block: () -> com.example.llmragplatform.generated.model.KnowledgeReindexResponse,
    ) {
        // 対象 job が消えていれば何もしない。
        val job = knowledgeReindexJobRepository.findById(jobId).orElse(null) ?: return
        // 実行開始時刻を記録し、job 状態を RUNNING へ更新する。
        val startedAt = Instant.now()
        job.status = KnowledgeReindexJobStatus.RUNNING
        job.startedAt = startedAt
        knowledgeReindexJobRepository.save(job)

        try {
            // 実際の再インデックス処理を実行する。
            val result = block()
            // 正常終了時は COMPLETED と結果情報を保存する。
            val completedAt = Instant.now()
            job.status = KnowledgeReindexJobStatus.COMPLETED
            job.completedAt = completedAt
            job.documentsProcessed = result.documentsProcessed
            job.chunksProcessed = result.chunksProcessed
            job.embeddingsUpdated = result.embeddingsUpdated
            job.vectorSearchEnabled = result.vectorSearchEnabled
            job.errorMessage = null
            knowledgeReindexJobRepository.save(job)
            knowledgeReindexJobMetrics.recordCompleted(scope, java.time.Duration.between(startedAt, completedAt))
        } catch (ex: Exception) {
            // 例外発生時は FAILED とエラーメッセージを保存する。
            val completedAt = Instant.now()
            job.status = KnowledgeReindexJobStatus.FAILED
            job.completedAt = completedAt
            job.errorMessage = ex.message ?: "reindex job failed"
            knowledgeReindexJobRepository.save(job)
            knowledgeReindexJobMetrics.recordFailed(scope, java.time.Duration.between(startedAt, completedAt))
        }
    }
}
