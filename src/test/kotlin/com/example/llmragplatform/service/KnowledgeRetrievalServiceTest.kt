package com.example.llmragplatform.service

import com.example.llmragplatform.config.RagProperties
import com.example.llmragplatform.domain.entity.KnowledgeDocument
import com.example.llmragplatform.domain.entity.KnowledgeDocumentAccessScope
import com.example.llmragplatform.domain.entity.KnowledgeDocumentChunk
import com.example.llmragplatform.infrastructure.repository.KnowledgeDocumentChunkRepository
import com.example.llmragplatform.infrastructure.repository.ChunkVectorMatch
import com.example.llmragplatform.infrastructure.repository.PgVectorChunkSearchRepository
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

class KnowledgeRetrievalServiceTest {

    @Test
    fun `buildKnowledgeContext returns top matched documents`() {
        val repository = mock<KnowledgeDocumentChunkRepository>()
        whenever(repository.findAll()).thenReturn(
            listOf(
                KnowledgeDocumentChunk(
                    id = 1,
                    knowledgeDocument = KnowledgeDocument(
                        id = 1,
                        title = "週報運用ガイド",
                        content = "週報は毎週金曜までに提出し、1on1で振り返る。",
                        createdAt = Instant.parse("2026-04-06T00:00:00Z")
                    ),
                    chunkIndex = 0,
                    content = "週報は毎週金曜までに提出し、1on1で振り返る。",
                    createdAt = Instant.parse("2026-04-06T00:00:00Z")
                ),
                KnowledgeDocumentChunk(
                    id = 2,
                    knowledgeDocument = KnowledgeDocument(
                        id = 2,
                        title = "評価面談ガイド",
                        content = "評価面談では期待値を先に明確化する。",
                        createdAt = Instant.parse("2026-04-06T00:00:00Z")
                    ),
                    chunkIndex = 0,
                    content = "評価面談では期待値を先に明確化する。",
                    createdAt = Instant.parse("2026-04-06T00:00:00Z")
                )
            )
        )
        val embeddingService = mock<KnowledgeEmbeddingService>()
        val pgVectorChunkSearchRepository = mock<PgVectorChunkSearchRepository>()
        val retrievalMetrics = KnowledgeRetrievalMetrics(SimpleMeterRegistry())
        val knowledgeAccessControlService = mock<KnowledgeAccessControlService>()
        whenever(embeddingService.embedQuery(org.mockito.kotlin.any())).thenReturn(null)
        whenever(knowledgeAccessControlService.canAccess(org.mockito.kotlin.any())).thenReturn(true)

        val result = KnowledgeRetrievalService(
            ragProperties = RagProperties(vectorSearchEnabled = false),
            knowledgeDocumentChunkRepository = repository,
            knowledgeEmbeddingService = embeddingService,
            pgVectorChunkSearchRepository = pgVectorChunkSearchRepository,
            knowledgeRetrievalMetrics = retrievalMetrics,
            knowledgeAccessControlService = knowledgeAccessControlService
        ).retrieveKnowledge(
            query = "週報の提出が遅れているので1on1で伝えたい"
        )

        assertEquals(true, result.promptContext.contains("週報運用ガイド"))
        assertEquals(false, result.promptContext.contains("評価面談ガイド"))
        assertEquals(1, result.documents.size)
        assertEquals("週報運用ガイド", result.documents.first().title)
        assertEquals(0, result.documents.first().chunkIndex)
        assertNull(result.documents.first().distanceScore)
        assertNull(result.documents.first().similarityScore)
    }

