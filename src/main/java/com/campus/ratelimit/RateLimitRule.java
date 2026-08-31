package com.campus.ratelimit;

public class RateLimitRule {

    private boolean enabled = true;
    private long capacity = 20L;
    private long refillTokens = 10L;
    private long refillSeconds = 1L;
    private String message = "当前访问人数较多，请稍后重试";
    private RateLimitFallbackMode fallbackMode = RateLimitFallbackMode.FAIL;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getCapacity() {
        return capacity;
    }

    public void setCapacity(long capacity) {
        this.capacity = capacity;
    }

    public long getRefillTokens() {
        return refillTokens;
    }

    public void setRefillTokens(long refillTokens) {
        this.refillTokens = refillTokens;
    }

    public long getRefillSeconds() {
        return refillSeconds;
    }

    public void setRefillSeconds(long refillSeconds) {
        this.refillSeconds = refillSeconds;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public RateLimitFallbackMode getFallbackMode() {
        return fallbackMode;
    }

    public void setFallbackMode(RateLimitFallbackMode fallbackMode) {
        this.fallbackMode = fallbackMode;
    }
}
