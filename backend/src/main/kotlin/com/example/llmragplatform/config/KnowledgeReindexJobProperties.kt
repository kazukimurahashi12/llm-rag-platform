package com.example.llmragplatform.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "app.reindex-jobs")
data class KnowledgeReindexJobProperties(
    val cleanupEnabled: Boolean = true,
    val retention: Duration = Duration.ofDays(7),
    val cleanupIntervalMs: Long = Duration.ofHours(1).toMillis(),
)
