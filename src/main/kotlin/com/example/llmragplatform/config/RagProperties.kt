package com.example.llmragplatform.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "rag")
data class RagProperties(
    val topK: Int = 3,
    val vectorSearchEnabled: Boolean = false,
    val embeddingModel: String = "text-embedding-3-small",
    val embeddingDimensions: Int = 1536,
    val minSimilarityScore: Double? = null,
    val rerankEnabled: Boolean = false,
    val rerankCandidateMultiplier: Int = 3,
)
