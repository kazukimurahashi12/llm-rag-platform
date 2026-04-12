package com.example.llmragplatform.service

import com.example.llmragplatform.config.RagProperties
import com.example.llmragplatform.domain.EmbeddingClient
import com.example.llmragplatform.domain.entity.KnowledgeDocumentChunk
import com.example.llmragplatform.infrastructure.repository.PgVectorChunkSearchRepository
import org.springframework.stereotype.Service

@Service
class KnowledgeEmbeddingService(
    private val ragProperties: RagProperties,
    private val embeddingClient: EmbeddingClient,
    private val pgVectorChunkSearchRepository: PgVectorChunkSearchRepository,
) {

    fun enrichChunks(chunks: List<KnowledgeDocumentChunk>): Int {
        if (!ragProperties.vectorSearchEnabled) {
            return 0
        }

        var updatedCount = 0
        chunks.forEach { chunk ->
            val embedding = embeddingClient.embed(chunk.content)
            pgVectorChunkSearchRepository.saveEmbedding(chunk.id, embedding)
            updatedCount += 1
        }
        return updatedCount
    }

    fun embedQuery(query: String): List<Float>? {
        if (!ragProperties.vectorSearchEnabled) {
            return null
        }
        return embeddingClient.embed(query)
    }
}
