package com.example.llmragplatform.infrastructure.repository

import com.example.llmragplatform.domain.entity.KnowledgeReindexJob
import com.example.llmragplatform.domain.entity.KnowledgeReindexJobStatus
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import jakarta.persistence.criteria.Predicate
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class KnowledgeReindexJobRepositoryImpl : KnowledgeReindexJobRepositoryCustom {

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    override fun search(
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
    ): KnowledgeReindexJobSearchResult {
        val criteriaBuilder = entityManager.criteriaBuilder

        val itemQuery = criteriaBuilder.createQuery(KnowledgeReindexJob::class.java)
        val itemRoot = itemQuery.from(KnowledgeReindexJob::class.java)
        val itemPredicates = buildPredicates(
            status = status,
            knowledgeDocumentId = knowledgeDocumentId,
            acceptedFrom = acceptedFrom,
            acceptedTo = acceptedTo,
            completedFrom = completedFrom,
            completedTo = completedTo,
            root = itemRoot,
        )
        itemQuery.select(itemRoot)
            .where(*itemPredicates.toTypedArray())
            .orderBy(
                when (sortDirection) {
                    KnowledgeReindexJobSortDirection.ASC -> criteriaBuilder.asc(itemRoot.get<Instant>(sortProperty(sortBy)))
                    KnowledgeReindexJobSortDirection.DESC -> criteriaBuilder.desc(itemRoot.get<Instant>(sortProperty(sortBy)))
                },
                criteriaBuilder.desc(itemRoot.get<Instant>("acceptedAt"))
            )

        val items = entityManager.createQuery(itemQuery)
            .setFirstResult(offset)
            .setMaxResults(limit)
            .resultList

        val countQuery = criteriaBuilder.createQuery(Long::class.java)
        val countRoot = countQuery.from(KnowledgeReindexJob::class.java)
        val countPredicates = buildPredicates(
            status = status,
            knowledgeDocumentId = knowledgeDocumentId,
            acceptedFrom = acceptedFrom,
            acceptedTo = acceptedTo,
            completedFrom = completedFrom,
            completedTo = completedTo,
            root = countRoot,
        )
        countQuery.select(criteriaBuilder.count(countRoot))
            .where(*countPredicates.toTypedArray())

        val totalCount = entityManager.createQuery(countQuery).singleResult

        return KnowledgeReindexJobSearchResult(items = items, totalCount = totalCount)
    }

    private fun sortProperty(sortBy: KnowledgeReindexJobSortBy): String {
        return when (sortBy) {
            KnowledgeReindexJobSortBy.ACCEPTED_AT -> "acceptedAt"
            KnowledgeReindexJobSortBy.COMPLETED_AT -> "completedAt"
        }
    }

    private fun buildPredicates(
        status: KnowledgeReindexJobStatus?,
        knowledgeDocumentId: Long?,
        acceptedFrom: Instant?,
        acceptedTo: Instant?,
        completedFrom: Instant?,
        completedTo: Instant?,
        root: jakarta.persistence.criteria.Root<KnowledgeReindexJob>,
    ): List<Predicate> {
        val criteriaBuilder = entityManager.criteriaBuilder
        val predicates = mutableListOf<Predicate>()

        status?.let {
            predicates += criteriaBuilder.equal(root.get<KnowledgeReindexJobStatus>("status"), it)
        }
        knowledgeDocumentId?.let {
            predicates += criteriaBuilder.equal(root.get<Long>("knowledgeDocumentId"), it)
        }
        acceptedFrom?.let {
            predicates += criteriaBuilder.greaterThanOrEqualTo(root.get("acceptedAt"), it)
        }
        acceptedTo?.let {
            predicates += criteriaBuilder.lessThanOrEqualTo(root.get("acceptedAt"), it)
        }
        completedFrom?.let {
            predicates += criteriaBuilder.isNotNull(root.get<Instant>("completedAt"))
            predicates += criteriaBuilder.greaterThanOrEqualTo(root.get("completedAt"), it)
        }
        completedTo?.let {
            predicates += criteriaBuilder.isNotNull(root.get<Instant>("completedAt"))
            predicates += criteriaBuilder.lessThanOrEqualTo(root.get("completedAt"), it)
        }

        return predicates
    }
}
