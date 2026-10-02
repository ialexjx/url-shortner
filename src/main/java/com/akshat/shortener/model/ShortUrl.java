package com.akshat.shortener.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * ==============================================================================
 * ShortUrl Entity
 * ==============================================================================
 * 
 * Database Schema Considerations:
 * 1. shortCode par UNIQUE constraint aur B-Tree index hai taaki O(log N) lookup
 *    ho sake jab L1/L2 cache miss ho.
 * 2. originalUrl ko TEXT / length 2048 diya hai kyunki modern tracking URLs
 *    (UTM parameters ke sath) kaafi lambi hoti hain.
 * 3. clickCount ko async batching se periodically update kiya jata hai taaki
 *    har redirect request par DB row lock (pessimistic lock) na lage.
 */
@Entity
@Table(
    name = "short_urls",
    indexes = {
        @Index(name = "idx_short_code", columnList = "shortCode", unique = true),
        @Index(name = "idx_created_at", columnList = "createdAt")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShortUrl {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String shortCode;

    @Column(nullable = false, length = 2048)
    private String originalUrl;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    @Builder.Default
    private Long clickCount = 0L;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isCustomAlias = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    /**
     * Check karta hai ki link expire ho gaya hai ya nahi.
     */
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }
}
