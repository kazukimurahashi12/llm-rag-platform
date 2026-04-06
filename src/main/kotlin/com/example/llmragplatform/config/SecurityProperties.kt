package com.example.llmragplatform.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.security")
data class SecurityProperties(
    val admin: User = User(),
    val operator: User = User(username = "operator", password = "operator-pass", roles = listOf("OPERATOR")),
) {
    data class User(
        val username: String = "admin",
        val password: String = "change-me",
        val roles: List<String> = listOf("ADMIN"),
    )
}
