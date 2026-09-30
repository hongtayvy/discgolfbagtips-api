package com.discgolfbagtips.api.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class FlightProfileTest {

    @Test
    void deltaIsTargetMinusActual() {
        FlightProfile target = new FlightProfile(5, 5, -2, 1);
        FlightProfile bagged = new FlightProfile(5, 4, -1, 1);

        FlightProfile delta = target.minus(bagged);

        assertThat(delta.speed()).isZero();
        assertThat(delta.glide()).isEqualTo(1.0);
        assertThat(delta.turn()).isEqualTo(-1.0);
        assertThat(delta.fade()).isZero();
    }

    @Test
    void distanceIsSymmetricAndZeroForIdenticalFlights() {
        FlightProfile left = new FlightProfile(7, 5, 0, 2);
        FlightProfile right = new FlightProfile(9, 3, 0, 4);

        assertThat(left.distanceTo(left)).isZero();
        assertThat(left.distanceTo(right)).isCloseTo(right.distanceTo(left), within(1e-9));
        assertThat(left.distanceTo(right)).isGreaterThan(0);
    }

    @Test
    void formatsWithoutTrailingZeroes() {
        assertThat(new FlightProfile(5, 4, -1, 1).format()).isEqualTo("5 / 4 / -1 / 1");
        assertThat(new FlightProfile(4.5, 4, -0.5, 1).format()).isEqualTo("4.5 / 4 / -0.5 / 1");
    }

    @Test
    void stabilityIndexIsTurnPlusFade() {
        assertThat(new FlightProfile(12, 5, -1, 3).stabilityIndex()).isEqualTo(2.0);
    }
}
