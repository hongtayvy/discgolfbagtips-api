package com.discgolfbagtips.api.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.discgolfbagtips.api.catalog.sync.DiscItDisc;
import org.junit.jupiter.api.Test;

class DiscItDiscTest {

    private DiscItDisc disc(String speed, String glide, String turn, String fade) {
        return new DiscItDisc("id-1", "Destroyer", "Innova", "Distance Driver", speed, glide, turn, fade,
                "Overstable", "https://example.test", "https://example.test/pic", "destroyer", "innova",
                "distance-driver", "overstable");
    }

    @Test
    void parsesDecimalFlightNumbers() {
        // DiscIt sends every number as a string, and half-steps are common.
        DiscItDisc parsed = disc("14.5", "5", "-0.5", "3");

        assertThat(parsed.usable()).isTrue();
        assertThat(parsed.speedValue()).isEqualTo(14.5);
        assertThat(parsed.turnValue()).isEqualTo(-0.5);
    }

    @Test
    void treatsUnparseableNumbersAsUnusable() {
        assertThat(disc("fast", "5", "-1", "3").usable()).isFalse();
        assertThat(disc("12", "5", "", "3").usable()).isFalse();
        assertThat(disc("12", "5", "-1", null).usable()).isFalse();
    }

    @Test
    void treatsMissingIdentityAsUnusable() {
        DiscItDisc noName = new DiscItDisc("id", "", "Innova", "Putter", "2", "3", "0", "1",
                null, null, null, null, null, null, null);
        assertThat(noName.usable()).isFalse();
    }

    @Test
    void readingABrokenNumberDirectlyFailsLoudly() {
        assertThatThrownBy(() -> disc("fast", "5", "-1", "3").speedValue())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unparseable speed");
    }
}
