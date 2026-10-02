package com.akshat.shortener.service;

import com.akshat.shortener.dto.AnalyticsResponse;
import com.akshat.shortener.dto.ShortenRequest;
import com.akshat.shortener.dto.ShortenResponse;
import com.akshat.shortener.dto.SystemHealthResponse;
import com.akshat.shortener.exception.AliasAlreadyExistsException;
import com.akshat.shortener.exception.InvalidUrlException;
import com.akshat.shortener.exception.ResourceNotFoundException;
import com.akshat.shortener.model.ClickEvent;
import com.akshat.shortener.model.ShortUrl;
import com.akshat.shortener.repository.ClickEventRepository;
import com.akshat.shortener.repository.ShortUrlRepository;
import com.akshat.shortener.util.Base62;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.management.ManagementFactory;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * ==============================================================================
 * Core URL Shortener Orchestration Service
 * ==============================================================================
 * 
 * Saare architectural components ko integrate karta hai:
 * 1. Rate Limiting (Abuse check)
 * 2. Range ID Generator (KGS Pattern)
 * 3. Base62 Encoder
 * 4. Guava Bloom Filter (Negative Caching Barrier)
 * 5. L1 Caffeine + L2 Redis Dual Cache
 * 6. High-Throughput Async Analytics Ingestion Engine
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UrlShortenerService {

    private final ShortUrlRepository shortUrlRepository;
    private final ClickEventRepository clickEventRepository;
    private final RangeIdGeneratorService rangeIdGeneratorService;
    private final BloomFilterService bloomFilterService;
    private final DualCacheService dualCacheService;
    private final AnalyticsBufferService analyticsBufferService;
    private final RateLimitingService rateLimitingService;

    @Value("${scalelink.base-url:http://localhost:8080}")
    private String baseUrl;

    // Reserved routes taaki custom alias API ya system endpoints ko overwrite na kare
    private static final Set<String> RESERVED_KEYWORDS = Set.of(
            "api", "admin", "analytics", "actuator", "health", "static", "css",
            "js", "favicon.ico", "dashboard", "swagger", "v3", "index", "h2-console", "metrics"
    );

    /**
     * URL Shorten karne ka core logic
     */
    @Transactional
    public ShortenResponse shortenUrl(ShortenRequest request, HttpServletRequest httpRequest) {
        String clientIp = extractClientIp(httpRequest);
        
        // 1. Abuse check: IP rate limit
        rateLimitingService.checkShortenLimit(clientIp);

        // 2. Original URL validate aur sanitize karo
        String sanitizedUrl = sanitizeAndValidateUrl(request.getOriginalUrl());

        String shortCode;
        boolean isCustom = false;

        // 3. Custom Alias vs Auto Range ID Generation
        if (request.getCustomAlias() != null && !request.getCustomAlias().trim().isEmpty()) {
            String alias = request.getCustomAlias().trim();
            validateCustomAlias(alias);

            // Check if already taken (Bloom filter + DB check)
            if (bloomFilterService.mightContain(alias) && shortUrlRepository.existsByShortCode(alias)) {
                throw new AliasAlreadyExistsException("Custom alias '" + alias + "' pehle se use me hai! Please koi dusra choose karein.");
            }
            shortCode = alias;
            isCustom = true;
        } else {
            // High-Performance KGS Token Allocation: In-memory atomic ID -> Base62
            long uniqueId = rangeIdGeneratorService.nextId();
            shortCode = Base62.encode(uniqueId);
        }

        // 4. Calculate Expiration Date
        LocalDateTime expiresAt = null;
        if (request.getTtlDays() != null && request.getTtlDays() > 0) {
            expiresAt = LocalDateTime.now().plusDays(request.getTtlDays());
        }

        // 5. Database me persist karo
        ShortUrl shortUrl = ShortUrl.builder()
                .shortCode(shortCode)
                .originalUrl(sanitizedUrl)
                .expiresAt(expiresAt)
                .isCustomAlias(isCustom)
                .isActive(true)
                .clickCount(0L)
                .build();

        shortUrlRepository.save(shortUrl);

        // 6. Guava Bloom Filter me register karo (taaki future lookups pass ho sakein)
        bloomFilterService.add(shortCode);

        // 7. L1 & L2 Dual Cache ko pre-warm karo (Sub-millisecond first redirect!)
        long ttlMinutes = 60 * 24; // Default 24 hours in cache
        if (expiresAt != null) {
            long remainingMinutes = Duration.between(LocalDateTime.now(), expiresAt).toMinutes();
            ttlMinutes = Math.max(1, remainingMinutes);
        }
        dualCacheService.put(shortCode, sanitizedUrl, ttlMinutes);

        log.info("URL successfully shortened: code={}, isCustom={}, expiresAt={}", shortCode, isCustom, expiresAt);

        // 8. Build response
        String fullShortUrl = normalizeBaseUrl(baseUrl) + "/" + shortCode;
        String analyticsUrl = normalizeBaseUrl(baseUrl) + "/api/v1/analytics/" + shortCode;

        return ShortenResponse.builder()
                .shortCode(shortCode)
                .shortUrl(fullShortUrl)
                .originalUrl(sanitizedUrl)
                .createdAt(shortUrl.getCreatedAt() != null ? shortUrl.getCreatedAt() : LocalDateTime.now())
                .expiresAt(expiresAt)
                .analyticsUrl(analyticsUrl)
                .isCustomAlias(isCustom)
                .build();
    }

    /**
     * Fast Redirect Resolution Path (Sub-millisecond target)
     */
    public String resolveAndTrack(String shortCode, HttpServletRequest httpRequest) {
        String clientIp = extractClientIp(httpRequest);

        // 1. Redirect flood check per IP
        rateLimitingService.checkRedirectLimit(clientIp);

        // 2. Probabilistic Barrier (Guava Bloom Filter):
        // Agar Bloom Filter bolta hai ki code nahi hai, toh 100% guarantee hai ki exist nahi karta.
        // Direct 404 throw kar dete hain, zero DB/Redis query!
        if (!bloomFilterService.mightContain(shortCode)) {
            throw new ResourceNotFoundException("Short URL '" + shortCode + "' exist nahi karta hai.");
        }

        // 3. Multi-tier Cache Check (L1 Caffeine -> L2 Redis)
        Optional<String> cachedUrl = dualCacheService.get(shortCode);
        String destinationUrl;

        if (cachedUrl.isPresent()) {
            destinationUrl = cachedUrl.get();
        } else {
            // 4. Cache Miss: Database Lookup
            ShortUrl shortUrl = shortUrlRepository.findByShortCode(shortCode)
                    .orElseThrow(() -> new ResourceNotFoundException("Short URL '" + shortCode + "' nahi mila."));

            if (!Boolean.TRUE.equals(shortUrl.getIsActive())) {
                throw new ResourceNotFoundException("Ye short URL deactivate ho chuka hai.");
            }

            if (shortUrl.isExpired()) {
                dualCacheService.evict(shortCode);
                throw new ResourceNotFoundException("Ye short URL expire ho chuka hai.");
            }

            destinationUrl = shortUrl.getOriginalUrl();

            // Cache Warm-up for next reader
            long ttlMinutes = 60 * 24;
            if (shortUrl.getExpiresAt() != null) {
                long remaining = Duration.between(LocalDateTime.now(), shortUrl.getExpiresAt()).toMinutes();
                ttlMinutes = Math.max(1, remaining);
            }
            dualCacheService.put(shortCode, destinationUrl, ttlMinutes);
        }

        // 5. Asynchronous Telemetry Ingestion (Non-blocking):
        // HTTP Redirect thread ko DB me row update karne ki zaroorat nahi hai.
        // Queue me push kiya aur turant return ho gaye!
        ClickEvent event = analyticsBufferService.buildEventFromRequest(shortCode, httpRequest);
        analyticsBufferService.enqueue(event);

        return destinationUrl;
    }

    /**
     * Analytics Aggregation for Dashboard
     */
    public AnalyticsResponse getAnalytics(String shortCode) {
        // 1. In-flight buffer events ko DB me sync flush kar do taaki real-time telemetry turant dikhe
        analyticsBufferService.flushBatch();

        ShortUrl shortUrl = shortUrlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new ResourceNotFoundException("Short code '" + shortCode + "' ke liye analytics nahi mila."));

        long eventCount = clickEventRepository.countByShortCode(shortCode);
        long totalClicks = Math.max(shortUrl.getClickCount() != null ? shortUrl.getClickCount() : 0L, eventCount);

        Map<String, Long> countryBreakdown = mapAggregations(clickEventRepository.countClicksByCountry(shortCode));
        Map<String, Long> deviceBreakdown = mapAggregations(clickEventRepository.countClicksByDeviceType(shortCode));
        Map<String, Long> refererBreakdown = mapAggregations(clickEventRepository.countClicksByReferer(shortCode));
        Map<String, Long> browserBreakdown = mapAggregations(clickEventRepository.countClicksByBrowser(shortCode));

        List<ClickEvent> recentEvents = clickEventRepository.findTop50ByShortCodeOrderByTimestampDesc(shortCode);

        String fullShortUrl = normalizeBaseUrl(baseUrl) + "/" + shortCode;

        return AnalyticsResponse.builder()
                .shortCode(shortCode)
                .shortUrl(fullShortUrl)
                .originalUrl(shortUrl.getOriginalUrl())
                .createdAt(shortUrl.getCreatedAt())
                .expiresAt(shortUrl.getExpiresAt())
                .isExpired(shortUrl.isExpired())
                .totalClicks(totalClicks)
                .countryBreakdown(countryBreakdown)
                .deviceBreakdown(deviceBreakdown)
                .refererBreakdown(refererBreakdown)
                .browserBreakdown(browserBreakdown)
                .recentEvents(recentEvents)
                .build();
    }

    /**
     * Real-time System Telemetry for Recruiters & Portfolio Demonstration
     */
    public SystemHealthResponse getSystemHealth() {
        long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
        Runtime runtime = Runtime.getRuntime();
        long usedMemoryMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long maxMemoryMb = runtime.maxMemory() / (1024 * 1024);

        Map<String, Object> architecture = new LinkedHashMap<>();
        architecture.put("javaVersion", System.getProperty("java.version"));
        architecture.put("virtualThreadsEnabled", true);
        architecture.put("idAllocationStrategy", "Distributed KGS Range Allocator (Atomically reserves blocks from DB)");
        architecture.put("negativeCachingBarrier", "Guava Bloom Filter (1M keys @ 1% FPP consumes ~1.2MB)");
        architecture.put("cachingTiers", "L1 Caffeine (JVM In-Memory) + L2 Redis (Distributed with Graceful Fallback)");
        architecture.put("analyticsIngestion", "Asynchronous Bounded Ring Buffer + Batch DB Flusher");
        architecture.put("rateLimiting", "Token Bucket per Client IP");

        return SystemHealthResponse.builder()
                .status("HEALTHY_OPTIMAL")
                .uptimeSeconds(uptimeMs / 1000)
                .virtualThreadsEnabled(true)
                .jvmMemoryUsedMb(usedMemoryMb)
                .jvmMemoryMaxMb(maxMemoryMb)
                .l1CacheEntries(dualCacheService.getL1CacheEstimatedSize())
                .bloomFilterEstimatedElements(bloomFilterService.getEstimatedElementCount())
                .analyticsQueueDepth(analyticsBufferService.getQueueDepth())
                .architectureHighlights(architecture)
                .build();
    }

    /**
     * Link Deactivation
     */
    @Transactional
    public void deactivateUrl(String shortCode) {
        ShortUrl shortUrl = shortUrlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new ResourceNotFoundException("Short code '" + shortCode + "' nahi mila."));
        shortUrl.setIsActive(false);
        shortUrlRepository.save(shortUrl);
        dualCacheService.evict(shortCode);
        log.info("Short URL deactivated: {}", shortCode);
    }

    private void validateCustomAlias(String alias) {
        if (RESERVED_KEYWORDS.contains(alias.toLowerCase())) {
            throw new InvalidUrlException("Alias '" + alias + "' system reserved keyword hai. Please koi dusra alias choose karein.");
        }
        if (!alias.matches("^[a-zA-Z0-9_-]{3,32}$")) {
            throw new InvalidUrlException("Custom alias sirf 3 se 32 alphanumeric characters, dash, ya underscore allow karta hai.");
        }
    }

    private String sanitizeAndValidateUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            throw new InvalidUrlException("Original URL empty nahi ho sakti.");
        }

        String trimmed = url.trim();

        // Agar user ne scheme nahi diya toh auto prepend https://
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            trimmed = "https://" + trimmed;
        }

        try {
            URI uri = URI.create(trimmed);
            if (uri.getHost() == null || !uri.getHost().contains(".")) {
                throw new InvalidUrlException("Invalid URL host structure: " + trimmed);
            }

            // Self-referential loop protection (Cannot shorten our own shortened URLs)
            if (baseUrl != null && trimmed.startsWith(baseUrl)) {
                throw new InvalidUrlException("Self-referencing URL shortener loop detect hui! Aap ScaleLink ke apne URL ko shorten nahi kar sakte.");
            }
        } catch (IllegalArgumentException e) {
            throw new InvalidUrlException("Malformed URL syntax: " + e.getMessage());
        }

        return trimmed;
    }

    private String normalizeBaseUrl(String url) {
        if (url == null) return "http://localhost:8080";
        if (url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }

    private String extractClientIp(HttpServletRequest request) {
        if (request == null) return "127.0.0.1";
        String xForwarded = request.getHeader("X-Forwarded-For");
        if (xForwarded != null && !xForwarded.isEmpty() && !"unknown".equalsIgnoreCase(xForwarded)) {
            return xForwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private Map<String, Long> mapAggregations(List<Object[]> rawList) {
        Map<String, Long> result = new LinkedHashMap<>();
        if (rawList != null) {
            for (Object[] row : rawList) {
                String key = row[0] != null ? row[0].toString() : "Unknown";
                Long count = row[1] instanceof Number ? ((Number) row[1]).longValue() : 0L;
                result.put(key, count);
            }
        }
        return result;
    }
}
