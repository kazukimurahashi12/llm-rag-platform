package com.example.llmragplatform.service

import com.example.llmragplatform.config.RagProperties
import com.example.llmragplatform.domain.entity.AceCategory
import com.example.llmragplatform.generated.model.GroundednessCaseEvaluationCaseRequest
import com.example.llmragplatform.generated.model.GroundednessCaseEvaluationCaseResult
import com.example.llmragplatform.generated.model.GroundednessCaseEvaluationEvidence
import com.example.llmragplatform.generated.model.GroundednessCaseEvaluationRequest
import com.example.llmragplatform.generated.model.GroundednessCaseEvaluationResponse
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service

@Service
/**
 * groundedness judge と fallback 方針をケース単位で評価するサービス。
 */
class GroundednessCaseEvaluationService(
    private val groundednessEvaluationService: GroundednessEvaluationService,
    private val ragProperties: RagProperties,
    private val objectMapper: ObjectMapper,
) {

    /**
     * classpath 上の標準 groundedness 評価ケースを読み込んで評価する。
     *
     * @return 標準 groundedness 評価ケースの集計結果。
     */
    fun evaluateDefaultCases(): GroundednessCaseEvaluationResponse {
        val resource = ClassPathResource(DEFAULT_GROUNDEDNESS_EVALUATION_RESOURCE)
        require(resource.exists()) {
            "Default groundedness evaluation file not found: $DEFAULT_GROUNDEDNESS_EVALUATION_RESOURCE"
        }
        return evaluate(loadDefaultRequest())
    }

    /**
     * 任意の groundedness 評価ケース一覧を実行し、精度指標を集計する。
     *
     * @param request 評価対象ケース一覧。
     * @return score、fallback、期待判定一致率を含む評価結果。
     */
    fun evaluate(request: GroundednessCaseEvaluationRequest): GroundednessCaseEvaluationResponse {
        val caseResults = request.cases.map { evaluateCase(it) }
        val totalCases = caseResults.size
        val matchedCases = caseResults.count { it.matched }
        val groundedCases = caseResults.count { it.actualStatus == GroundednessCaseEvaluationCaseResult.ActualStatusEnum.GROUNDED }
        val lowGroundednessCases = caseResults.count { it.actualStatus == GroundednessCaseEvaluationCaseResult.ActualStatusEnum.LOW_GROUNDEDNESS }
        val noEvidenceCases = caseResults.count { it.actualStatus == GroundednessCaseEvaluationCaseResult.ActualStatusEnum.NO_EVIDENCE }
        val parseFailedCases = caseResults.count { it.actualStatus == GroundednessCaseEvaluationCaseResult.ActualStatusEnum.PARSE_FAILED }
        val judgeErrorCases = caseResults.count { it.actualStatus == GroundednessCaseEvaluationCaseResult.ActualStatusEnum.JUDGE_ERROR }
        val fallbackAppliedCases = caseResults.count { it.fallbackApplied }
        val averageGroundednessScore = if (totalCases == 0) {
            0.0
        } else {
            caseResults.sumOf { it.groundednessScore } / totalCases.toDouble()
        }

        return GroundednessCaseEvaluationResponse()
            .totalCases(totalCases)
            .matchedCases(matchedCases)
            .groundedCases(groundedCases)
            .lowGroundednessCases(lowGroundednessCases)
            .noEvidenceCases(noEvidenceCases)
            .parseFailedCases(parseFailedCases)
            .judgeErrorCases(judgeErrorCases)
            .fallbackAppliedCases(fallbackAppliedCases)
            .accuracy(if (totalCases == 0) 0.0 else matchedCases.toDouble() / totalCases.toDouble())
            .averageGroundednessScore(averageGroundednessScore)
            .caseResults(caseResults)
    }

    /**
     * 単一ケースに対して groundedness judge を実行し、期待値との一致を判定する。
     *
     * @param requestCase 評価対象のケース。
     * @return ケース単位の評価結果。
     */
    private fun evaluateCase(
        requestCase: GroundednessCaseEvaluationCaseRequest,
    ): GroundednessCaseEvaluationCaseResult {
        val retrievedKnowledge = toRetrievedKnowledge(requestCase.retrievedDocuments)
        val evaluation = groundednessEvaluationService.evaluate(
            situation = requestCase.situation,
            targetGoal = requestCase.targetGoal,
            advice = requestCase.advice,
            retrievedKnowledge = retrievedKnowledge
        )
        val fallbackApplied = ragProperties.groundednessFallbackEnabled && shouldApplyFallback(evaluation)
        val expectedStatus = GroundednessCaseEvaluationCaseResult.ExpectedStatusEnum.fromValue(
            requestCase.expectedStatus.value
        )
        val expectedFallbackApplied = requestCase.expectedFallbackApplied ?: false
        val actualStatus = GroundednessCaseEvaluationCaseResult.ActualStatusEnum.fromValue(evaluation.status.name)
        val matched = actualStatus.value == expectedStatus.value && fallbackApplied == expectedFallbackApplied

        return GroundednessCaseEvaluationCaseResult()
            .label(requestCase.label)
            .situation(requestCase.situation)
            .targetGoal(requestCase.targetGoal)
            .advice(requestCase.advice)
            .expectedStatus(expectedStatus)
            .actualStatus(actualStatus)
            .expectedFallbackApplied(expectedFallbackApplied)
            .fallbackApplied(fallbackApplied)
            .matched(matched)
            .groundednessScore(evaluation.score)
            .reason(evaluation.reason)
    }

    /**
     * API モデルの evidence 一覧を groundedness judge 用の内部モデルへ変換する。
     *
     * @param evidenceItems 取得根拠一覧。
     * @return groundedness judge が受け取れる retrieval 結果モデル。
     */
    private fun toRetrievedKnowledge(evidenceItems: List<GroundednessCaseEvaluationEvidence>): RetrievedKnowledge {
        val documents = evidenceItems.mapIndexed { index, evidence ->
            RetrievedKnowledgeDocument(
                id = index.toLong() + 1L,
                title = evidence.title,
                excerpt = evidence.excerpt,
                chunkIndex = index,
                aceCategory = AceCategory.valueOf(evidence.aceCategory.value),
                distanceScore = null,
                similarityScore = null
            )
        }
        val promptContext = documents.joinToString("\n") { document -> "${document.title}: ${document.excerpt}" }
        return RetrievedKnowledge(promptContext = promptContext, documents = documents)
    }

    /**
     * classpath 上の標準 groundedness 評価ケース JSON を request モデルへ変換する。
     *
     * @return 標準 groundedness 評価ケース。
     */
    private fun loadDefaultRequest(): GroundednessCaseEvaluationRequest {
        val resource = ClassPathResource(DEFAULT_GROUNDEDNESS_EVALUATION_RESOURCE)
        require(resource.exists()) {
            "Default groundedness evaluation file not found: $DEFAULT_GROUNDEDNESS_EVALUATION_RESOURCE"
        }
        return resource.inputStream.use { inputStream ->
            objectMapper.readValue(inputStream, GroundednessCaseEvaluationRequest::class.java)
        }
    }

    companion object {
        private const val DEFAULT_GROUNDEDNESS_EVALUATION_RESOURCE = "evaluation/groundedness-cases.json"
    }

    /**
     * groundedness 評価結果から fallback 適用要否を判定する。
     *
     * @param evaluation groundedness 評価結果。
     * @return fallback を適用する場合は true。
     */
    private fun shouldApplyFallback(evaluation: GroundednessEvaluationResult): Boolean {
        return when (evaluation.status) {
            GroundednessStatus.NO_EVIDENCE -> true
            GroundednessStatus.LOW_GROUNDEDNESS -> evaluation.score < ragProperties.groundednessFallbackScoreThreshold
            GroundednessStatus.PARSE_FAILED,
            GroundednessStatus.JUDGE_ERROR,
            GroundednessStatus.GROUNDED -> false
        }
    }
}
