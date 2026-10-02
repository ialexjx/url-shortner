package com.akshat.shortener;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * ==============================================================================
 * ScaleLink - Distributed High-Performance URL Shortener Engine
 * ==============================================================================
 * 
 * Key Architectural Innovations:
 * 1. Java 21 Project Loom (Virtual Threads): 10,000+ concurrent redirects with minimal memory.
 * 2. Range-Based ID Allocator (KGS Pattern): Atomic token reservation without DB bottleneck.
 * 3. Base62 Bi-directional Encoder: URL-safe alphanumeric representation.
 * 4. Guava Probabilistic Bloom Filter: Negative caching barrier to prevent DB cache penetration.
 * 5. L1 Caffeine + L2 Redis Dual Caching: Sub-millisecond reads with graceful degradation.
 * 6. High-Throughput Async Analytics: Non-blocking bounded ring buffer + batch database writer.
 * 7. Token Bucket Rate Limiting: Abuse and denial-of-service protection.
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class UrlShortenerApplication {

    public static void main(String[] args) {
        SpringApplication.run(UrlShortenerApplication.class, args);
        System.out.println("""
            ==============================================================================
            🚀 ScaleLink Distributed URL Shortener Engine Successfully Started!
            👉 Local Dashboard: http://localhost:8080
            👉 Health Endpoint: http://localhost:8080/api/v1/system/status
            ==============================================================================
            """);
    }
}
