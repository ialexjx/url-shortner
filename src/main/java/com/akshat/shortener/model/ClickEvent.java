package com.akshat.shortener.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * ==============================================================================
 * ClickEvent Entity
 * ==============================================================================
 * 
 * Har redirect event ka audit trail aur telemetry store karta hai.
 * 
 * Engineering Note:
 * Ye table append-only hai. Kabhi bhi incoming HTTP thread is table me synchronously
 * insert nahi karega! Redirect response (302) turant de diya jata hai, aur click event
 * ko ek in-memory bounded queue me push karte hain jisko background Virtual Thread
 * batch me 200 records at a time insert karta hai (High Throughput Batch Ingestion).
 */
@Entity
@Table(
    name = "click_events",
    indexes = {
        @Index(name = "idx_click_short_code", columnList = "shortCode"),
        @Index(name = "idx_click_timestamp", columnList = "timestamp")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClickEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String shortCode;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    // GDPR & Privacy safe: Pura raw IP store karne ke bajaye SHA-256 hash rakhte hain
    @Column(length = 64)
    private String ipHash;

    @Column(length = 64)
    private String country;

    @Column(length = 256)
    private String referer;

    @Column(length = 512)
    private String userAgent;

    @Column(length = 32)
    private String deviceType; // Mobile, Desktop, Tablet, Bot

    @Column(length = 64)
    private String browser;    // Chrome, Safari, Firefox, Edge, Other
}
