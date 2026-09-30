package com.discgolfbagtips.api.config;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Two independent limits per caller: a generous one covering the whole API (catalog type-ahead is
 * chatty) and a tight one for {@code POST /api/v1/recommendations}, which spends real money and
 * real free-tier quota on every call.
 */
@Service
public class RateLimitService {

    /** Buckets untouched for this long are dropped, keeping the map bounded without an LRU. */
    private static final Duration IDLE_EVICTION = Duration.ofMinutes(30);

    public enum Tier {
        GENERAL,
        RECOMMENDATION
    }

    private final BagTipsProperties.RateLimit config;
    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public RateLimitService(BagTipsProperties properties) {
        this.config = properties.rateLimit();
    }

    public boolean enabled() {
        return config.enabled();
    }

    public TokenBucket.Verdict check(String clientKey, Tier tier) {
        if (!config.enabled()) {
            return new TokenBucket.Verdict(true, -1, -1, Duration.ZERO);
        }
        TokenBucket bucket = buckets.computeIfAbsent(tier.name() + '|' + clientKey, key -> newBucket(tier));
        return bucket.tryConsume();
    }

    @Scheduled(fixedDelay = 10, timeUnit = java.util.concurrent.TimeUnit.MINUTES)
    void evictIdleBuckets() {
        long cutoff = System.nanoTime() - IDLE_EVICTION.toNanos();
        buckets.entrySet().removeIf(entry -> entry.getValue().lastAccessNanos() < cutoff);
    }

    int trackedBuckets() {
        return buckets.size();
    }

    private TokenBucket newBucket(Tier tier) {
        return switch (tier) {
            case GENERAL -> new TokenBucket(config.capacity(), config.refillTokens(), config.refillPeriod());
            case RECOMMENDATION -> new TokenBucket(config.recommendationCapacity(),
                    config.recommendationRefillTokens(), config.recommendationRefillPeriod());
        };
    }
}
