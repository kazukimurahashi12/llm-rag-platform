package com.example.llmragplatform.service

import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component

@Component
class PromptManager {

    private val templateCache = mutableMapOf<String, String>()

    fun buildPrompt(templateName: String, variables: Map<String, String>): String {
        val template = loadTemplate(templateName)
        return variables.entries.fold(template) { acc, (key, value) ->
            acc.replace("{{$key}}", value)
        }
    }

    private fun loadTemplate(name: String): String {
        return templateCache.getOrPut(name) {
            ClassPathResource("prompts/$name.txt")
                .inputStream
                .bufferedReader()
                .use { it.readText() }
        }
    }
}
