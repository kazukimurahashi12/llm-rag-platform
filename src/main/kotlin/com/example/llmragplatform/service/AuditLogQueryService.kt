package com.example.llmragplatform.service

import com.example.llmragplatform.domain.entity.AuditLog
import com.example.llmragplatform.exception.ResourceNotFoundException
import com.example.llmragplatform.generated.model.AuditLogDetailResponse
import com.example.llmragplatform.generated.model.AuditLogListResponse
import com.example.llmragplatform.generated.model.AuditLogSummaryItem
import com.example.llmragplatform.infrastructure.repository.AuditLogRepository
import jakarta.persistence.criteria.Predicate
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Service
/**
 * 監査ログの一覧取得と詳細表示用レスポンス変換を担当するサービス。
 */
class AuditLogQueryService(
    private val auditLogRepository: AuditLogRepository,
    private val piiMaskingService: PiiMaskingService,
) {

    /**
     * 条件に一致する監査ログ一覧を取得し、ページング済みレスポンスへ変換する。
     *
     * @param limit 取得件数上限。
     * @param offset 取得開始位置。
     * @param model モデル名での絞り込み条件。
     * @param from 作成日時の下限。
     * @param to 作成日時の上限。
     * @return 一覧項目と総件数を含む監査ログ一覧レスポンス。
     */
    fun getAuditLogs(
        limit: Int,
        offset: Int,
        model: String?,
        from: OffsetDateTime?,
        to: OffsetDateTime?,
    ): AuditLogListResponse {
        val safeLimit = limit.coerceIn(1, 100)
        val safeOffset = offset.coerceAtLeast(0)
        val specification = buildSpecification(model, from?.toInstant(), to?.toInstant())
        val allMatchedLogs = auditLogRepository.findAll(specification, Sort.by(Sort.Direction.DESC, "createdAt"))
        val items = allMatchedLogs.drop(safeOffset).take(safeLimit).map(::toSummaryItem)

        return AuditLogListResponse()
            .items(items)
            .totalCount(allMatchedLogs.size.toLong())
            .limit(safeLimit)
            .offset(safeOffset)
    }

    /**
     * 監査ログ 1 件を取得し、権限に応じて内容をマスクして返す。
     *
     * @param auditLogId 取得したい監査ログ ID。
     * @param includeSensitiveContent true の場合は全文を返し、false の場合はマスク済み内容を返す。
     * @return 監査ログ詳細レスポンス。
     */
    fun getAuditLogDetail(auditLogId: Long, includeSensitiveContent: Boolean): AuditLogDetailResponse {
        val log = auditLogRepository.findById(auditLogId)
            .orElseThrow { ResourceNotFoundException("Audit log not found: $auditLogId") }

        return AuditLogDetailResponse()
            .id(log.id)
            .model(log.model)
            .prompt(toVisiblePrompt(log, includeSensitiveContent))
            .response(toVisibleResponse(log, includeSensitiveContent))
            .promptTokens(log.promptTokens)
            .completionTokens(log.completionTokens)
            .totalTokens(log.totalTokens)
            .costJpy(log.costJpy)
            .latencyMs(log.latencyMs)
            .createdAt(OffsetDateTime.ofInstant(log.createdAt, ZoneOffset.UTC))
    }

    /**
     * 監査ログエンティティを一覧表示用の簡略レスポンスへ変換する。
     *
     * @param log 変換元の監査ログエンティティ。
     * @return 一覧表示用の監査ログ要約。
     */
    private fun toSummaryItem(log: AuditLog): AuditLogSummaryItem {
        return AuditLogSummaryItem()
            .id(log.id)
            .model(log.model)
            .promptTokens(log.promptTokens)
            .completionTokens(log.completionTokens)
            .totalTokens(log.totalTokens)
            .costJpy(log.costJpy)
            .latencyMs(log.latencyMs)
            .createdAt(OffsetDateTime.ofInstant(log.createdAt, ZoneOffset.UTC))
    }

    /**
     * 監査ログ検索条件から JPA Specification を構築する。
     *
     * @param model モデル名での絞り込み条件。
     * @param from 作成日時の下限。
     * @param to 作成日時の上限。
     * @return 指定条件に一致する監査ログ抽出用 Specification。
     */
    private fun buildSpecification(
        model: String?,
        from: Instant?,
        to: Instant?,
    ): Specification<AuditLog> {
        return Specification { root, _, criteriaBuilder ->
            val predicates = mutableListOf<Predicate>()

            model?.takeIf { it.isNotBlank() }?.let {
                predicates += criteriaBuilder.equal(root.get<String>("model"), it)
            }
            from?.let {
                predicates += criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), it)
            }
            to?.let {
                predicates += criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), it)
            }

            criteriaBuilder.and(*predicates.toTypedArray())
        }
    }

    /**
     * 権限に応じて表示可能な prompt 文字列へ変換する。
     *
     * @param log 表示対象の監査ログ。
     * @param includeSensitiveContent true の場合は全文を返す。
     * @return 表示用 prompt 文字列。
     */
    private fun toVisiblePrompt(log: AuditLog, includeSensitiveContent: Boolean): String {
        if (includeSensitiveContent) {
            return log.prompt
        }
        return abbreviateForOperator(piiMaskingService.maskText(log.prompt))
    }

    /**
     * 権限に応じて表示可能な response 文字列へ変換する。
     *
     * @param log 表示対象の監査ログ。
     * @param includeSensitiveContent true の場合は全文を返す。
     * @return 表示用 response 文字列。
     */
    private fun toVisibleResponse(log: AuditLog, includeSensitiveContent: Boolean): String {
        if (includeSensitiveContent) {
            return log.response
        }
        return abbreviateForOperator(piiMaskingService.maskText(log.response))
    }

    /**
     * operator 向け表示で長すぎる本文を省略する。
     *
     * @param value 省略対象の文字列。
     * @param maxLength そのまま表示する最大文字数。
     * @return 必要に応じて省略記号付きにした文字列。
     */
    private fun abbreviateForOperator(value: String, maxLength: Int = 80): String {
        return if (value.length <= maxLength) {
            value
        } else {
            value.take(maxLength) + "... [REDACTED]"
        }
    }
}
