package com.discgolfbagtips.api.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.discgolfbagtips.api.TestDiscs;
import com.discgolfbagtips.api.catalog.Disc;
import com.discgolfbagtips.api.catalog.PlasticFamily;
import com.discgolfbagtips.api.catalog.PlasticType;
import com.discgolfbagtips.api.catalog.WearState;
import com.discgolfbagtips.api.player.CourseType;
import com.discgolfbagtips.api.player.PlayerProfile;
import com.discgolfbagtips.api.player.SkillLevel;
import com.discgolfbagtips.api.player.ThrowingStyle;
import java.util.List;
import org.junit.jupiter.api.Test;

class RedundancyAnalyzerTest {

    private final RedundancyAnalyzer analyzer = new RedundancyAnalyzer();

    private final PlasticType star =
            TestDiscs.plastic("Innova", "Star", PlasticFamily.GRIPPY_PREMIUM, 0.1, 4, 4, "premium");

    private PlayerProfile profile(SkillLevel skill) {
        return new PlayerProfile(skill, ThrowingStyle.BACKHAND, CourseType.MIXED);
    }

    private Disc destroyer() {
        return TestDiscs.disc("destroyer", "Innova", "Destroyer", "Distance Driver", 12, 5, -1, 3);
    }

    private BagDisc copy(Disc disc, Integer grams, WearState wear) {
        return new BagDisc(disc, star, "Star", grams, wear);
    }

    @Test
    void oneDiscCannotBeRedundant() {
        assertThat(analyzer.analyze(List.of(copy(destroyer(), 175, WearState.NEW)),
                profile(SkillLevel.BEGINNER))).isEmpty();
        assertThat(analyzer.analyze(List.of(), profile(SkillLevel.BEGINNER))).isEmpty();
    }

    @Test
    void twoIdenticalDiscsAreFlaggedForABeginner() {
        List<BagRedundancy> found = analyzer.analyze(
                List.of(copy(destroyer(), 175, WearState.NEW), copy(destroyer(), 175, WearState.NEW)),
                profile(SkillLevel.BEGINNER));

        assertThat(found).hasSize(1);
        BagRedundancy redundancy = found.getFirst();
        assertThat(redundancy.separation()).isZero();
        assertThat(redundancy.level()).isEqualTo(BagRedundancy.Level.WARNING);
        assertThat(redundancy.reason()).contains("effectively interchangeable", "frees a slot");
        assertThat(redundancy.discNames()).containsExactly("Innova Destroyer", "Innova Destroyer");
    }

    /**
     * The case the feature exists for: a pro carrying several copies of one mold at different wear
     * levels is carrying distinct flights, not the same disc repeatedly.
     */
    @Test
    void theSameThreeDiscsAreOnlyAnFyiForAProWithWearData() {
        List<BagDisc> bag = List.of(
                copy(destroyer(), 175, WearState.NEW),
                copy(destroyer(), 175, WearState.SEASONED),
                copy(destroyer(), 175, WearState.BEAT_IN));

        BagRedundancy beginner = analyzer.analyze(bag, profile(SkillLevel.BEGINNER)).getFirst();
        List<BagRedundancy> pro = analyzer.analyze(bag, profile(SkillLevel.PROFESSIONAL));

        assertThat(beginner.level()).isIn(BagRedundancy.Level.WARNING, BagRedundancy.Level.NOTE);
        if (!pro.isEmpty()) {
            assertThat(pro.getFirst().level()).isEqualTo(BagRedundancy.Level.FYI);
            assertThat(pro.getFirst().severity()).isLessThan(beginner.severity());
            assertThat(pro.getFirst().reason()).contains("deliberate spread");
        }
    }

    @Test
    void severityFallsAsSkillRises() {
        List<BagDisc> bag = List.of(copy(destroyer(), 175, WearState.NEW), copy(destroyer(), 175, WearState.NEW));

        double beginner = analyzer.analyze(bag, profile(SkillLevel.BEGINNER)).getFirst().severity();
        double intermediate = analyzer.analyze(bag, profile(SkillLevel.INTERMEDIATE)).getFirst().severity();
        double advanced = analyzer.analyze(bag, profile(SkillLevel.ADVANCED)).getFirst().severity();

        assertThat(beginner).isGreaterThan(intermediate).isGreaterThan(advanced);
    }

    @Test
    void discsInTheSameCellThatActuallyFlyDifferentlyAreNotRedundant() {
        // Both land in the distance-driver slot, but three points of stability apart.
        BagDisc flippy = new BagDisc(
                TestDiscs.disc("a", "Innova", "Sidewinder", "Distance Driver", 9.5, 5, -3, 1),
                star, "Star", 175, WearState.NEW);
        BagDisc meathook = new BagDisc(
                TestDiscs.disc("b", "Innova", "Firebird", "Distance Driver", 9.5, 3, 0, 4),
                star, "Star", 175, WearState.NEW);

        assertThat(analyzer.analyze(List.of(flippy, meathook), profile(SkillLevel.BEGINNER))).isEmpty();
    }

    @Test
    void aThirdCopyIsWorseThanASecond() {
        List<BagDisc> two = List.of(copy(destroyer(), 175, WearState.NEW), copy(destroyer(), 175, WearState.NEW));
        List<BagDisc> three = List.of(copy(destroyer(), 175, WearState.NEW),
                copy(destroyer(), 175, WearState.NEW), copy(destroyer(), 175, WearState.NEW));

        assertThat(analyzer.analyze(three, profile(SkillLevel.BEGINNER)).getFirst().severity())
                .isGreaterThan(analyzer.analyze(two, profile(SkillLevel.BEGINNER)).getFirst().severity());
    }

    /** Weight alone separates two copies of a mold, which should soften the verdict. */
    @Test
    void weightDifferenceCountsAsSeparation() {
        List<BagDisc> identical = List.of(copy(destroyer(), 175, WearState.NEW),
                copy(destroyer(), 175, WearState.NEW));
        List<BagDisc> split = List.of(copy(destroyer(), 175, WearState.NEW),
                copy(destroyer(), 150, WearState.NEW));

        double same = analyzer.analyze(identical, profile(SkillLevel.INTERMEDIATE)).getFirst().separation();
        double different = analyzer.analyze(split, profile(SkillLevel.INTERMEDIATE)).getFirst().separation();

        assertThat(different).isGreaterThan(same);
    }

    @Test
    void differentMoldsFlyingTheSameLineAreStillRedundant() {
        BagDisc buzzz = new BagDisc(
                TestDiscs.disc("a", "Discraft", "Buzzz", "Midrange", 5, 4, -1, 1), star, "Star", 177,
                WearState.NEW);
        BagDisc clone = new BagDisc(
                TestDiscs.disc("b", "Innova", "Mako3", "Midrange", 5, 4, 0, 0), star, "Star", 177,
                WearState.NEW);

        List<BagRedundancy> found = analyzer.analyze(List.of(buzzz, clone), profile(SkillLevel.BEGINNER));

        assertThat(found).hasSize(1);
        assertThat(found.getFirst().discNames()).containsExactly("Discraft Buzzz", "Innova Mako3");
        assertThat(found.getFirst().reason()).contains("both cover the", "midrange");
    }
}
