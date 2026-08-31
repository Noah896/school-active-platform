package com.campus.ratelimit;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitManager {

    private final RateLimitProperties properties;
    private final Map<String, TokenBucketLimiter> limiters = new ConcurrentHashMap<>();

    public RateLimitManager(RateLimitProperties properties) {
        this.properties = properties;
    }

    public RateLimitRule getRule(String scene) {
        if (!properties.isEnabled() || scene == null) {
            return null;
        }
        return properties.getScenes().get(scene);
    }

    public boolean tryAcquire(String scene, RateLimitRule rule) {
        if (scene == null || rule == null || !rule.isEnabled()) {
            return true;
        }
        TokenBucketLimiter limiter = limiters.computeIfAbsent(scene,
                key -> new TokenBucketLimiter(rule.getCapacity(), rule.getRefillTokens(), rule.getRefillSeconds()));
        return limiter.tryAcquire();
    }
}
