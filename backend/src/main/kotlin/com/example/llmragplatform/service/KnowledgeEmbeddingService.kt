package com.example.llmragplatform.service

import com.example.llmragplatform.config.RagProperties
import com.example.llmragplatform.domain.EmbeddingClient
import com.example.llmragplatform.domain.entity.KnowledgeDocumentChunk
import com.example.llmragplatform.infrastructure.repository.PgVectorChunkSearchRepository
import org.springframework.stereotype.Service

@Service
/**
 * 文書 chunk や検索 query に対して embedding を生成するサービス。
 */
class KnowledgeEmbeddingService(
    private val ragProperties: RagProperties,
    private val embeddingClient: EmbeddingClient,
    private val pgVectorChunkSearchRepository: PgVectorChunkSearchRepository,
) {

    /**
     * chunk 一覧へ embedding を付与し、pgvector 列へ保存する。
     *
     * @param chunks embedding 付与対象の chunk 一覧。
     * @return 実際に更新した chunk 件数。
     */
    fun enrichChunks(chunks: List<KnowledgeDocumentChunk>): Int {
        // vector 検索が無効なら embedding は付与せず 0 件更新として返す。
        if (!ragProperties.vectorSearchEnabled) {
            return 0
        }

        // 実際に更新できた件数を数えるためのカウンタを用意する。
        var updatedCount = 0
        // 渡された chunk を 1 件ずつ処理する。
        chunks.forEach { chunk ->
            // chunk 本文から embedding を生成する。
            val embedding = embeddingClient.embed(chunk.content)
            // 生成した embedding を該当 chunk に保存する。
            pgVectorChunkSearchRepository.saveEmbedding(chunk.id, embedding)
            // 更新件数を 1 件増やす。
            updatedCount += 1
        }
        // 更新できた総件数を返す。
        return updatedCount
    }

    /**
     * 検索クエリ文字列を vector 検索用 embedding へ変換する。
     *
     * @param query embedding 化したい検索クエリ。
     * @return vector 検索有効時は embedding、無効時は null。
     */
    fun embedQuery(query: String): List<Float>? {
        // vector 検索が無効なら query embedding は作らず null を返す。
        if (!ragProperties.vectorSearchEnabled) {
            return null
        }
        // 検索クエリ文字列から embedding を生成して返す。
        return embeddingClient.embed(query)
    }
}
