package com.example.llmragplatform.infrastructure.repository

import com.example.llmragplatform.config.RagProperties
import org.springframework.jdbc.core.ConnectionCallback
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class PgVectorChunkSearchRepository(
    private val jdbcTemplate: JdbcTemplate,
    private val ragProperties: RagProperties,
) {
    companion object {
        // ivfflat index 利用時の recall を上げるため、検索時の probes 数を固定で上げておく。
        private const val ivfFlatProbeCount = 100
    }

    fun saveEmbedding(chunkId: Long, embedding: List<Float>) {
        // 該当 chunk の embedding 列へ、vector 型として埋め込んだ値を保存する。
        jdbcTemplate.update(
            // PostgreSQL の vector 型へ cast して update する。
            "update knowledge_document_chunks set embedding = cast(? as vector) where id = ?",
            // embedding 配列を pgvector が受け取れる文字列表現へ変換する。
            toVectorLiteral(embedding),
            // 更新対象 chunk の ID を渡す。
            chunkId
        )
    }

    fun findNearestChunks(queryEmbedding: List<Float>, limit: Int): List<ChunkVectorMatch> {
        // query embedding との cosine distance を計算し、近い順に chunk を取る SQL を定義する。
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

        // 同一 connection 上で probes 設定と検索 SQL を実行する。
        return jdbcTemplate.execute(ConnectionCallback<List<ChunkVectorMatch>> { connection ->
            // ivfflat index の探索範囲を広げ、少量データでもヒットしやすくする。
            connection.createStatement().use { statement ->
                statement.execute("SET ivfflat.probes = $ivfFlatProbeCount")
            }

            // 近傍検索 SQL を prepared statement として作成する。
            connection.prepareStatement(sql).use { preparedStatement ->
                // 距離計算用の query embedding を 1 つ目のプレースホルダへ設定する。
                preparedStatement.setString(1, toVectorLiteral(queryEmbedding))
                // order by 用の query embedding を 2 つ目のプレースホルダへ設定する。
                preparedStatement.setString(2, toVectorLiteral(queryEmbedding))
                // 取得件数上限を設定する。
                preparedStatement.setInt(3, limit.coerceAtLeast(1))

                // SQL を実行し、結果セットをアプリ側モデルへ変換する。
                preparedStatement.executeQuery().use { resultSet ->
                    buildList {
                        // 1 行ずつ RowMapper で変換するために mapper を用意する。
                        val rowMapper = rowMapper()
                        // RowMapper へ渡す行番号を初期化する。
                        var rowNum = 0
                        // 結果セットを最後まで読み進める。
                        while (resultSet.next()) {
                            // 1 行分を ChunkVectorMatch へ変換して結果リストへ追加する。
                            add(rowMapper.mapRow(resultSet, rowNum++)!!)
                        }
                    }
                }
            }
        }) ?: emptyList()
    }

    private fun toVectorLiteral(embedding: List<Float>): String {
        // 設定された次元数と一致しない embedding は保存・検索に使わない。
        require(embedding.size == ragProperties.embeddingDimensions) {
            "Expected embedding dimension ${ragProperties.embeddingDimensions} but was ${embedding.size}"
        }
        // pgvector が解釈できる `[x,y,z]` 形式の文字列へ変換する。
        return embedding.joinToString(prefix = "[", postfix = "]", separator = ",")
    }

    private fun rowMapper(): RowMapper<ChunkVectorMatch> {
        // SQL の 1 行をアプリ内の検索結果モデルへ変換する mapper を返す。
        return RowMapper { rs, _ ->
            ChunkVectorMatch(
                // chunk ID を設定する。
                chunkId = rs.getLong("id"),
                // 親文書 ID を設定する。
                documentId = rs.getLong("knowledge_document_id"),
                // 親文書タイトルを設定する。
                title = rs.getString("title"),
                // chunk の順番を設定する。
                chunkIndex = rs.getInt("chunk_index"),
                // chunk 本文を設定する。
                content = rs.getString("content"),
                // pgvector が返した距離スコアを設定する。
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
