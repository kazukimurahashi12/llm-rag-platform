package com.example.llmragplatform.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PiiMaskingServiceTest {

    private val service = PiiMaskingService()

    @Test
    fun `maskText masks email phone and employee id`() {
        val masked = service.maskText(
            "連絡先は yamada@example.com、電話は090-1234-5678、社員番号: A12345 です。"
        )

        assertEquals(
            "連絡先は [MASKED_EMAIL]、電話は[MASKED_PHONE]、社員番号: [MASKED_EMPLOYEE_ID] です。",
            masked
        )
    }
}
