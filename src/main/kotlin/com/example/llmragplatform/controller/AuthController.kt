package com.example.llmragplatform.controller

import com.example.llmragplatform.generated.api.AuthApi
import com.example.llmragplatform.generated.model.AuthTokenRequest
import com.example.llmragplatform.generated.model.AuthTokenResponse
import com.example.llmragplatform.service.AuthService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
class AuthController(
    private val authService: AuthService,
) : AuthApi {

    override fun issueAuthToken(authTokenRequest: AuthTokenRequest): ResponseEntity<AuthTokenResponse> {
        return ResponseEntity.ok(
            authService.issueToken(
                username = authTokenRequest.username,
                password = authTokenRequest.password
            )
        )
    }
}
