package com.discgolfbagtips.api.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.discgolfbagtips.api.TestDiscs;
import com.discgolfbagtips.api.catalog.PlasticFamily;
import com.discgolfbagtips.api.catalog.PlasticType;
import com.discgolfbagtips.api.catalog.StabilityClass;
import com.discgolfbagtips.api.catalog.WearState;
import org.junit.jupiter.api.Test;

class StabilityBreakdownTest {

    private final PlasticType dx = TestDiscs.plastic("Innova", "DX", PlasticFamily.BASE, -0.3, 1, 4, "base");
    private final PlasticType champion =
            TestDiscs.plastic("Innova", "Champion", PlasticFamily.PREMIUM, 0.4, 5, 2, "premium");

    @Test
    void sumsTheFourTerms() {
        StabilityBreakdown breakdown = new StabilityBreakdown(2.0, -0.3, -0.4, -1.4);

        assertThat(breakdown.effective()).isEqualTo(-0.1);
        assertThat(breakdown.totalShift()).isEqualTo(-2.1);
        assertThat(breakdown.publishedClass()).isEqualTo(StabilityClass.OVERSTABLE);
        assertThat(breakdown.effectiveClass()).isEqualTo(StabilityClass.STABLE);
        assertThat(breakdown.movedClass()).isTrue();
    }

    @Test
    void showsItsArithmetic() {
        String explanation = new StabilityBreakdown(2.0, -0.3, -0.4, -1.4).explain();

        assertThat(explanation).isEqualTo("2 published -0.3 plastic -0.4 weight -1.4 wear = -0.1");
    }

    @Test
    void omitsTermsThatContributeNothing() {
        String explanation = new StabilityBreakdown(1.0, 0, 0, 0).explain();

        assertThat(explanation).isEqualTo("1 published = 1");
        assertThat(explanation).doesNotContain("plastic", "weight", "wear");
    }

    /**
     * The headline case for modelling wear at all: the same mold, same wear, different plastic, and
     * only one of them is still the disc the flight numbers describe.
     */
    @Test
    void aBeatInTeebirdIsADifferentDiscInDxThanInChampion() {
        var teebird = TestDiscs.disc("t", "Innova", "Teebird", "Control Driver", 7, 5, 0, 2);

        BagDisc beatInDx = new BagDisc(teebird, dx, "DX", 168, WearState.WELL_WORN);
        BagDisc beatInChampion = new BagDisc(teebird, champion, "Champion", 175, WearState.WELL_WORN);

        // The DX one has become a different disc; the Champion one is merely a softer version of itself.
        assertThat(beatInDx.effectiveStabilityIndex()).isLessThan(beatInChampion.effectiveStabilityIndex());
        assertThat(beatInDx.effectiveStability()).isEqualTo(StabilityClass.UNDERSTABLE);
        assertThat(beatInDx.stabilityBreakdown().movedClass()).isTrue();

        assertThat(beatInChampion.effectiveStability()).isEqualTo(StabilityClass.OVERSTABLE);
        assertThat(beatInChampion.stabilityBreakdown().movedClass()).isFalse();
        assertThat(beatInChampion.effectiveStabilityIndex())
                .isLessThan(firebirdless(beatInChampion));
    }

    /** The published index, for comparing "what it was" against "what it is". */
    private double firebirdless(BagDisc bagDisc) {
        return bagDisc.stabilityBreakdown().published();
    }

    @Test
    void aNewMaxWeightPremiumDiscFliesItsPublishedNumbers() {
        var firebird = TestDiscs.disc("f", "Innova", "Firebird", "Control Driver", 9, 3, 0, 4);
        BagDisc asBought = new BagDisc(firebird, champion, "Champion", 175, WearState.NEW);

        assertThat(asBought.wearShift()).isZero();
        assertThat(asBought.stabilityBreakdown().movedClass()).isFalse();
        assertThat(asBought.effectiveStability()).isEqualTo(StabilityClass.VERY_OVERSTABLE);
    }

    @Test
    void theLabelNamesEveryAxisThePlayerGave() {
        var buzzz = TestDiscs.disc("b", "Discraft", "Buzzz", "Midrange", 5, 4, -1, 1);

        assertThat(new BagDisc(buzzz, dx, "DX", 177, WearState.BEAT_IN).label())
                .isEqualTo("Discraft Buzzz (DX, 177 g, beat in)");
        // Anything the player left out stays out of the label rather than being guessed at.
        assertThat(BagDisc.of(buzzz, null, null).label())
                .isEqualTo("Discraft Buzzz (unspecified plastic)");
    }
}
