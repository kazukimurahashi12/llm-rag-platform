package com.example.llmragplatform.controller

import com.example.llmragplatform.config.SecurityProperties
import com.example.llmragplatform.domain.entity.AuditLog
import com.example.llmragplatform.infrastructure.repository.AuditLogRepository
import com.example.llmragplatform.security.JwtTokenService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant

@SpringBootTest(
    properties = [
        "openai.api-key=test-key",
        "app.security.admin.username=test-admin",
        "app.security.admin.password=test-admin-password",
        "app.security.operator.username=test-operator",
        "app.security.operator.password=test-operator-password",
        "spring.datasource.url=jdbc:h2:mem:auditlogapitest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
    ]
)
@AutoConfigureMockMvc
class AuditLogControllerIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var auditLogRepository: AuditLogRepository

    @Autowired
    private lateinit var jwtTokenService: JwtTokenService

    @Autowired
    private lateinit var securityProperties: SecurityProperties

    @BeforeEach
    fun setUp() {
        auditLogRepository.deleteAll()
        auditLogRepository.saveAll(
            listOf(
                AuditLog(
                    model = "gpt-4o-mini",
                    prompt = "older prompt",
                    response = "older response",
                    promptTokens = 90,
                    completionTokens = 30,
                    totalTokens = 120,
                    costJpy = 0.0047,
                    latencyMs = 120,
                    createdAt = Instant.parse("2026-04-05T08:00:00Z")
                ),
                AuditLog(
                    model = "gpt-4o",
                    prompt = "newer prompt",
                    response = "newer response",
                    promptTokens = 180,
                    completionTokens = 70,
                    totalTokens = 250,
                    costJpy = 0.1725,
                    latencyMs = 240,
                    createdAt = Instant.parse("2026-04-05T09:00:00Z")
                ),
                AuditLog(
                    model = "gpt-4o-mini",
                    prompt = "latest prompt with mail latest@example.com and phone 090-1111-2222 for operator visibility test",
                    response = "latest response with latest@example.com and 090-1111-2222 included for redaction testing",
                    promptTokens = 210,
                    completionTokens = 90,
                    totalTokens = 300,
                    costJpy = 0.1125,
                    latencyMs = 180,
                    createdAt = Instant.parse("2026-04-05T10:00:00Z")
                )
            )
        )
    }

    @Test
    fun `get audit logs returns latest records with limit`() {
        mockMvc.perform(
            get("/v1/audit-logs")
                .with(httpBasic("test-admin", "test-admin-password"))
                .param("limit", "1")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.totalCount").value(3))
            .andExpect(jsonPath("$.limit").value(1))
            .andExpect(jsonPath("$.offset").value(0))
            .andExpect(jsonPath("$.items[0].model").value("gpt-4o-mini"))
            .andExpect(jsonPath("$.items[0].totalTokens").value(300))
            .andExpect(jsonPath("$.items[0].createdAt").value("2026-04-05T10:00:00Z"))
    }

    @Test
    fun `get audit logs filters by model and date range and applies offset`() {
        mockMvc.perform(
            get("/v1/audit-logs")
                .with(httpBasic("test-admin", "test-admin-password"))
                .param("model", "gpt-4o-mini")
                .param("from", "2026-04-05T08:30:00Z")
                .param("to", "2026-04-05T10:30:00Z")
                .param("offset", "0")
                .param("limit", "10")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalCount").value(1))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].totalTokens").value(300))

        mockMvc.perform(
            get("/v1/audit-logs")
                .with(httpBasic("test-admin", "test-admin-password"))
                .param("offset", "1")
                .param("limit", "1")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalCount").value(3))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].model").value("gpt-4o"))
            .andExpect(jsonPath("$.items[0].totalTokens").value(250))
    }

    @Test
    fun `get audit log detail returns full record`() {
        val savedLog = auditLogRepository.findAll().maxBy { it.createdAt }

        mockMvc.perform(
            get("/v1/audit-logs/{auditLogId}", savedLog.id)
                .with(httpBasic("test-admin", "test-admin-password"))
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(savedLog.id))
            .andExpect(jsonPath("$.model").value("gpt-4o-mini"))
            .andExpect(jsonPath("$.prompt").value("latest prompt with mail latest@example.com and phone 090-1111-2222 for operator visibility test"))
            .andExpect(jsonPath("$.response").value("latest response with latest@example.com and 090-1111-2222 included for redaction testing"))
            .andExpect(jsonPath("$.totalTokens").value(300))
            .andExpect(jsonPath("$.createdAt").value("2026-04-05T10:00:00Z"))
    }

    @Test
    fun `get audit log detail returns 404 when record does not exist`() {
        mockMvc.perform(
            get("/v1/audit-logs/{auditLogId}", 999999)
                .with(httpBasic("test-admin", "test-admin-password"))
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.message").value("Audit log not found: 999999"))
    }

    @Test
    fun `get audit logs returns 401 without authentication`() {
        mockMvc.perform(
            get("/v1/audit-logs")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.message").value("Authentication is required"))
    }

    @Test
    fun `get audit logs allows operator user`() {
        mockMvc.perform(
            get("/v1/audit-logs")
                .with(httpBasic("test-operator", "test-operator-password"))
                .param("limit", "1")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(1))
    }

    @Test
    fun `get audit log detail returns redacted content for operator user`() {
        val savedLog = auditLogRepository.findAll().maxBy { it.createdAt }

        mockMvc.perform(
            get("/v1/audit-logs/{auditLogId}", savedLog.id)
                .with(httpBasic("test-operator", "test-operator-password"))
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(savedLog.id))
            .andExpect(jsonPath("$.model").value("gpt-4o-mini"))
            .andExpect(jsonPath("$.prompt").value("latest prompt with mail [MASKED_EMAIL] and phone [MASKED_PHONE] for operator vis... [REDACTED]"))
            .andExpect(jsonPath("$.response").value("latest response with [MASKED_EMAIL] and [MASKED_PHONE] included for redaction te... [REDACTED]"))
    }

    private fun httpBasic(username: String, @Suppress("UNUSED_PARAMETER") password: String): RequestPostProcessor {
        val roles = when (username) {
            securityProperties.admin.username -> securityProperties.admin.roles
            securityProperties.operator.username -> securityProperties.operator.roles
            else -> emptyList()
        }
        val token = jwtTokenService.generateAccessToken(username, roles).token
        return RequestPostProcessor { request ->
            request.addHeader("Authorization", "Bearer $token")
            request
        }
    }
}
