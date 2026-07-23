package com.validdoc.security;

import org.springframework.stereotype.Component;

@Component
public class LoginRateLimiter {

    private static final int MAX_ATTEMPTS = 5;
    private static final long WINDOW_MILLIS = 60_000;

    private final RateLimiter rateLimiter = new RateLimiter(MAX_ATTEMPTS, WINDOW_MILLIS);

    public boolean tryConsume(String key) {
        return rateLimiter.tryConsume(key);
    }
}