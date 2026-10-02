package com.akshat.shortener.service;

import com.akshat.shortener.model.ClickEvent;
import com.akshat.shortener.repository.ClickEventRepository;
import com.akshat.shortener.repository.ShortUrlRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ==============================================================================
 * High-Throughput Async Analytics Ingestion Engine
 * ==============================================================================
 * 
 * System Design Problem:
 * Real-world URL shorteners (bit.ly, tinyurl) me 99% traffic Read/Redirects hota hai.
 * Agar har redirect HTTP request par:
 * 1. Synchronously ClickEvent insert karein
 * 2. ShortUrl table me click_count = click_count + 1 karein (Row Lock)
 * Toh database connection pool crash ho jayega aur latency 5ms se 300ms+ chali jayegi!
 * 
 * Solution:
 * 1. Redirect request (302) turant user ko return kar di jati hai (< 5ms).
 * 2. ClickEvent telemetry object ko ek in-memory bounded queue me enqueue kiya jata hai.
 * 3. Background worker (Spring Scheduler + Java 21 Virtual Threads) har 2 second me
 *    200 events ka batch banakar save karta hai.
 * 4. ShortUrl click counts ko memory me aggregate karke single bulk UPDATE query chalata hai.
 * 5. Bounded Queue se Memory Leak prevention: Queue full hone par load shedding karta hai.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsBufferService {

    private final ClickEventRepository clickEventRepository;
    private final ShortUrlRepository shortUrlRepository;

    @Value("${scalelink.analytics.queue-capacity:50000}")
    private int queueCapacity;

    @Value("${scalelink.analytics.batch-size:200}")
    private int batchSize;

    private BlockingQueue<ClickEvent> eventQueue;
    private final AtomicLong droppedEvents = new AtomicLong(0);
    private final AtomicLong totalProcessedEvents = new AtomicLong(0);

    @PostConstruct
    public void init() {
        log.info("AnalyticsBufferService initializing with capacity: {}, batchSize: {}", queueCapacity, batchSize);
        this.eventQueue = new ArrayBlockingQueue<>(queueCapacity);
    }

    /**
     * Non-blocking Enqueue:
     * User thread ko kabhi block nahi karta. Agar queue full ho (extreme overload),
     * toh gracefully event drop karke counter increment karta hai (Graceful Degradation).
     */
    public void enqueue(ClickEvent event) {
        if (event == null || eventQueue == null) return;

        boolean added = eventQueue.offer(event);
        if (!added) {
            droppedEvents.incrementAndGet();
            log.warn("Analytics queue full! Event drop hua (Total dropped: {})", droppedEvents.get());
        }
    }

    /**
     * Scheduled Batch Ingestion Worker:
     * Har 2 second me run hota hai, queue se batchSize jitne records nikalta hai
     * aur bulk database write karta hai.
     */
    @Scheduled(fixedDelayString = "${scalelink.analytics.flush-interval-ms:2000}")
    @Transactional
    public void flushBatch() {
        if (eventQueue == null || eventQueue.isEmpty()) {
            return;
        }

        List<ClickEvent> batch = new ArrayList<>(batchSize);
        eventQueue.drainTo(batch, batchSize);

        if (batch.isEmpty()) {
            return;
        }

        try {
            // 1. Bulk insert all click events
            clickEventRepository.saveAll(batch);

            // 2. Click count aggregation by shortCode taaki DB row lock minimize ho
            Map<String, Long> codeCounts = new HashMap<>();
            for (ClickEvent event : batch) {
                codeCounts.merge(event.getShortCode(), 1L, Long::sum);
            }

            // 3. Batch increment short url click counts
            for (Map.Entry<String, Long> entry : codeCounts.entrySet()) {
                shortUrlRepository.incrementClickCount(entry.getKey(), entry.getValue());
            }

            totalProcessedEvents.addAndGet(batch.size());
            log.debug("Batch flushed: {} click events persisted across {} short codes.", batch.size(), codeCounts.size());
        } catch (Exception e) {
            log.error("Batch analytics flush failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Application shutdown ke time bache hue saare events flush karta hai (Graceful Shutdown)
     */
    @PreDestroy
    public void onShutdown() {
        log.info("Graceful shutdown: analytics queue flush kar rahe hain... (Remaining: {})", eventQueue.size());
        while (!eventQueue.isEmpty()) {
            flushBatch();
        }
        log.info("Analytics queue completely flushed. Total processed: {}, Total dropped: {}",
                totalProcessedEvents.get(), droppedEvents.get());
    }

    /**
     * HTTP Request se Telemetry extract karke ClickEvent construct karta hai
     */
    public ClickEvent buildEventFromRequest(String shortCode, HttpServletRequest request) {
        String userAgent = request != null ? request.getHeader("User-Agent") : "";
        String referer = request != null ? request.getHeader("Referer") : "";
        String rawIp = extractClientIp(request);
        String ipHash = hashIp(rawIp);
        String country = extractCountry(request);

        String deviceType = detectDeviceType(userAgent);
        String browser = detectBrowser(userAgent);

        return ClickEvent.builder()
                .shortCode(shortCode)
                .timestamp(LocalDateTime.now())
                .ipHash(ipHash)
                .country(country)
                .referer(referer != null && referer.length() > 250 ? referer.substring(0, 250) : referer)
                .userAgent(userAgent != null && userAgent.length() > 500 ? userAgent.substring(0, 500) : userAgent)
                .deviceType(deviceType)
                .browser(browser)
                .build();
    }

    private String extractClientIp(HttpServletRequest request) {
        if (request == null) return "127.0.0.1";
        String[] headers = {"X-Forwarded-For", "CF-Connecting-IP", "X-Real-IP"};
        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                return ip.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }

    private String extractCountry(HttpServletRequest request) {
        if (request == null) return "Localhost / Dev";
        String clientIp = extractClientIp(request);
        if ("127.0.0.1".equals(clientIp) || "0:0:0:0:0:0:0:1".equals(clientIp)) {
            return "Localhost / Dev";
        }
        // Cloudflare ya Render proxy headers
        String cfCountry = request.getHeader("CF-IPCountry");
        if (cfCountry != null && !cfCountry.isEmpty()) {
            return cfCountry;
        }
        String xCountry = request.getHeader("X-Country-Code");
        if (xCountry != null && !xCountry.isEmpty()) {
            return xCountry;
        }
        String xForwardedCountry = request.getHeader("X-Forwarded-Country");
        if (xForwardedCountry != null && !xForwardedCountry.isEmpty()) {
            return xForwardedCountry;
        }
        return "India / Cloud";
    }

    private String hashIp(String ip) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((ip + "_scalelink_salt").getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.substring(0, 16); // Safe 16-char pseudo-anonymous hash
        } catch (NoSuchAlgorithmException e) {
            return "hashed_ip";
        }
    }

    private String detectDeviceType(String ua) {
        if (ua == null || ua.isEmpty()) return "Unknown";
        String lower = ua.toLowerCase();
        if (lower.contains("bot") || lower.contains("spider") || lower.contains("crawler")) return "Bot";
        if (lower.contains("ipad") || lower.contains("tablet")) return "Tablet";
        if (lower.contains("mobile") || lower.contains("android") || lower.contains("iphone")) return "Mobile";
        return "Desktop";
    }

    private String detectBrowser(String ua) {
        if (ua == null || ua.isEmpty()) return "Unknown";
        String lower = ua.toLowerCase();
        if (lower.contains("edg")) return "Edge";
        if (lower.contains("chrome") && !lower.contains("edg")) return "Chrome";
        if (lower.contains("safari") && !lower.contains("chrome")) return "Safari";
        if (lower.contains("firefox")) return "Firefox";
        if (lower.contains("opera") || lower.contains("opr")) return "Opera";
        return "Other";
    }

    public int getQueueDepth() {
        return eventQueue != null ? eventQueue.size() : 0;
    }

    public long getDroppedEventsCount() {
        return droppedEvents.get();
    }

    public long getTotalProcessedEvents() {
        return totalProcessedEvents.get();
    }
}
