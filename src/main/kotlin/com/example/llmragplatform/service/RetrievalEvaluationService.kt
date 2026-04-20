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
class RetrievalEvaluationService(
    private val knowledgeRetrievalService: KnowledgeRetrievalService,
    private val objectMapper: ObjectMapper,
) {

    fun evaluateDefaultCases(topK: Int?): RetrievalEvaluationResponse {
        val resource = ClassPathResource(DEFAULT_RETRIEVAL_EVALUATION_RESOURCE)
        require(resource.exists()) {
            "Default retrieval evaluation file not found: $DEFAULT_RETRIEVAL_EVALUATION_RESOURCE"
        }

        val request = loadDefaultRequest()
        if (topK != null) {
            request.topK(topK)
        }
        return evaluate(request)
    }

    fun compareDefaultCases(request: RetrievalEvaluationComparisonRequest): RetrievalEvaluationComparisonResponse {
        val variantResults = request.variants.map { variant ->
            val evaluationRequest = loadDefaultRequest()
            variant.topK?.let { topK -> evaluationRequest.topK(topK) }
            val evaluation = evaluate(
                request = evaluationRequest,
                options = RetrievalOptions(
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

    fun evaluate(request: RetrievalEvaluationRequest): RetrievalEvaluationResponse {
        val requestedTopK = request.topK ?: 0
        return evaluate(
            request = request,
            options = RetrievalOptions(topK = requestedTopK)
        )
    }

    private fun evaluate(
        request: RetrievalEvaluationRequest,
        options: RetrievalOptions,
    ): RetrievalEvaluationResponse {
        val requestedTopK = request.topK ?: 0
        val caseResults = request.cases.map { evaluateCase(it, options) }
        val totalCases = caseResults.size
        val matchedCases = caseResults.count { it.matched }
        val averageRetrievedCount = if (totalCases == 0) {
            0.0
        } else {
            caseResults.sumOf { it.retrievedCount }.toDouble() / totalCases.toDouble()
        }
        val hitRate = if (totalCases == 0) {
            0.0
        } else {
            matchedCases.toDouble() / totalCases.toDouble()
        }
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

    private fun evaluateCase(
        requestCase: RetrievalEvaluationCaseRequest,
        options: RetrievalOptions,
    ): RetrievalEvaluationCaseResult {
        val retrievedKnowledge = knowledgeRetrievalService.retrieveKnowledge(
            query = requestCase.query,
            options = options
        )
        val expectedTitles = requestCase.expectedDocumentTitles
        val expectedLookup = expectedTitles.associateBy { normalize(it) }
        val retrievedTitles = retrievedKnowledge.documents.map { it.title }
        val matchedTitles = retrievedTitles
            .mapNotNull { title -> expectedLookup[normalize(title)] }
            .distinct()
        val firstRelevantRank = retrievedTitles.indexOfFirst { title -> expectedLookup.containsKey(normalize(title)) }
            .takeIf { index -> index >= 0 }
            ?.let { index -> index + 1 }
        val reciprocalRank = firstRelevantRank?.let { rank -> 1.0 / rank.toDouble() } ?: 0.0
        val recallAtK = if (expectedTitles.isEmpty()) {
            0.0
        } else {
            matchedTitles.size.toDouble() / expectedTitles.distinctBy { normalize(it) }.size.toDouble()
        }
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

    private fun normalize(value: String): String {
        return value.trim().lowercase(Locale.getDefault())
    }

    private fun loadDefaultRequest(): RetrievalEvaluationRequest {
        val resource = ClassPathResource(DEFAULT_RETRIEVAL_EVALUATION_RESOURCE)
        require(resource.exists()) {
            "Default retrieval evaluation file not found: $DEFAULT_RETRIEVAL_EVALUATION_RESOURCE"
        }

        return resource.inputStream.use { inputStream ->
            objectMapper.readValue(inputStream, RetrievalEvaluationRequest::class.java)
        }
    }

    private fun averageOf(totalCases: Int, totalScore: Double): Double {
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
