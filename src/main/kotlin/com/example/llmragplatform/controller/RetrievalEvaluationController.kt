package com.example.llmragplatform.controller

import com.example.llmragplatform.generated.api.EvaluationApi
import com.example.llmragplatform.generated.model.RetrievalEvaluationComparisonRequest
import com.example.llmragplatform.generated.model.RetrievalEvaluationComparisonResponse
import com.example.llmragplatform.generated.model.RetrievalEvaluationRequest
import com.example.llmragplatform.generated.model.RetrievalEvaluationResponse
import com.example.llmragplatform.service.RetrievalEvaluationService
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.RestController

@RestController
class RetrievalEvaluationController(
    private val retrievalEvaluationService: RetrievalEvaluationService,
) : EvaluationApi {

    @PreAuthorize("hasRole('ADMIN')")
    override fun evaluateRetrieval(
        retrievalEvaluationRequest: RetrievalEvaluationRequest,
    ): ResponseEntity<RetrievalEvaluationResponse> {
        return ResponseEntity.ok(
            retrievalEvaluationService.evaluate(retrievalEvaluationRequest)
        )
    }

    @PreAuthorize("hasRole('ADMIN')")
    override fun evaluateDefaultRetrievalCases(topK: Int?): ResponseEntity<RetrievalEvaluationResponse> {
        return ResponseEntity.ok(
            retrievalEvaluationService.evaluateDefaultCases(topK)
        )
    }

    @PreAuthorize("hasRole('ADMIN')")
    override fun compareRetrievalEvaluations(
        retrievalEvaluationComparisonRequest: RetrievalEvaluationComparisonRequest,
    ): ResponseEntity<RetrievalEvaluationComparisonResponse> {
        return ResponseEntity.ok(
            retrievalEvaluationService.compareDefaultCases(retrievalEvaluationComparisonRequest)
        )
    }
}
