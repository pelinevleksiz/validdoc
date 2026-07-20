package com.validdoc.security;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class LoginRateLimiter {

    private static final int MAX_ATTEMPTS = 5;
    private static final long WINDOW_MILLIS = 60_000;

    private final ConcurrentHashMap<String, Window> attemptsByKey = new ConcurrentHashMap<>();

    public boolean tryConsume(String key) {
        long now = System.currentTimeMillis();
        Window window = attemptsByKey.computeIfAbsent(key, k -> new Window(now));

        synchronized (window) {
            if (now - window.startedAt > WINDOW_MILLIS) {
                window.startedAt = now;
                window.count.set(0);
            }
            return window.count.incrementAndGet() <= MAX_ATTEMPTS;
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