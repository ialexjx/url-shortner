package com.akshat.shortener.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShortenResponse {
    private String shortCode;
    private String shortUrl;
    private String originalUrl;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private String analyticsUrl;

    @JsonProperty("isCustomAlias")
    private boolean isCustomAlias;

    private boolean burnAfterReading;

    @JsonProperty("isProtected")
    private boolean isProtected;
}
