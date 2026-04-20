package com.example.llmragplatform.service

import com.example.llmragplatform.generated.model.RetrievalEvaluationCaseRequest
import com.example.llmragplatform.generated.model.RetrievalEvaluationComparisonRequest
import com.example.llmragplatform.generated.model.RetrievalEvaluationVariantRequest
import com.example.llmragplatform.generated.model.RetrievalEvaluationRequest
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RetrievalEvaluationServiceTest {

    @Test
    fun `evaluate returns hit rate from retrieval results`() {
        val knowledgeRetrievalService = mock<KnowledgeRetrievalService>()
        whenever(knowledgeRetrievalService.retrieveKnowledge(eq("週報の相談"), eq(RetrievalOptions(topK = 3)))).thenReturn(
            RetrievedKnowledge(
                promptContext = "",
                documents = listOf(
                    RetrievedKnowledgeDocument(
                        id = 99,
                        title = "一般ガイド",
                        excerpt = "excerpt",
                        chunkIndex = 0,
                        distanceScore = null,
                        similarityScore = null
                    ),
                    RetrievedKnowledgeDocument(
                        id = 1,
                        title = "週報運用ガイド",
                        excerpt = "excerpt",
                        chunkIndex = 0,
                        distanceScore = null,
                        similarityScore = null
                    )
                )
            )
        )
        whenever(knowledgeRetrievalService.retrieveKnowledge(eq("評価面談の相談"), eq(RetrievalOptions(topK = 3)))).thenReturn(
            RetrievedKnowledge(
                promptContext = "",
                documents = listOf(
                    RetrievedKnowledgeDocument(
                        id = 2,
                        title = "1on1 ガイド",
                        excerpt = "excerpt",
                        chunkIndex = 0,
                        distanceScore = null,
                        similarityScore = null
                    )
                )
            )
        )

        val result = RetrievalEvaluationService(knowledgeRetrievalService, ObjectMapper()).evaluate(
            RetrievalEvaluationRequest()
                .topK(3)
                .cases(
                    listOf(
                        RetrievalEvaluationCaseRequest()
                            .label("weekly-report")
                            .query("週報の相談")
                            .expectedDocumentTitles(listOf("週報運用ガイド")),
                        RetrievalEvaluationCaseRequest()
                            .label("review")
                            .query("評価面談の相談")
                            .expectedDocumentTitles(listOf("評価面談ガイド")),
                    )
                )
        )

        assertEquals(2, result.totalCases)
        assertEquals(1, result.matchedCases)
        assertEquals(0.5, result.hitRate)
        assertEquals(1.5, result.averageRetrievedCount)
        assertEquals(0.25, result.meanReciprocalRank)
        assertEquals(0.5, result.averageRecallAtK)
        assertEquals(0.25, result.averagePrecisionAtK)
        assertEquals(true, result.caseResults[0].matched)
        assertEquals(false, result.caseResults[1].matched)
        assertEquals(listOf("週報運用ガイド"), result.caseResults[0].matchedDocumentTitles)
        assertEquals(2, result.caseResults[0].firstRelevantRank)
        assertEquals(0.5, result.caseResults[0].reciprocalRank)
        assertEquals(1.0, result.caseResults[0].recallAtK)
        assertEquals(0.5, result.caseResults[0].precisionAtK)
    }

    @Test
    fun `evaluateDefaultCases loads bundled retrieval cases`() {
        val knowledgeRetrievalService = mock<KnowledgeRetrievalService>()
        whenever(knowledgeRetrievalService.retrieveKnowledge(any<String>(), eq(RetrievalOptions(topK = 2)))).thenReturn(
            RetrievedKnowledge(
                promptContext = "",
                documents = emptyList()
            )
        )

        val result = RetrievalEvaluationService(knowledgeRetrievalService, ObjectMapper()).evaluateDefaultCases(topK = 2)

        assertEquals(2, result.topK)
        assertEquals(3, result.totalCases)
        assertEquals(0, result.matchedCases)
    }

    @Test
    fun `compareDefaultCases runs bundled cases for each variant`() {
        val knowledgeRetrievalService = mock<KnowledgeRetrievalService>()
        whenever(
            knowledgeRetrievalService.retrieveKnowledge(
                any<String>(),
                eq(RetrievalOptions(topK = 1, minSimilarityScore = 0.7, rerankEnabled = false))
            )
        ).thenReturn(
            RetrievedKnowledge(promptContext = "", documents = emptyList())
        )
        whenever(
            knowledgeRetrievalService.retrieveKnowledge(
                any<String>(),
                eq(RetrievalOptions(topK = 3, minSimilarityScore = null, rerankEnabled = true))
            )
        ).thenReturn(
            RetrievedKnowledge(promptContext = "", documents = emptyList())
        )

        val result = RetrievalEvaluationService(knowledgeRetrievalService, ObjectMapper()).compareDefaultCases(
            RetrievalEvaluationComparisonRequest()
                .variants(
                    listOf(
                        RetrievalEvaluationVariantRequest()
                            .label("top1")
                            .topK(1)
                            .minSimilarityScore(0.7)
                            .rerankEnabled(false),
                        RetrievalEvaluationVariantRequest()
                            .label("top3")
                            .topK(3)
                            .rerankEnabled(true)
                    )
                )
        )

        assertEquals(2, result.variantResults.size)
        assertEquals("top1", result.variantResults[0].label)
        assertEquals(1, result.variantResults[0].topK)
        assertEquals(0.7, result.variantResults[0].minSimilarityScore)
        assertEquals(false, result.variantResults[0].rerankEnabled)
        assertEquals("top3", result.variantResults[1].label)
        assertEquals(3, result.variantResults[1].topK)
        assertEquals(true, result.variantResults[1].rerankEnabled)
    }
}
