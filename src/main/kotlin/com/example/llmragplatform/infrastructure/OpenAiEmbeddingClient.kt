package com.example.llmragplatform.infrastructure

import com.example.llmragplatform.config.RagProperties
import com.example.llmragplatform.domain.EmbeddingClient
import com.example.llmragplatform.exception.OpenAiIntegrationException
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class OpenAiEmbeddingClient(
    private val openAiRestClient: RestClient,
    private val ragProperties: RagProperties,
) : EmbeddingClient {

    override fun embed(input: String): List<Float> {
        val response = openAiRestClient.post()
            .uri("/embeddings")
            .body(
                mapOf(
                    "model" to ragProperties.embeddingModel,
                    "input" to input
                )
            )
            .retrieve()
            .body(JsonNode::class.java)
            ?: throw OpenAiIntegrationException("Empty embedding response returned from OpenAI")

        val embeddingNode = response.path("data").path(0).path("embedding")
        if (!embeddingNode.isArray || embeddingNode.isEmpty) {
            throw OpenAiIntegrationException("OpenAI embedding response did not contain vector data")
        }

        return embeddingNode.map { it.floatValue() }
    }
}
