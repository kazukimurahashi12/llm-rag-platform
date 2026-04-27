package com.example.llmragplatform.service

/**
 * retrieval の結果を prompt 用文脈と根拠文書一覧でまとめたモデル。
 *
 * @property promptContext prompt に差し込むため連結済みの文脈文字列。
 * @property documents API 応答用の根拠文書一覧。
 */
data class RetrievedKnowledge(
    val promptContext: String,
    val documents: List<RetrievedKnowledgeDocument>,
)

/**
 * retrieval で取得した 1 件分の根拠文書情報を表すモデル。
 *
 * @property id 元文書 ID。
 * @property title 元文書タイトル。
 * @property excerpt API 返却用の本文抜粋。
 * @property chunkIndex 文書内の chunk 順番。
 * @property distanceScore vector 検索時の距離スコア。
 * @property similarityScore vector 検索時の利用者向け類似度スコア。
 */
data class RetrievedKnowledgeDocument(
    val id: Long,
    val title: String,
    val excerpt: String,
    val chunkIndex: Int,
    val distanceScore: Double?,
    val similarityScore: Double?,
)
