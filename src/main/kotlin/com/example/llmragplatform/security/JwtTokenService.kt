package com.example.llmragplatform.security

import com.example.llmragplatform.config.SecurityProperties
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Service
class JwtTokenService(
    private val securityProperties: SecurityProperties,
    private val objectMapper: ObjectMapper,
) {

    fun generateAccessToken(
        username: String,
        roles: List<String>,
        issuedAt: Instant = Instant.now(),
    ): JwtAccessToken {
        val expiresAt = issuedAt.plusSeconds(securityProperties.jwt.expirationSeconds)
        val header = encodeJson(
            mapOf(
                "alg" to "HS256",
                "typ" to "JWT"
            )
        )
        val payload = encodeJson(
            mapOf(
                "sub" to username,
                "roles" to roles,
                "iss" to securityProperties.jwt.issuer,
                "iat" to issuedAt.epochSecond,
                "exp" to expiresAt.epochSecond
            )
        )
        val signingInput = "$header.$payload"
        val signature = sign(signingInput)
        return JwtAccessToken(
            token = "$signingInput.$signature",
            expiresAt = expiresAt
        )
    }

    fun parseAndValidate(token: String, now: Instant = Instant.now()): JwtClaims {
        val segments = token.split('.')
        require(segments.size == 3) { "Invalid JWT format" }

        val signingInput = "${segments[0]}.${segments[1]}"
        val expectedSignature = sign(signingInput)
        val providedSignature = segments[2]
        require(
            MessageDigest.isEqual(
                expectedSignature.toByteArray(StandardCharsets.UTF_8),
                providedSignature.toByteArray(StandardCharsets.UTF_8)
            )
        ) { "Invalid JWT signature" }

        val payloadNode = decodeJson(segments[1])
        val issuer = payloadNode.path("iss").asText()
        require(issuer == securityProperties.jwt.issuer) { "Invalid JWT issuer" }

        val subject = payloadNode.path("sub").asText()
        require(subject.isNotBlank()) { "JWT subject is required" }

        val expiresAt = Instant.ofEpochSecond(payloadNode.path("exp").asLong())
        require(expiresAt.isAfter(now)) { "JWT is expired" }

        val issuedAt = Instant.ofEpochSecond(payloadNode.path("iat").asLong())
        val roles = payloadNode.path("roles")
            .map(JsonNode::asText)
            .filter(String::isNotBlank)

        return JwtClaims(
            subject = subject,
            roles = roles,
            issuedAt = issuedAt,
            expiresAt = expiresAt
        )
    }

    private fun encodeJson(value: Any): String {
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(objectMapper.writeValueAsBytes(value))
    }

    private fun decodeJson(encoded: String): JsonNode {
        val decoded = Base64.getUrlDecoder().decode(encoded)
        return objectMapper.readTree(decoded)
    }

    private fun sign(value: String): String {
        val secretKey = SecretKeySpec(
            securityProperties.jwt.secret.toByteArray(StandardCharsets.UTF_8),
            "HmacSHA256"
        )
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(secretKey)
        val signature = mac.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(signature)
    }
}

data class JwtAccessToken(
    val token: String,
    val expiresAt: Instant,
)

data class JwtClaims(
    val subject: String,
    val roles: List<String>,
    val issuedAt: Instant,
    val expiresAt: Instant,
)
