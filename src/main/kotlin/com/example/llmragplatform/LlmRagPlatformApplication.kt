package com.example.llmragplatform

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableAsync
@EnableScheduling
class LlmRagPlatformApplication

fun main(args: Array<String>) {
    runApplication<LlmRagPlatformApplication>(*args)
}
