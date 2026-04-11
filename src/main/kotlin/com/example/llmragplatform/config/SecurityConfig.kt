package com.example.llmragplatform.config

import com.example.llmragplatform.generated.model.ErrorResponse
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableMethodSecurity
class SecurityConfig {

    @Bean
    fun passwordEncoder(): PasswordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()

    @Bean
    fun userDetailsService(
        securityProperties: SecurityProperties,
        passwordEncoder: PasswordEncoder,
    ): UserDetailsService {
        val adminUser = User.builder()
            .username(securityProperties.admin.username)
            .password(passwordEncoder.encode(securityProperties.admin.password))
            .roles(*securityProperties.admin.roles.toTypedArray())
            .build()
        val operatorUser = User.builder()
            .username(securityProperties.operator.username)
            .password(passwordEncoder.encode(securityProperties.operator.password))
            .roles(*securityProperties.operator.roles.toTypedArray())
            .build()

        return InMemoryUserDetailsManager(adminUser, operatorUser)
    }

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        objectMapper: ObjectMapper,
    ): SecurityFilterChain {
        return http
            .csrf { it.disable() }
            .authorizeHttpRequests {
                it.requestMatchers(HttpMethod.POST, "/v1/knowledge-documents/*/reindex").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.POST, "/v1/knowledge-documents/reindex").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.POST, "/v1/knowledge-documents").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.GET, "/v1/knowledge-documents").hasAnyRole("ADMIN", "OPERATOR")
                    .requestMatchers("/v1/audit-logs/**").hasAnyRole("ADMIN", "OPERATOR")
                    .anyRequest().permitAll()
            }
            .httpBasic(Customizer.withDefaults())
            .exceptionHandling {
                it.authenticationEntryPoint { _, response, _ ->
                    writeErrorResponse(
                        response = response,
                        objectMapper = objectMapper,
                        status = HttpStatus.UNAUTHORIZED,
                        message = "Authentication is required"
                    )
                }
                it.accessDeniedHandler { _, response, _ ->
                    writeErrorResponse(
                        response = response,
                        objectMapper = objectMapper,
                        status = HttpStatus.FORBIDDEN,
                        message = "Audit log access is restricted"
                    )
                }
            }
            .build()
    }

    private fun writeErrorResponse(
        response: HttpServletResponse,
        objectMapper: ObjectMapper,
        status: HttpStatus,
        message: String,
    ) {
        response.status = status.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.writer.write(
            objectMapper.writeValueAsString(
                ErrorResponse()
                    .status(status.value())
                    .message(message)
                    .details(emptyList())
            )
        )
    }
}
