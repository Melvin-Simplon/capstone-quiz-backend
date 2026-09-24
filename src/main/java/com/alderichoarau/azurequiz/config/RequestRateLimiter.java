package com.alderichoarau.azurequiz.config;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class RequestRateLimiter {
    private static final int SWEEP_THRESHOLD = 10_000;

    private final int maxRequests;
    private final Duration window;
    private final Supplier<Instant> clock;
    private final Map<String, Counter> counters = new ConcurrentHashMap<>();

    public RequestRateLimiter(int maxRequests, Duration window, Supplier<Instant> clock) {
        this.maxRequests = maxRequests;
        this.window = window;
        this.clock = clock;
    }

    public RequestRateLimiter(int maxRequests, Duration window) {
        this(maxRequests, window, Instant::now);
    }

    public boolean tryAcquire(String key) {
        Instant now = clock.get();

        if (counters.size() > SWEEP_THRESHOLD) {
            counters.values().removeIf(counter -> counter.hasExpired(now, window));
        }

        Counter counter = counters.computeIfAbsent(key, ignored -> new Counter(now));
        return counter.tryAcquire(now, window, maxRequests);
    }

    public long retryAfterSeconds(String key) {
        Counter counter = counters.get(key);
        if (counter == null) {
            return 0;
        }
        long elapsed = Duration.between(counter.startedAt(), clock.get()).getSeconds();
        return Math.max(1, window.getSeconds() - elapsed);
    }

    int trackedKeys() {
        return counters.size();
    }

    private static final class Counter {
        private volatile Instant startedAt;
        private final AtomicInteger count = new AtomicInteger();

        private Counter(Instant startedAt) {
            this.startedAt = startedAt;
        }

        private synchronized boolean tryAcquire(Instant now, Duration window, int maxRequests) {
            if (hasExpired(now, window)) {
                startedAt = now;
                count.set(0);
            }
            return count.incrementAndGet() <= maxRequests;
        }

        private boolean hasExpired(Instant now, Duration window) {
            return Duration.between(startedAt, now).compareTo(window) >= 0;
        }

        private Instant startedAt() {
            return startedAt;
        }
    }
}
