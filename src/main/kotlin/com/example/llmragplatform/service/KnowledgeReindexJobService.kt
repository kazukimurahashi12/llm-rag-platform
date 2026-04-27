package com.example.llmragplatform.service

import com.example.llmragplatform.domain.entity.KnowledgeReindexJob
import com.example.llmragplatform.domain.entity.KnowledgeReindexJobStatus
import com.example.llmragplatform.exception.ResourceNotFoundException
import com.example.llmragplatform.generated.model.KnowledgeReindexJobAcceptedResponse
import com.example.llmragplatform.generated.model.KnowledgeReindexJobListResponse
import com.example.llmragplatform.generated.model.KnowledgeReindexJobStatusResponse
import com.example.llmragplatform.generated.model.KnowledgeReindexResponse
import com.example.llmragplatform.infrastructure.repository.KnowledgeDocumentRepository
import com.example.llmragplatform.infrastructure.repository.KnowledgeReindexJobRepository
import com.example.llmragplatform.infrastructure.repository.KnowledgeReindexJobSortBy
import com.example.llmragplatform.infrastructure.repository.KnowledgeReindexJobSortDirection
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Service
/**
 * 再インデックス job の受付、一覧取得、削除、再試行を管理するサービス。
 */
class KnowledgeReindexJobService(
    private val knowledgeDocumentRepository: KnowledgeDocumentRepository,
    private val knowledgeReindexJobRepository: KnowledgeReindexJobRepository,
    private val knowledgeReindexJobAsyncService: KnowledgeReindexJobAsyncService,
    private val knowledgeReindexJobMetrics: KnowledgeReindexJobMetrics,
) {

    /**
     * 全文書対象の再インデックス job を受け付ける。
     *
     * @return 受付済み job の基本情報レスポンス。
     */
    fun submitAllDocumentsJob(): KnowledgeReindexJobAcceptedResponse {
        // 全文書対象の新規 job を QUEUED 状態で保存する。
        val job = knowledgeReindexJobRepository.save(
            KnowledgeReindexJob(
                jobId = UUID.randomUUID().toString(),
                status = KnowledgeReindexJobStatus.QUEUED,
                acceptedAt = Instant.now(),
                knowledgeDocumentId = null
            )
        )
        // 受付件数メトリクスを記録し、非同期実行を開始する。
        knowledgeReindexJobMetrics.recordAccepted(scope = "all", trigger = "initial")
        knowledgeReindexJobAsyncService.executeAllDocumentsJob(job.jobId)
        return toAcceptedResponse(job)
    }

    /**
     * 単一文書対象の再インデックス job を受け付ける。
     *
     * @param documentId 対象文書 ID。
     * @return 受付済み job の基本情報レスポンス。
     */
    fun submitSingleDocumentJob(documentId: Long): KnowledgeReindexJobAcceptedResponse {
        if (!knowledgeDocumentRepository.existsById(documentId)) {
            throw ResourceNotFoundException("Knowledge document not found: $documentId")
        }

        // 単一文書対象の新規 job を QUEUED 状態で保存する。
        val job = knowledgeReindexJobRepository.save(
            KnowledgeReindexJob(
                jobId = UUID.randomUUID().toString(),
                status = KnowledgeReindexJobStatus.QUEUED,
                acceptedAt = Instant.now(),
                knowledgeDocumentId = documentId
            )
        )
        // 受付件数メトリクスを記録し、非同期実行を開始する。
        knowledgeReindexJobMetrics.recordAccepted(scope = "document", trigger = "initial")
        knowledgeReindexJobAsyncService.executeSingleDocumentJob(job.jobId, documentId)
        return toAcceptedResponse(job)
    }

    /**
     * job ID を指定して現在状態を取得する。
     *
     * @param jobId 取得したい job ID。
     * @return job の状態と実行結果を含むレスポンス。
     */
    fun getJob(jobId: String): KnowledgeReindexJobStatusResponse {
        // 指定 job を取得し、状態表示用レスポンスへ変換する。
        val job = knowledgeReindexJobRepository.findById(jobId)
            .orElseThrow { ResourceNotFoundException("Knowledge reindex job not found: $jobId") }
        return toStatusResponse(job)
    }

    /**
     * 条件付きで reindex job 一覧を検索し、ページング済みレスポンスへ変換する。
     *
     * @param limit 取得件数上限。
     * @param offset 取得開始位置。
     * @param status job 状態での絞り込み条件。
     * @param knowledgeDocumentId 文書 ID での絞り込み条件。
     * @param acceptedFrom acceptedAt の下限。
     * @param acceptedTo acceptedAt の上限。
     * @param completedFrom completedAt の下限。
     * @param completedTo completedAt の上限。
     * @param sortBy ソート項目。
     * @param sortDirection ソート方向。
     * @return 条件に一致する job 一覧レスポンス。
     */
    fun getJobs(
        limit: Int,
        offset: Int,
        status: String?,
        knowledgeDocumentId: Long?,
        acceptedFrom: OffsetDateTime?,
        acceptedTo: OffsetDateTime?,
        completedFrom: OffsetDateTime?,
        completedTo: OffsetDateTime?,
        sortBy: String?,
        sortDirection: String?,
    ): KnowledgeReindexJobListResponse {
        // limit / offset を API 許容値へ補正する。
        val safeLimit = limit.coerceIn(1, 100)
        val safeOffset = offset.coerceAtLeast(0)
        // 文字列指定の状態条件を enum へ正規化する。
        val normalizedStatus = status?.takeIf { it.isNotBlank() }?.let { statusValue ->
            runCatching { KnowledgeReindexJobStatus.valueOf(statusValue) }
                .getOrElse { throw IllegalArgumentException("Invalid reindex job status: $statusValue") }
        }
        // 日時条件を Instant へ変換して扱いやすくする。
        val acceptedFromInstant = acceptedFrom?.toInstant()
        val acceptedToInstant = acceptedTo?.toInstant()
        val completedFromInstant = completedFrom?.toInstant()
        val completedToInstant = completedTo?.toInstant()
        // ソート項目と方向を内部 enum へ正規化する。
        val normalizedSortBy = when (sortBy?.takeIf { it.isNotBlank() } ?: "acceptedAt") {
            "acceptedAt" -> KnowledgeReindexJobSortBy.ACCEPTED_AT
            "completedAt" -> KnowledgeReindexJobSortBy.COMPLETED_AT
            else -> throw IllegalArgumentException("Invalid reindex job sortBy: $sortBy")
        }
        val normalizedSortDirection = when ((sortDirection?.takeIf { it.isNotBlank() } ?: "desc").lowercase()) {
            "asc" -> KnowledgeReindexJobSortDirection.ASC
            "desc" -> KnowledgeReindexJobSortDirection.DESC
            else -> throw IllegalArgumentException("Invalid reindex job sortDirection: $sortDirection")
        }

        // 日時範囲の前後関係が不正ならエラーにする。
        if (acceptedFromInstant != null && acceptedToInstant != null && acceptedFromInstant.isAfter(acceptedToInstant)) {
            throw IllegalArgumentException("acceptedFrom must be earlier than or equal to acceptedTo")
        }
        if (completedFromInstant != null && completedToInstant != null && completedFromInstant.isAfter(completedToInstant)) {
            throw IllegalArgumentException("completedFrom must be earlier than or equal to completedTo")
        }

        // 条件に一致する job 一覧と総件数を repository から取得する。
        val searchResult = knowledgeReindexJobRepository.search(
            limit = safeLimit,
            offset = safeOffset,
            status = normalizedStatus,
            knowledgeDocumentId = knowledgeDocumentId,
            acceptedFrom = acceptedFromInstant,
            acceptedTo = acceptedToInstant,
            completedFrom = completedFromInstant,
            completedTo = completedToInstant,
            sortBy = normalizedSortBy,
            sortDirection = normalizedSortDirection,
        )
        // 各 job を API レスポンスへ変換する。
        val items = searchResult.items.map(::toStatusResponse)

        return KnowledgeReindexJobListResponse()
            .items(items)
            .totalCount(searchResult.totalCount)
            .limit(safeLimit)
            .offset(safeOffset)
    }

    @Transactional
    /**
     * 完了済みまたは失敗済みの job を削除する。
     *
     * @param jobId 削除対象の job ID。
     */
    fun deleteJob(jobId: String) {
        // 削除対象 job を取得し、なければ 404 用例外を投げる。
        val job = knowledgeReindexJobRepository.findById(jobId)
            .orElseThrow { ResourceNotFoundException("Knowledge reindex job not found: $jobId") }

        if (job.status == KnowledgeReindexJobStatus.QUEUED || job.status == KnowledgeReindexJobStatus.RUNNING) {
            // 実行中 job の削除は整合性維持のため禁止する。
            throw IllegalArgumentException("Knowledge reindex job cannot be deleted while active: $jobId")
        }

        // 削除実行後に件数メトリクスを記録する。
        knowledgeReindexJobRepository.delete(job)
        knowledgeReindexJobMetrics.recordDeleted(scope = job.scope())
    }

    /**
     * FAILED 状態の job をもとに再試行 job を新規受付する。
     *
     * @param jobId 再試行元の job ID。
     * @return 新しく受付した job の基本情報レスポンス。
     */
    fun retryJob(jobId: String): KnowledgeReindexJobAcceptedResponse {
        // 再試行元 job を取得し、なければ 404 用例外を投げる。
        val job = knowledgeReindexJobRepository.findById(jobId)
            .orElseThrow { ResourceNotFoundException("Knowledge reindex job not found: $jobId") }

        if (job.status != KnowledgeReindexJobStatus.FAILED) {
            // FAILED 以外は再試行対象にできない。
            throw IllegalArgumentException("Only failed reindex jobs can be retried: $jobId")
        }

        // 再試行メトリクスを記録し、元 job の対象範囲に応じて新規受付する。
        knowledgeReindexJobMetrics.recordRetried(scope = job.scope())
        return if (job.knowledgeDocumentId == null) {
            submitAllDocumentsJob(trigger = "retry")
        } else {
            submitSingleDocumentJob(job.knowledgeDocumentId, trigger = "retry")
        }
    }

    /**
     * 全文書対象の job を指定 trigger 付きで新規作成して実行する。
     *
     * @param trigger initial や retry などの受付契機。
     * @return 受付済み job の基本情報レスポンス。
     */
    private fun submitAllDocumentsJob(trigger: String): KnowledgeReindexJobAcceptedResponse {
        // trigger 情報つきで全文書対象の再試行 job を保存する。
        val job = knowledgeReindexJobRepository.save(
            KnowledgeReindexJob(
                jobId = UUID.randomUUID().toString(),
                status = KnowledgeReindexJobStatus.QUEUED,
                acceptedAt = Instant.now(),
                knowledgeDocumentId = null
            )
        )
        // 受付メトリクスを記録し、非同期実行を開始する。
        knowledgeReindexJobMetrics.recordAccepted(scope = "all", trigger = trigger)
        knowledgeReindexJobAsyncService.executeAllDocumentsJob(job.jobId)
        return toAcceptedResponse(job)
    }

    /**
     * 単一文書対象の job を指定 trigger 付きで新規作成して実行する。
     *
     * @param documentId 対象文書 ID。
     * @param trigger initial や retry などの受付契機。
     * @return 受付済み job の基本情報レスポンス。
     */
    private fun submitSingleDocumentJob(documentId: Long, trigger: String): KnowledgeReindexJobAcceptedResponse {
        if (!knowledgeDocumentRepository.existsById(documentId)) {
            throw ResourceNotFoundException("Knowledge document not found: $documentId")
        }

        // trigger 情報つきで単一文書対象の再試行 job を保存する。
        val job = knowledgeReindexJobRepository.save(
            KnowledgeReindexJob(
                jobId = UUID.randomUUID().toString(),
                status = KnowledgeReindexJobStatus.QUEUED,
                acceptedAt = Instant.now(),
                knowledgeDocumentId = documentId
            )
        )
        // 受付メトリクスを記録し、非同期実行を開始する。
        knowledgeReindexJobMetrics.recordAccepted(scope = "document", trigger = trigger)
        knowledgeReindexJobAsyncService.executeSingleDocumentJob(job.jobId, documentId)
        return toAcceptedResponse(job)
    }

    /**
     * job エンティティを受付レスポンスへ変換する。
     *
     * @param job 変換元の job エンティティ。
     * @return 受付済み job の基本情報レスポンス。
     */
    private fun toAcceptedResponse(job: KnowledgeReindexJob): KnowledgeReindexJobAcceptedResponse {
        // 受付直後に返す最小限の job 情報へ変換する。
        return KnowledgeReindexJobAcceptedResponse()
            .jobId(job.jobId)
            .status(job.status.name)
            .acceptedAt(job.acceptedAt.atOffset(ZoneOffset.UTC))
    }

    /**
     * job エンティティを状態表示用レスポンスへ変換する。
     *
     * @param job 変換元の job エンティティ。
     * @return job 状態と結果を含むレスポンス。
     */
    private fun toStatusResponse(job: KnowledgeReindexJob): KnowledgeReindexJobStatusResponse {
        // job 状態と、存在すれば reindex 結果をレスポンスへ詰め替える。
        return KnowledgeReindexJobStatusResponse()
            .jobId(job.jobId)
            .status(job.status.name)
            .acceptedAt(job.acceptedAt.atOffset(ZoneOffset.UTC))
            .startedAt(job.startedAt?.atOffset(ZoneOffset.UTC))
            .completedAt(job.completedAt?.atOffset(ZoneOffset.UTC))
            .knowledgeDocumentId(job.knowledgeDocumentId)
            .result(
                if (job.documentsProcessed == null && job.chunksProcessed == null && job.embeddingsUpdated == null && job.vectorSearchEnabled == null) {
                    null
                } else {
                    // 実行結果がある場合だけ KnowledgeReindexResponse を組み立てる。
                    KnowledgeReindexResponse()
                        .documentsProcessed(job.documentsProcessed)
                        .chunksProcessed(job.chunksProcessed)
                        .embeddingsUpdated(job.embeddingsUpdated)
                        .vectorSearchEnabled(job.vectorSearchEnabled)
                }
            )
            .errorMessage(job.errorMessage)
    }

    /**
     * job の対象範囲をメトリクス用 scope 文字列へ変換する。
     *
     * @return knowledgeDocumentId の有無に応じた scope 文字列。
     */
    private fun KnowledgeReindexJob.scope(): String {
        // 文書 ID がなければ全件、あれば単一文書 job として扱う。
        return if (knowledgeDocumentId == null) "all" else "document"
    }
}
