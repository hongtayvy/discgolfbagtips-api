package com.discgolfbagtips.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class TokenBucketTest {

    private final AtomicLong clock = new AtomicLong(0);

    private TokenBucket bucket(int capacity, int refillTokens, Duration period) {
        return new TokenBucket(capacity, refillTokens, period, clock::get);
    }

    @Test
    void allowsUpToCapacityThenRefuses() {
        TokenBucket bucket = bucket(3, 3, Duration.ofMinutes(1));

        assertThat(bucket.tryConsume().allowed()).isTrue();
        assertThat(bucket.tryConsume().allowed()).isTrue();
        TokenBucket.Verdict last = bucket.tryConsume();
        assertThat(last.allowed()).isTrue();
        assertThat(last.remaining()).isZero();

        TokenBucket.Verdict refused = bucket.tryConsume();
        assertThat(refused.allowed()).isFalse();
        assertThat(refused.remaining()).isZero();
        assertThat(refused.retryAfter()).isPositive();
    }

    @Test
    void refillsProportionallyToElapsedTime() {
        TokenBucket bucket = bucket(10, 10, Duration.ofSeconds(10));
        for (int i = 0; i < 10; i++) {
            assertThat(bucket.tryConsume().allowed()).isTrue();
        }
        assertThat(bucket.tryConsume().allowed()).isFalse();

        // One token per second: after three seconds exactly three calls should get through.
        clock.addAndGet(Duration.ofSeconds(3).toNanos());
        assertThat(bucket.tryConsume().allowed()).isTrue();
        assertThat(bucket.tryConsume().allowed()).isTrue();
        assertThat(bucket.tryConsume().allowed()).isTrue();
        assertThat(bucket.tryConsume().allowed()).isFalse();
    }

    @Test
    void neverRefillsBeyondCapacity() {
        TokenBucket bucket = bucket(2, 2, Duration.ofSeconds(1));
        clock.addAndGet(Duration.ofHours(1).toNanos());

        assertThat(bucket.tryConsume().allowed()).isTrue();
        assertThat(bucket.tryConsume().allowed()).isTrue();
        assertThat(bucket.tryConsume().allowed()).isFalse();
    }

    @Test
    void retryAfterReflectsTheRefillRate() {
        TokenBucket bucket = bucket(1, 1, Duration.ofSeconds(60));
        assertThat(bucket.tryConsume().allowed()).isTrue();

        TokenBucket.Verdict refused = bucket.tryConsume();
        assertThat(refused.retryAfter()).isBetween(Duration.ofSeconds(59), Duration.ofSeconds(60));
    }

    @Test
    void rejectsNonsensicalConfiguration() {
        assertThatThrownBy(() -> bucket(0, 1, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> bucket(1, 1, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
