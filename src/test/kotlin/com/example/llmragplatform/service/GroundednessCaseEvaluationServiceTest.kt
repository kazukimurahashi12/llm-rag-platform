package com.example.llmragplatform.service

import com.example.llmragplatform.config.RagProperties
import com.example.llmragplatform.generated.model.GroundednessCaseEvaluationCaseRequest
import com.example.llmragplatform.generated.model.GroundednessCaseEvaluationEvidence
import com.example.llmragplatform.generated.model.GroundednessCaseEvaluationRequest
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class GroundednessCaseEvaluationServiceTest {

    @Test
    fun `evaluate returns groundedness accuracy and fallback metrics`() {
        val groundednessEvaluationService = mock<GroundednessEvaluationService>()
        whenever(groundednessEvaluationService.evaluate(any(), any(), any(), any()))
            .thenReturn(
                GroundednessEvaluationResult(
                    score = 0.9,
                    reason = "根拠に沿っています。",
                    status = GroundednessStatus.GROUNDED
                )
            )
            .thenReturn(
                GroundednessEvaluationResult(
                    score = 0.2,
                    reason = "根拠が不足しています。",
                    status = GroundednessStatus.NO_EVIDENCE
                )
            )

        val service = GroundednessCaseEvaluationService(
            groundednessEvaluationService = groundednessEvaluationService,
            ragProperties = RagProperties(groundednessFallbackEnabled = true),
            objectMapper = ObjectMapper()
        )

        val result = service.evaluate(
            GroundednessCaseEvaluationRequest()
                .cases(
                    listOf(
                        GroundednessCaseEvaluationCaseRequest()
                            .label("grounded")
                            .situation("週報提出が遅れている")
                            .targetGoal("重要性を理解してほしい")
                            .advice("背景確認と次回行動の合意を行う。")
                            .expectedStatus(GroundednessCaseEvaluationCaseRequest.ExpectedStatusEnum.GROUNDED)
                            .expectedFallbackApplied(false)
                            .retrievedDocuments(
                                listOf(
                                    GroundednessCaseEvaluationEvidence()
                                        .title("週報提出遅延が続くメンバーへの対応")
                                        .excerpt("背景確認と改善行動の合意を行う。")
                                        .aceCategory(GroundednessCaseEvaluationEvidence.AceCategoryEnum.ABILITY)
                                )
                            ),
                        GroundednessCaseEvaluationCaseRequest()
                            .label("low")
                            .situation("役割期待が曖昧")
                            .targetGoal("安全側で進めたい")
                            .advice("厳しく評価を下げると伝える。")
                            .expectedStatus(GroundednessCaseEvaluationCaseRequest.ExpectedStatusEnum.NO_EVIDENCE)
                            .expectedFallbackApplied(true)
                            .retrievedDocuments(emptyList())
                    )
                )
        )

        assertEquals(2, result.totalCases)
        assertEquals(2, result.matchedCases)
        assertEquals(1, result.groundedCases)
        assertEquals(0, result.lowGroundednessCases)
        assertEquals(1, result.noEvidenceCases)
        assertEquals(0, result.parseFailedCases)
        assertEquals(0, result.judgeErrorCases)
        assertEquals(1, result.fallbackAppliedCases)
        assertEquals(1.0, result.accuracy)
        assertEquals(0.55, result.averageGroundednessScore, 0.0000001)
        assertEquals(true, result.caseResults[1].fallbackApplied)
    }

    @Test
    fun `evaluateDefaultCases loads bundled groundedness cases`() {
        val groundednessEvaluationService = mock<GroundednessEvaluationService>()
        whenever(groundednessEvaluationService.evaluate(any(), any(), any(), any()))
            .thenReturn(
                GroundednessEvaluationResult(
                    score = 0.8,
                    reason = "根拠に沿っています。",
                    status = GroundednessStatus.GROUNDED
                )
            )

        val service = GroundednessCaseEvaluationService(
            groundednessEvaluationService = groundednessEvaluationService,
            ragProperties = RagProperties(groundednessFallbackEnabled = true),
            objectMapper = ObjectMapper()
        )

        val result = service.evaluateDefaultCases()

        assertEquals(12, result.totalCases)
        val knowledgeCaptor = argumentCaptor<RetrievedKnowledge>()
        verify(groundednessEvaluationService, org.mockito.kotlin.times(12)).evaluate(any(), any(), any(), knowledgeCaptor.capture())
        assertEquals(true, knowledgeCaptor.allValues.any { it.promptContext.contains("1on1での改善指摘の基本方針") })
    }
}
