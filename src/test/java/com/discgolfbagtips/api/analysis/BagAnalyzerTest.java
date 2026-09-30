package com.discgolfbagtips.api.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.discgolfbagtips.api.TestDiscs;
import com.discgolfbagtips.api.catalog.DiscSlot;
import com.discgolfbagtips.api.catalog.PlasticFamily;
import com.discgolfbagtips.api.catalog.PlasticType;
import com.discgolfbagtips.api.catalog.WearState;
import com.discgolfbagtips.api.catalog.StabilityClass;
import com.discgolfbagtips.api.player.CourseType;
import com.discgolfbagtips.api.player.PlayerProfile;
import com.discgolfbagtips.api.player.SkillLevel;
import com.discgolfbagtips.api.player.ThrowingStyle;
import com.discgolfbagtips.api.player.WeatherCondition;
import java.util.List;
import org.junit.jupiter.api.Test;

class BagAnalyzerTest {

    private final BagAnalyzer analyzer = new BagAnalyzer(new RedundancyAnalyzer());

    private BagDisc bagged(String name, double speed, double glide, double turn, double fade) {
        return BagDisc.of(TestDiscs.disc(name, "Innova", name, "Midrange", speed, glide, turn, fade), null, null);
    }

    @Test
    void reportsMissingSlotsForAThinBag() {
        // Two stable midranges only: no putter, no drivers, no stability spread.
        List<BagDisc> bag = List.of(bagged("Buzzz", 5, 4, -1, 1), bagged("Mako3", 5, 4, 0, 0));
        BagAnalysis analysis = analyzer.analyze(bag,
                new PlayerProfile(SkillLevel.INTERMEDIATE, ThrowingStyle.BACKHAND, CourseType.MIXED),
                WeatherCondition.NORMAL, List.of());

        assertThat(analysis.gaps()).isNotEmpty();
        assertThat(analysis.gaps())
                .extracting(BagGap::slot)
                .contains(DiscSlot.PUTT_AND_APPROACH);
        assertThat(analysis.notes())
                .anyMatch(note -> note.contains("No putter or approach disc"));
        assertThat(analysis.coverageScore()).isLessThan(50);
    }

    @Test
    void windPushesOverstableGapsToTheTop() {
        List<BagDisc> bag = List.of(
                bagged("Aviar", 2, 3, 0, 1),
                bagged("Buzzz", 5, 4, -1, 1),
                bagged("Leopard", 6, 5, -2, 1),
                bagged("Wraith", 11, 5, -1, 3));

        BagAnalysis calm = analyzer.analyze(bag,
                new PlayerProfile(SkillLevel.INTERMEDIATE, ThrowingStyle.BACKHAND, CourseType.MIXED),
                WeatherCondition.NORMAL, List.of());
        BagAnalysis windy = analyzer.analyze(bag,
                new PlayerProfile(SkillLevel.INTERMEDIATE, ThrowingStyle.BACKHAND, CourseType.MIXED),
                WeatherCondition.WINDY, List.of());

        double calmOverstable = severityFor(calm, StabilityClass.OVERSTABLE);
        double windyOverstable = severityFor(windy, StabilityClass.OVERSTABLE);
        assertThat(windyOverstable).isGreaterThan(calmOverstable);
    }

    @Test
    void forehandPlayersAreSteeredTowardOverstableGaps() {
        List<BagDisc> bag = List.of(
                bagged("Aviar", 2, 3, 0, 1),
                bagged("Buzzz", 5, 4, -1, 1),
                bagged("Leopard", 6, 5, -2, 1));

        BagAnalysis backhand = analyzer.analyze(bag,
                new PlayerProfile(SkillLevel.ADVANCED, ThrowingStyle.BACKHAND, CourseType.MIXED),
                WeatherCondition.NORMAL, List.of());
        BagAnalysis forehand = analyzer.analyze(bag,
                new PlayerProfile(SkillLevel.ADVANCED, ThrowingStyle.FOREHAND, CourseType.MIXED),
                WeatherCondition.NORMAL, List.of());

        assertThat(severityFor(forehand, StabilityClass.OVERSTABLE))
                .isGreaterThan(severityFor(backhand, StabilityClass.OVERSTABLE));
    }

    @Test
    void beginnersAreNotSteeredIntoHighSpeedDrivers() {
        List<BagDisc> bag = List.of(bagged("Aviar", 2, 3, 0, 1), bagged("Buzzz", 5, 4, -1, 1));
        BagAnalysis analysis = analyzer.analyze(bag,
                new PlayerProfile(SkillLevel.BEGINNER, ThrowingStyle.BACKHAND, CourseType.WOODED),
                WeatherCondition.NORMAL, List.of());

        BagGap top = analysis.primaryGap();
        assertThat(top).isNotNull();
        assertThat(top.slot()).isNotEqualTo(DiscSlot.DISTANCE_DRIVER);
        assertThat(top.target().speed()).isLessThanOrEqualTo(SkillLevel.BEGINNER.maxUsefulSpeed());
    }

