package com.example.llmragplatform.infrastructure.repository

import com.example.llmragplatform.domain.entity.KnowledgeReindexJob
import com.example.llmragplatform.domain.entity.KnowledgeReindexJobStatus
import java.time.Instant

interface KnowledgeReindexJobRepositoryCustom {
    fun search(
        limit: Int,
        offset: Int,
        status: KnowledgeReindexJobStatus?,
        knowledgeDocumentId: Long?,
        acceptedFrom: Instant?,
        acceptedTo: Instant?,
        completedFrom: Instant?,
        completedTo: Instant?,
        sortBy: KnowledgeReindexJobSortBy,
        sortDirection: KnowledgeReindexJobSortDirection,
    ): KnowledgeReindexJobSearchResult
}

data class KnowledgeReindexJobSearchResult(
    val items: List<KnowledgeReindexJob>,
    val totalCount: Long,
)

enum class KnowledgeReindexJobSortBy {
    ACCEPTED_AT,
    COMPLETED_AT,
}

enum class KnowledgeReindexJobSortDirection {
    ASC,
    DESC,
}
