package com.validdoc.security;

import org.springframework.stereotype.Component;

@Component
public class UploadRateLimiter {

    private static final int MAX_ATTEMPTS = 20;
    private static final long WINDOW_MILLIS = 60_000;

    private final RateLimiter rateLimiter = new RateLimiter(MAX_ATTEMPTS, WINDOW_MILLIS);

    public boolean tryConsume(String key) {
        return rateLimiter.tryConsume(key);
    }
}