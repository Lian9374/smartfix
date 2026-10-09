package com.smartfix.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class RegistrationRateLimiterTest {
    private final Clock clock = mock(Clock.class);

    @Test void exactlyFiveConcurrentAttemptsAreAllowed() throws Exception {
        var limiter = new RegistrationRateLimiter(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
                5, Duration.ofHours(1), 100, 4096);
        var pool = Executors.newFixedThreadPool(12);
        var start = new CyclicBarrier(12);
        try {
            var jobs = IntStream.range(0, 12).mapToObj(i -> pool.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return limiter.reserve("127.0.0.1");
            })).toList();
            int allowed = 0;
            for (var job : jobs) { if (job.get(10, TimeUnit.SECONDS) == 0) { allowed++; } }
            assertThat(allowed).isEqualTo(5);
        } finally { pool.shutdownNow(); }
    }

    @Test void rollingWindowExpiresAndRetryAfterRoundsUp() {
        var limiter = new RegistrationRateLimiter(clock, 1, Duration.ofHours(1), 100, 2);
        when(clock.millis()).thenReturn(0L);
        assertThat(limiter.reserve("a")).isZero();
        when(clock.millis()).thenReturn(1L);
        assertThat(limiter.reserve("a")).isEqualTo(3600);
        assertThat(limiter.reserve("b")).isZero();
        when(clock.millis()).thenReturn(3_600_000L);
        assertThat(limiter.reserve("a")).isZero();
    }

    @Test void addressFloodCannotEvictExistingQuotas() {
        var limiter = new RegistrationRateLimiter(clock, 1, Duration.ofHours(1), 100, 2);
        assertThat(limiter.reserve("a")).isZero();
        assertThat(limiter.reserve("b")).isZero();
        assertThat(limiter.reserve("c")).isPositive();
        assertThat(limiter.reserve("a")).isPositive();
        when(clock.millis()).thenReturn(3_600_000L);
        assertThat(limiter.reserve("c")).isZero();
    }

    @Test void globalQuotaBoundsRequestsFromDifferentAddresses() {
        var limiter = new RegistrationRateLimiter(clock, 5, Duration.ofHours(1), 2, 4096);
        assertThat(limiter.reserve("a")).isZero();
        assertThat(limiter.reserve("b")).isZero();
        assertThat(limiter.reserve("c")).isEqualTo(60);
        when(clock.millis()).thenReturn(60_000L);
        assertThat(limiter.reserve("c")).isZero();
    }
}
