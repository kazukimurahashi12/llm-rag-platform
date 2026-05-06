package com.example.llmragplatform.service

import com.example.llmragplatform.config.RagProperties
import com.example.llmragplatform.domain.LlmClient
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
/**
 * 生成済み advice が取得根拠に沿っているかを LLM-as-a-judge で評価するサービス。
 */
class GroundednessEvaluationService(
    private val llmClient: LlmClient,
    private val ragProperties: RagProperties,
    private val objectMapper: ObjectMapper,
    @Value("\${openai.default-model}") private val defaultModel: String,
) {

    /**
     * 相談内容、取得根拠、回答文を使って groundedness を採点する。
     *
     * @param situation 相談中の状況説明。
     * @param targetGoal 達成したい目標。
     * @param advice 生成済みの助言文。
     * @param retrievedKnowledge retrieval で取得した根拠情報。
     * @return groundedness のスコア、理由、状態を含む評価結果。
     */
    fun evaluate(
        situation: String,
        targetGoal: String,
        advice: String,
        retrievedKnowledge: RetrievedKnowledge,
    ): GroundednessEvaluationResult {
        // 根拠文書がない場合は、高い groundedness を主張できないため低スコア扱いにする。
        if (retrievedKnowledge.documents.isEmpty()) {
            return GroundednessEvaluationResult(
                score = 0.0,
                reason = "根拠文書が取得できていないため、回答が根拠に沿っているかを確認できません。",
                status = GroundednessStatus.LOW_GROUNDEDNESS
            )
        }

        // judge 用には文書単位の抜粋をまとめ、過剰な重複を避ける。
        val evidenceText = retrievedKnowledge.documents
            .distinctBy { "${it.id}-${it.chunkIndex}" }
            .joinToString("\n") { document ->
                "- ${document.title}: ${document.excerpt}"
            }
        // groundedness judge 用の system prompt を構築する。
        val systemPrompt = """
            You are a groundedness evaluator for a management support AI.
            Read the user situation, goal, retrieved evidence, and generated advice.
            Evaluate whether the generated advice is supported by the retrieved evidence.
            Return strict JSON only with keys:
            groundednessScore: number between 0.0 and 1.0
            reason: short Japanese explanation
        """.trimIndent()
        // judge に渡す user message を組み立てる。
        val userMessage = """
            状況:
            $situation

            目標:
            $targetGoal

            取得根拠:
            $evidenceText

            生成回答:
            $advice
        """.trimIndent()

        return runCatching {
            // 追加の LLM 呼び出しで groundedness を採点する。
            val judgeResponse = llmClient.chat(defaultModel, systemPrompt, userMessage)
            val tree = objectMapper.readTree(judgeResponse.content)
            val score = tree.path("groundednessScore").asDouble(0.0).coerceIn(0.0, 1.0)
            val reason = tree.path("reason").asText("取得根拠との対応関係を評価できませんでした。")
            GroundednessEvaluationResult(
                score = score,
                reason = reason,
                status = if (score >= ragProperties.groundednessThreshold) {
                    GroundednessStatus.GROUNDED
                } else {
                    GroundednessStatus.LOW_GROUNDEDNESS
                }
            )
        }.getOrElse {
            // judge が失敗した場合も API 全体は落とさず、低スコア扱いで返す。
            GroundednessEvaluationResult(
                score = 0.0,
                reason = "groundedness 判定の解析に失敗したため、低信頼として扱いました。",
                status = GroundednessStatus.LOW_GROUNDEDNESS
            )
        }
    }
}

/**
 * groundedness 評価結果の内部モデル。
 */
data class GroundednessEvaluationResult(
    val score: Double,
    val reason: String,
    val status: GroundednessStatus,
)

/**
 * groundedness 判定状態。
 */
enum class GroundednessStatus {
    GROUNDED,
    LOW_GROUNDEDNESS,
}
