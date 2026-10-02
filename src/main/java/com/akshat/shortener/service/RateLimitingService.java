package com.akshat.shortener.service;

import com.akshat.shortener.exception.RateLimitExceededException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ==============================================================================
 * In-Memory Token Bucket Rate Limiter (Abuse Defense)
 * ==============================================================================
 * 
 * Kyun zaroori hai?
 * URL shortener par spam bots bahut jaldi attack karte hain:
 * 1. Shorten endpoint spam karke DB storage bhar dena (Storage Exhaustion).
 * 2. Rapid redirects karke server bandwidth consume karna.
 * 
 * Solution:
 * Token Bucket per Client-IP:
 * - Caffeine Cache me har IP ke counters rakhte hain with 1 minute TTL.
 * - Inactive IPs ka data 1 minute me automatically JVM heap se garbage collect ho jata hai (Zero Memory Leak).
 * - Limit cross hone par HTTP 429 (Too Many Requests) throw hota hai.
 */
@Slf4j
@Service
public class RateLimitingService {

    @Value("${scalelink.rate-limit.shorten-per-minute:30}")
    private int shortenLimitPerMinute;

    @Value("${scalelink.rate-limit.redirect-per-second:200}")
    private int redirectLimitPerSecond;

    private Cache<String, AtomicInteger> shortenRateBuckets;
    private Cache<String, AtomicInteger> redirectRateBuckets;

    @PostConstruct
    public void init() {
        log.info("RateLimitingService initialized. Shorten limit: {}/min, Redirect limit: {}/sec",
                shortenLimitPerMinute, redirectLimitPerSecond);

        // 1 minute window for shorten requests
        this.shortenRateBuckets = Caffeine.newBuilder()
                .expireAfterWrite(1, TimeUnit.MINUTES)
                .maximumSize(50000)
                .build();

        // 1 second window for redirects
        this.redirectRateBuckets = Caffeine.newBuilder()
                .expireAfterWrite(1, TimeUnit.SECONDS)
                .maximumSize(100000)
                .build();
    }

    /**
     * URL Shorten request ke liye rate limit check karta hai
     */
    public void checkShortenLimit(String clientIp) {
        if (clientIp == null) return;

        AtomicInteger counter = shortenRateBuckets.get(clientIp, k -> new AtomicInteger(0));
        int currentRequests = counter.incrementAndGet();

        if (currentRequests > shortenLimitPerMinute) {
            log.warn("Rate limit breached for IP: {} ({} > {} req/min)", clientIp, currentRequests, shortenLimitPerMinute);
            throw new RateLimitExceededException("Rate limit exceed ho gaya hai! Aap 1 minute me maximum " 
                    + shortenLimitPerMinute + " links bana sakte hain. Please thodi der baad try karein.");
        }
    }

    /**
     * Redirect flood ke liye rate limit check karta hai
     */
    public void checkRedirectLimit(String clientIp) {
        if (clientIp == null) return;

        AtomicInteger counter = redirectRateBuckets.get(clientIp, k -> new AtomicInteger(0));
        int currentRequests = counter.incrementAndGet();

        if (currentRequests > redirectLimitPerSecond) {
            log.warn("Redirect flood breached for IP: {} ({} > {} req/sec)", clientIp, currentRequests, redirectLimitPerSecond);
            throw new RateLimitExceededException("Too many redirect requests! Please wait a moment.");
        }
    }
}
