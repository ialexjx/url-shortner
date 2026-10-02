package com.akshat.shortener.exception;

/**
 * ==============================================================================
 * Custom Business Exceptions
 * ==============================================================================
 * 
 * Kyun banaya?
 * Generic RuntimeException fekne ke bajaye specific domain exceptions throw karte hain.
 * Isse GlobalExceptionHandler accurately HTTP status codes (404, 400, 409, 429) map kar pata hai.
 */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
