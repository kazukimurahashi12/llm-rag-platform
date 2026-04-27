package com.example.llmragplatform.service

import com.example.llmragplatform.exception.PromptInjectionDetectedException
import com.example.llmragplatform.generated.model.PromptInjectionEvaluationCaseRequest
import com.example.llmragplatform.generated.model.PromptInjectionEvaluationCaseResult
import com.example.llmragplatform.generated.model.PromptInjectionEvaluationRequest
import com.example.llmragplatform.generated.model.PromptInjectionEvaluationResponse
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service

@Service
/**
 * prompt injection guard の検知精度を評価ケース単位で集計するサービス。
 */
class PromptInjectionEvaluationService(
    private val promptInjectionGuardService: PromptInjectionGuardService,
    private val objectMapper: ObjectMapper,
) {

    /**
     * 標準評価ケース JSON を読み込み、guard の検知精度を集計する。
     *
     * @return 標準ケースに対する prompt injection 評価結果。
     */
    fun evaluateDefaultCases(): PromptInjectionEvaluationResponse {
        // 標準評価ケースファイルが存在することを先に確認する。
        val resource = ClassPathResource(DEFAULT_PROMPT_INJECTION_EVALUATION_RESOURCE)
        require(resource.exists()) {
            "Default prompt injection evaluation file not found: $DEFAULT_PROMPT_INJECTION_EVALUATION_RESOURCE"
        }

        // 標準ケースを読み込んで通常評価処理へ流す。
        return evaluate(loadDefaultRequest())
    }

    /**
     * 任意の評価ケース一覧に対して block / allow の精度指標を計算する。
     *
     * @param request 評価ケース一覧を持つ評価リクエスト。
     * @return detection rate、false positive rate、accuracy を含む評価結果。
     */
    fun evaluate(request: PromptInjectionEvaluationRequest): PromptInjectionEvaluationResponse {
        // 全ケースを guard に流し、ケース単位結果を先に作る。
        val caseResults = request.cases.map { evaluateCase(it) }
        val totalCases = caseResults.size
        // 期待値ベースで block / allow の件数を集計する。
        val expectedBlockedCases = caseResults.count { it.expectedBlocked }
        val expectedAllowedCases = totalCases - expectedBlockedCases
        // guard 判定が期待どおりだった件数を block 側と allow 側で数える。
        val correctlyBlockedCases = caseResults.count { it.expectedBlocked && it.blocked }
        val correctlyAllowedCases = caseResults.count { !it.expectedBlocked && !it.blocked }
        val matchedCases = caseResults.count { it.matched }

        // 集計件数から精度指標を計算して返す。
        return PromptInjectionEvaluationResponse()
            .totalCases(totalCases)
            .expectedBlockedCases(expectedBlockedCases)
            .expectedAllowedCases(expectedAllowedCases)
            .correctlyBlockedCases(correctlyBlockedCases)
            .correctlyAllowedCases(correctlyAllowedCases)
            .detectionRate(rate(correctlyBlockedCases, expectedBlockedCases))
            .falsePositiveRate(rate(expectedAllowedCases - correctlyAllowedCases, expectedAllowedCases))
            .accuracy(rate(matchedCases, totalCases))
            .caseResults(caseResults)
    }

    /**
     * 単一ケースに対して guard を実行し、期待結果との一致を判定する。
     *
     * @param requestCase 評価対象の単一ケース。
     * @return block / allow 判定と期待値比較を含むケース結果。
     */
    private fun evaluateCase(requestCase: PromptInjectionEvaluationCaseRequest): PromptInjectionEvaluationCaseResult {
        // guard が例外を投げたかどうかで block 判定を得る。
        val detectionMessage = try {
            promptInjectionGuardService.validateUserInput(requestCase.input)
            null
        } catch (ex: PromptInjectionDetectedException) {
            ex.message
        }
        // 実際の block 判定と期待値を比較してケース結果を組み立てる。
        val blocked = detectionMessage != null
        val expectedBlocked = requestCase.expectedBlocked

        return PromptInjectionEvaluationCaseResult()
            .label(requestCase.label)
            .input(requestCase.input)
            .expectedBlocked(expectedBlocked)
            .blocked(blocked)
            .matched(blocked == expectedBlocked)
            .detectionMessage(detectionMessage)
            .expectedOutcome(if (expectedBlocked) OUTCOME_BLOCK else OUTCOME_ALLOW)
            .actualOutcome(if (blocked) OUTCOME_BLOCK else OUTCOME_ALLOW)
    }

    /**
     * classpath 上の標準 prompt injection 評価ケース JSON を request モデルへ変換する。
     *
     * @return 標準評価ケースを復元した評価リクエスト。
     */
    private fun loadDefaultRequest(): PromptInjectionEvaluationRequest {
        // 標準評価ケースファイルが存在することを確認する。
        val resource = ClassPathResource(DEFAULT_PROMPT_INJECTION_EVALUATION_RESOURCE)
        require(resource.exists()) {
            "Default prompt injection evaluation file not found: $DEFAULT_PROMPT_INJECTION_EVALUATION_RESOURCE"
        }

        // JSON を request モデルへ復元して返す。
        return resource.inputStream.use { inputStream ->
            objectMapper.readValue(inputStream, PromptInjectionEvaluationRequest::class.java)
        }
    }

    /**
     * 分子と分母から率を計算し、分母 0 の場合は 0.0 を返す。
     *
     * @param numerator 割合計算の分子。
     * @param denominator 割合計算の分母。
     * @return 0.0 から 1.0 の率。
     */
    private fun rate(numerator: Int, denominator: Int): Double {
        // 分母 0 のときは 0.0 を返してゼロ除算を避ける。
        return if (denominator == 0) {
            0.0
        } else {
            numerator.toDouble() / denominator.toDouble()
        }
    }

    companion object {
        private const val DEFAULT_PROMPT_INJECTION_EVALUATION_RESOURCE = "evaluation/prompt-injection-cases.json"
        private const val OUTCOME_BLOCK = "BLOCK"
        private const val OUTCOME_ALLOW = "ALLOW"
    }
}
