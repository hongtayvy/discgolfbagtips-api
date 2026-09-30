package com.discgolfbagtips.api.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.discgolfbagtips.api.TestDiscs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DiscClassificationTest {

    @ParameterizedTest
    @CsvSource({
            "2, PUTT_AND_APPROACH",
            "4, PUTT_AND_APPROACH",
            "4.5, MIDRANGE",
            "6, MIDRANGE",
            "7, FAIRWAY_DRIVER",
            "9, FAIRWAY_DRIVER",
            "10, DISTANCE_DRIVER",
            "14.5, DISTANCE_DRIVER"
    })
    void mapsSpeedToSlot(double speed, DiscSlot expected) {
        assertThat(DiscSlot.forSpeed(speed)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "-4, VERY_UNDERSTABLE",
            "-1, UNDERSTABLE",
            "0, STABLE",
            "1, STABLE",
            "2, OVERSTABLE",
            "4, VERY_OVERSTABLE"
    })
    void mapsStabilityIndexToClass(double index, StabilityClass expected) {
        assertThat(StabilityClass.forIndex(index)).isEqualTo(expected);
    }

    @Test
    void wellKnownDiscsLandWhereAPlayerWouldExpect() {
        // Buzzz 5/4/-1/1 is the archetypal straight midrange.
        assertThat(TestDiscs.disc("a", "Discraft", "Buzzz", "Midrange", 5, 4, -1, 1).stabilityClass())
                .isEqualTo(StabilityClass.STABLE);
        // Firebird 9/3/0/4 is the archetypal utility overstable driver.
        assertThat(TestDiscs.disc("b", "Innova", "Firebird", "Control Driver", 9, 3, 0, 4).stabilityClass())
                .isEqualTo(StabilityClass.VERY_OVERSTABLE);
        // Sidewinder 9/5/-3/1 is a classic understable turnover driver.
        assertThat(TestDiscs.disc("c", "Innova", "Sidewinder", "Distance Driver", 9, 5, -3, 1).stabilityClass())
                .isEqualTo(StabilityClass.VERY_UNDERSTABLE);
    }

    @Test
    void slugsAreStableAcrossFormattingDifferences() {
        assertThat(Slugs.of("Big Z")).isEqualTo("big-z");
        assertThat(Slugs.of("  ESP  ")).isEqualTo("esp");
        assertThat(Slugs.of("Pro-D")).isEqualTo("pro-d");
        assertThat(Slugs.of("400G")).isEqualTo("400g");
        assertThat(Slugs.of(null)).isEmpty();
    }
}
