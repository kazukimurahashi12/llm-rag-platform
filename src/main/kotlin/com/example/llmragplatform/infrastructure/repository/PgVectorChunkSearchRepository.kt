package com.example.llmragplatform.infrastructure.repository

import com.example.llmragplatform.config.RagProperties
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository

@Repository
class PgVectorChunkSearchRepository(
    private val jdbcTemplate: JdbcTemplate,
    private val ragProperties: RagProperties,
) {

    fun saveEmbedding(chunkId: Long, embedding: List<Float>) {
        jdbcTemplate.update(
            "update knowledge_document_chunks set embedding = cast(? as vector) where id = ?",
            toVectorLiteral(embedding),
            chunkId
        )
    }

    fun findNearestChunks(queryEmbedding: List<Float>, limit: Int): List<ChunkVectorMatch> {
        val sql = """
            select c.id,
                   c.knowledge_document_id,
                   d.title,
                   c.chunk_index,
                   c.content,
                   c.embedding <=> cast(? as vector) as distance_score
            from knowledge_document_chunks c
            join knowledge_documents d on d.id = c.knowledge_document_id
            where c.embedding is not null
            order by c.embedding <=> cast(? as vector)
            limit ?
        """.trimIndent()

        return jdbcTemplate.query(
            sql,
            rowMapper(),
            toVectorLiteral(queryEmbedding),
            toVectorLiteral(queryEmbedding),
            limit.coerceAtLeast(1)
        )
    }

    private fun toVectorLiteral(embedding: List<Float>): String {
        require(embedding.size == ragProperties.embeddingDimensions) {
            "Expected embedding dimension ${ragProperties.embeddingDimensions} but was ${embedding.size}"
        }
        return embedding.joinToString(prefix = "[", postfix = "]", separator = ",")
    }

    private fun rowMapper(): RowMapper<ChunkVectorMatch> {
        return RowMapper { rs, _ ->
            ChunkVectorMatch(
                chunkId = rs.getLong("id"),
                documentId = rs.getLong("knowledge_document_id"),
                title = rs.getString("title"),
                chunkIndex = rs.getInt("chunk_index"),
                content = rs.getString("content"),
                distanceScore = rs.getDouble("distance_score")
            )
        }
    }
}

data class ChunkVectorMatch(
    val chunkId: Long,
    val documentId: Long,
    val title: String,
    val chunkIndex: Int,
    val content: String,
    val distanceScore: Double,
)
