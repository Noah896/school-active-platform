package com.campus;

import org.junit.jupiter.api.Test;

public class Mytest {

    @Test
    public void test() {
        long refillNanos = 1 * 1_000_000_000L;

        long availableTokens = 0;
        long lastRefillTime = System.nanoTime();

        long now = System.nanoTime();
        long elapsed = now - refillNanos;
        if (elapsed < refillNanos) {
            return;
        }
        long cycles = elapsed / refillNanos;
        long tokensToAdd = cycles * 80;

    }
}
