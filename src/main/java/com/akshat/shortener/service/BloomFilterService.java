package com.akshat.shortener.service;

import com.akshat.shortener.repository.ShortUrlRepository;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ==============================================================================
 * Guava Bloom Filter Negative Cache Barrier
 * ==============================================================================
 * 
 * System Design Problem (Cache Penetration / DB Exhaustion):
 * Agar koi malicious crawler ya bot millions of random URLs request karta hai 
 * (e.g., http://short.ly/xyz99999), toh:
 * 1. Wo L1 cache me nahi milega (Cache Miss).
 * 2. Wo Redis L2 cache me nahi milega (Cache Miss).
 * 3. Har invalid request seedha PostgreSQL DB par SELECT query maregi! 
 *    Result: DB connection pool exhaust ho jayega aur legitimate users crash ho jayenge.
 * 
 * Solution (Bloom Filter):
 * 1. Ye ek probabilistic data structure hai jo O(k) hash functions se bit-array me check karta hai.
 * 2. Agar Bloom Filter ne bola "NAHI HAI", toh wo 100% guarantee hai ki DB me nahi hai!
 *    Hum wahin se instant 404 return kar dete hain, zero DB query.
 * 3. Agar usne bola "HO SAKTA HAI", tabhi hum cache aur DB check karte hain.
 * 4. Memory Footprint: 10 Lakh (1M) keys par 1% FPP ke liye sirf ~1.2 MB RAM lagti hai!
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BloomFilterService {

    private final ShortUrlRepository shortUrlRepository;

    @Value("${scalelink.bloom-filter.expected-insertions:1000000}")
    private int expectedInsertions;

    @Value("${scalelink.bloom-filter.fpp:0.01}")
    private double fpp;

    private BloomFilter<String> bloomFilter;
    private final AtomicLong elementCount = new AtomicLong(0);

    @PostConstruct
    public void init() {
        log.info("Bloom Filter initialize kar rahe hain. Expected keys: {}, FPP: {}%", expectedInsertions, (fpp * 100));
        
        this.bloomFilter = BloomFilter.create(
                Funnels.stringFunnel(StandardCharsets.UTF_8),
                expectedInsertions,
                fpp
        );

        // Application startup par existing saare short codes filter me bhar dete hain (Warm-up)
        try {
            List<String> existingCodes = shortUrlRepository.findAllActiveShortCodes();
            for (String code : existingCodes) {
                bloomFilter.put(code);
            }
            elementCount.set(existingCodes.size());
            log.info("Bloom Filter warm-up complete! Total active keys loaded: {}", existingCodes.size());
        } catch (Exception e) {
            log.warn("Bloom filter warm-up ke waqt DB query fail hui (agar initial boot hai toh normal hai): {}", e.getMessage());
        }
    }

    /**
     * Naya short code Bloom Filter me add karta hai.
     */
    public void add(String shortCode) {
        if (shortCode != null && bloomFilter != null) {
            bloomFilter.put(shortCode);
            elementCount.incrementAndGet();
        }
    }

    /**
     * Check karta hai ki short code exist kar sakta hai ya nahi.
     * Return FALSE = Definite Negative (100% sure ki DB me nahi hai).
     * Return TRUE = Probable Positive (DB/Cache check karna padega).
     */
    public boolean mightContain(String shortCode) {
        if (shortCode == null || bloomFilter == null) {
            return false;
        }
        return bloomFilter.mightContain(shortCode);
    }

    public long getEstimatedElementCount() {
        return elementCount.get();
    }
}
