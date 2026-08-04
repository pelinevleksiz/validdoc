package com.validdoc.security;

import org.springframework.stereotype.Component;

@Component
public class LoginRateLimiter {

    private static final int MAX_ATTEMPTS = 5;
    private static final long WINDOW_MILLIS = 60_000;

    private final RateLimiter rateLimiter = new RateLimiter(MAX_ATTEMPTS, WINDOW_MILLIS, true);

    public boolean isBlocked(String key) {
        return rateLimiter.isBlocked(key);
    }

    public void recordFailure(String key) {
        rateLimiter.recordFailure(key);
    }

    public long getRetryAfterMillis(String key) {
        return rateLimiter.getRetryAfterMillis(key);
    }

    public long getRemainingAttempts(String key) {
        return rateLimiter.getRemainingAttempts(key);
    }
}