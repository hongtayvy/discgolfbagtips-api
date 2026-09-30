package com.discgolfbagtips.api.config;

import java.time.Duration;
import java.util.function.LongSupplier;

/**
 * A lazily-refilled token bucket. Refill is computed from elapsed time on read rather than by a
 * background thread, so an idle bucket costs nothing.
 */
public final class TokenBucket {

    private final long capacity;
    private final long refillTokens;
    private final long refillPeriodNanos;
    private final LongSupplier nanoClock;

    private double tokens;
    private long lastRefillNanos;
    private volatile long lastAccessNanos;

    public TokenBucket(long capacity, long refillTokens, Duration refillPeriod) {
        this(capacity, refillTokens, refillPeriod, System::nanoTime);
    }

    public TokenBucket(long capacity, long refillTokens, Duration refillPeriod, LongSupplier nanoClock) {
        if (capacity <= 0 || refillTokens <= 0 || refillPeriod.isZero() || refillPeriod.isNegative()) {
            throw new IllegalArgumentException("capacity, refillTokens and refillPeriod must all be positive");
        }
        this.capacity = capacity;
        this.refillTokens = refillTokens;
        this.refillPeriodNanos = refillPeriod.toNanos();
        this.nanoClock = nanoClock;
        this.tokens = capacity;
        this.lastRefillNanos = nanoClock.getAsLong();
        this.lastAccessNanos = this.lastRefillNanos;
    }

    /** @return the outcome of attempting to spend a single token. */
    public synchronized Verdict tryConsume() {
        long now = nanoClock.getAsLong();
        lastAccessNanos = now;
        refill(now);
        if (tokens >= 1.0d) {
            tokens -= 1.0d;
            return new Verdict(true, capacity, (long) Math.floor(tokens), Duration.ZERO);
        }
        double deficit = 1.0d - tokens;
        long waitNanos = (long) Math.ceil(deficit * refillPeriodNanos / refillTokens);
        return new Verdict(false, capacity, 0L, Duration.ofNanos(waitNanos));
    }

    public long lastAccessNanos() {
        return lastAccessNanos;
    }

    private void refill(long now) {
        long elapsed = now - lastRefillNanos;
        if (elapsed <= 0) {
            return;
        }
        double replenished = (double) elapsed * refillTokens / refillPeriodNanos;
        if (replenished > 0) {
            tokens = Math.min(capacity, tokens + replenished);
            lastRefillNanos = now;
        }
    }

    /** Result of a bucket check, shaped for the {@code X-RateLimit-*} headers. */
    public record Verdict(boolean allowed, long limit, long remaining, Duration retryAfter) {
    }
}
