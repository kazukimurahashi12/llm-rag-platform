package com.example.llmragplatform.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.security")
data class SecurityProperties(
    val admin: User = User(),
    val operator: User = User(username = "operator", password = "operator-pass", roles = listOf("OPERATOR")),
    val jwt: Jwt = Jwt(),
) {
    data class User(
        val username: String = "admin",
        val password: String = "change-me",
        val roles: List<String> = listOf("ADMIN"),
    )

    data class Jwt(
        val secret: String = "change-this-jwt-secret-in-production-at-least-32-bytes",
        val expirationSeconds: Long = 3600,
        val issuer: String = "llm-rag-platform",
    )
}
