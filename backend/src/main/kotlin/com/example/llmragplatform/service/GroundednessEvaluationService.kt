package com.example.llmragplatform.service

import com.example.llmragplatform.config.RagProperties
import com.example.llmragplatform.domain.LlmClient
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.ResourceAccessException

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
                status = GroundednessStatus.NO_EVIDENCE
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
            You must return JSON only. Do not include markdown, code fences, or explanations outside JSON.
            Use exactly this schema:
            {
              "groundednessScore": 0.0 to 1.0,
              "reason": "short Japanese explanation",
              "status": "GROUNDED" or "LOW_GROUNDEDNESS"
            }
            If the advice contains a major claim not supported by evidence, choose LOW_GROUNDEDNESS.
            If only part of the advice is supported but important parts overreach, choose LOW_GROUNDEDNESS.
            Judge semantic consistency, not exact wording match.
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
            val tree = objectMapper.readTree(extractJsonObject(judgeResponse.content))
            val score = tree.path("groundednessScore").asDouble(0.0).coerceIn(0.0, 1.0)
            val reason = tree.path("reason").asText("取得根拠との対応関係を評価できませんでした。")
            val status = tree.path("status").asText("").takeIf { it.isNotBlank() }?.let { statusValue ->
                runCatching { GroundednessStatus.valueOf(statusValue) }.getOrNull()
            } ?: if (score >= ragProperties.groundednessThreshold) {
                GroundednessStatus.GROUNDED
            } else {
                GroundednessStatus.LOW_GROUNDEDNESS
            }
            GroundednessEvaluationResult(
                score = score,
                reason = reason,
                status = status
            )
        }.getOrElse { ex ->
            when (ex) {
                is ResourceAccessException -> GroundednessEvaluationResult(
                    score = 0.0,
                    reason = "groundedness judge の外部呼び出しに失敗しました。",
                    status = GroundednessStatus.JUDGE_ERROR
                )
                else -> GroundednessEvaluationResult(
                    score = 0.0,
                    reason = "groundedness 判定の解析に失敗しました。",
                    status = GroundednessStatus.PARSE_FAILED
                )
            }
        }
    }

    /**
     * judge 応答から JSON 本体だけを抜き出す。
     *
     * @param content judge が返した生文字列。
     * @return JSON オブジェクト部分。
     */
    private fun extractJsonObject(content: String): String {
        val trimmed = content.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return trimmed
        }
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        require(start >= 0 && end > start) {
            "JSON object not found in groundedness judge response"
        }
        return trimmed.substring(start, end + 1)
    }
}

/**
 * groundedness 評価結果の内部モデル。
 */
data class GroundednessEvaluationResult(
    val score: Double,
    val reason: String,
    val status: GroundednessStatus,
    val fallbackApplied: Boolean = false,
)

/**
 * groundedness 判定状態。
 */
enum class GroundednessStatus {
    GROUNDED,
    LOW_GROUNDEDNESS,
    NO_EVIDENCE,
    PARSE_FAILED,
    JUDGE_ERROR,
}
