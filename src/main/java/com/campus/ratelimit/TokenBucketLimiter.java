package com.campus.ratelimit;

public class TokenBucketLimiter {

    private final long capacity;
    private final long refillTokens;
    private final long refillNanos;
    private long availableTokens;
    private long lastRefillTime;

    public TokenBucketLimiter(long capacity, long refillTokens, long refillSeconds) {
        this.capacity = Math.max(1L, capacity);
        this.refillTokens = Math.max(1L, refillTokens);
        this.refillNanos = Math.max(1L, refillSeconds) * 1_000_000_000L;
        this.availableTokens = this.capacity;
        this.lastRefillTime = System.nanoTime();
    }

    public synchronized boolean tryAcquire() {
        refill();
        if (availableTokens < 1L) {
            return false;
        }
        availableTokens--;
        return true;
    }

    private void refill() {
        long now = System.nanoTime();
        long elapsed = now - lastRefillTime;
        if (elapsed < refillNanos) {
            return;
        }
        long cycles = elapsed / refillNanos;
        long tokensToAdd = cycles * refillTokens;
        availableTokens = Math.min(capacity, availableTokens + tokensToAdd);
        lastRefillTime += cycles * refillNanos;
    }
}
