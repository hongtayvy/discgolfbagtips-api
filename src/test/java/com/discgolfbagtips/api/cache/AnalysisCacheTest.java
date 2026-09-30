package com.discgolfbagtips.api.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.discgolfbagtips.api.TestProperties;
import com.discgolfbagtips.api.player.CourseConditions;
import com.discgolfbagtips.api.player.CourseType;
import com.discgolfbagtips.api.player.PlayerProfile;
import com.discgolfbagtips.api.player.SkillLevel;
import com.discgolfbagtips.api.player.ThrowingStyle;
import com.discgolfbagtips.api.player.WeatherCondition;
import com.discgolfbagtips.api.recommendation.dto.BagAnalysisRequest;
import com.discgolfbagtips.api.recommendation.dto.BagDiscRequest;
import com.discgolfbagtips.api.recommendation.dto.RecommendationFilters;
import com.discgolfbagtips.api.catalog.WearState;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AnalysisCacheTest {

    private BagAnalysisRequest request(List<BagDiscRequest> bag, WeatherCondition weather,
            RecommendationFilters filters) {
        return new BagAnalysisRequest(bag,
                new PlayerProfile(SkillLevel.INTERMEDIATE, ThrowingStyle.FOREHAND, CourseType.WOODED),
                new CourseConditions(weather), filters, null);
    }

    private BagDiscRequest disc(String name, Integer grams, WearState wear) {
        return new BagDiscRequest(null, name, "Innova", "Star", grams, wear);
    }

    // --- key ---------------------------------------------------------------

    @Test
    void reorderingTheSameBagIsTheSameKey() {
        var a = request(List.of(disc("Buzzz", 177, null), disc("Aviar", 175, null)),
                WeatherCondition.WINDY, null);
        var b = request(List.of(disc("Aviar", 175, null), disc("Buzzz", 177, null)),
                WeatherCondition.WINDY, null);

        assertThat(AnalysisCacheKey.of("lineup", a)).isEqualTo(AnalysisCacheKey.of("lineup", b));
    }

    /** The whole point of lazy per-condition analysis: each tab is its own entry. */
    @Test
    void conditionChangesTheKey() {
        var windy = request(List.of(disc("Buzzz", 177, null)), WeatherCondition.WINDY, null);
        var hot = request(List.of(disc("Buzzz", 177, null)), WeatherCondition.HOT, null);

        assertThat(AnalysisCacheKey.of("lineup", windy)).isNotEqualTo(AnalysisCacheKey.of("lineup", hot));
    }

    @Test
    void everyPerInstanceAxisChangesTheKey() {
        var base = request(List.of(disc("Buzzz", 177, WearState.NEW)), WeatherCondition.WINDY, null);
        var heavier = request(List.of(disc("Buzzz", 180, WearState.NEW)), WeatherCondition.WINDY, null);
        var worn = request(List.of(disc("Buzzz", 177, WearState.BEAT_IN)), WeatherCondition.WINDY, null);

        String key = AnalysisCacheKey.of("lineup", base);
        assertThat(AnalysisCacheKey.of("lineup", heavier)).isNotEqualTo(key);
        assertThat(AnalysisCacheKey.of("lineup", worn)).isNotEqualTo(key);
    }

    @Test
    void filtersAndEndpointBothChangeTheKey() {
        var plain = request(List.of(disc("Buzzz", 177, null)), WeatherCondition.WINDY, null);
        var filtered = request(List.of(disc("Buzzz", 177, null)), WeatherCondition.WINDY,
                new RecommendationFilters(List.of("Innova"), List.of(), null));

        assertThat(AnalysisCacheKey.of("lineup", filtered))
                .isNotEqualTo(AnalysisCacheKey.of("lineup", plain));
        assertThat(AnalysisCacheKey.of("recommendations", plain))
                .isNotEqualTo(AnalysisCacheKey.of("lineup", plain));
    }

    // --- cache -------------------------------------------------------------

    @Test
    void computesOnceThenServesFromCache() {
        AnalysisCache cache = new AnalysisCache(TestProperties.defaults());
        AtomicInteger computations = new AtomicInteger();

        var first = cache.get("k", () -> "value-" + computations.incrementAndGet(), v -> true);
        var second = cache.get("k", () -> "value-" + computations.incrementAndGet(), v -> true);

        assertThat(first.fromCache()).isFalse();
        assertThat(second.fromCache()).isTrue();
        assertThat(second.value()).isEqualTo(first.value());
        assertThat(computations.get()).isEqualTo(1);
        assertThat(cache.stats().hits()).isEqualTo(1);
    }

    /** A transient failure must be served but not stored, or one blip poisons a day. */
    @Test
    void refusesToStoreWhatThePredicateRejects() {
        AnalysisCache cache = new AnalysisCache(TestProperties.defaults());
        AtomicInteger computations = new AtomicInteger();

        cache.get("k", () -> "degraded-" + computations.incrementAndGet(), v -> false);
        var second = cache.get("k", () -> "degraded-" + computations.incrementAndGet(), v -> false);

        assertThat(second.fromCache()).isFalse();
        assertThat(computations.get()).isEqualTo(2);
        assertThat(cache.stats().entries()).isZero();
    }

    @Test
    void distinctKeysDoNotCollide() {
        AnalysisCache cache = new AnalysisCache(TestProperties.defaults());

        cache.get("a", () -> "first", v -> true);
        var b = cache.get("b", () -> "second", v -> true);

        assertThat(b.value()).isEqualTo("second");
        assertThat(cache.stats().entries()).isEqualTo(2);
    }

    @Test
    void reportsItsOwnHealth() {
        AnalysisCache cache = new AnalysisCache(TestProperties.defaults());
        cache.get("a", () -> "x", v -> true);
        cache.get("a", () -> "x", v -> true);

        AnalysisCache.Stats stats = cache.stats();
        assertThat(stats.enabled()).isTrue();
        assertThat(stats.hits()).isEqualTo(1);
        assertThat(stats.misses()).isEqualTo(1);
        assertThat(stats.hitRatePercent()).isEqualTo(50.0);
    }
}
