package com.example.llmragplatform.service

import com.example.llmragplatform.infrastructure.repository.AuditLogRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(
    properties = [
        "openai.api-key=test-key",
        "spring.datasource.url=jdbc:h2:mem:auditlogtest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
    ]
)
class AuditLogAsyncServiceIntegrationTest {

    @Autowired
    private lateinit var auditLogAsyncService: AuditLogAsyncService

    @Autowired
    private lateinit var auditLogRepository: AuditLogRepository

    @BeforeEach
    fun setUp() {
        auditLogRepository.deleteAll()
    }

    @Test
    fun `save persists audit log to database`() {
        auditLogAsyncService.save(
            model = "gpt-4o-mini",
            prompt = "system prompt\n---\nuser message",
            response = "advice response",
            promptTokens = 120,
            completionTokens = 80,
            totalTokens = 200,
            costJpy = 0.0099,
            latencyMs = 150
        )

        val savedLog = waitUntilSaved()

        assertEquals("gpt-4o-mini", savedLog.model)
        assertEquals("system prompt\n---\nuser message", savedLog.prompt)
        assertEquals("advice response", savedLog.response)
        assertEquals(120, savedLog.promptTokens)
        assertEquals(80, savedLog.completionTokens)
        assertEquals(200, savedLog.totalTokens)
        assertEquals(0.0099, savedLog.costJpy, 0.0000001)
        assertEquals(150, savedLog.latencyMs)
        assertTrue(savedLog.createdAt.toEpochMilli() > 0)
    }

    private fun waitUntilSaved(): com.example.llmragplatform.domain.entity.AuditLog {
        repeat(20) {
            val allLogs = auditLogRepository.findAll()
            if (allLogs.isNotEmpty()) {
                return allLogs.single()
            }
            Thread.sleep(100)
        }
        error("Audit log was not persisted within timeout")
    }
}
