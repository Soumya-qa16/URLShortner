package com.example.dto;
import java.time.LocalDateTime;
import java.util.Map;

public class AnalyticsResponse {

    private String shortKey;
    private String longUrl;
    private long totalClicks;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private Map<String, Long> userAgents;

    public AnalyticsResponse() {
    }

    public AnalyticsResponse(String shortKey, String longUrl, long totalClicks, 
                             LocalDateTime createdAt, LocalDateTime expiresAt, 
                             Map<String, Long> userAgents) {
        this.shortKey = shortKey;
        this.longUrl = longUrl;
        this.totalClicks = totalClicks;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.userAgents = userAgents;
    }

    public String getShortKey() {
        return shortKey;
    }

    public void setShortKey(String shortKey) {
        this.shortKey = shortKey;
    }

    public String getLongUrl() {
        return longUrl;
    }

    public void setLongUrl(String longUrl) {
        this.longUrl = longUrl;
    }

    public long getTotalClicks() {
        return totalClicks;
    }

    public void setTotalClicks(long totalClicks) {
        this.totalClicks = totalClicks;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Map<String, Long> getUserAgents() {
        return userAgents;
    }

    public void setUserAgents(Map<String, Long> userAgents) {
        this.userAgents = userAgents;
    }
}