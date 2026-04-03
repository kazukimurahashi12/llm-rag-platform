package com.example.llmragplatform.service

import com.example.llmragplatform.generated.model.AdviceRequest
import com.example.llmragplatform.generated.model.AdviceResponse

interface AdviceService {
    fun generateAdvice(request: AdviceRequest): AdviceResponse
}
