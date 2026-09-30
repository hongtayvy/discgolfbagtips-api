package com.discgolfbagtips.api.cache;

import com.discgolfbagtips.api.config.BagTipsProperties;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Caches assembled analyses so that flipping between condition tabs does not re-run the pipeline.
 *
 * <p>The API lazily analyses one condition at a time rather than pre-computing all five, so a player
 * comparing "windy" against "hot" and back again is the common path — and the second view of a
 * condition costs nothing.
 *
 * <p>Used explicitly rather than through {@code @Cacheable} for one reason: a degraded answer must
 * not be stored. When the reasoning model is unreachable the pipeline still returns something useful
 * from its deterministic fallback, and caching that for a day would turn a thirty-second outage into
 * a day of quietly worse recommendations.
 */
@Component
public class AnalysisCache {

    private static final Logger log = LoggerFactory.getLogger(AnalysisCache.class);

    private final com.github.benmanes.caffeine.cache.Cache<String, Object> cache;
    private final boolean enabled;

    public AnalysisCache(BagTipsProperties properties) {
        BagTipsProperties.Cache config = properties.cache();
        this.enabled = config.enabled();
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(config.ttl())
                .maximumSize(config.maxEntries())
                .recordStats()
                .build();
        log.info("Analysis cache: enabled={} ttl={} maxEntries={}", enabled, config.ttl(),
                config.maxEntries());
    }

    /**
     * @param cacheable decides whether a freshly computed value is worth keeping — a degraded result
     *                  is returned to the caller but not stored
     */
    public <T> CachedResult<T> get(String key, java.util.function.Supplier<T> compute,
            Predicate<T> cacheable) {

        if (!enabled) {
            return new CachedResult<>(compute.get(), false);
        }
        @SuppressWarnings("unchecked")
        T hit = (T) cache.getIfPresent(key);
        if (hit != null) {
            return new CachedResult<>(hit, true);
        }
        T computed = compute.get();
        if (cacheable.test(computed)) {
            cache.put(key, computed);
        }
        return new CachedResult<>(computed, false);
    }

    public Optional<Object> peek(String key) {
        return enabled ? Optional.ofNullable(cache.getIfPresent(key)) : Optional.empty();
    }

    public void invalidateAll() {
        cache.invalidateAll();
    }

    public Stats stats() {
        CacheStats raw = cache.stats();
        return new Stats(enabled, cache.estimatedSize(), raw.hitCount(), raw.missCount(),
                Math.round(raw.hitRate() * 1000.0) / 10.0, raw.evictionCount());
    }

    /** @param fromCache true when this response was served without re-running the pipeline. */
    public record CachedResult<T>(T value, boolean fromCache) {
    }

    public record Stats(boolean enabled, long entries, long hits, long misses, double hitRatePercent,
            long evictions) {
    }

    /** Only set once; kept here so both the recommendation and lineup paths agree on the shape. */
    public static Duration defaultTtl() {
        return Duration.ofHours(24);
    }
}
