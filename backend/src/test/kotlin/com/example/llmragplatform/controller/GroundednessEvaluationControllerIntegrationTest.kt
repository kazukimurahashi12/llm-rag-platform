package com.example.llmragplatform.controller

import com.example.llmragplatform.config.SecurityProperties
import com.example.llmragplatform.security.JwtTokenService
import com.example.llmragplatform.service.GroundednessCaseEvaluationService
import com.example.llmragplatform.generated.model.GroundednessCaseEvaluationCaseResult
import com.example.llmragplatform.generated.model.GroundednessCaseEvaluationResponse
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(
    properties = [
        "openai.api-key=test-key",
        "app.security.admin.username=test-admin",
        "app.security.admin.password=test-admin-password",
        "app.security.admin.roles[0]=ADMIN",
        "app.security.operator.username=test-operator",
        "app.security.operator.password=test-operator-password",
        "app.security.operator.roles[0]=OPERATOR",
        "spring.datasource.url=jdbc:h2:mem:groundednessevaluationtest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
    ]
)
@AutoConfigureMockMvc
class GroundednessEvaluationControllerIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jwtTokenService: JwtTokenService

    @Autowired
    private lateinit var securityProperties: SecurityProperties

    @MockBean
    private lateinit var groundednessCaseEvaluationService: GroundednessCaseEvaluationService

    @Test
    fun `evaluate groundedness returns metrics for admin`() {
        whenever(groundednessCaseEvaluationService.evaluate(any())).thenReturn(
            GroundednessCaseEvaluationResponse()
                .totalCases(2)
                .matchedCases(2)
                .groundedCases(1)
                .lowGroundednessCases(0)
                .noEvidenceCases(1)
                .parseFailedCases(0)
                .judgeErrorCases(0)
                .fallbackAppliedCases(1)
                .accuracy(1.0)
                .averageGroundednessScore(0.55)
                .caseResults(
                    listOf(
                        GroundednessCaseEvaluationCaseResult()
                            .label("grounded")
                            .situation("週報提出が遅れている")
                            .targetGoal("重要性を理解してほしい")
                            .advice("背景確認と次回行動の合意を行う。")
                            .expectedStatus(GroundednessCaseEvaluationCaseResult.ExpectedStatusEnum.GROUNDED)
                            .actualStatus(GroundednessCaseEvaluationCaseResult.ActualStatusEnum.GROUNDED)
                            .expectedFallbackApplied(false)
                            .fallbackApplied(false)
                            .matched(true)
                            .groundednessScore(0.9)
                            .reason("根拠に沿っています。")
                    )
                )
        )

        mockMvc.perform(
            post("/v1/groundedness-evaluations")
                .with(httpBasic("test-admin", "test-admin-password"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "cases": [
                        {
                          "label": "grounded",
                          "situation": "週報提出が遅れている",
                          "targetGoal": "重要性を理解してほしい",
                          "advice": "背景確認と次回行動の合意を行う。",
                          "expectedStatus": "GROUNDED",
                          "expectedFallbackApplied": false,
                          "retrievedDocuments": [
                            {
                              "title": "週報提出遅延が続くメンバーへの対応",
                              "excerpt": "背景確認と改善行動の合意を行う。",
                              "aceCategory": "ABILITY"
                            }
                          ]
                        }
                      ]
                    }
                    """.trimIndent()
                )
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalCases").value(2))
            .andExpect(jsonPath("$.matchedCases").value(2))
            .andExpect(jsonPath("$.noEvidenceCases").value(1))
            .andExpect(jsonPath("$.fallbackAppliedCases").value(1))
            .andExpect(jsonPath("$.accuracy").value(1.0))
    }

    @Test
    fun `evaluate default groundedness cases returns bundled result for admin`() {
        whenever(groundednessCaseEvaluationService.evaluateDefaultCases()).thenReturn(
            GroundednessCaseEvaluationResponse()
                .totalCases(6)
                .matchedCases(5)
                .groundedCases(3)
                .lowGroundednessCases(3)
                .noEvidenceCases(0)
                .parseFailedCases(0)
                .judgeErrorCases(0)
                .fallbackAppliedCases(3)
                .accuracy(5.0 / 6.0)
                .averageGroundednessScore(0.58)
                .caseResults(emptyList())
        )

        mockMvc.perform(
            get("/v1/groundedness-evaluations/default")
                .with(httpBasic("test-admin", "test-admin-password"))
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalCases").value(6))
            .andExpect(jsonPath("$.matchedCases").value(5))
    }

    @Test
    fun `evaluate groundedness returns 403 for operator`() {
        mockMvc.perform(
            post("/v1/groundedness-evaluations")
                .with(httpBasic("test-operator", "test-operator-password"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "cases": [
                        {
                          "situation": "test",
                          "targetGoal": "test",
                          "advice": "test",
                          "expectedStatus": "GROUNDED",
                          "retrievedDocuments": []
                        }
                      ]
                    }
                    """.trimIndent()
                )
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isForbidden)
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
