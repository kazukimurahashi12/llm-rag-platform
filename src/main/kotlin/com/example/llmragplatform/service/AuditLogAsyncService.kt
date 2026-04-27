package com.example.llmragplatform.service

import com.example.llmragplatform.domain.entity.AuditLog
import com.example.llmragplatform.infrastructure.repository.AuditLogRepository
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
/**
 * 監査ログ保存を非同期で実行し、API 応答時間への影響を減らすサービス。
 */
class AuditLogAsyncService(
    private val auditLogRepository: AuditLogRepository,
) {

    @Async
    /**
     * 生成結果の監査ログを非同期保存する。
     *
     * @param model 利用した LLM モデル名。
     * @param prompt 保存対象の prompt 文字列。
     * @param response 保存対象の response 文字列。
     * @param promptTokens prompt 側 token 数。
     * @param completionTokens completion 側 token 数。
     * @param totalTokens 合計 token 数。
     * @param costJpy 円換算した概算コスト。
     * @param latencyMs リクエスト処理時間。
     */
    fun save(
        model: String,
        prompt: String,
        response: String,
        promptTokens: Int,
        completionTokens: Int,
        totalTokens: Int,
        costJpy: Double,
        latencyMs: Long,
    ) {
        // 受け取った監査情報を永続化エンティティへ詰め替えて保存する。
        auditLogRepository.save(
            AuditLog(
                // 利用モデル名を保存する。
                model = model,
                // prompt 文字列を保存する。
                prompt = prompt,
                // response 文字列を保存する。
                response = response,
                // prompt token 数を保存する。
                promptTokens = promptTokens,
                // completion token 数を保存する。
                completionTokens = completionTokens,
                // 合計 token 数を保存する。
                totalTokens = totalTokens,
                // 概算コストを保存する。
                costJpy = costJpy,
                // レイテンシを保存する。
                latencyMs = latencyMs
            )
        )
    }
}
