package com.example.llmragplatform.service

import org.springframework.stereotype.Service

@Service
/**
 * ナレッジ本文を固定長ベースで chunk 分割するサービス。
 */
class KnowledgeChunkingService {

    /**
     * 本文を chunkSize と overlap に従って複数の chunk へ分割する。
     *
     * @param content 分割対象の本文。
     * @param chunkSize 1 chunk あたりの目安文字数。
     * @param overlap 隣接 chunk 間で重ねる文字数。
     * @return 分割後の chunk 文字列一覧。
     */
    fun chunk(content: String, chunkSize: Int = 180, overlap: Int = 40): List<String> {
        // 先頭末尾空白と連続空白を整えて chunk しやすい形へ正規化する。
        val normalized = content.trim().replace(Regex("""\s+"""), " ")
        if (normalized.isBlank()) {
            // 空文字列なら chunk は 0 件として返す。
            return emptyList()
        }

        if (normalized.length <= chunkSize) {
            // 1 chunk に収まる長さならそのまま返す。
            return listOf(normalized)
        }

        // 指定サイズと overlap に従って順次 chunk を切り出す。
        val chunks = mutableListOf<String>()
        var start = 0

        while (start < normalized.length) {
            // 現在位置から chunkSize ぶんだけ切り出す。
            val end = minOf(start + chunkSize, normalized.length)
            chunks += normalized.substring(start, end).trim()
            if (end == normalized.length) {
                // 末尾に到達したら分割を終了する。
                break
            }
            // 次の chunk は overlap 分だけ戻した位置から開始する。
            start = maxOf(0, end - overlap)
        }

        return chunks
    }
}
