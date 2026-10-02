package com.akshat.shortener.exception;

/**
 * Custom alias already reserved / taken exception (HTTP 409 Conflict)
 */
public class AliasAlreadyExistsException extends RuntimeException {
    public AliasAlreadyExistsException(String message) {
        super(message);
    }
}
