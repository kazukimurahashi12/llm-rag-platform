package com.example.llmragplatform.security

import com.example.llmragplatform.generated.model.ErrorResponse
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtTokenService: JwtTokenService,
    private val objectMapper: ObjectMapper,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val authorization = request.getHeader("Authorization")
        if (authorization.isNullOrBlank() || !authorization.startsWith("Bearer ")) {
            filterChain.doFilter(request, response)
            return
        }

        val token = authorization.removePrefix("Bearer ").trim()
        try {
            val claims = jwtTokenService.parseAndValidate(token)
            val authorities = claims.roles.map { role -> SimpleGrantedAuthority("ROLE_$role") }
            val authentication = UsernamePasswordAuthenticationToken(
                claims.subject,
                null,
                authorities
            )
            SecurityContextHolder.getContext().authentication = authentication
            filterChain.doFilter(request, response)
        } catch (_: IllegalArgumentException) {
            writeUnauthorizedResponse(response)
        }
    }

    private fun writeUnauthorizedResponse(response: HttpServletResponse) {
        SecurityContextHolder.clearContext()
        response.status = HttpStatus.UNAUTHORIZED.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.writer.write(
            objectMapper.writeValueAsString(
                ErrorResponse()
                    .status(HttpStatus.UNAUTHORIZED.value())
                    .message("Invalid or expired bearer token")
                    .details(emptyList())
            )
        )
    }
}
