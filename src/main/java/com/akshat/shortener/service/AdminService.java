package com.akshat.shortener.service;

import com.akshat.shortener.dto.AdminOverviewResponse;
import com.akshat.shortener.dto.AdminUrlItemResponse;
import com.akshat.shortener.exception.ResourceNotFoundException;
import com.akshat.shortener.model.ShortUrl;
import com.akshat.shortener.repository.ShortUrlRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * ==============================================================================
 * Admin Portal Management Service
 * ==============================================================================
 * 
 * Kyun banaya?
 * Real-world production projects me Admin control bohot crucial hota hai:
 * 1. Malicious / Phishing URLs ko instant deactivate ya delete karna.
 * 2. System-wide global metrics monitor karna (Total links, Total redirects, Memory).
 * 3. Search and filter through all shortened links with pagination.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final ShortUrlRepository shortUrlRepository;
    private final DualCacheService dualCacheService;
    private final BloomFilterService bloomFilterService;
    private final AnalyticsBufferService analyticsBufferService;

    @Value("${scalelink.base-url:http://localhost:8080}")
    private String baseUrl;

    @Value("${scalelink.admin.secret-key:admin123}")
    private String adminSecretKey;

    /**
     * Admin Key validation check (Demo Key: admin123)
     */
    public boolean isValidAdminKey(String key) {
        if (key == null) return false;
        return adminSecretKey.equals(key.trim());
    }

    /**
     * Global Admin Overview (KPI Cards)
     */
    @Transactional(readOnly = true)
    public AdminOverviewResponse getOverview() {
        long totalUrls = shortUrlRepository.count();
        long totalClicks = shortUrlRepository.sumTotalClicks();
        long activeUrls = shortUrlRepository.countByIsActiveTrue();
        long inactiveUrls = shortUrlRepository.countByIsActiveFalse();

        Runtime runtime = Runtime.getRuntime();
        long usedMemoryMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long maxMemoryMb = runtime.maxMemory() / (1024 * 1024);

        return AdminOverviewResponse.builder()
                .totalUrls(totalUrls)
                .totalClicks(totalClicks)
                .activeUrls(activeUrls)
                .inactiveUrls(inactiveUrls)
                .l1CacheEntries(dualCacheService.getL1CacheEstimatedSize())
                .bloomFilterEntries(bloomFilterService.getEstimatedElementCount())
                .asyncQueueDepth(analyticsBufferService.getQueueDepth())
                .jvmMemoryUsedMb(usedMemoryMb)
                .jvmMemoryMaxMb(maxMemoryMb)
                .build();
    }

    /**
     * Paginated list of shortened URLs with search support
     */
    @Transactional(readOnly = true)
    public Page<AdminUrlItemResponse> listUrls(String search, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)));

        Page<ShortUrl> entityPage;
        if (search != null && !search.trim().isEmpty()) {
            String q = search.trim();
            entityPage = shortUrlRepository.findByShortCodeContainingIgnoreCaseOrOriginalUrlContainingIgnoreCaseOrderByCreatedAtDesc(q, q, pageable);
        } else {
            entityPage = shortUrlRepository.findAllByOrderByCreatedAtDesc(pageable);
        }

        String normalizedBase = baseUrl != null && baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;

        return entityPage.map(entity -> AdminUrlItemResponse.builder()
                .shortCode(entity.getShortCode())
                .shortUrl(normalizedBase + "/" + entity.getShortCode())
                .originalUrl(entity.getOriginalUrl())
                .createdAt(entity.getCreatedAt())
                .expiresAt(entity.getExpiresAt())
                .isExpired(entity.isExpired())
                .isActive(Boolean.TRUE.equals(entity.getIsActive()))
                .isCustomAlias(Boolean.TRUE.equals(entity.getIsCustomAlias()))
                .clickCount(entity.getClickCount() != null ? entity.getClickCount() : 0L)
                .build()
        );
    }

    /**
     * Link status toggle (Active <-> Inactive)
     */
    @Transactional
    public boolean toggleUrlStatus(String shortCode) {
        ShortUrl shortUrl = shortUrlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new ResourceNotFoundException("Short code '" + shortCode + "' nahi mila."));

        boolean newStatus = !Boolean.TRUE.equals(shortUrl.getIsActive());
        shortUrl.setIsActive(newStatus);
        shortUrlRepository.save(shortUrl);

        if (!newStatus) {
            // Deactivate hone par cache se evict karo
            dualCacheService.evict(shortCode);
            log.info("Admin: Short URL '{}' deactivated.", shortCode);
        } else {
            // Reactivate hone par cache aur bloom filter me restore karo
            bloomFilterService.add(shortCode);
            long ttlMinutes = 60 * 24;
            if (shortUrl.getExpiresAt() != null) {
                long remaining = Duration.between(LocalDateTime.now(), shortUrl.getExpiresAt()).toMinutes();
                ttlMinutes = Math.max(1, remaining);
            }
            dualCacheService.put(shortCode, shortUrl.getOriginalUrl(), ttlMinutes);
            log.info("Admin: Short URL '{}' reactivated.", shortCode);
        }

        return newStatus;
    }

    /**
     * Link permanent delete
     */
    @Transactional
    public void deleteUrl(String shortCode) {
        ShortUrl shortUrl = shortUrlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new ResourceNotFoundException("Short code '" + shortCode + "' nahi mila."));

        shortUrlRepository.delete(shortUrl);
        dualCacheService.evict(shortCode);
        log.info("Admin: Short URL '{}' permanently deleted from database & cache.", shortCode);
    }
}
