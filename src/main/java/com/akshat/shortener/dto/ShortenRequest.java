package com.akshat.shortener.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * ==============================================================================
 * ShortenRequest DTO
 * ==============================================================================
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShortenRequest {

    @NotBlank(message = "Original URL khali nahi ho sakta")
    @Size(max = 2048, message = "URL 2048 characters se zyada lambi nahi ho sakti")
    private String originalUrl;

    /**
     * Optional custom alias: e.g. "akshat-resume", "fintech-lab"
     * Validation ensures URL-safe characters only.
     */
    @Pattern(
        regexp = "^[a-zA-Z0-9_-]{3,32}$|^$",
        message = "Custom alias sirf 3 se 32 alphanumeric characters, dash, ya underscore ho sakta hai"
    )
    private String customAlias;

    /**
     * Optional TTL in days. Null = Never expires.
     */
    private Integer ttlDays;

    /**
     * Optional single-use mode: automatically deactivates after the very first click.
     */
    private Boolean burnAfterReading;

    /**
     * Optional secret passcode / PIN to protect the link behind a secure vault screen.
     */
    @Size(max = 64, message = "Passcode 64 characters se zyada lamba nahi ho sakta")
    private String passcode;
}
