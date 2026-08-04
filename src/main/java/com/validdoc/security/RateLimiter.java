package com.validdoc.security;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class RateLimiter {

    private final int maxAttempts;
    private final long windowMillis;
    private final boolean slidingWindow;
    private final ConcurrentHashMap<String, Window> attemptsByKey = new ConcurrentHashMap<>();

    public RateLimiter(int maxAttempts, long windowMillis) {
        this(maxAttempts, windowMillis, false);
    }

    public RateLimiter(int maxAttempts, long windowMillis, boolean slidingWindow) {
        this.maxAttempts = maxAttempts;
        this.windowMillis = windowMillis;
        this.slidingWindow = slidingWindow;
    }

    public boolean tryConsume(String key) {
        Window window = windowFor(key);
        synchronized (window) {
            resetIfExpired(window);
            boolean allowed = window.count.incrementAndGet() <= maxAttempts;
            if (slidingWindow) {
                window.startedAt = System.currentTimeMillis();
            }
            return allowed;
        }
    }

    public void recordFailure(String key) {
        Window window = windowFor(key);
        synchronized (window) {
            resetIfExpired(window);
            window.count.incrementAndGet();
            if (slidingWindow) {
                window.startedAt = System.currentTimeMillis();
            }
        }
    }

    public boolean isBlocked(String key) {
        Window window = attemptsByKey.get(key);
        if (window == null) {
            return false;
        }
        synchronized (window) {
            resetIfExpired(window);
            return window.count.get() >= maxAttempts;
        }
    }

    public long getRemainingAttempts(String key) {
        Window window = attemptsByKey.get(key);
        if (window == null) {
            return maxAttempts;
        }
        synchronized (window) {
            resetIfExpired(window);
            return Math.max(maxAttempts - window.count.get(), 0);
        }
    }

    public long getRetryAfterMillis(String key) {
        Window window = attemptsByKey.get(key);
        if (window == null) {
            return 0;
        }
        synchronized (window) {
            long elapsed = System.currentTimeMillis() - window.startedAt;
            return Math.max(windowMillis - elapsed, 0);
        }
    }

    private Window windowFor(String key) {
        return attemptsByKey.computeIfAbsent(key, k -> new Window(System.currentTimeMillis()));
    }

    private void resetIfExpired(Window window) {
        if (System.currentTimeMillis() - window.startedAt > windowMillis) {
            window.startedAt = System.currentTimeMillis();
            window.count.set(0);
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