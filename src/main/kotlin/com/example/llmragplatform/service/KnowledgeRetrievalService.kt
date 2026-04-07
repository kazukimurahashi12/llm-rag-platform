package com.example.llmragplatform.service

import com.example.llmragplatform.infrastructure.repository.KnowledgeDocumentRepository
import org.springframework.stereotype.Service
import java.util.Locale

@Service
class KnowledgeRetrievalService(
    private val knowledgeDocumentRepository: KnowledgeDocumentRepository,
) {

    fun retrieveKnowledge(query: String, topK: Int = 2): RetrievedKnowledge {
        val keywords = extractKeywords(query)
        if (keywords.isEmpty()) {
            return RetrievedKnowledge(
                promptContext = "追加ナレッジなし",
                documents = emptyList()
            )
        }

        val matchedDocuments = knowledgeDocumentRepository.findAll()
            .map { document ->
                val searchableText = "${document.title} ${document.content}".lowercase(Locale.getDefault())
                val score = keywords.count { keyword -> searchableText.contains(keyword) }
                document to score
            }
            .filter { (_, score) -> score > 0 }
            .sortedWith(compareByDescending<Pair<*, Int>> { it.second })
            .take(topK)
            .map { (document, _) -> document as com.example.llmragplatform.domain.entity.KnowledgeDocument }

        if (matchedDocuments.isEmpty()) {
            return RetrievedKnowledge(
                promptContext = "追加ナレッジなし",
                documents = emptyList()
            )
        }

        val documents = matchedDocuments.map { document ->
            RetrievedKnowledgeDocument(
                id = document.id,
                title = document.title,
                excerpt = document.content.take(200)
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
}