    @Test
    fun `retrieveKnowledge includes distance score for vector matches`() {
        val repository = mock<KnowledgeDocumentChunkRepository>()
        val weeklyReportChunk = KnowledgeDocumentChunk(
            id = 1,
            knowledgeDocument = KnowledgeDocument(
                id = 1,
                title = "週報運用ガイド",
                content = "週報は毎週金曜までに提出し、1on1で振り返る。",
                createdAt = Instant.parse("2026-04-06T00:00:00Z")
            ),
            chunkIndex = 0,
            content = "週報は毎週金曜までに提出し、1on1で振り返る。",
            createdAt = Instant.parse("2026-04-06T00:00:00Z")
        )
        whenever(repository.findAllById(listOf(1L))).thenReturn(listOf(weeklyReportChunk))

        val embeddingService = mock<KnowledgeEmbeddingService>()
        whenever(embeddingService.embedQuery(org.mockito.kotlin.any())).thenReturn(List(1536) { 0.1f })
        val meterRegistry = SimpleMeterRegistry()
        val retrievalMetrics = KnowledgeRetrievalMetrics(meterRegistry)
        val knowledgeAccessControlService = mock<KnowledgeAccessControlService>()
        whenever(knowledgeAccessControlService.canAccess(org.mockito.kotlin.any())).thenReturn(true)

        val pgVectorChunkSearchRepository = mock<PgVectorChunkSearchRepository>()
        whenever(pgVectorChunkSearchRepository.findNearestChunks(org.mockito.kotlin.any(), org.mockito.kotlin.eq(3))).thenReturn(
            listOf(
                ChunkVectorMatch(
                    chunkId = 1,
                    documentId = 1,
                    title = "週報運用ガイド",
                    chunkIndex = 0,
                    content = "週報は毎週金曜までに提出し、1on1で振り返る。",
                    distanceScore = 0.1234
                )
            )
        )

        val result = KnowledgeRetrievalService(
            ragProperties = RagProperties(vectorSearchEnabled = true, topK = 3),
            knowledgeDocumentChunkRepository = repository,
            knowledgeEmbeddingService = embeddingService,
            pgVectorChunkSearchRepository = pgVectorChunkSearchRepository,
            knowledgeRetrievalMetrics = retrievalMetrics,
            knowledgeAccessControlService = knowledgeAccessControlService
        ).retrieveKnowledge(
            query = "週報の提出が遅れているので1on1で伝えたい"
        )

        assertEquals(1, result.documents.size)
        assertEquals("週報運用ガイド", result.documents.first().title)
        assertEquals(0.1234, result.documents.first().distanceScore)
        assertEquals(0.8766, result.documents.first().similarityScore)
        assertEquals(
            1.0,
            meterRegistry.get("knowledge.retrieval.vector.accepted")
                .counter()
                .count()
        )
    }

    @Test
    fun `retrieveKnowledge excludes vector matches below similarity threshold and falls back to keyword search`() {
        val repository = mock<KnowledgeDocumentChunkRepository>()
        val weeklyReportChunk = KnowledgeDocumentChunk(
            id = 1,
            knowledgeDocument = KnowledgeDocument(
                id = 1,
                title = "週報運用ガイド",
                content = "週報は毎週金曜までに提出し、1on1で振り返る。",
                createdAt = Instant.parse("2026-04-06T00:00:00Z")
            ),
            chunkIndex = 0,
            content = "週報は毎週金曜までに提出し、1on1で振り返る。",
            createdAt = Instant.parse("2026-04-06T00:00:00Z")
        )
        whenever(repository.findAllById(listOf(1L))).thenReturn(listOf(weeklyReportChunk))
        whenever(repository.findAll()).thenReturn(listOf(weeklyReportChunk))

        val embeddingService = mock<KnowledgeEmbeddingService>()
        whenever(embeddingService.embedQuery(org.mockito.kotlin.any())).thenReturn(List(1536) { 0.1f })
        val meterRegistry = SimpleMeterRegistry()
        val retrievalMetrics = KnowledgeRetrievalMetrics(meterRegistry)
        val knowledgeAccessControlService = mock<KnowledgeAccessControlService>()
        whenever(knowledgeAccessControlService.canAccess(org.mockito.kotlin.any())).thenReturn(true)

        val pgVectorChunkSearchRepository = mock<PgVectorChunkSearchRepository>()
        whenever(pgVectorChunkSearchRepository.findNearestChunks(org.mockito.kotlin.any(), org.mockito.kotlin.eq(3))).thenReturn(
            listOf(
                ChunkVectorMatch(
                    chunkId = 1,
                    documentId = 1,
                    title = "週報運用ガイド",
                    chunkIndex = 0,
                    content = "週報は毎週金曜までに提出し、1on1で振り返る。",
                    distanceScore = 0.4
                )
            )
        )

        val result = KnowledgeRetrievalService(
            ragProperties = RagProperties(vectorSearchEnabled = true, topK = 3, minSimilarityScore = 0.7),
            knowledgeDocumentChunkRepository = repository,
            knowledgeEmbeddingService = embeddingService,
            pgVectorChunkSearchRepository = pgVectorChunkSearchRepository,
            knowledgeRetrievalMetrics = retrievalMetrics,
            knowledgeAccessControlService = knowledgeAccessControlService
        ).retrieveKnowledge(
            query = "週報の提出が遅れているので1on1で伝えたい"
        )

        assertEquals(1, result.documents.size)
        assertEquals("週報運用ガイド", result.documents.first().title)
        assertNull(result.documents.first().distanceScore)
        assertNull(result.documents.first().similarityScore)
        verify(repository).findAll()
        assertEquals(
            1.0,
            meterRegistry.get("knowledge.retrieval.vector.threshold.filtered")
                .counter()
                .count()
        )
        assertEquals(
            1.0,
            meterRegistry.get("knowledge.retrieval.vector.threshold.fallback")
                .counter()
                .count()
        )
    }

