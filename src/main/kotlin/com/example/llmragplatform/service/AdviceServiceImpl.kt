package com.example.llmragplatform.service

import com.example.llmragplatform.domain.LlmClient
import com.example.llmragplatform.generated.model.AdviceRequest
import com.example.llmragplatform.generated.model.AdviceResponse
import com.example.llmragplatform.generated.model.RetrievedDocument
import com.example.llmragplatform.generated.model.UsageInfo
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class AdviceServiceImpl(
    private val llmClient: LlmClient,
    private val promptManager: PromptManager,
    private val knowledgeRetrievalService: KnowledgeRetrievalService,
    private val promptInjectionGuardService: PromptInjectionGuardService,
    private val costCalculator: CostCalculator,
    private val piiMaskingService: PiiMaskingService,
    private val auditLogAsyncService: AuditLogAsyncService,
    @Value("\${openai.default-model}") private val defaultModel: String,
) : AdviceService {

    override fun generateAdvice(request: AdviceRequest): AdviceResponse {
        // リクエストからメンバー状況を取り出す。
        val memberContext = request.memberContext
        // 指定モデルがあればそれを使い、未指定または空ならデフォルトモデルを使う。
        val model = request.setting?.model?.takeIf { it.isNotBlank() } ?: defaultModel
        // 指定トーンがあればそれを使い、未指定または空なら empathetic を使う。
        val tone = request.setting?.tone?.takeIf { it.isNotBlank() } ?: "empathetic"
        // 応答時間を計測するために開始時刻を記録する。
        val startTime = System.currentTimeMillis()
        // 典型的な prompt injection パターンを入力段階で拒否する。
        promptInjectionGuardService.validateUserInput(memberContext.situation, memberContext.targetGoal)

        // 相談内容と目標を検索クエリとして結合する。
        val retrievedKnowledge = knowledgeRetrievalService.retrieveKnowledge(
            // situation と targetGoal の両方を使って関連ナレッジを検索する。
            query = "${memberContext.situation}\n${memberContext.targetGoal}"
        )

        // プロンプトテンプレートに実データを埋め込んで system prompt を構築する。
        val systemPrompt = promptManager.buildPrompt(
            // 利用するプロンプトテンプレート名を指定する。
            templateName = "management-coach-v1.0",
            // テンプレート変数へ相談内容、目標、トーン、RAG 文脈を渡す。
            variables = mapOf(
                // 現在の状況をテンプレートへ渡す。
                "situation" to memberContext.situation,
                // 達成したい目標をテンプレートへ渡す。
                "goal" to memberContext.targetGoal,
                // 出力トーンをテンプレートへ渡す。
                "tone" to tone,
                // 取得したナレッジ文脈をテンプレートへ渡す。
                "knowledgeContext" to retrievedKnowledge.promptContext
            )
        )

        // LLM へ渡す user message を組み立てる。
        val userMessage = """
            // メンバーの状況を user message に含める。
            状況: ${memberContext.situation}
            // 達成したい目標を user message に含める。
            達成したい目標: ${memberContext.targetGoal}
        """.trimIndent()

        // LLM を呼び出して助言文を生成する。
        val llmResponse = llmClient.chat(model, systemPrompt, userMessage)

        // 現在時刻との差分から処理時間を算出する。
        val latencyMs = System.currentTimeMillis() - startTime
        // prompt token と completion token の合計を計算する。
        val totalTokens = llmResponse.promptTokens + llmResponse.completionTokens
        // モデル単価と token 数から概算コストを計算する。
        val costJpy = costCalculator.calculateCostJpy(
            // コスト計算対象のモデル名を渡す。
            model = model,
            // 入力 token 数を渡す。
            promptTokens = llmResponse.promptTokens,
            // 出力 token 数を渡す。
            completionTokens = llmResponse.completionTokens
        )
        // 監査ログに保存する前に prompt 内の個人情報をマスクする。
        val maskedPrompt = piiMaskingService.maskText("$systemPrompt\n---\n$userMessage")
        // 監査ログに保存する前に応答内の個人情報をマスクする。
        val maskedResponse = piiMaskingService.maskText(llmResponse.content)

        // 監査ログを非同期で保存する。
        auditLogAsyncService.save(
            // 利用モデルを記録する。
            model = model,
            // マスク済み prompt を記録する。
            prompt = maskedPrompt,
            // マスク済み response を記録する。
            response = maskedResponse,
            // prompt token 数を記録する。
            promptTokens = llmResponse.promptTokens,
            // completion token 数を記録する。
            completionTokens = llmResponse.completionTokens,
            // 合計 token 数を記録する。
            totalTokens = totalTokens,
            // 概算コストを記録する。
            costJpy = costJpy,
            // レイテンシを記録する。
            latencyMs = latencyMs
        )

        // API レスポンスを組み立てて返す。
        return AdviceResponse()
            // 生成した助言本文を設定する。
            .advice(llmResponse.content)
            // 検索で取得した根拠文書一覧を設定する。
            .retrievedDocuments(
                // 内部の取得結果を API モデルへ変換する。
                retrievedKnowledge.documents.map { document ->
                    // 1 件分の根拠文書レスポンスを作る。
                    RetrievedDocument()
                        // 文書 ID を設定する。
                        .id(document.id)
                        // 文書タイトルを設定する。
                        .title(document.title)
                        // 抜粋本文を設定する。
                        .excerpt(document.excerpt)
                        // chunk 番号を設定する。
                        .chunkIndex(document.chunkIndex)
                        // vector 検索時の距離スコアを設定する。
                        .distanceScore(document.distanceScore)
                        // vector 検索時の利用者向け類似度スコアを設定する。
                        .similarityScore(document.similarityScore)
                }
            )
            // usage 情報を設定する。
            .usage(
                // usage モデルを作る。
                UsageInfo()
                    // 利用モデル名を設定する。
                    .model(model)
                    // prompt token 数を設定する。
                    .promptTokens(llmResponse.promptTokens)
                    // completion token 数を設定する。
                    .completionTokens(llmResponse.completionTokens)
                    // 合計 token 数を設定する。
                    .totalTokens(totalTokens)
                    // 概算コストを設定する。
                    .estimatedCostJpy(costJpy)
            )
    }
}
