package com.example.llmragplatform.service

import com.example.llmragplatform.config.SecurityProperties
import com.example.llmragplatform.domain.entity.KnowledgeDocument
import com.example.llmragplatform.domain.entity.KnowledgeDocumentAccessScope
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service

@Service
class KnowledgeAccessControlService(
    private val securityProperties: SecurityProperties,
) {

    fun currentAccessibleScopes(): Set<KnowledgeDocumentAccessScope> {
        val authentication = SecurityContextHolder.getContext().authentication
        if (authentication == null || !authentication.isAuthenticated || authentication is AnonymousAuthenticationToken) {
            return setOf(KnowledgeDocumentAccessScope.SHARED)
        }

        return if (authentication.name == securityProperties.admin.username) {
            setOf(KnowledgeDocumentAccessScope.SHARED, KnowledgeDocumentAccessScope.ADMIN_ONLY)
        } else {
            setOf(KnowledgeDocumentAccessScope.SHARED)
        }
    }

    fun canAccess(document: KnowledgeDocument): Boolean {
        if (isAdmin()) {
            return true
        }

        val currentUsername = currentUsername()
        if (currentUsername != null && currentUsername in document.allowedUsernames) {
            return true
        }

        return document.accessScope == KnowledgeDocumentAccessScope.SHARED && document.allowedUsernames.isEmpty()
    }

    private fun currentUsername(): String? {
        val authentication = SecurityContextHolder.getContext().authentication
        if (authentication == null || !authentication.isAuthenticated || authentication is AnonymousAuthenticationToken) {
            return null
        }
        return authentication.name
    }

    private fun isAdmin(): Boolean {
        return currentUsername() == securityProperties.admin.username
    }
}
