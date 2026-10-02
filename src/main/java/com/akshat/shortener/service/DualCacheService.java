package com.akshat.shortener.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ==============================================================================
 * Dual-Tier Caching Service (L1 Caffeine + L2 Redis with Graceful Fallback)
 * ==============================================================================
 * 
 * Architecture Breakdown:
 * 1. L1 Cache (Caffeine): 
 *    - Process-local in-memory JVM cache.
 *    - Zero network serialization overhead, sub-millisecond (< 0.1ms) read latency.
 *    - Most frequent 50,000 URLs are served directly from RAM.
 * 
 * 2. L2 Cache (Redis):
 *    - Distributed cache layer across all container replicas / pods.
 *    - Latency ~1-3ms.
 * 
 * 3. Graceful Degradation (Render Free-Tier Friendly!):
 *    - Render par Redis add-on paid ho sakta hai ya down ho sakta hai.
 *    - Agar Redis down hai ya URL blank hai, toh application crash NAHI hogi!
 *    - L2 silently bypass ho jayega aur application L1 Caffeine se 100% smooth chalegi.
 */
@Slf4j
@Service
public class DualCacheService {

    @Value("${scalelink.cache.l1-max-size:50000}")
    private long l1MaxSize;

    @Value("${scalelink.cache.l1-expire-minutes:30}")
    private long l1ExpireMinutes;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    private Cache<String, String> l1Cache;
    private final AtomicBoolean redisAvailable = new AtomicBoolean(true);
    private static final String REDIS_PREFIX = "shortener:url:";

    @PostConstruct
    public void init() {
        log.info("L1 Caffeine cache initializing: maxSize={}, expireAfterWrite={}m", l1MaxSize, l1ExpireMinutes);
        this.l1Cache = Caffeine.newBuilder()
                .maximumSize(l1MaxSize)
                .expireAfterWrite(l1ExpireMinutes, TimeUnit.MINUTES)
                .recordStats()
                .build();

        if (redisTemplate == null) {
            log.warn("RedisTemplate bean nahi mila! L2 distributed cache disabled rahega (L1 Caffeine active).");
            redisAvailable.set(false);
        }
    }

    /**
     * Cache Lookup (L1 -> L2):
     * Pehle L1 check karta hai, miss hone par L2 check karta hai aur L1 ko warm up karta hai.
     */
    public Optional<String> get(String shortCode) {
        if (shortCode == null) return Optional.empty();

        // 1. L1 Caffeine check (Sub-millisecond)
        String cachedInL1 = l1Cache.getIfPresent(shortCode);
        if (cachedInL1 != null) {
            return Optional.of(cachedInL1);
        }

        // 2. L2 Redis check (Distributed)
        if (redisAvailable.get() && redisTemplate != null) {
            try {
                String cachedInRedis = redisTemplate.opsForValue().get(REDIS_PREFIX + shortCode);
                if (cachedInRedis != null) {
                    // L1 ko warm up kar do taaki agla read L1 se ho
                    l1Cache.put(shortCode, cachedInRedis);
                    return Optional.of(cachedInRedis);
                }
            } catch (Exception e) {
                handleRedisFailure("L2 Cache read failed", e);
            }
        }

        return Optional.empty();
    }

    /**
     * Cache Write (L1 aur L2 dono me store karta hai)
     */
    public void put(String shortCode, String originalUrl, long ttlMinutes) {
        if (shortCode == null || originalUrl == null) return;

        // L1 me put karo
        l1Cache.put(shortCode, originalUrl);

        // L2 Redis me put karo
        if (redisAvailable.get() && redisTemplate != null) {
            try {
                long duration = ttlMinutes > 0 ? ttlMinutes : (l1ExpireMinutes * 2);
                redisTemplate.opsForValue().set(
                        REDIS_PREFIX + shortCode,
                        originalUrl,
                        Duration.ofMinutes(duration)
                );
            } catch (Exception e) {
                handleRedisFailure("L2 Cache write failed", e);
            }
        }
    }

    /**
     * Cache Invalidation:
     * Link delete ya deactivate hone par L1 aur L2 dono se clear karta hai.
     */
    public void evict(String shortCode) {
        if (shortCode == null) return;

        l1Cache.invalidate(shortCode);

        if (redisAvailable.get() && redisTemplate != null) {
            try {
                redisTemplate.delete(REDIS_PREFIX + shortCode);
            } catch (Exception e) {
                handleRedisFailure("L2 Cache evict failed", e);
            }
        }
    }

    private void handleRedisFailure(String action, Exception e) {
        if (redisAvailable.compareAndSet(true, false)) {
            log.warn("{}: Redis connection unreachable! Gracefully falling back to L1 Caffeine only. Error: {}", action, e.getMessage());
        }
    }

    public long getL1CacheEstimatedSize() {
        return l1Cache != null ? l1Cache.estimatedSize() : 0;
    }

    public boolean isRedisAvailable() {
        return redisAvailable.get() && redisTemplate != null;
    }
}
