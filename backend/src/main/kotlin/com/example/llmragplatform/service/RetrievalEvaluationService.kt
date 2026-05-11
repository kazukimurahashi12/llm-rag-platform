package com.example.llmragplatform.service

import com.example.llmragplatform.generated.model.RetrievalEvaluationCaseRequest
import com.example.llmragplatform.generated.model.RetrievalEvaluationCaseResult
import com.example.llmragplatform.generated.model.RetrievalEvaluationComparisonRequest
import com.example.llmragplatform.generated.model.RetrievalEvaluationComparisonResponse
import com.example.llmragplatform.generated.model.RetrievalEvaluationRequest
import com.example.llmragplatform.generated.model.RetrievalEvaluationResponse
import com.example.llmragplatform.generated.model.RetrievalEvaluationVariantResult
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service
import java.util.Locale

@Service
/**
 * retrieval 条件ごとの検索精度を評価ケース単位で集計する、RAGの検索品質を測るService
 */
class RetrievalEvaluationService(
    private val knowledgeRetrievalService: KnowledgeRetrievalService,
    private val objectMapper: ObjectMapper,
) {

    /**
     * 標準評価ケース JSON を読み込み、必要なら topK を上書きして評価する。
     *
     * @param topK 評価時に使う取得件数。null の場合は評価ファイルまたは既定設定を使う。
     * @return 標準評価ケースを集計した retrieval 評価結果。
     */
    fun evaluateDefaultCases(topK: Int?): RetrievalEvaluationResponse {
        // classpath 上の標準評価ケースが存在することを先に検証
        val resource = ClassPathResource(DEFAULT_RETRIEVAL_EVALUATION_RESOURCE)
        require(resource.exists()) {
            "Default retrieval evaluation file not found: $DEFAULT_RETRIEVAL_EVALUATION_RESOURCE"
        }

        // 標準評価ケースを読み込み、必要なら呼び出し時の topK で上書き
        val request = loadDefaultRequest()
        if (topK != null) {
            request.topK(topK)
        }
        return evaluate(request)
    }

    /**
     * 標準評価ケースに対して複数の検索条件を流し、比較結果をまとめて返す。
     * 標準評価ケースを使った、検索条件のベンチマーク比較メソッド
     *
     * @param request 比較したい variant 一覧を含む評価比較リクエスト。
     * @return variant ごとの評価指標を並べた比較結果。
     */
    fun compareDefaultCases(request: RetrievalEvaluationComparisonRequest): RetrievalEvaluationComparisonResponse {
        // 比較条件ごとに同じ標準評価ケースを実行して指標を横並びで集計
        val variantResults = request.variants.map { variant ->
            // 評価ケースのJSONを読み込む
            val evaluationRequest = loadDefaultRequest()
            variant.topK?.let { topK -> evaluationRequest.topK(topK) }
            val evaluation = evaluate(
                request = evaluationRequest,
                options = RetrievalOptions(
                    // variantごとの検索条件を RetrievalOptions へ反映する。
                    topK = evaluationRequest.topK ?: 0,
                    minSimilarityScore = variant.minSimilarityScore,
                    rerankEnabled = variant.rerankEnabled
                )
            )
            RetrievalEvaluationVariantResult()
                .label(variant.label)
                .topK(evaluation.topK)
                .minSimilarityScore(variant.minSimilarityScore)
                .rerankEnabled(variant.rerankEnabled)
                .totalCases(evaluation.totalCases)
                .matchedCases(evaluation.matchedCases)
                .hitRate(evaluation.hitRate)
                .meanReciprocalRank(evaluation.meanReciprocalRank)
                .averageRecallAtK(evaluation.averageRecallAtK)
                .averagePrecisionAtK(evaluation.averagePrecisionAtK)
                .averageRetrievedCount(evaluation.averageRetrievedCount)
        }

        return RetrievalEvaluationComparisonResponse()
            .variantResults(variantResults)
    }

    /**
     * 単一の評価リクエストを既定の RetrievalOptions へ変換して実行する。
     *
     * @param request 評価ケース一覧と topK を含む評価リクエスト。
     * @return 指定された評価ケース群の集約結果。
     */
    fun evaluate(request: RetrievalEvaluationRequest): RetrievalEvaluationResponse {
        // 指定がなければ 0 として扱い、下流側で既定動作へ委ねる。
        val requestedTopK = request.topK ?: 0
        return evaluate(
            request = request,
            options = RetrievalOptions(topK = requestedTopK)
        )
    }

    /**
     * 評価ケース群を実行し、集約済みの retrieval 指標を計算して返す。
     *
     * @param request 評価対象のケース一覧を持つリクエスト。
     * @param options retrieval 実行時に使う検索条件。
     * @return hit rate、MRR、Recall@K、Precision@K などをまとめた評価結果。
     */
    private fun evaluate(
        request: RetrievalEvaluationRequest,
        options: RetrievalOptions,
    ): RetrievalEvaluationResponse {
        // すべての評価ケースを同じ検索条件で実行する。
        val requestedTopK = request.topK ?: 0
        val caseResults = request.cases.map { evaluateCase(it, options) }
        val totalCases = caseResults.size
        val matchedCases = caseResults.count { it.matched }
        // 1 ケースあたりに平均で何件の文書を返しているかを集計する。
        val averageRetrievedCount = if (totalCases == 0) {
            0.0
        } else {
            caseResults.sumOf { it.retrievedCount }.toDouble() / totalCases.toDouble()
        }
        // 期待文書を 1 件以上拾えたケースの割合を hit rate として扱う。
        val hitRate = if (totalCases == 0) {
            0.0
        } else {
            matchedCases.toDouble() / totalCases.toDouble()
        }
        // ケース単位の順位系・再現率系・適合率系の指標を平均化する。
        val meanReciprocalRank = averageOf(totalCases, caseResults.sumOf { it.reciprocalRank })
        val averageRecallAtK = averageOf(totalCases, caseResults.sumOf { it.recallAtK })
        val averagePrecisionAtK = averageOf(totalCases, caseResults.sumOf { it.precisionAtK })

        return RetrievalEvaluationResponse()
            .topK(if (requestedTopK > 0) requestedTopK else 0)
            .totalCases(totalCases)
            .matchedCases(matchedCases)
            .hitRate(hitRate)
            .meanReciprocalRank(meanReciprocalRank)
            .averageRecallAtK(averageRecallAtK)
            .averagePrecisionAtK(averagePrecisionAtK)
            .averageRetrievedCount(averageRetrievedCount)
            .caseResults(caseResults)
    }

    /**
     * 1 件の評価ケースに対して retrieval を実行し、ケース単位の指標を計算する。
     *
     * @param requestCase query と期待文書タイトル一覧を含む単一ケース。
     * @param options このケースの retrieval に適用する検索条件。
     * @return ケース単位の一致有無、順位、Recall@K、Precision@K を含む結果。
     */
    private fun evaluateCase(
        requestCase: RetrievalEvaluationCaseRequest,
        options: RetrievalOptions,
    ): RetrievalEvaluationCaseResult {
        // このケースの query で実際に retrieval を実行する。
        val retrievedKnowledge = knowledgeRetrievalService.retrieveKnowledge(
            query = requestCase.query,
            options = options
        )
        val expectedTitles = requestCase.expectedDocumentTitles
        // 表記揺れの影響を減らすため、期待タイトルは正規化した lookup を作る。
        val expectedLookup = expectedTitles.associateBy { normalize(it) }
        val retrievedTitles = retrievedKnowledge.documents.map { it.title }
        // 取得文書のうち期待タイトルに一致したものだけを重複除去して残す。
        val matchedTitles = retrievedTitles
            .mapNotNull { title -> expectedLookup[normalize(title)] }
            .distinct()
        // 最初に期待文書が現れた順位を 1-based rank で求める。
        val firstRelevantRank = retrievedTitles.indexOfFirst { title -> expectedLookup.containsKey(normalize(title)) }
            .takeIf { index -> index >= 0 }
            ?.let { index -> index + 1 }
        // 最初の一致順位から reciprocal rank を計算する。
        val reciprocalRank = firstRelevantRank?.let { rank -> 1.0 / rank.toDouble() } ?: 0.0
        // Recall@K は期待文書のうち何件拾えたかで計算する。
        val recallAtK = if (expectedTitles.isEmpty()) {
            0.0
        } else {
            matchedTitles.size.toDouble() / expectedTitles.distinctBy { normalize(it) }.size.toDouble()
        }
        // Precision@K は取得文書のうち何件が期待文書かで計算する。
        val precisionAtK = if (retrievedTitles.isEmpty()) {
            0.0
        } else {
            matchedTitles.size.toDouble() / retrievedTitles.size.toDouble()
        }

        return RetrievalEvaluationCaseResult()
            .label(requestCase.label)
            .query(requestCase.query)
            .expectedDocumentTitles(expectedTitles)
            .retrievedDocumentTitles(retrievedTitles)
            .matchedDocumentTitles(matchedTitles)
            .matched(matchedTitles.isNotEmpty())
            .retrievedCount(retrievedTitles.size)
            .firstRelevantRank(firstRelevantRank)
            .reciprocalRank(reciprocalRank)
            .recallAtK(recallAtK)
            .precisionAtK(precisionAtK)
    }

    /**
     * タイトル比較用に文字列を正規化する。
     *
     * @param value 正規化前の文字列。
     * @return 前後空白除去と小文字化を行った比較用文字列。
     */
    private fun normalize(value: String): String {
        // タイトル比較用に前後空白除去と小文字化を行う。
        return value.trim().lowercase(Locale.getDefault())
    }

    /**
     * classpath 上の標準評価ケース JSON を読み込んで request モデルへ変換する。
     *
     * @return 標準評価ケース JSON を復元した評価リクエスト。
     */
    private fun loadDefaultRequest(): RetrievalEvaluationRequest {
        // 標準評価ケース JSON を request モデルへ復元する。
        val resource = ClassPathResource(DEFAULT_RETRIEVAL_EVALUATION_RESOURCE)
        require(resource.exists()) {
            "Default retrieval evaluation file not found: $DEFAULT_RETRIEVAL_EVALUATION_RESOURCE"
        }

        return resource.inputStream.use { inputStream ->
            objectMapper.readValue(inputStream, RetrievalEvaluationRequest::class.java)
        }
    }

    /**
     * 合計値とケース数から平均値を計算し、0 件時は 0.0 を返す。
     *
     * @param totalCases 平均化の母数となるケース数。
     * @param totalScore 平均化したい合計スコア。
     * @return ケース数 0 を考慮した平均値。
     */
    private fun averageOf(totalCases: Int, totalScore: Double): Double {
        // ケース数 0 のときは 0.0 を返してゼロ除算を防ぐ。
        return if (totalCases == 0) {
            0.0
        } else {
            totalScore / totalCases.toDouble()
        }
    }

    companion object {
        private const val DEFAULT_RETRIEVAL_EVALUATION_RESOURCE = "evaluation/retrieval-cases.json"
    }
}
