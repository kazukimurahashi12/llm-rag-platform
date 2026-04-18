package com.example.llmragplatform.service

import com.example.llmragplatform.exception.InvalidCredentialsException
import com.example.llmragplatform.generated.model.AuthTokenResponse
import com.example.llmragplatform.security.JwtTokenService
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Service
class AuthService(
    private val userDetailsService: UserDetailsService,
    private val passwordEncoder: PasswordEncoder,
    private val jwtTokenService: JwtTokenService,
) {

    fun issueToken(username: String, password: String): AuthTokenResponse {
        val userDetails = try {
            userDetailsService.loadUserByUsername(username)
        } catch (_: UsernameNotFoundException) {
            throw InvalidCredentialsException()
        }

        if (!passwordEncoder.matches(password, userDetails.password)) {
            throw InvalidCredentialsException()
        }

        val roles = userDetails.authorities
            .map { authority -> authority.authority.removePrefix("ROLE_") }
            .filter(String::isNotBlank)

        val accessToken = jwtTokenService.generateAccessToken(
            username = userDetails.username,
            roles = roles
        )

        return AuthTokenResponse()
            .accessToken(accessToken.token)
            .tokenType("Bearer")
            .expiresAt(OffsetDateTime.ofInstant(accessToken.expiresAt, ZoneOffset.UTC))
            .username(userDetails.username)
            .roles(roles)
    }
}
