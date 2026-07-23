package com.validdoc.security;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class RateLimiter {

    private final int maxAttempts;
    private final long windowMillis;
    private final ConcurrentHashMap<String, Window> attemptsByKey = new ConcurrentHashMap<>();

    public RateLimiter(int maxAttempts, long windowMillis) {
        this.maxAttempts = maxAttempts;
        this.windowMillis = windowMillis;
    }

    public boolean tryConsume(String key) {
        long now = System.currentTimeMillis();
        Window window = attemptsByKey.computeIfAbsent(key, k -> new Window(now));

        synchronized (window) {
            if (now - window.startedAt > windowMillis) {
                window.startedAt = now;
                window.count.set(0);
            }
            return window.count.incrementAndGet() <= maxAttempts;
        }
    }

    private static final class Window {
        private volatile long startedAt;
        private final AtomicInteger count = new AtomicInteger(0);

        private Window(long startedAt) {
            this.startedAt = startedAt;
        }
    }
}