    @Test
    fun `retrieveKnowledge reranks vector matches when enabled`() {
        val repository = mock<KnowledgeDocumentChunkRepository>()
        val weakLexicalChunk = KnowledgeDocumentChunk(
            id = 1,
            knowledgeDocument = KnowledgeDocument(
                id = 1,
                title = "一般ガイド",
                content = "業務の基本をまとめた文書。",
                createdAt = Instant.parse("2026-04-06T00:00:00Z")
            ),
            chunkIndex = 0,
            content = "業務の基本をまとめた文書。",
            createdAt = Instant.parse("2026-04-06T00:00:00Z")
        )
        val strongLexicalChunk = KnowledgeDocumentChunk(
            id = 2,
            knowledgeDocument = KnowledgeDocument(
                id = 2,
                title = "週報 1on1 改善ガイド",
                content = "週報提出の遅れを 1on1 で改善する方法。",
                createdAt = Instant.parse("2026-04-06T00:00:00Z")
            ),
            chunkIndex = 0,
            content = "週報提出の遅れを 1on1 で改善する方法。",
            createdAt = Instant.parse("2026-04-06T00:00:00Z")
        )
        whenever(repository.findAllById(listOf(1L, 2L))).thenReturn(listOf(weakLexicalChunk, strongLexicalChunk))

        val embeddingService = mock<KnowledgeEmbeddingService>()
        whenever(embeddingService.embedQuery(org.mockito.kotlin.any())).thenReturn(List(1536) { 0.1f })
        val retrievalMetrics = KnowledgeRetrievalMetrics(SimpleMeterRegistry())
        val knowledgeAccessControlService = mock<KnowledgeAccessControlService>()
        whenever(knowledgeAccessControlService.canAccess(org.mockito.kotlin.any())).thenReturn(true)

        val pgVectorChunkSearchRepository = mock<PgVectorChunkSearchRepository>()
        whenever(pgVectorChunkSearchRepository.findNearestChunks(org.mockito.kotlin.any(), org.mockito.kotlin.eq(3))).thenReturn(
            listOf(
                ChunkVectorMatch(
                    chunkId = 1,
                    documentId = 1,
                    title = "一般ガイド",
                    chunkIndex = 0,
                    content = "業務の基本をまとめた文書。",
                    distanceScore = 0.05
                ),
                ChunkVectorMatch(
                    chunkId = 2,
                    documentId = 2,
                    title = "週報 1on1 改善ガイド",
                    chunkIndex = 0,
                    content = "週報提出の遅れを 1on1 で改善する方法。",
                    distanceScore = 0.20
                )
            )
        )

        val result = KnowledgeRetrievalService(
            ragProperties = RagProperties(
                vectorSearchEnabled = true,
                topK = 1,
                rerankEnabled = true,
                rerankCandidateMultiplier = 3
            ),
            knowledgeDocumentChunkRepository = repository,
            knowledgeEmbeddingService = embeddingService,
            pgVectorChunkSearchRepository = pgVectorChunkSearchRepository,
            knowledgeRetrievalMetrics = retrievalMetrics,
            knowledgeAccessControlService = knowledgeAccessControlService
        ).retrieveKnowledge(
            query = "週報の提出が遅れているので1on1で改善したい"
        )

        assertEquals(1, result.documents.size)
        assertEquals("週報 1on1 改善ガイド", result.documents.first().title)
    }
}
