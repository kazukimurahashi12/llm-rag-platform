package com.example.llmragplatform.controller

import com.example.llmragplatform.config.SecurityProperties
import com.example.llmragplatform.infrastructure.repository.KnowledgeDocumentRepository
import com.example.llmragplatform.infrastructure.repository.KnowledgeDocumentChunkRepository
import com.example.llmragplatform.infrastructure.repository.KnowledgeReindexJobRepository
import com.example.llmragplatform.domain.entity.KnowledgeDocumentAccessScope
import com.example.llmragplatform.domain.entity.KnowledgeDocument
import com.example.llmragplatform.domain.entity.KnowledgeDocumentChunk
import com.example.llmragplatform.domain.entity.KnowledgeReindexJob
import com.example.llmragplatform.domain.entity.KnowledgeReindexJobStatus
import com.example.llmragplatform.security.JwtTokenService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
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
        "spring.datasource.url=jdbc:h2:mem:knowledgedoctest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
    ]
)
@AutoConfigureMockMvc
class KnowledgeDocumentControllerIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var knowledgeDocumentRepository: KnowledgeDocumentRepository

    @Autowired
    private lateinit var knowledgeDocumentChunkRepository: KnowledgeDocumentChunkRepository

    @Autowired
    private lateinit var knowledgeReindexJobRepository: KnowledgeReindexJobRepository

    @Autowired
    private lateinit var jwtTokenService: JwtTokenService

    @Autowired
    private lateinit var securityProperties: SecurityProperties

    @BeforeEach
    fun setUp() {
        knowledgeReindexJobRepository.deleteAll()
        knowledgeDocumentChunkRepository.deleteAll()
        knowledgeDocumentRepository.deleteAll()
        val savedDocuments = knowledgeDocumentRepository.saveAll(
            listOf(
                KnowledgeDocument(
                    title = "評価面談ガイド",
                    content = "期待値を先に揃える。",
                    accessScope = KnowledgeDocumentAccessScope.ADMIN_ONLY,
                    createdAt = Instant.parse("2026-04-07T08:00:00Z")
                ),
                KnowledgeDocument(
                    title = "週報運用ガイド",
                    content = "週報は毎週金曜までに提出する。",
                    accessScope = KnowledgeDocumentAccessScope.SHARED,
                    createdAt = Instant.parse("2026-04-07T09:00:00Z")
                )
            )
        )
        knowledgeDocumentChunkRepository.saveAll(
            savedDocuments.mapIndexed { index, document ->
                KnowledgeDocumentChunk(
                    knowledgeDocument = document,
                    chunkIndex = 0,
                    content = document.content,
                    createdAt = Instant.parse("2026-04-07T0${8 + index}:00:00Z")
                )
            }
        )
    }

    @Test
    fun `create knowledge document persists document for admin`() {
        mockMvc.perform(
            post("/v1/knowledge-documents")
                .with(httpBasic("test-admin", "test-admin-password"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "title": "週報運用ガイド",
                      "content": "週報は毎週金曜までに提出し、1on1で振り返る。",
                      "accessScope": "ADMIN_ONLY",
                      "allowedUsernames": ["test-operator"]
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.title").value("週報運用ガイド"))
            .andExpect(jsonPath("$.content").value("週報は毎週金曜までに提出し、1on1で振り返る。"))
            .andExpect(jsonPath("$.accessScope").value("ADMIN_ONLY"))
            .andExpect(jsonPath("$.allowedUsernames[0]").value("test-operator"))

        val savedDocuments = knowledgeDocumentRepository.findAll()
        val savedChunks = knowledgeDocumentChunkRepository.findAll()
        assertEquals(3, savedDocuments.size)
        assertEquals(3, savedChunks.size)
    }

    @Test
    fun `create knowledge document returns 403 for operator`() {
        mockMvc.perform(
            post("/v1/knowledge-documents")
                .with(httpBasic("test-operator", "test-operator-password"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"title":"x","content":"y"}""")
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `reindex knowledge documents returns processed counts for admin`() {
        val jobId = mockMvc.perform(
            post("/v1/knowledge-documents/reindex")
                .with(httpBasic("test-admin", "test-admin-password"))
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.jobId").isString)
            .andExpect(jsonPath("$.status").exists())
            .andReturn()
            .response
            .contentAsString
            .let { com.jayway.jsonpath.JsonPath.read<String>(it, "$.jobId") }

        waitForCompletedJob(jobId)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.result.documentsProcessed").value(2))
            .andExpect(jsonPath("$.result.chunksProcessed").value(2))
            .andExpect(jsonPath("$.result.embeddingsUpdated").value(0))
            .andExpect(jsonPath("$.result.vectorSearchEnabled").value(false))
    }

    @Test
    fun `reindex knowledge documents returns 403 for operator`() {
        mockMvc.perform(
            post("/v1/knowledge-documents/reindex")
                .with(httpBasic("test-operator", "test-operator-password"))
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `reindex single knowledge document returns processed counts for admin`() {
        val targetDocumentId = knowledgeDocumentRepository.findAll()
            .first { it.title == "週報運用ガイド" }
            .id

        val jobId = mockMvc.perform(
            post("/v1/knowledge-documents/$targetDocumentId/reindex")
                .with(httpBasic("test-admin", "test-admin-password"))
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.jobId").isString)
            .andReturn()
            .response
            .contentAsString
            .let { com.jayway.jsonpath.JsonPath.read<String>(it, "$.jobId") }

        waitForCompletedJob(jobId)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.knowledgeDocumentId").value(targetDocumentId))
            .andExpect(jsonPath("$.result.documentsProcessed").value(1))
            .andExpect(jsonPath("$.result.chunksProcessed").value(1))
            .andExpect(jsonPath("$.result.embeddingsUpdated").value(0))
            .andExpect(jsonPath("$.result.vectorSearchEnabled").value(false))
    }

    @Test
    fun `reindex single knowledge document returns 403 for operator`() {
        val targetDocumentId = knowledgeDocumentRepository.findAll()
            .first()
            .id

        mockMvc.perform(
            post("/v1/knowledge-documents/$targetDocumentId/reindex")
                .with(httpBasic("test-operator", "test-operator-password"))
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `reindex single knowledge document returns 404 when not found`() {
        mockMvc.perform(
            post("/v1/knowledge-documents/99999/reindex")
                .with(httpBasic("test-admin", "test-admin-password"))
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.message").value("Knowledge document not found: 99999"))
    }

    @Test
    fun `get knowledge documents returns latest documents for admin`() {
        mockMvc.perform(
            get("/v1/knowledge-documents")
                .with(httpBasic("test-admin", "test-admin-password"))
                .param("limit", "1")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalCount").value(2))
            .andExpect(jsonPath("$.limit").value(1))
            .andExpect(jsonPath("$.offset").value(0))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].title").value("週報運用ガイド"))
    }

    @Test
    fun `get knowledge documents allows operator`() {
        mockMvc.perform(
            get("/v1/knowledge-documents")
                .with(httpBasic("test-operator", "test-operator-password"))
                .param("offset", "0")
                .param("limit", "1")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalCount").value(1))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].title").value("週報運用ガイド"))
    }

    @Test
    fun `get knowledge documents returns 401 without authentication`() {
        mockMvc.perform(
            get("/v1/knowledge-documents")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `get knowledge reindex job returns 404 when job not found`() {
        mockMvc.perform(
            get("/v1/knowledge-documents/reindex-jobs/missing-job-id")
                .with(httpBasic("test-admin", "test-admin-password"))
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.message").value("Knowledge reindex job not found: missing-job-id"))
    }

    @Test
    fun `get knowledge reindex jobs returns latest jobs for admin`() {
        val firstJobId = mockMvc.perform(
            post("/v1/knowledge-documents/reindex")
                .with(httpBasic("test-admin", "test-admin-password"))
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isAccepted)
            .andReturn()
            .response
            .contentAsString
            .let { com.jayway.jsonpath.JsonPath.read<String>(it, "$.jobId") }

        waitForCompletedJob(firstJobId)

        val secondJobId = mockMvc.perform(
            post("/v1/knowledge-documents/reindex")
                .with(httpBasic("test-admin", "test-admin-password"))
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isAccepted)
            .andReturn()
            .response
            .contentAsString
            .let { com.jayway.jsonpath.JsonPath.read<String>(it, "$.jobId") }

        waitForCompletedJob(secondJobId)

        mockMvc.perform(
            get("/v1/knowledge-documents/reindex-jobs")
                .with(httpBasic("test-admin", "test-admin-password"))
                .param("limit", "1")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalCount").value(2))
            .andExpect(jsonPath("$.limit").value(1))
            .andExpect(jsonPath("$.offset").value(0))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].jobId").value(secondJobId))
    }

    @Test
    fun `get knowledge reindex jobs filters by status and knowledgeDocumentId`() {
        val targetDocumentId = knowledgeDocumentRepository.findAll()
            .first { it.title == "週報運用ガイド" }
            .id

        val allDocumentsJobId = mockMvc.perform(
            post("/v1/knowledge-documents/reindex")
                .with(httpBasic("test-admin", "test-admin-password"))
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isAccepted)
            .andReturn()
            .response
            .contentAsString
            .let { com.jayway.jsonpath.JsonPath.read<String>(it, "$.jobId") }

        waitForCompletedJob(allDocumentsJobId)

        val singleDocumentJobId = mockMvc.perform(
            post("/v1/knowledge-documents/$targetDocumentId/reindex")
                .with(httpBasic("test-admin", "test-admin-password"))
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isAccepted)
            .andReturn()
            .response
            .contentAsString
            .let { com.jayway.jsonpath.JsonPath.read<String>(it, "$.jobId") }

        waitForCompletedJob(singleDocumentJobId)

        mockMvc.perform(
            get("/v1/knowledge-documents/reindex-jobs")
                .with(httpBasic("test-admin", "test-admin-password"))
                .param("status", "COMPLETED")
                .param("knowledgeDocumentId", targetDocumentId.toString())
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalCount").value(1))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].jobId").value(singleDocumentJobId))
            .andExpect(jsonPath("$.items[0].knowledgeDocumentId").value(targetDocumentId))
    }

    @Test
    fun `get knowledge reindex jobs returns 400 for invalid status`() {
        mockMvc.perform(
            get("/v1/knowledge-documents/reindex-jobs")
                .with(httpBasic("test-admin", "test-admin-password"))
                .param("status", "INVALID_STATUS")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("Invalid reindex job status: INVALID_STATUS"))
    }

    @Test
    fun `get knowledge reindex jobs returns 403 for operator`() {
        mockMvc.perform(
            get("/v1/knowledge-documents/reindex-jobs")
                .with(httpBasic("test-operator", "test-operator-password"))
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `get knowledge reindex jobs filters by acceptedAt and completedAt ranges`() {
        knowledgeReindexJobRepository.deleteAll()
        knowledgeReindexJobRepository.saveAll(
            listOf(
                KnowledgeReindexJob(
                    jobId = "job-in-range",
                    status = KnowledgeReindexJobStatus.COMPLETED,
                    acceptedAt = Instant.parse("2026-04-08T10:00:00Z"),
                    startedAt = Instant.parse("2026-04-08T10:00:05Z"),
                    completedAt = Instant.parse("2026-04-08T10:10:00Z")
                ),
                KnowledgeReindexJob(
                    jobId = "job-before-range",
                    status = KnowledgeReindexJobStatus.COMPLETED,
                    acceptedAt = Instant.parse("2026-04-08T08:00:00Z"),
                    startedAt = Instant.parse("2026-04-08T08:00:05Z"),
                    completedAt = Instant.parse("2026-04-08T08:10:00Z")
                ),
                KnowledgeReindexJob(
                    jobId = "job-no-completion",
                    status = KnowledgeReindexJobStatus.RUNNING,
                    acceptedAt = Instant.parse("2026-04-08T10:30:00Z"),
                    startedAt = Instant.parse("2026-04-08T10:30:05Z")
                )
            )
        )

        mockMvc.perform(
            get("/v1/knowledge-documents/reindex-jobs")
                .with(httpBasic("test-admin", "test-admin-password"))
                .param("acceptedFrom", "2026-04-08T09:00:00Z")
                .param("acceptedTo", "2026-04-08T11:00:00Z")
                .param("completedFrom", "2026-04-08T09:30:00Z")
                .param("completedTo", "2026-04-08T10:30:00Z")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalCount").value(1))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].jobId").value("job-in-range"))
    }

    @Test
    fun `get knowledge reindex jobs returns 400 for invalid acceptedAt range`() {
        mockMvc.perform(
            get("/v1/knowledge-documents/reindex-jobs")
                .with(httpBasic("test-admin", "test-admin-password"))
                .param("acceptedFrom", "2026-04-08T11:00:00Z")
                .param("acceptedTo", "2026-04-08T10:00:00Z")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("acceptedFrom must be earlier than or equal to acceptedTo"))
    }

    @Test
    fun `get knowledge reindex jobs returns 400 for invalid completedAt range`() {
        mockMvc.perform(
            get("/v1/knowledge-documents/reindex-jobs")
                .with(httpBasic("test-admin", "test-admin-password"))
                .param("completedFrom", "2026-04-08T11:00:00Z")
                .param("completedTo", "2026-04-08T10:00:00Z")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("completedFrom must be earlier than or equal to completedTo"))
    }

    @Test
    fun `get knowledge reindex jobs supports completedAt ascending sort`() {
        knowledgeReindexJobRepository.deleteAll()
        knowledgeReindexJobRepository.saveAll(
            listOf(
                KnowledgeReindexJob(
                    jobId = "job-completed-late",
                    status = KnowledgeReindexJobStatus.COMPLETED,
                    acceptedAt = Instant.parse("2026-04-08T10:00:00Z"),
                    completedAt = Instant.parse("2026-04-08T10:20:00Z")
                ),
                KnowledgeReindexJob(
                    jobId = "job-completed-early",
                    status = KnowledgeReindexJobStatus.COMPLETED,
                    acceptedAt = Instant.parse("2026-04-08T11:00:00Z"),
                    completedAt = Instant.parse("2026-04-08T10:05:00Z")
                )
            )
        )

        mockMvc.perform(
            get("/v1/knowledge-documents/reindex-jobs")
                .with(httpBasic("test-admin", "test-admin-password"))
                .param("sortBy", "completedAt")
                .param("sortDirection", "asc")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].jobId").value("job-completed-early"))
            .andExpect(jsonPath("$.items[1].jobId").value("job-completed-late"))
    }

    @Test
    fun `get knowledge reindex jobs returns 400 for invalid sortBy`() {
        mockMvc.perform(
            get("/v1/knowledge-documents/reindex-jobs")
                .with(httpBasic("test-admin", "test-admin-password"))
                .param("sortBy", "status")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("Invalid reindex job sortBy: status"))
    }

    @Test
    fun `get knowledge reindex jobs returns 400 for invalid sortDirection`() {
        mockMvc.perform(
            get("/v1/knowledge-documents/reindex-jobs")
                .with(httpBasic("test-admin", "test-admin-password"))
                .param("sortDirection", "sideways")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("Invalid reindex job sortDirection: sideways"))
    }

    @Test
    fun `delete knowledge reindex job removes completed job for admin`() {
        val jobId = mockMvc.perform(
            post("/v1/knowledge-documents/reindex")
                .with(httpBasic("test-admin", "test-admin-password"))
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isAccepted)
            .andReturn()
            .response
            .contentAsString
            .let { com.jayway.jsonpath.JsonPath.read<String>(it, "$.jobId") }

        waitForCompletedJob(jobId)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("COMPLETED"))

        mockMvc.perform(
            delete("/v1/knowledge-documents/reindex-jobs/$jobId")
                .with(httpBasic("test-admin", "test-admin-password"))
        )
            .andExpect(status().isNoContent)

        mockMvc.perform(
            get("/v1/knowledge-documents/reindex-jobs/$jobId")
                .with(httpBasic("test-admin", "test-admin-password"))
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.message").value("Knowledge reindex job not found: $jobId"))
    }

    @Test
    fun `delete knowledge reindex job returns 400 for active job`() {
        val jobId = "running-job-id"
        knowledgeReindexJobRepository.save(
            KnowledgeReindexJob(
                jobId = jobId,
                status = KnowledgeReindexJobStatus.RUNNING,
                acceptedAt = Instant.parse("2026-04-08T10:00:00Z"),
                startedAt = Instant.parse("2026-04-08T10:00:05Z")
            )
        )

        mockMvc.perform(
            delete("/v1/knowledge-documents/reindex-jobs/$jobId")
                .with(httpBasic("test-admin", "test-admin-password"))
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("Knowledge reindex job cannot be deleted while active: $jobId"))
    }

    @Test
    fun `delete knowledge reindex job returns 404 when job not found`() {
        mockMvc.perform(
            delete("/v1/knowledge-documents/reindex-jobs/missing-job-id")
                .with(httpBasic("test-admin", "test-admin-password"))
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.message").value("Knowledge reindex job not found: missing-job-id"))
    }

    @Test
    fun `delete knowledge reindex job returns 403 for operator`() {
        val jobId = mockMvc.perform(
            post("/v1/knowledge-documents/reindex")
                .with(httpBasic("test-admin", "test-admin-password"))
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isAccepted)
            .andReturn()
            .response
            .contentAsString
            .let { com.jayway.jsonpath.JsonPath.read<String>(it, "$.jobId") }

        waitForCompletedJob(jobId)

        mockMvc.perform(
            delete("/v1/knowledge-documents/reindex-jobs/$jobId")
                .with(httpBasic("test-operator", "test-operator-password"))
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `retry knowledge reindex job accepts failed all documents job for admin`() {
        val failedJobId = "failed-all-documents-job"
        knowledgeReindexJobRepository.save(
            KnowledgeReindexJob(
                jobId = failedJobId,
                status = KnowledgeReindexJobStatus.FAILED,
                acceptedAt = Instant.parse("2026-04-08T11:00:00Z"),
                startedAt = Instant.parse("2026-04-08T11:00:05Z"),
                completedAt = Instant.parse("2026-04-08T11:00:10Z"),
                errorMessage = "temporary failure"
            )
        )

        val retriedJobId = mockMvc.perform(
            post("/v1/knowledge-documents/reindex-jobs/$failedJobId/retry")
                .with(httpBasic("test-admin", "test-admin-password"))
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.jobId").isString)
            .andExpect(jsonPath("$.status").value("QUEUED"))
            .andReturn()
            .response
            .contentAsString
            .let { com.jayway.jsonpath.JsonPath.read<String>(it, "$.jobId") }

        waitForCompletedJob(retriedJobId)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.result.documentsProcessed").value(2))
    }

    @Test
    fun `retry knowledge reindex job returns 400 for non failed job`() {
        val completedJobId = "completed-job-id"
        knowledgeReindexJobRepository.save(
            KnowledgeReindexJob(
                jobId = completedJobId,
                status = KnowledgeReindexJobStatus.COMPLETED,
                acceptedAt = Instant.parse("2026-04-08T12:00:00Z"),
                startedAt = Instant.parse("2026-04-08T12:00:05Z"),
                completedAt = Instant.parse("2026-04-08T12:00:10Z")
            )
        )

        mockMvc.perform(
            post("/v1/knowledge-documents/reindex-jobs/$completedJobId/retry")
                .with(httpBasic("test-admin", "test-admin-password"))
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("Only failed reindex jobs can be retried: $completedJobId"))
    }

    @Test
    fun `retry knowledge reindex job returns 404 when job not found`() {
        mockMvc.perform(
            post("/v1/knowledge-documents/reindex-jobs/missing-job-id/retry")
                .with(httpBasic("test-admin", "test-admin-password"))
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.message").value("Knowledge reindex job not found: missing-job-id"))
    }

    @Test
    fun `retry knowledge reindex job returns 403 for operator`() {
        val failedJobId = "failed-job-for-operator"
        knowledgeReindexJobRepository.save(
            KnowledgeReindexJob(
                jobId = failedJobId,
                status = KnowledgeReindexJobStatus.FAILED,
                acceptedAt = Instant.parse("2026-04-08T13:00:00Z"),
                completedAt = Instant.parse("2026-04-08T13:00:10Z"),
                errorMessage = "temporary failure"
            )
        )

        mockMvc.perform(
            post("/v1/knowledge-documents/reindex-jobs/$failedJobId/retry")
                .with(httpBasic("test-operator", "test-operator-password"))
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isForbidden)
    }

    private fun waitForCompletedJob(jobId: String): org.springframework.test.web.servlet.ResultActions {
        repeat(20) {
            val result = mockMvc.perform(
                get("/v1/knowledge-documents/reindex-jobs/$jobId")
                    .with(httpBasic("test-admin", "test-admin-password"))
                    .accept(MediaType.APPLICATION_JSON)
            )
            val responseBody = result.andReturn().response.contentAsString
            val status = com.jayway.jsonpath.JsonPath.read<String>(responseBody, "$.status")
            if (status == "COMPLETED" || status == "FAILED") {
                return result
            }
            Thread.sleep(100)
        }
        return mockMvc.perform(
            get("/v1/knowledge-documents/reindex-jobs/$jobId")
                .with(httpBasic("test-admin", "test-admin-password"))
                .accept(MediaType.APPLICATION_JSON)
        )
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
