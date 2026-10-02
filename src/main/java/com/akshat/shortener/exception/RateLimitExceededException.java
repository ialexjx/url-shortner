package com.akshat.shortener.exception;

/**
 * IP rate limit exceeded exception (HTTP 429 Too Many Requests)
 */
public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException(String message) {
        super(message);
    }
}
