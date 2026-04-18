package com.example.llmragplatform.exception

import com.example.llmragplatform.generated.model.ErrorResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.client.RestClientResponseException

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val details = ex.bindingResult.fieldErrors
            .map { fieldError -> "${fieldError.field}: ${fieldError.defaultMessage ?: "invalid value"}" }
            .ifEmpty { listOf("request validation failed") }

        return ResponseEntity.badRequest().body(
            ErrorResponse()
                .status(HttpStatus.BAD_REQUEST.value())
                .message("Validation error")
                .details(details)
        )
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(ex: IllegalArgumentException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.badRequest().body(
            ErrorResponse()
                .status(HttpStatus.BAD_REQUEST.value())
                .message(ex.message)
                .details(emptyList())
        )
    }

    @ExceptionHandler(InvalidCredentialsException::class)
    fun handleInvalidCredentialsException(ex: InvalidCredentialsException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
            ErrorResponse()
                .status(HttpStatus.UNAUTHORIZED.value())
                .message(ex.message)
                .details(emptyList())
        )
    }

    @ExceptionHandler(OpenAiIntegrationException::class)
    fun handleOpenAiIntegrationException(ex: OpenAiIntegrationException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(
            ErrorResponse()
                .status(HttpStatus.BAD_GATEWAY.value())
                .message(ex.message)
                .details(ex.details)
        )
    }

    @ExceptionHandler(PromptInjectionDetectedException::class)
    fun handlePromptInjectionDetectedException(ex: PromptInjectionDetectedException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.badRequest().body(
            ErrorResponse()
                .status(HttpStatus.BAD_REQUEST.value())
                .message(ex.message)
                .details(emptyList())
        )
    }

    @ExceptionHandler(RestClientResponseException::class)
    fun handleRestClientResponseException(ex: RestClientResponseException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(
            ErrorResponse()
                .status(HttpStatus.BAD_GATEWAY.value())
                .message("OpenAI API request failed")
                .details(listOf(ex.responseBodyAsString.take(500)))
        )
    }

    @ExceptionHandler(ResourceNotFoundException::class)
    fun handleResourceNotFoundException(ex: ResourceNotFoundException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            ErrorResponse()
                .status(HttpStatus.NOT_FOUND.value())
                .message(ex.message)
                .details(emptyList())
        )
    }
}
