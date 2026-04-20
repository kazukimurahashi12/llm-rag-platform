package com.example.llmragplatform.controller

import com.example.llmragplatform.config.SecurityProperties
import com.example.llmragplatform.domain.entity.AuditLog
import com.example.llmragplatform.domain.entity.KnowledgeReindexJob
import com.example.llmragplatform.domain.entity.KnowledgeReindexJobStatus
import com.example.llmragplatform.infrastructure.repository.AuditLogRepository
import com.example.llmragplatform.infrastructure.repository.KnowledgeReindexJobRepository
import com.example.llmragplatform.security.JwtTokenService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.RequestPostProcessor
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
        "spring.datasource.url=jdbc:h2:mem:dashboardapitest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
    ]
)
@AutoConfigureMockMvc
class DashboardControllerIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var auditLogRepository: AuditLogRepository

    @Autowired
    private lateinit var knowledgeReindexJobRepository: KnowledgeReindexJobRepository

    @Autowired
    private lateinit var jwtTokenService: JwtTokenService

    @Autowired
    private lateinit var securityProperties: SecurityProperties

    @BeforeEach
    fun setUp() {
        knowledgeReindexJobRepository.deleteAll()
        auditLogRepository.deleteAll()

        auditLogRepository.saveAll(
            listOf(
                AuditLog(
                    model = "gpt-4o-mini",
                    prompt = "prompt-1",
                    response = "response-1",
                    promptTokens = 100,
                    completionTokens = 50,
                    totalTokens = 150,
                    costJpy = 0.10,
                    latencyMs = 120,
                    createdAt = Instant.parse("2026-04-18T10:00:00Z")
                ),
                AuditLog(
                    model = "gpt-4o-mini",
                    prompt = "prompt-2",
                    response = "response-2",
                    promptTokens = 120,
                    completionTokens = 60,
                    totalTokens = 180,
                    costJpy = 0.20,
                    latencyMs = 180,
                    createdAt = Instant.parse("2026-04-18T10:05:00Z")
                )
            )
        )

        knowledgeReindexJobRepository.saveAll(
            listOf(
                KnowledgeReindexJob(
                    jobId = "completed-job-1",
                    status = KnowledgeReindexJobStatus.COMPLETED,
                    acceptedAt = Instant.parse("2026-04-18T11:00:00Z"),
                    completedAt = Instant.parse("2026-04-18T11:01:00Z")
                ),
                KnowledgeReindexJob(
                    jobId = "completed-job-2",
                    status = KnowledgeReindexJobStatus.COMPLETED,
                    acceptedAt = Instant.parse("2026-04-18T11:05:00Z"),
                    completedAt = Instant.parse("2026-04-18T11:06:00Z")
                ),
                KnowledgeReindexJob(
                    jobId = "failed-job-1",
                    status = KnowledgeReindexJobStatus.FAILED,
                    acceptedAt = Instant.parse("2026-04-18T11:10:00Z"),
                    completedAt = Instant.parse("2026-04-18T11:11:00Z"),
                    errorMessage = "temporary failure"
                )
            )
        )
    }

    @Test
    fun `get dashboard summary returns aggregated kpis for operator`() {
        mockMvc.perform(
            get("/v1/dashboard/summary")
                .with(httpBasic("test-operator", "test-operator-password"))
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalAdviceRequests").value(2))
            .andExpect(jsonPath("$.averageLatencyMs").value(150.0))
            .andExpect(jsonPath("$.averageCostJpy").value(0.15))
            .andExpect(jsonPath("$.completedReindexJobs").value(2))
            .andExpect(jsonPath("$.failedReindexJobs").value(1))
            .andExpect(jsonPath("$.reindexSuccessRate").value(0.6666666666666666))
    }

    @Test
    fun `get dashboard summary returns 401 without authentication`() {
        mockMvc.perform(
            get("/v1/dashboard/summary")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isUnauthorized)
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
