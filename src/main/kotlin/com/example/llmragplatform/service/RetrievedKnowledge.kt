package com.example.llmragplatform.service

data class RetrievedKnowledge(
    val promptContext: String,
    val documents: List<RetrievedKnowledgeDocument>,
)

data class RetrievedKnowledgeDocument(
    val id: Long,
    val title: String,
    val excerpt: String,
    val chunkIndex: Int,
    val distanceScore: Double?,
    val similarityScore: Double?,
)
