package com.akshat.shortener.dto;

import com.akshat.shortener.model.ClickEvent;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalyticsResponse {
    private String shortCode;
    private String shortUrl;
    private String originalUrl;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private boolean isExpired;
    private long totalClicks;
    
    // Aggregation breakdowns
    private Map<String, Long> countryBreakdown;
    private Map<String, Long> deviceBreakdown;
    private Map<String, Long> refererBreakdown;
    private Map<String, Long> browserBreakdown;
    
    // Recent raw telemetry (last 20-50 clicks)
    private List<ClickEvent> recentEvents;
}
