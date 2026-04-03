package com.example.llmragplatform.infrastructure

import com.example.llmragplatform.domain.LlmClient
import com.example.llmragplatform.domain.LlmResponse
import com.example.llmragplatform.exception.OpenAiIntegrationException
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class OpenAiLlmClient(
    private val openAiRestClient: RestClient,
) : LlmClient {

    override fun chat(model: String, systemPrompt: String, userMessage: String): LlmResponse {
        val payload = mapOf(
            "model" to model,
            "instructions" to systemPrompt,
            "input" to listOf(
                mapOf(
                    "role" to "user",
                    "content" to listOf(
                        mapOf(
                            "type" to "input_text",
                            "text" to userMessage
                        )
                    )
                )
            )
        )

        val response = openAiRestClient.post()
            .uri("/responses")
            .body(payload)
            .retrieve()
            .body(JsonNode::class.java)
            ?: throw OpenAiIntegrationException("Empty response returned from OpenAI")

        return LlmResponse(
            content = extractContent(response),
            model = model,
            promptTokens = response.path("usage").path("input_tokens").asInt(0),
            completionTokens = response.path("usage").path("output_tokens").asInt(0)
        )
    }

    private fun extractContent(response: JsonNode): String {
        response.path("output").forEach { outputItem ->
            outputItem.path("content").forEach { contentItem ->
                val text = contentItem.path("text").asText("")
                if (text.isNotBlank()) {
                    return text
                }
            }
        }

        val outputText = response.path("output_text").asText("")
        if (outputText.isNotBlank()) {
            return outputText
        }

        throw OpenAiIntegrationException(
            message = "OpenAI response did not contain advice text",
            details = listOf(response.toPrettyString().take(1000))
        )
    }
}
