package com.example.llmragplatform.config

import org.springframework.boot.autoconfigure.web.client.RestClientBuilderConfigurer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient

@Configuration
class OpenAiClientConfig {

    @Bean
    fun openAiRestClient(
        builderConfigurer: RestClientBuilderConfigurer,
        properties: OpenAiProperties,
    ): RestClient {
        val builder = RestClient.builder()
            .baseUrl("https://api.openai.com/v1")
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer ${properties.apiKey}")
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)

        return builderConfigurer.configure(builder).build()
    }
}
