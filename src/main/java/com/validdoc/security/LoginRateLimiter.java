package com.validdoc.security;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

@Component
public class LoginRateLimiter {

    private static final int MAX_ATTEMPTS = 5;
    private static final long WINDOW_MILLIS = 60_000;
    private final ConcurrentHashMap<String, Attempts> attemptsByKey = new ConcurrentHashMap<>();

    public boolean isBlocked(String key) {
        Attempts attempts = attemptsByKey.get(key);
        if (attempts == null) {
            return false;
        }
        synchronized (attempts) {
            resetIfExpired(attempts);
            return attempts.count.get() >= MAX_ATTEMPTS;
        }
    }

    public void recordFailure(String key) {
        long now = System.currentTimeMillis();
        Attempts attempts = attemptsByKey.computeIfAbsent(key, k -> new Attempts(now));
        synchronized (attempts) {
            resetIfExpired(attempts);
            attempts.count.incrementAndGet();
            attempts.lastFailureAt = now;
        }
    }

    public long getRetryAfterMillis(String key) {
        Attempts attempts = attemptsByKey.get(key);
        if (attempts == null) {
            return 0;
        }
        synchronized (attempts) {
            long elapsed = System.currentTimeMillis() - attempts.lastFailureAt;
            return Math.max(WINDOW_MILLIS - elapsed, 0);
        }
    }

    public long getRemainingAttempts(String key) {
        Attempts attempts = attemptsByKey.get(key);
        if (attempts == null) {
            return MAX_ATTEMPTS;
        }
        synchronized (attempts) {
            resetIfExpired(attempts);
            return Math.max(MAX_ATTEMPTS - attempts.count.get(), 0);
        }
    }

    private void resetIfExpired(Attempts attempts) {
        if (System.currentTimeMillis() - attempts.lastFailureAt > WINDOW_MILLIS) {
            attempts.count.set(0);
        }
    }

    private static final class Attempts {
        private volatile long lastFailureAt;
        private final AtomicInteger count = new AtomicInteger(0);

        private Attempts(long lastFailureAt) {
            this.lastFailureAt = lastFailureAt;
        }
    }
}