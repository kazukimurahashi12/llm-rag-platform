package com.example.llmragplatform.domain

interface EmbeddingClient {
    fun embed(input: String): List<Float>
}
