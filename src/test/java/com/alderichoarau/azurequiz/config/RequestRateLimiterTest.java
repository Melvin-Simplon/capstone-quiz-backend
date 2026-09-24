package com.alderichoarau.azurequiz.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RequestRateLimiterTest {
    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));

    private RequestRateLimiter limiter(int maxRequests) {
        return new RequestRateLimiter(maxRequests, Duration.ofMinutes(1), now::get);
    }

    private void advance(Duration duration) {
        now.updateAndGet(instant -> instant.plus(duration));
    }

    @Test
    @DisplayName("lets a caller through up to the limit, then turns it away")
    void allowsUpToTheLimit() {
        RequestRateLimiter limiter = limiter(3);

        assertThat(limiter.tryAcquire("1.2.3.4")).isTrue();
        assertThat(limiter.tryAcquire("1.2.3.4")).isTrue();
        assertThat(limiter.tryAcquire("1.2.3.4")).isTrue();
        assertThat(limiter.tryAcquire("1.2.3.4")).isFalse();
    }

    @Test
    @DisplayName("counts each caller separately, so one flood does not lock the others out")
    void countsCallersSeparately() {
        RequestRateLimiter limiter = limiter(1);

        assertThat(limiter.tryAcquire("1.2.3.4")).isTrue();
        assertThat(limiter.tryAcquire("1.2.3.4")).isFalse();
        assertThat(limiter.tryAcquire("5.6.7.8")).isTrue();
    }

    @Test
    @DisplayName("refills once the window has passed")
    void refillsAfterTheWindow() {
        RequestRateLimiter limiter = limiter(1);

        assertThat(limiter.tryAcquire("1.2.3.4")).isTrue();
        assertThat(limiter.tryAcquire("1.2.3.4")).isFalse();

        advance(Duration.ofSeconds(61));

        assertThat(limiter.tryAcquire("1.2.3.4")).isTrue();
    }

    @Test
    @DisplayName("reports how long is left before the allowance comes back")
    void reportsRetryAfter() {
        RequestRateLimiter limiter = limiter(1);
        limiter.tryAcquire("1.2.3.4");

        advance(Duration.ofSeconds(20));

        assertThat(limiter.retryAfterSeconds("1.2.3.4")).isEqualTo(40);
        assertThat(limiter.retryAfterSeconds("never.seen")).isZero();
    }

    @Test
    @DisplayName("never reports zero seconds to a caller that has just been turned away")
    void neverReportsZeroWhileBlocked() {
        RequestRateLimiter limiter = limiter(1);
        limiter.tryAcquire("1.2.3.4");

        advance(Duration.ofSeconds(59).plusMillis(900));

        assertThat(limiter.retryAfterSeconds("1.2.3.4")).isEqualTo(1);
    }
}
