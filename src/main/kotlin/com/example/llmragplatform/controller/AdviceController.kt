package com.example.llmragplatform.controller

import com.example.llmragplatform.generated.api.AdviceApi
import com.example.llmragplatform.generated.model.AdviceRequest
import com.example.llmragplatform.generated.model.AdviceResponse
import com.example.llmragplatform.service.AdviceService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
class AdviceController(
    private val adviceService: AdviceService,
) : AdviceApi {

    override fun getAdvice(adviceRequest: AdviceRequest): ResponseEntity<AdviceResponse> {
        return ResponseEntity.ok(adviceService.generateAdvice(adviceRequest))
    }
}
