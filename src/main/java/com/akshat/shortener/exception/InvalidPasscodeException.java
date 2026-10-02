package com.akshat.shortener.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class InvalidPasscodeException extends RuntimeException {
    public InvalidPasscodeException(String message) {
        super(message);
    }
}
