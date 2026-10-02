package com.akshat.shortener.controller;

import com.akshat.shortener.dto.AnalyticsResponse;
import com.akshat.shortener.dto.ShortenRequest;
import com.akshat.shortener.dto.ShortenResponse;
import com.akshat.shortener.dto.SystemHealthResponse;
import com.akshat.shortener.service.UrlShortenerService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * ==============================================================================
 * REST API Controller for URL Management & Analytics
 * ==============================================================================
 * 
 * Endpoints:
 * - POST   /api/v1/shorten          : Long URL ko short URL me convert karta hai
 * - GET    /api/v1/analytics/{code} : Click analytics, country, browser, device breakdown
 * - GET    /api/v1/system/status    : System telemetry, cache hits, memory, Loom threads
 * - DELETE /api/v1/urls/{code}      : Short link ko deactivate karta hai
 * - GET    /health                  : Render deployment zero-downtime health check
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // Cross-origin requests allow karte hain for portfolio demo
public class UrlShortenerApiController {

    private final UrlShortenerService urlShortenerService;

    /**
     * Naya Short URL create karne ka API
     */
    @PostMapping("/api/v1/shorten")
    public ResponseEntity<ShortenResponse> shortenUrl(
            @Valid @RequestBody ShortenRequest request,
            HttpServletRequest httpRequest
    ) {
        log.info("REST shorten request received for URL: {}", request.getOriginalUrl());
        ShortenResponse response = urlShortenerService.shortenUrl(request, httpRequest);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * Analytics and telemetry fetch karne ka API
     */
    @GetMapping("/api/v1/analytics/{shortCode}")
    public ResponseEntity<AnalyticsResponse> getAnalytics(@PathVariable String shortCode) {
        log.info("REST analytics request for code: {}", shortCode);
        AnalyticsResponse response = urlShortenerService.getAnalytics(shortCode);
        return ResponseEntity.ok(response);
    }

    /**
     * System health, memory, and architecture telemetry
     */
    @GetMapping("/api/v1/system/status")
    public ResponseEntity<SystemHealthResponse> getSystemStatus() {
        SystemHealthResponse health = urlShortenerService.getSystemHealth();
        return ResponseEntity.ok(health);
    }

    /**
     * Render.com zero-downtime health probe
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> renderHealthProbe() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "scalelink-url-shortener",
                "timestamp", System.currentTimeMillis()
        ));
    }

    /**
     * Short URL deactivation
     */
    @DeleteMapping("/api/v1/urls/{shortCode}")
    public ResponseEntity<Map<String, String>> deactivateUrl(@PathVariable String shortCode) {
        log.info("REST deactivate request for code: {}", shortCode);
        urlShortenerService.deactivateUrl(shortCode);
        return ResponseEntity.ok(Map.of(
                "message", "Short URL '" + shortCode + "' successfully deactivate ho gaya.",
                "status", "DEACTIVATED"
        ));
    }
}
