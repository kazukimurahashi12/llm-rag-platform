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
        // 呼び出し側から topK が渡されていればそれを使い、0 以下なら設定値を使う。
        val safeTopK = if (topK > 0) topK else ragProperties.topK
        // まず vector 検索を試し、ヒットすればその結果を優先して返す。
        val vectorMatchedChunks = retrieveByVector(query, safeTopK)
        if (vectorMatchedChunks.isNotEmpty()) {
            // vector 検索結果を API 向けの取得結果モデルへ変換して返す。
            return toRetrievedKnowledgeFromVector(vectorMatchedChunks)
        }

        // vector 検索で取れなかった場合に備えて、キーワード検索用の token を抽出する。
        val keywords = extractKeywords(query)
        if (keywords.isEmpty()) {
            // 検索語が作れない場合は、追加ナレッジなしとして空結果を返す。
            return RetrievedKnowledge(
                promptContext = "追加ナレッジなし",
                documents = emptyList()
            )
        }

        // すべての chunk を取得し、タイトルと本文に対する簡易キーワード一致数を計算する。
        val matchedChunks = knowledgeDocumentChunkRepository.findAll()
            .map { chunk ->
                // 文書タイトルと chunk 本文を連結し、小文字化して検索しやすい文字列を作る。
                val searchableText = "${chunk.knowledgeDocument.title} ${chunk.content}".lowercase(Locale.getDefault())
                // 抽出したキーワードのうち、何個含まれるかをスコアとして数える。
                val score = keywords.count { keyword -> searchableText.contains(keyword) }
                // chunk と score を組にして後続処理へ渡す。
                chunk to score
            }
            // 1 件も一致しない chunk は除外する。
            .filter { (_, score) -> score > 0 }
            // スコアの高い順に並べる。
            .sortedWith(compareByDescending<Pair<*, Int>> { it.second })
            // 上位 topK 件だけを使う。
            .take(safeTopK)
            // score を外し、chunk だけへ戻す。
            .map { (chunk, _) -> chunk as KnowledgeDocumentChunk }

        if (matchedChunks.isEmpty()) {
            // キーワード検索でもヒットしなければ、追加ナレッジなしとして返す。
            return RetrievedKnowledge(
                promptContext = "追加ナレッジなし",
                documents = emptyList()
            )
        }

        // キーワード検索結果を取得結果モデルへ変換して返す。
        return toRetrievedKnowledge(matchedChunks)
    }

    companion object {
        private const val ragPropertiesFallbackTopK = 0
    }

    private fun retrieveByVector(query: String, topK: Int): List<VectorMatchedChunk> {
        // vector 検索が無効なら何も返さず、呼び出し元で fallback させる。
        if (!ragProperties.vectorSearchEnabled) {
            return emptyList()
        }

        // クエリ文から embedding を生成し、失敗したら fallback へ流す。
        val queryEmbedding = knowledgeEmbeddingService.embedQuery(query) ?: return emptyList()
        // pgvector で近傍検索を行い、距離スコアつきの候補を取得する。
        val pgVectorMatches = pgVectorChunkSearchRepository.findNearestChunks(
            queryEmbedding = queryEmbedding,
            limit = topK.coerceAtLeast(1)
        )
        // 検索結果から chunk ID 一覧を取り出す。
        val chunkIds = pgVectorMatches.map { it.chunkId }
        if (chunkIds.isEmpty()) {
            // 近傍検索結果が空なら fallback へ流す。
            return emptyList()
        }

        // JPA 経由で chunk 実体をまとめて取得し、ID で引ける形にする。
        val chunksById = knowledgeDocumentChunkRepository.findAllById(chunkIds).associateBy { it.id }
        // 距離スコア側も chunk ID で引ける形にする。
        val matchesById = pgVectorMatches.associateBy { it.chunkId }
        // 元の検索順位を保ったまま、chunk 実体と距離スコアを結びつける。
        return chunkIds.mapNotNull { chunkId ->
            // chunk 実体が取れなければその候補は捨てる。
            val chunk = chunksById[chunkId] ?: return@mapNotNull null
            // 距離スコアが取れなければその候補は捨てる。
            val match = matchesById[chunkId] ?: return@mapNotNull null
            // 取得した chunk と距離スコアを 1 件分の結果としてまとめる。
            VectorMatchedChunk(
                chunk = chunk,
                distanceScore = match.distanceScore
            )
        }
    }

    private fun toRetrievedKnowledge(chunks: List<KnowledgeDocumentChunk>): RetrievedKnowledge {
        // キーワード検索結果を、API 応答と prompt 生成で使う共通モデルへ変換する。
        val documents = chunks.map { chunk ->
            // 1 件分の根拠文書情報を作る。
            RetrievedKnowledgeDocument(
                // 元文書 ID を設定する。
                id = chunk.knowledgeDocument.id,
                // 元文書タイトルを設定する。
                title = chunk.knowledgeDocument.title,
                // prompt が肥大化しすぎないよう本文先頭 200 文字だけ抜粋する。
                excerpt = chunk.content.take(200),
                // どの chunk か分かるよう index を入れる。
                chunkIndex = chunk.chunkIndex,
                // キーワード検索には距離スコアがないため null を設定する。
                distanceScore = null,
                // キーワード検索には類似度スコアもないため null を設定する。
                similarityScore = null
            )
        }

        // prompt に差し込めるテキストと、根拠文書一覧をまとめて返す。
        return RetrievedKnowledge(
            // prompt 用に、タイトルと抜粋を 1 行ずつ連結する。
            promptContext = documents.joinToString("\n") { "- ${it.title}: ${it.excerpt}" },
            // API 応答用の文書一覧をそのまま返す。
            documents = documents
        )
    }

    private fun toRetrievedKnowledgeFromVector(chunks: List<VectorMatchedChunk>): RetrievedKnowledge {
        // vector 検索結果を、距離スコアつきの取得結果モデルへ変換する。
        val documents = chunks.map { matchedChunk ->
            // 1 件分の根拠文書情報を作る。
            RetrievedKnowledgeDocument(
                // 元文書 ID を設定する。
                id = matchedChunk.chunk.knowledgeDocument.id,
                // 元文書タイトルを設定する。
                title = matchedChunk.chunk.knowledgeDocument.title,
                // chunk 本文の先頭 200 文字を抜粋する。
                excerpt = matchedChunk.chunk.content.take(200),
                // どの chunk か分かるよう index を入れる。
                chunkIndex = matchedChunk.chunk.chunkIndex,
                // pgvector の距離スコアをそのまま設定する。
                distanceScore = matchedChunk.distanceScore,
                // 利用者向けには 0.0 - 1.0 の近似類似度も返す。
                similarityScore = toSimilarityScore(matchedChunk.distanceScore)
            )
        }

        // prompt に差し込めるテキストと、根拠文書一覧をまとめて返す。
        return RetrievedKnowledge(
            // prompt 用に、タイトルと抜粋を 1 行ずつ連結する。
            promptContext = documents.joinToString("\n") { "- ${it.title}: ${it.excerpt}" },
            // API 応答用の文書一覧をそのまま返す。
            documents = documents
        )
    }

    private fun extractKeywords(query: String): List<String> {
        // 記号を空白に置き換えつつ小文字化し、検索用に正規化する。
        val normalizedQuery = query.lowercase(Locale.getDefault())
            .replace(Regex("""[^\p{L}\p{N}\s]"""), " ")

        // 空白区切りで 2 文字以上の token を単語候補として抽出する。
        val wordTokens = normalizedQuery
            .split(Regex("""\s+"""))
            .filter { it.length >= 2 }

        // 日本語のように空白が少ない文でも拾えるよう、空白を除いた全文を作る。
        val compactText = normalizedQuery.replace(Regex("""\s+"""), "")
        // 2 文字ずつの bigram を作って検索語候補に加える。
        val bigramTokens = compactText
            .windowed(size = 2, step = 1, partialWindows = false)
            .distinct()

        // 単語 token と bigram token をまとめ、重複を除いて返す。
        return (wordTokens + bigramTokens).distinct()
    }

    private fun toSimilarityScore(distanceScore: Double): Double {
        return (1.0 - distanceScore).coerceIn(0.0, 1.0)
    }

    private data class VectorMatchedChunk(
        val chunk: KnowledgeDocumentChunk,
        val distanceScore: Double,
    )
}
