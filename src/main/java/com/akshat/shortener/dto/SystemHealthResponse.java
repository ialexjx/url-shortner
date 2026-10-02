package com.akshat.shortener.dto;

import lombok.*;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemHealthResponse {
    private String status;
    private long uptimeSeconds;
    private boolean virtualThreadsEnabled;
    private long jvmMemoryUsedMb;
    private long jvmMemoryMaxMb;
    private long l1CacheEntries;
    private long bloomFilterEstimatedElements;
    private int analyticsQueueDepth;
    private Map<String, Object> architectureHighlights;
}