    @Test
    void everyGapCarriesTheFlightDeltaAgainstTheClosestBaggedDisc() {
        List<BagDisc> bag = List.of(bagged("Buzzz", 5, 4, -1, 1));
        BagAnalysis analysis = analyzer.analyze(bag,
                new PlayerProfile(SkillLevel.INTERMEDIATE, ThrowingStyle.BOTH, CourseType.MIXED),
                WeatherCondition.NORMAL, List.of());

        assertThat(analysis.gaps()).isNotEmpty();
        assertThat(analysis.gaps()).allSatisfy(gap -> {
            assertThat(gap.nearestInBag()).isNotNull();
            assertThat(gap.delta()).isNotNull();
            assertThat(gap.reason()).contains("flight delta");
        });
    }

    @Test
    void anEmptyBagStillProducesAGap() {
        BagAnalysis analysis = analyzer.analyze(List.of(),
                new PlayerProfile(SkillLevel.BEGINNER, ThrowingStyle.BACKHAND, CourseType.WOODED),
                WeatherCondition.NORMAL, List.of());

        assertThat(analysis.primaryGap()).isNotNull();
        assertThat(analysis.primaryGap().nearestInBag()).isNull();
        assertThat(analysis.notes()).anyMatch(note -> note.contains("bag is empty"));
    }

    @Test
    void aCompleteBagFallsBackToARefinementGap() {
        List<BagDisc> bag = List.of(
                bagged("Aviar", 2, 3, 0, 1), bagged("Zone", 4, 3, 0, 3), bagged("Glow Aviar", 3, 3, -1, 0),
                bagged("Buzzz", 5, 4, -1, 1), bagged("Zombee", 5, 5, -2, 1), bagged("Buzzz OS", 5, 4, 0, 3),
                bagged("Teebird", 7, 5, 0, 2), bagged("Leopard", 6, 5, -2, 1), bagged("Firebird", 9, 3, 0, 4),
                bagged("Wraith", 11, 5, -1, 3), bagged("Sidewinder", 9, 5, -3, 1), bagged("Destroyer", 12, 5, -1, 3));

        BagAnalysis analysis = analyzer.analyze(bag,
                new PlayerProfile(SkillLevel.ADVANCED, ThrowingStyle.BOTH, CourseType.MIXED),
                WeatherCondition.NORMAL, List.of());

        assertThat(analysis.primaryGap()).isNotNull();
        assertThat(analysis.coverageScore()).isGreaterThan(60);
    }

    @Test
    void plasticShiftMovesADiscBetweenStabilityCells() {
        var champion = TestDiscs.plastic("Innova", "Champion", PlasticFamily.PREMIUM, 0.4, 5, 2, "premium");
        var dx = TestDiscs.plastic("Innova", "DX", PlasticFamily.BASE, -0.3, 1, 4, "base");

        // Teebird at 7/5/0/2 sits at index 2.0 — overstable. In DX it drops to 1.7, still overstable;
        // the point of the assertion is that the plastic moves the number the analyzer reasons about.
        var teebird = TestDiscs.disc("t", "Innova", "Teebird", "Control Driver", 7, 5, 0, 2);
        BagDisc inChampion = BagDisc.of(teebird, champion, "Champion");
        BagDisc inDx = BagDisc.of(teebird, dx, "DX");

        assertThat(inChampion.effectiveStabilityIndex()).isGreaterThan(inDx.effectiveStabilityIndex());
        assertThat(inChampion.plasticShift()).isEqualTo(0.4);
    }

    // --- weight and wear ----------------------------------------------------

    private final PlasticType dx = TestDiscs.plastic("Innova", "DX", PlasticFamily.BASE, -0.3, 1, 4, "base");

    private PlayerProfile intermediate() {
        return new PlayerProfile(SkillLevel.INTERMEDIATE, ThrowingStyle.BACKHAND, CourseType.MIXED);
    }

