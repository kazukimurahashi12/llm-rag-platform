package com.example.llmragplatform.service

import com.example.llmragplatform.domain.entity.KnowledgeDocumentChunk
import com.example.llmragplatform.config.RagProperties
import com.example.llmragplatform.infrastructure.repository.KnowledgeDocumentChunkRepository
import com.example.llmragplatform.infrastructure.repository.PgVectorChunkSearchRepository
import org.springframework.stereotype.Service
import java.util.Locale

@Service
class KnowledgeRetrievalService(
    private val ragProperties: RagProperties,
    private val knowledgeDocumentChunkRepository: KnowledgeDocumentChunkRepository,
    private val knowledgeEmbeddingService: KnowledgeEmbeddingService,
    private val pgVectorChunkSearchRepository: PgVectorChunkSearchRepository,
) {

    fun retrieveKnowledge(query: String, topK: Int = ragPropertiesFallbackTopK): RetrievedKnowledge {
        val safeTopK = if (topK > 0) topK else ragProperties.topK
        val vectorMatchedChunks = retrieveByVector(query, safeTopK)
        if (vectorMatchedChunks.isNotEmpty()) {
            return toRetrievedKnowledgeFromVector(vectorMatchedChunks)
        }

        val keywords = extractKeywords(query)
        if (keywords.isEmpty()) {
            return RetrievedKnowledge(
                promptContext = "追加ナレッジなし",
                documents = emptyList()
            )
        }

        val matchedChunks = knowledgeDocumentChunkRepository.findAll()
            .map { chunk ->
                val searchableText = "${chunk.knowledgeDocument.title} ${chunk.content}".lowercase(Locale.getDefault())
                val score = keywords.count { keyword -> searchableText.contains(keyword) }
                chunk to score
            }
            .filter { (_, score) -> score > 0 }
            .sortedWith(compareByDescending<Pair<*, Int>> { it.second })
            .take(safeTopK)
            .map { (chunk, _) -> chunk as KnowledgeDocumentChunk }

        if (matchedChunks.isEmpty()) {
            return RetrievedKnowledge(
                promptContext = "追加ナレッジなし",
                documents = emptyList()
            )
        }

        return toRetrievedKnowledge(matchedChunks)
    }

    companion object {
        private const val ragPropertiesFallbackTopK = 0
    }

    private fun retrieveByVector(query: String, topK: Int): List<VectorMatchedChunk> {
        if (!ragProperties.vectorSearchEnabled) {
            return emptyList()
        }

        val queryEmbedding = knowledgeEmbeddingService.embedQuery(query) ?: return emptyList()
        val pgVectorMatches = pgVectorChunkSearchRepository.findNearestChunks(
            queryEmbedding = queryEmbedding,
            limit = topK.coerceAtLeast(1)
        )
        val chunkIds = pgVectorMatches.map { it.chunkId }
        if (chunkIds.isEmpty()) {
            return emptyList()
        }

        val chunksById = knowledgeDocumentChunkRepository.findAllById(chunkIds).associateBy { it.id }
        val matchesById = pgVectorMatches.associateBy { it.chunkId }
        return chunkIds.mapNotNull { chunkId ->
            val chunk = chunksById[chunkId] ?: return@mapNotNull null
            val match = matchesById[chunkId] ?: return@mapNotNull null
            VectorMatchedChunk(
                chunk = chunk,
                distanceScore = match.distanceScore
            )
        }
    }

    private fun toRetrievedKnowledge(chunks: List<KnowledgeDocumentChunk>): RetrievedKnowledge {
        val documents = chunks.map { chunk ->
            RetrievedKnowledgeDocument(
                id = chunk.knowledgeDocument.id,
                title = chunk.knowledgeDocument.title,
                excerpt = chunk.content.take(200),
                chunkIndex = chunk.chunkIndex,
                distanceScore = null
            )
        }

        return RetrievedKnowledge(
            promptContext = documents.joinToString("\n") { "- ${it.title}: ${it.excerpt}" },
            documents = documents
        )
    }

    private fun toRetrievedKnowledgeFromVector(chunks: List<VectorMatchedChunk>): RetrievedKnowledge {
        val documents = chunks.map { matchedChunk ->
            RetrievedKnowledgeDocument(
                id = matchedChunk.chunk.knowledgeDocument.id,
                title = matchedChunk.chunk.knowledgeDocument.title,
                excerpt = matchedChunk.chunk.content.take(200),
                chunkIndex = matchedChunk.chunk.chunkIndex,
                distanceScore = matchedChunk.distanceScore
            )
        }

        return RetrievedKnowledge(
            promptContext = documents.joinToString("\n") { "- ${it.title}: ${it.excerpt}" },
            documents = documents
        )
    }

    private fun extractKeywords(query: String): List<String> {
        val normalizedQuery = query.lowercase(Locale.getDefault())
            .replace(Regex("""[^\p{L}\p{N}\s]"""), " ")

        val wordTokens = normalizedQuery
            .split(Regex("""\s+"""))
            .filter { it.length >= 2 }

        val compactText = normalizedQuery.replace(Regex("""\s+"""), "")
        val bigramTokens = compactText
            .windowed(size = 2, step = 1, partialWindows = false)
            .distinct()

        return (wordTokens + bigramTokens).distinct()
    }

    private data class VectorMatchedChunk(
        val chunk: KnowledgeDocumentChunk,
        val distanceScore: Double,
    )
}
