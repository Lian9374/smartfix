package com.smartfix.auth.service;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Atomic, bounded, per-instance anonymous registration attempt limiter.
 * Uses the socket peer address; untrusted forwarding headers are never parsed here.
 * A distributed ingress limiter is required when deploying multiple application nodes.
 */
@Component
public class RegistrationRateLimiter {
    private final Clock clock;
    private final int perAddress;
    private final int globalLimit;
    private final int maxAddresses;
    private final long windowMillis;
    private final Map<String, ArrayDeque<Long>> attempts = new HashMap<>();
    private final ArrayDeque<Long> global = new ArrayDeque<>();

    public RegistrationRateLimiter(Clock clock,
            @Value("${smartfix.registration.rate-limit.max-per-address:5}") int perAddress,
            @Value("${smartfix.registration.rate-limit.window:PT1H}") Duration window,
            @Value("${smartfix.registration.rate-limit.max-global-per-minute:100}") int globalLimit,
            @Value("${smartfix.registration.rate-limit.max-addresses:4096}") int maxAddresses) {
        if (perAddress < 1 || globalLimit < 1 || maxAddresses < 1 || window == null
                || window.toMillis() < 1) {
            throw new IllegalArgumentException("Registration rate-limit settings must be positive");
        }
        this.clock = clock;
        this.perAddress = perAddress;
        this.windowMillis = window.toMillis();
        this.globalLimit = globalLimit;
        this.maxAddresses = maxAddresses;
    }

    /** Reserves an attempt before validation/password hashing; zero means allowed. */
    public synchronized long reserve(String peerAddress) {
        long now = clock.millis();
        prune(global, now - 60_000);
        attempts.values().forEach(q -> prune(q, now - windowMillis));
        attempts.values().removeIf(ArrayDeque::isEmpty);
        String key = peerAddress == null || peerAddress.isBlank() ? "unknown" : peerAddress;
        ArrayDeque<Long> address = attempts.get(key);
        if (global.size() >= globalLimit) { return seconds(global.peekFirst() + 60_000 - now); }
        if (address != null && address.size() >= perAddress) {
            return seconds(address.peekFirst() + windowMillis - now);
        }
        if (address == null) {
            // Do not evict an active bucket: eviction would reset somebody's quota.
            if (attempts.size() >= maxAddresses) { return seconds(windowMillis); }
            address = new ArrayDeque<>();
            attempts.put(key, address);
        }
        address.addLast(now);
        global.addLast(now);
        return 0;
    }

    private static void prune(ArrayDeque<Long> queue, long cutoff) {
        while (!queue.isEmpty() && queue.peekFirst() <= cutoff) { queue.removeFirst(); }
    }

    private static long seconds(long millis) { return Math.max(1, (millis + 999) / 1000); }
}
