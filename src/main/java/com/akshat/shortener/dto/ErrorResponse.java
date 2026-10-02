package com.akshat.shortener.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * ==============================================================================
 * Standard API Error Response (RFC-7807 aligned)
 * ==============================================================================
 * 
 * Kyun zaroori hai?
 * Production APIs me unstructured stack trace kabhi client ko expose nahi karni chahiye.
 * Ye response format user-friendly message, timestamp, HTTP status, aur validation errors
 * cleanly return karta hai taaki frontend / mobile apps easily parse kar sakein.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErrorResponse {
    private int status;
    private String error;
    private String message;
    private String path;
    private LocalDateTime timestamp;
    private Map<String, String> validationErrors;
}
