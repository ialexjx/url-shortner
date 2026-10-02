package com.akshat.shortener.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ==============================================================================
 * Admin Portal Overview & Global Metrics DTO
 * ==============================================================================
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminOverviewResponse {
    private long totalUrls;
    private long totalClicks;
    private long activeUrls;
    private long inactiveUrls;
    private long l1CacheEntries;
    private long bloomFilterEntries;
    private int asyncQueueDepth;
    private long jvmMemoryUsedMb;
    private long jvmMemoryMaxMb;
}
