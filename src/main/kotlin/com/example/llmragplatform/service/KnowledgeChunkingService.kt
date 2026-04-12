package com.example.llmragplatform.service

import org.springframework.stereotype.Service

@Service
class KnowledgeChunkingService {

    fun chunk(content: String, chunkSize: Int = 180, overlap: Int = 40): List<String> {
        val normalized = content.trim().replace(Regex("""\s+"""), " ")
        if (normalized.isBlank()) {
            return emptyList()
        }

        if (normalized.length <= chunkSize) {
            return listOf(normalized)
        }

        val chunks = mutableListOf<String>()
        var start = 0

        while (start < normalized.length) {
            val end = minOf(start + chunkSize, normalized.length)
            chunks += normalized.substring(start, end).trim()
            if (end == normalized.length) {
                break
            }
            start = maxOf(0, end - overlap)
        }

        return chunks
    }
}
