package com.trackmybuds.userservice.adapter.`in`.web

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

// Extends ResponseEntityExceptionHandler so Spring's built-in handlers keep mapping
// framework exceptions to their correct status (400/404/405/...) — rendered as RFC 7807
// via spring.mvc.problemdetails.enabled=true. The catch-all below is reached only for
// genuinely-unexpected exceptions, which become 500.
@RestControllerAdvice
internal class GlobalExceptionHandler : ResponseEntityExceptionHandler() {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ProblemDetail {
        log.error("Unhandled exception", ex)
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected error occurred",
        )
    }
}
