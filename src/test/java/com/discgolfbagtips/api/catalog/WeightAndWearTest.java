package com.discgolfbagtips.api.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class WeightAndWearTest {

    // --- weight -------------------------------------------------------------

    @Test
    void anUnknownWeightChangesNothing() {
        assertThat(DiscWeight.stabilityShift(DiscSlot.DISTANCE_DRIVER, null)).isZero();
        assertThat(DiscWeight.classify(null)).isEqualTo("unspecified weight");
        assertThat(DiscWeight.describe(DiscSlot.MIDRANGE, null)).contains("used unadjusted");
    }

    @Test
    void lighterDiscsFlyMoreUnderstableAndHeavierOnesDoNot() {
        double light = DiscWeight.stabilityShift(DiscSlot.DISTANCE_DRIVER, 150);
        double standard = DiscWeight.stabilityShift(DiscSlot.DISTANCE_DRIVER, 170);
        double maxWeight = DiscWeight.stabilityShift(DiscSlot.DISTANCE_DRIVER, 175);

        assertThat(light).isNegative();
        assertThat(standard).isNegative();
        assertThat(light).isLessThan(standard);
        assertThat(maxWeight).isPositive();
    }

    @Test
    void theSameWeightMeansDifferentThingsInDifferentSlots() {
        // 172 g is under a midrange's reference weight but at a distance driver's.
        assertThat(DiscWeight.stabilityShift(DiscSlot.MIDRANGE, 172)).isNegative();
        assertThat(DiscWeight.stabilityShift(DiscSlot.DISTANCE_DRIVER, 172)).isNegative()
                .isGreaterThan(DiscWeight.stabilityShift(DiscSlot.MIDRANGE, 172));
    }

    @Test
    void weightAloneCannotOverwhelmAMold() {
        // A 100 g driver is absurd, but it must not flip a Firebird into a roller by arithmetic alone.
        assertThat(DiscWeight.stabilityShift(DiscSlot.DISTANCE_DRIVER, DiscWeight.MIN_GRAMS))
                .isGreaterThanOrEqualTo(-1.5);
        assertThat(DiscWeight.stabilityShift(DiscSlot.PUTT_AND_APPROACH, DiscWeight.MAX_GRAMS))
                .isLessThanOrEqualTo(1.5);
    }

    @ParameterizedTest
    @CsvSource({
            "145, very light",
            "152, 150-class",
            "165, light to mid weight",
            "172, standard weight",
            "175, max weight"
    })
    void classifiesWeightTheWayPlayersTalk(int grams, String expected) {
        assertThat(DiscWeight.classify(grams)).isEqualTo(expected);
    }

    @Test
    void beginnersAreSteeredLighterAndWindSteersThemBack() {
        var beginner = DiscWeight.recommend(DiscSlot.DISTANCE_DRIVER, 1, false);
        var advanced = DiscWeight.recommend(DiscSlot.DISTANCE_DRIVER, 3, false);
        var beginnerInWind = DiscWeight.recommend(DiscSlot.DISTANCE_DRIVER, 1, true);

        assertThat(beginner.maxGrams()).isLessThan(advanced.maxGrams());
        assertThat(beginner.rationale()).contains("carries further");
        assertThat(beginnerInWind.minGrams()).isGreaterThan(beginner.minGrams());
        assertThat(beginnerInWind.rationale()).contains("wind");
        // Never recommends above the weight the published numbers describe.
        assertThat(advanced.maxGrams())
                .isLessThanOrEqualTo(DiscWeight.referenceGrams(DiscSlot.DISTANCE_DRIVER));
    }

    // --- wear ---------------------------------------------------------------

    @Test
    void wearOnlyEverMovesADiscTowardUnderstable() {
        for (WearState state : WearState.values()) {
            assertThat(state.stabilityShift(3)).isLessThanOrEqualTo(0.0);
        }
        assertThat(WearState.NEW.stabilityShift(1)).isZero();
    }

    @Test
    void moreUseMeansMoreShift() {
        assertThat(WearState.WELL_WORN.stabilityShift(3))
                .isLessThan(WearState.BEAT_IN.stabilityShift(3))
                .isLessThan(WearState.SEASONED.stabilityShift(3));
    }

    @Test
    void durablePlasticResistsWearAndBaseGradeAmplifiesIt() {
        double inBaseGrade = WearState.BEAT_IN.stabilityShift(1);
        double inMidGrade = WearState.BEAT_IN.stabilityShift(3);
        double inPremium = WearState.BEAT_IN.stabilityShift(5);

        // This is the whole point: the same "beat in" means very different things per blend.
        assertThat(inBaseGrade).isLessThan(inMidGrade);
        assertThat(inMidGrade).isLessThan(inPremium);
        assertThat(inBaseGrade).isLessThan(inPremium * 2);
    }

    @Test
    void anUnknownPlasticIsTreatedAsMidGradeRatherThanGuessedAt() {
        assertThat(WearState.BEAT_IN.stabilityShift(null))
                .isEqualTo(WearState.BEAT_IN.stabilityShift(3));
    }
}
