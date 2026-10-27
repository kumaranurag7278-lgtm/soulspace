package com.soulspace.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory rate limiter using token bucket per client key (IP or identifier).
 */
@Service
public class RateLimiter {

    private static class Bucket {
        int tokens;
        long lastRefill;

        Bucket(int tokens, long lastRefill) {
            this.tokens = tokens;
            this.lastRefill = lastRefill;
        }
    }

    private final Map<String, Map<String, Bucket>> categoryBuckets = new ConcurrentHashMap<>();

    /**
     * Checks if a request is allowed under the rate limit.
     *
     * @param category The rate limit category (e.g., "board_post", "chat_match", "board_react")
     * @param key The client identifier (typically client IP or client ID)
     * @param maxTokens Maximum allowed burst tokens
     * @param refillTimeMs Time in ms for a full refill of tokens
     * @return true if the action is allowed, false if rate limited.
     */
    public boolean allow(String category, String key, int maxTokens, long refillTimeMs) {
        if (key == null || key.isBlank()) return true;
        Map<String, Bucket> buckets = categoryBuckets.computeIfAbsent(category, k -> new ConcurrentHashMap<>());
        long now = System.currentTimeMillis();

        Bucket b = buckets.computeIfAbsent(key, k -> new Bucket(maxTokens, now));
        synchronized (b) {
            long elapsed = now - b.lastRefill;
            if (elapsed > 0) {
                int refill = (int) ((elapsed * maxTokens) / refillTimeMs);
                if (refill > 0) {
                    b.tokens = Math.min(maxTokens, b.tokens + refill);
                    b.lastRefill = now;
                }
            }
            if (b.tokens > 0) {
                b.tokens--;
                return true;
            }
            return false;
        }
    }
}
