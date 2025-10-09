package com.ghkdqhrbals.mod.controller

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.client.HttpClientErrorException

@ConditionalOnClass(LoginControllerModuleMarker::class)
@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(HttpClientErrorException::class)
    fun handleHttpClientError(ex: HttpClientErrorException): ResponseEntity<String> {
        val status = ex.statusCode
        val message = ex.responseBodyAsString.ifBlank { ex.message ?: "OAuth request failed" }
        return ResponseEntity.status(status).body(message)
    }
}