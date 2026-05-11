package com.example.llmragplatform.service

import com.example.llmragplatform.domain.entity.KnowledgeReindexJob
import com.example.llmragplatform.domain.entity.KnowledgeReindexJobStatus
import com.example.llmragplatform.infrastructure.repository.KnowledgeReindexJobRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Instant

@SpringBootTest(
    properties = [
        "openai.api-key=test-key",
        "spring.datasource.url=jdbc:h2:mem:reindexjobcleanuptest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "app.reindex-jobs.cleanup-enabled=true",
        "app.reindex-jobs.retention=1d"
    ]
)
class KnowledgeReindexJobCleanupServiceIntegrationTest {

    @Autowired
    private lateinit var knowledgeReindexJobRepository: KnowledgeReindexJobRepository

    @Autowired
    private lateinit var knowledgeReindexJobCleanupService: KnowledgeReindexJobCleanupService

    @BeforeEach
    fun setUp() {
        knowledgeReindexJobRepository.deleteAll()
    }

    @Test
    fun `purgeExpiredJobs deletes only expired completed and failed jobs`() {
        val now = Instant.now()
        knowledgeReindexJobRepository.saveAll(
            listOf(
                KnowledgeReindexJob(
                    jobId = "completed-old",
                    status = KnowledgeReindexJobStatus.COMPLETED,
                    acceptedAt = now.minusSeconds(200000),
                    startedAt = now.minusSeconds(199000),
                    completedAt = now.minusSeconds(190000),
                    documentsProcessed = 1,
                    chunksProcessed = 1,
                    embeddingsUpdated = 1,
                    vectorSearchEnabled = true
                ),
                KnowledgeReindexJob(
                    jobId = "failed-old",
                    status = KnowledgeReindexJobStatus.FAILED,
                    acceptedAt = now.minusSeconds(200000),
                    startedAt = now.minusSeconds(199000),
                    completedAt = now.minusSeconds(190000),
                    errorMessage = "failed"
                ),
                KnowledgeReindexJob(
                    jobId = "running-old",
                    status = KnowledgeReindexJobStatus.RUNNING,
                    acceptedAt = now.minusSeconds(200000),
                    startedAt = now.minusSeconds(199000),
                    completedAt = null
                ),
                KnowledgeReindexJob(
                    jobId = "completed-new",
                    status = KnowledgeReindexJobStatus.COMPLETED,
                    acceptedAt = now.minusSeconds(1000),
                    startedAt = now.minusSeconds(900),
                    completedAt = now.minusSeconds(600),
                    documentsProcessed = 1,
                    chunksProcessed = 1,
                    embeddingsUpdated = 1,
                    vectorSearchEnabled = true
                )
            )
        )

        knowledgeReindexJobCleanupService.purgeExpiredJobs()

        val remainingJobIds = knowledgeReindexJobRepository.findAll().map { it.jobId }.sorted()
        assertEquals(listOf("completed-new", "running-old"), remainingJobIds)
    }
}
