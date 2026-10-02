package com.akshat.shortener.service;

import com.akshat.shortener.model.IdRangeAllocation;
import com.akshat.shortener.repository.IdRangeAllocationRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ==============================================================================
 * Distributed Range ID Generator (Key Generation Service - KGS Pattern)
 * ==============================================================================
 * 
 * Kyun banaya?
 * 1. Database auto-increment (IDENTITY) par depend karenge toh har shorten request par
 *    DB insert karna padega pehle ID lene ke liye, jisse DB bottleneck banta hai.
 * 2. Snowflake algorithm me 64-bit bits partition karna padta hai aur workerId coordinate
 *    karni padti hai jo multi-pod/Kubernetes setup me complex ho jata hai.
 * 3. Range-based Allocation (Twitter/Flickr KGS pattern):
 *    - Server pod DB se ek baar me 10,000 IDs ka block (range) atomically reserve kar leta hai
 *      (e.g., [100000 to 110000]).
 *    - Ab agle 10,000 URL shorten requests ke liye DB ko touch bhi nahi karna padta!
 *    - In-memory AtomicLong se sub-microsecond me unique ID milti hai.
 *    - Multi-pod safe: Do pods kabhi same range nahi lete kyunki DB me SELECT FOR UPDATE
 *      (Pessimistic Write Lock) laga hua hai.
 * 4. Programmatic TransactionTemplate:
 *    - @PostConstruct ke andar Spring AOP proxy bypass ho jata hai. Isliye TransactionTemplate
 *      use kiya hai taaki guaranteed active transaction rahe Pessimistic Lock ke waqt.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RangeIdGeneratorService {

    private static final String SEQUENCE_KEY = "GLOBAL_URL_SEQ";
    private static final long INITIAL_OFFSET = 100_000L; // Starting from 100K taaki 3-4 alphanumeric characters milein

    private final IdRangeAllocationRepository allocationRepository;
    private final PlatformTransactionManager transactionManager;

    @Value("${scalelink.id-generator.range-block-size:10000}")
    private long rangeBlockSize;

    // In-memory atomic state for this JVM instance
    private final AtomicLong currentId = new AtomicLong(0);
    private volatile long maxRangeId = 0;

    private final Object lock = new Object();
    private TransactionTemplate transactionTemplate;

    /**
     * Application boot hone par first range pre-fetch kar leta hai
     */
    @PostConstruct
    public void init() {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        log.info("RangeIdGenerator initializing with block size: {}", rangeBlockSize);
        fetchNextRangeFromDatabase();
    }

    /**
     * Next global unique ID generate karta hai.
     * Ultra fast (AtomicLong increment) jab tak local block exhaust na ho.
     */
    public long nextId() {
        while (true) {
            long id = currentId.getAndIncrement();
            if (id < maxRangeId) {
                return id;
            }

            // Agar range exhaust ho gayi hai, toh thread synchronize hoke naya block layega
            synchronized (lock) {
                // Double check pattern: Kya kisi dusre thread ne already naya block fetch kar liya?
                if (currentId.get() >= maxRangeId) {
                    fetchNextRangeFromDatabase();
                }
            }
        }
    }

    /**
     * Database me pessimistic write lock ke sath naya block reserve karta hai.
     * Programmatic transactionTemplate ensure karta hai ki transaction active rahe.
     */
    public void fetchNextRangeFromDatabase() {
        if (transactionTemplate == null) {
            this.transactionTemplate = new TransactionTemplate(transactionManager);
            this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        }

        transactionTemplate.execute(status -> {
            log.info("Range exhausted ya initial boot! DB se naya range block reserve kar rahe hain...");

            IdRangeAllocation allocation = allocationRepository.findByIdWithLock(SEQUENCE_KEY)
                    .orElseGet(() -> {
                        log.info("Sequence '{}' DB me nahi mila. Initial offset {} ke sath create kar rahe hain.", SEQUENCE_KEY, INITIAL_OFFSET);
                        IdRangeAllocation initial = IdRangeAllocation.builder()
                                .id(SEQUENCE_KEY)
                                .currentMaxId(INITIAL_OFFSET)
                                .stepSize(rangeBlockSize)
                                .updatedAt(LocalDateTime.now())
                                .build();
                        return allocationRepository.saveAndFlush(initial);
                    });

            long startRange = allocation.getCurrentMaxId();
            long endRange = startRange + allocation.getStepSize();

            // DB me next starting pointer save kar do
            allocation.setCurrentMaxId(endRange);
            allocation.setUpdatedAt(LocalDateTime.now());
            allocationRepository.saveAndFlush(allocation);

            // In-memory counters update karo
            this.maxRangeId = endRange;
            this.currentId.set(startRange);

            log.info("Naya Range Block successfully allocate ho gaya: [{} -> {}). Remaining in block: {}",
                    startRange, endRange, (endRange - startRange));
            return null;
        });
    }

    public long getCurrentAllocatedId() {
        return currentId.get();
    }

    public long getMaxRangeId() {
        return maxRangeId;
    }
}
