package com.example.llmragplatform.service

import org.springframework.stereotype.Service

@Service
class PiiMaskingService {

    private val maskingRules = listOf(
        Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""") to "[MASKED_EMAIL]",
        Regex("""(?<!\d)(?:\+81[- ]?)?(?:0\d{1,4}[- ]?\d{1,4}[- ]?\d{4})(?!\d)""") to "[MASKED_PHONE]",
        Regex("""社員番号[:：]?\s*[A-Za-z0-9-]+""") to "社員番号: [MASKED_EMPLOYEE_ID]"
    )

    fun maskText(text: String): String {
        return maskingRules.fold(text) { masked, (pattern, replacement) ->
            pattern.replace(masked, replacement)
        }
    }
}