    /**
     * The reason wear is worth modelling: a bag whose only overstable driver has been beaten into a
     * straight disc has an overstable hole, and the flight numbers alone cannot see it.
     */
    @Test
    void aBeatInOverstableDriverStopsCoveringTheOverstableCell() {
        var firebird = TestDiscs.disc("fb", "Innova", "Firebird", "Control Driver", 9, 3, 0, 4);

        List<BagDisc> withNewFirebird = List.of(
                bagged("Aviar", 2, 3, 0, 1), bagged("Buzzz", 5, 4, -1, 1),
                new BagDisc(firebird, dx, "DX", 175, WearState.NEW));
        List<BagDisc> withWornFirebird = List.of(
                bagged("Aviar", 2, 3, 0, 1), bagged("Buzzz", 5, 4, -1, 1),
                new BagDisc(firebird, dx, "DX", 175, WearState.WELL_WORN));

        BagAnalysis fresh = analyzer.analyze(withNewFirebird, intermediate(), WeatherCondition.NORMAL, List.of());
        BagAnalysis worn = analyzer.analyze(withWornFirebird, intermediate(), WeatherCondition.NORMAL, List.of());

        assertThat(coverageFor(fresh, DiscSlot.FAIRWAY_DRIVER, StabilityClass.VERY_OVERSTABLE)).isEqualTo(1);
        assertThat(coverageFor(worn, DiscSlot.FAIRWAY_DRIVER, StabilityClass.VERY_OVERSTABLE)).isZero();
        assertThat(worn.notes())
                .anyMatch(note -> note.contains("Wear has moved discs out of their published stability"));
    }

    @Test
    void everyGapCarriesAWeightWindowToBuyInto() {
        BagAnalysis analysis = analyzer.analyze(List.of(bagged("Buzzz", 5, 4, -1, 1)),
                intermediate(), WeatherCondition.NORMAL, List.of());

        assertThat(analysis.gaps()).allSatisfy(gap -> {
            assertThat(gap.targetWeight()).isNotNull();
            assertThat(gap.targetWeight().minGrams()).isLessThanOrEqualTo(gap.targetWeight().maxGrams());
            assertThat(gap.targetWeight().rationale()).isNotBlank();
        });
    }

    @Test
    void aDevelopingArmThrowingAnAllMaxWeightBagIsToldSo() {
        List<BagDisc> maxWeight = List.of(
                new BagDisc(TestDiscs.disc("a", "Innova", "Aviar", "Putter", 2, 3, 0, 1), null, null, 175, null),
                new BagDisc(TestDiscs.disc("b", "Discraft", "Buzzz", "Midrange", 5, 4, -1, 1), null, null, 180, null),
                new BagDisc(TestDiscs.disc("c", "Innova", "Teebird", "Control Driver", 7, 5, 0, 2), null, null, 175,
                        null),
                new BagDisc(TestDiscs.disc("d", "Innova", "Wraith", "Distance Driver", 11, 5, -1, 3), null, null, 175,
                        null));

        BagAnalysis beginner = analyzer.analyze(maxWeight,
                new PlayerProfile(SkillLevel.BEGINNER, ThrowingStyle.BACKHAND, CourseType.OPEN),
                WeatherCondition.NORMAL, List.of());
        BagAnalysis professional = analyzer.analyze(maxWeight,
                new PlayerProfile(SkillLevel.PROFESSIONAL, ThrowingStyle.BACKHAND, CourseType.OPEN),
                WeatherCondition.NORMAL, List.of());

        assertThat(beginner.notes()).anyMatch(note -> note.contains("at or near max weight"));
        assertThat(professional.notes()).noneMatch(note -> note.contains("at or near max weight"));
    }

    @Test
    void aFastArmThrowingVeryLightDiscsIsToldSo() {
        List<BagDisc> light = List.of(
                new BagDisc(TestDiscs.disc("a", "Innova", "Destroyer", "Distance Driver", 12, 5, -1, 3),
                        null, null, 152, null));

        BagAnalysis analysis = analyzer.analyze(light,
                new PlayerProfile(SkillLevel.ADVANCED, ThrowingStyle.BACKHAND, CourseType.OPEN),
                WeatherCondition.NORMAL, List.of());

        assertThat(analysis.notes()).anyMatch(note -> note.contains("under 160 g"));
    }

    @Test
    void aBagWithNoWeightsGivenGetsNoWeightNotes() {
        BagAnalysis analysis = analyzer.analyze(
                List.of(bagged("Aviar", 2, 3, 0, 1), bagged("Buzzz", 5, 4, -1, 1), bagged("Teebird", 7, 5, 0, 2)),
                new PlayerProfile(SkillLevel.BEGINNER, ThrowingStyle.BACKHAND, CourseType.OPEN),
                WeatherCondition.NORMAL, List.of());

        assertThat(analysis.notes()).noneMatch(note -> note.contains("max weight") || note.contains("160 g"));
    }

    private int coverageFor(BagAnalysis analysis, DiscSlot slot, StabilityClass stability) {
        return analysis.coverage().getOrDefault(slot.name() + "/" + stability.name(), 0);
    }

    private double severityFor(BagAnalysis analysis, StabilityClass stability) {
        return analysis.gaps().stream()
                .filter(gap -> gap.stabilityClass() == stability)
                .mapToDouble(BagGap::severity)
                .max()
                .orElse(0.0);
    }
}
