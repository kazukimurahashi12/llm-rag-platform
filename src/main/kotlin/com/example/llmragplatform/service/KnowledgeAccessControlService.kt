package com.example.llmragplatform.service

import com.example.llmragplatform.config.SecurityProperties
import com.example.llmragplatform.domain.entity.KnowledgeDocument
import com.example.llmragplatform.domain.entity.KnowledgeDocumentAccessScope
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service

@Service
/**
 * 現在の認証情報に基づいてナレッジ文書の参照可否を判定するサービス。
 */
class KnowledgeAccessControlService(
    private val securityProperties: SecurityProperties,
) {

    /**
     * 現在の利用者が参照可能な access scope 一覧を返す。
     *
     * @return 現在ユーザーに許可される access scope の集合。
     */
    fun currentAccessibleScopes(): Set<KnowledgeDocumentAccessScope> {
        // 認証情報を取り出し、未認証なら共有文書だけを許可する。
        val authentication = SecurityContextHolder.getContext().authentication
        if (authentication == null || !authentication.isAuthenticated || authentication is AnonymousAuthenticationToken) {
            return setOf(KnowledgeDocumentAccessScope.SHARED)
        }

        // admin は共有・管理者専用の両方、それ以外は共有だけを許可する。
        return if (authentication.name == securityProperties.admin.username) {
            setOf(KnowledgeDocumentAccessScope.SHARED, KnowledgeDocumentAccessScope.ADMIN_ONLY)
        } else {
            setOf(KnowledgeDocumentAccessScope.SHARED)
        }
    }

    /**
     * 指定した文書を現在の利用者が参照できるか判定する。
     *
     * @param document 判定対象のナレッジ文書。
     * @return 参照可能な場合は true。
     */
    fun canAccess(document: KnowledgeDocument): Boolean {
        if (isAdmin()) {
            // admin はすべての文書へアクセスできる。
            return true
        }

        // 明示許可ユーザーに現在ユーザーが含まれていればアクセス可とする。
        val currentUsername = currentUsername()
        if (currentUsername != null && currentUsername in document.allowedUsernames) {
            return true
        }

        // それ以外は shared かつ明示許可なしの文書だけを公開する。
        return document.accessScope == KnowledgeDocumentAccessScope.SHARED && document.allowedUsernames.isEmpty()
    }

    /**
     * 現在の認証情報からユーザー名を取得する。
     *
     * @return 認証済みユーザー名。未認証の場合は null。
     */
    private fun currentUsername(): String? {
        // 認証情報がなければ現在ユーザーなしとして扱う。
        val authentication = SecurityContextHolder.getContext().authentication
        if (authentication == null || !authentication.isAuthenticated || authentication is AnonymousAuthenticationToken) {
            return null
        }
        // 認証済みなら principal 名を返す。
        return authentication.name
    }

    /**
     * 現在の利用者が admin ユーザーかどうかを判定する。
     *
     * @return admin の場合は true。
     */
    private fun isAdmin(): Boolean {
        // 現在ユーザー名が設定済み admin と一致するかで判定する。
        return currentUsername() == securityProperties.admin.username
    }
}
