package com.akshat.shortener.exception;

/**
 * Malformed or unsupported URL exception (HTTP 400 Bad Request)
 */
public class InvalidUrlException extends RuntimeException {
    public InvalidUrlException(String message) {
        super(message);
    }
}
