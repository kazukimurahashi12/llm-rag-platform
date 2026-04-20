package com.example.llmragplatform.controller

import com.example.llmragplatform.config.SecurityProperties
import com.example.llmragplatform.domain.entity.KnowledgeDocument
import com.example.llmragplatform.domain.entity.KnowledgeDocumentAccessScope
import com.example.llmragplatform.domain.entity.KnowledgeDocumentChunk
import com.example.llmragplatform.infrastructure.repository.KnowledgeDocumentChunkRepository
import com.example.llmragplatform.infrastructure.repository.KnowledgeDocumentRepository
import com.example.llmragplatform.security.JwtTokenService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant

@SpringBootTest(
    properties = [
        "openai.api-key=test-key",
        "app.security.admin.username=test-admin",
        "app.security.admin.password=test-admin-password",
        "app.security.admin.roles[0]=ADMIN",
        "app.security.operator.username=test-operator",
        "app.security.operator.password=test-operator-password",
        "app.security.operator.roles[0]=OPERATOR",
        "spring.datasource.url=jdbc:h2:mem:retrievalevaluationtest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
    ]
)
@AutoConfigureMockMvc
class RetrievalEvaluationControllerIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var knowledgeDocumentRepository: KnowledgeDocumentRepository

    @Autowired
    private lateinit var knowledgeDocumentChunkRepository: KnowledgeDocumentChunkRepository

    @Autowired
    private lateinit var jwtTokenService: JwtTokenService

    @Autowired
    private lateinit var securityProperties: SecurityProperties

    @BeforeEach
    fun setUp() {
        knowledgeDocumentChunkRepository.deleteAll()
        knowledgeDocumentRepository.deleteAll()

        val documents = knowledgeDocumentRepository.saveAll(
            listOf(
                KnowledgeDocument(
                    title = "週報運用ガイド",
                    content = "週報は毎週金曜までに提出し、1on1で振り返る。",
                    accessScope = KnowledgeDocumentAccessScope.SHARED,
                    createdAt = Instant.parse("2026-04-18T00:00:00Z")
                ),
                KnowledgeDocument(
                    title = "評価面談ガイド",
                    content = "評価面談では期待値を先に明確化する。",
                    accessScope = KnowledgeDocumentAccessScope.SHARED,
                    createdAt = Instant.parse("2026-04-18T00:10:00Z")
                )
            )
        )

        knowledgeDocumentChunkRepository.saveAll(
            documents.map { document ->
                KnowledgeDocumentChunk(
                    knowledgeDocument = document,
                    chunkIndex = 0,
                    content = document.content,
                    createdAt = Instant.parse("2026-04-18T00:20:00Z")
                )
            }
        )
    }

    @Test
    fun `evaluate retrieval returns hit rate for admin`() {
        mockMvc.perform(
            post("/v1/retrieval-evaluations")
                .with(httpBasic("test-admin", "test-admin-password"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "topK": 3,
                      "cases": [
                        {
                          "label": "weekly-report",
                          "query": "週報の提出が遅れているので1on1で伝えたい",
                          "expectedDocumentTitles": ["週報運用ガイド"]
                        },
                        {
                          "label": "review",
                          "query": "評価面談で期待値を合わせたい",
                          "expectedDocumentTitles": ["評価面談ガイド"]
                        }
                      ]
                    }
                    """.trimIndent()
                )
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.topK").value(3))
            .andExpect(jsonPath("$.totalCases").value(2))
            .andExpect(jsonPath("$.matchedCases").value(2))
            .andExpect(jsonPath("$.hitRate").value(1.0))
            .andExpect(jsonPath("$.meanReciprocalRank").value(1.0))
            .andExpect(jsonPath("$.averageRecallAtK").value(1.0))
            .andExpect(jsonPath("$.averagePrecisionAtK").value(1.0))
            .andExpect(jsonPath("$.caseResults.length()").value(2))
            .andExpect(jsonPath("$.caseResults[0].label").value("weekly-report"))
            .andExpect(jsonPath("$.caseResults[0].firstRelevantRank").value(1))
            .andExpect(jsonPath("$.caseResults[0].reciprocalRank").value(1.0))
            .andExpect(jsonPath("$.caseResults[0].recallAtK").value(1.0))
            .andExpect(jsonPath("$.caseResults[0].precisionAtK").value(1.0))
    }

    @Test
    fun `evaluate retrieval returns 403 for operator`() {
        mockMvc.perform(
            post("/v1/retrieval-evaluations")
                .with(httpBasic("test-operator", "test-operator-password"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "cases": [
                        {
                          "query": "週報の提出が遅れている",
                          "expectedDocumentTitles": ["週報運用ガイド"]
                        }
                      ]
                    }
                    """.trimIndent()
                )
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `evaluate default retrieval cases returns result for admin`() {
        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/v1/retrieval-evaluations/default")
                .with(httpBasic("test-admin", "test-admin-password"))
                .param("topK", "2")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.topK").value(2))
            .andExpect(jsonPath("$.totalCases").value(3))
            .andExpect(jsonPath("$.caseResults.length()").value(3))
    }

    @Test
    fun `compare retrieval evaluations returns variant metrics for admin`() {
        mockMvc.perform(
            post("/v1/retrieval-evaluations/comparisons")
                .with(httpBasic("test-admin", "test-admin-password"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "variants": [
                        {
                          "label": "top1",
                          "topK": 1,
                          "minSimilarityScore": 0.5,
                          "rerankEnabled": false
                        },
                        {
                          "label": "top3",
                          "topK": 3,
                          "rerankEnabled": true
                        }
                      ]
                    }
                    """.trimIndent()
                )
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.variantResults.length()").value(2))
            .andExpect(jsonPath("$.variantResults[0].label").value("top1"))
            .andExpect(jsonPath("$.variantResults[0].topK").value(1))
            .andExpect(jsonPath("$.variantResults[0].minSimilarityScore").value(0.5))
            .andExpect(jsonPath("$.variantResults[0].rerankEnabled").value(false))
            .andExpect(jsonPath("$.variantResults[1].label").value("top3"))
            .andExpect(jsonPath("$.variantResults[1].topK").value(3))
            .andExpect(jsonPath("$.variantResults[1].rerankEnabled").value(true))
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
