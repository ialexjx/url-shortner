package com.akshat.shortener.exception;

import com.akshat.shortener.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * ==============================================================================
 * Global Exception Handler (@RestControllerAdvice)
 * ==============================================================================
 * 
 * Kyun banaya?
 * Jab bhi application me koi error ya business violation hota hai, Spring Boot by default
 * white-label error page ya generic JSON deta hai. 
 * Is class ke through hum centralize error handling karte hain:
 * 1. Sabhi exceptions ko clean JSON format me transform karna.
 * 2. Proper HTTP status code dena (404, 400, 409, 429, 500).
 * 3. Client ko sensitive internal stack traces se protect karna.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Short URL na milne par 404 Not Found return karega
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        log.warn("Resource not found: {} for path: {}", ex.getMessage(), request.getRequestURI());
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request.getRequestURI(), null);
    }

    /**
     * Agar custom alias pehle se kisi ne le liya ho toh 409 Conflict return karega
     */
    @ExceptionHandler(AliasAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleAliasExists(AliasAlreadyExistsException ex, HttpServletRequest request) {
        log.warn("Alias collision: {} for path: {}", ex.getMessage(), request.getRequestURI());
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage(), request.getRequestURI(), null);
    }

    /**
     * Agar client ne invalid URL pass kiya (e.g. bina http/https ya loopback) toh 400 Bad Request
     */
    @ExceptionHandler(InvalidUrlException.class)
    public ResponseEntity<ErrorResponse> handleInvalidUrl(InvalidUrlException ex, HttpServletRequest request) {
        log.warn("Invalid URL requested: {} for path: {}", ex.getMessage(), request.getRequestURI());
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getRequestURI(), null);
    }

    @ExceptionHandler(InvalidPasscodeException.class)
    public ResponseEntity<ErrorResponse> handleInvalidPasscode(InvalidPasscodeException ex, HttpServletRequest request) {
        log.warn("Invalid passcode attempt: {} for path: {}", ex.getMessage(), request.getRequestURI());
        return buildResponse(HttpStatus.UNAUTHORIZED, ex.getMessage(), request.getRequestURI(), null);
    }

    @ExceptionHandler(ProtectedLinkException.class)
    public ResponseEntity<ErrorResponse> handleProtectedLink(ProtectedLinkException ex, HttpServletRequest request) {
        log.warn("Access attempt on protected link without auth: {} for path: {}", ex.getMessage(), request.getRequestURI());
        return buildResponse(HttpStatus.UNAUTHORIZED, ex.getMessage(), request.getRequestURI(), null);
    }

    /**
     * Rate limit exceed hone par 429 Too Many Requests return karega
     */
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimit(RateLimitExceededException ex, HttpServletRequest request) {
        log.warn("Rate limit breached: {} for IP/path: {}", ex.getMessage(), request.getRequestURI());
        return buildResponse(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), request.getRequestURI(), null);
    }

    /**
     * Hibernate / Bean validation errors (@Valid @NotBlank, @Pattern etc) ko field-wise map karega
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return buildResponse(HttpStatus.BAD_REQUEST, "Validation failed for request body", request.getRequestURI(), errors);
    }

    /**
     * Illegal argument jaise invalid Base62 decoding etc
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Illegal argument: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getRequestURI(), null);
    }

    /**
     * Catch-all handler for unhandled server errors (HTTP 500)
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled internal server error on path {}: ", request.getRequestURI(), ex);
        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal server error occurred. Please try again later.",
                request.getRequestURI(),
                null
        );
    }

    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status, String message, String path, Map<String, String> validationErrors) {
        ErrorResponse response = ErrorResponse.builder()
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(path)
                .timestamp(LocalDateTime.now())
                .validationErrors(validationErrors)
                .build();
        return new ResponseEntity<>(response, status);
    }
}
