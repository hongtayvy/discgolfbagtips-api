package com.discgolfbagtips.api.eval;

import com.discgolfbagtips.api.analysis.BagGap;
import com.discgolfbagtips.api.analysis.FlightProfile;
import com.discgolfbagtips.api.catalog.Disc;
import com.discgolfbagtips.api.catalog.DiscSlot;
import com.discgolfbagtips.api.catalog.DiscWeight;
import com.discgolfbagtips.api.catalog.StabilityClass;
import com.discgolfbagtips.api.player.CourseType;
import com.discgolfbagtips.api.player.PlayerProfile;
import com.discgolfbagtips.api.player.SkillLevel;
import com.discgolfbagtips.api.player.ThrowingStyle;
import com.discgolfbagtips.api.player.WeatherCondition;
import java.util.List;
import java.util.function.Predicate;

/**
 * One retrieval question with a known-correct answer set.
 *
 * <p>Relevance is defined by the catalog's own {@code category} and {@code stability} labels, which
 * are assigned upstream by people rather than derived from the four numbers. That matters: those
 * labels disagree with computed {@code turn + fade} for 30% of the catalog, and with the
 * speed-derived slot for 16%.
 *
 * <p>An earlier version of this file defined relevance by flight-number thresholds, which made the
 * benchmark circular — the flight-number baseline was being scored by the very rule that generated
 * its ranking, and it returned a meaningless 1.000 on every scenario. Using the human labels gives
 * the embedding models access to real signal (the labels appear verbatim in the passage) that the
 * numeric baseline genuinely does not have.
 *
 * @param relevant  what counts as a correct answer
 * @param sanity    a few molds a knowledgeable player would expect to see, for eyeballing output
 */
public record EvalScenario(
        String id,
        String question,
        DiscSlot slot,
        StabilityClass stability,
        FlightProfile target,
        PlayerProfile profile,
        WeatherCondition weather,
        Predicate<Disc> relevant,
        List<String> sanity) {

    /** The gap the production query builder will be asked to describe. */
    public BagGap toGap() {
        return new BagGap(slot, stability, 1.0, BagGap.GapKind.MISSING,
                "Nothing in the bag covers the %s slot with %s stability, which is where %s"
                        .formatted(slot.label(), stability.label(), slot.purpose()),
                target, null, null,
                DiscWeight.recommend(slot, profile.skillLevel().rank(), weather == WeatherCondition.WINDY));
    }

    private static final List<String> APPROACH = List.of("Putter", "Approach Discs");
    private static final List<String> FAIRWAY = List.of("Control Driver", "Hybrid Driver");
    private static final List<String> OVERSTABLE = List.of("Overstable", "Very Overstable");
    private static final List<String> UNDERSTABLE = List.of("Understable", "Very Understable");

    private static boolean labelled(Disc d, List<String> categories, List<String> stabilities) {
        return categories.contains(d.category())
                && (stabilities.isEmpty() || stabilities.contains(d.stabilityLabel()));
    }

    private static PlayerProfile profile(SkillLevel skill, ThrowingStyle style, CourseType course) {
        return new PlayerProfile(skill, style, course);
    }

    /**
     * Eight gaps spanning the slot and stability matrix. Each relevant set is between roughly 4% and
     * 23% of the catalog, so there is real headroom above random guessing.
     */
    public static List<EvalScenario> all() {
        return List.of(
                new EvalScenario("overstable-approach",
                        "an overstable approach disc for forehand shots in wind",
                        DiscSlot.PUTT_AND_APPROACH, StabilityClass.OVERSTABLE,
                        new FlightProfile(3, 4, 0, 3),
                        profile(SkillLevel.INTERMEDIATE, ThrowingStyle.FOREHAND, CourseType.MIXED),
                        WeatherCondition.WINDY,
                        d -> labelled(d, APPROACH, OVERSTABLE),
                        List.of("Zone", "Harp", "Justice", "Pig")),

                new EvalScenario("putting-putter",
                        "a straight putter for putting and short upshots",
                        DiscSlot.PUTT_AND_APPROACH, StabilityClass.STABLE,
                        new FlightProfile(3, 4, -1, 1),
                        profile(SkillLevel.INTERMEDIATE, ThrowingStyle.BACKHAND, CourseType.MIXED),
                        WeatherCondition.NORMAL,
                        d -> labelled(d, List.of("Putter"), List.of("Stable")),
                        List.of("Aviar", "Judge", "Luna", "Wizard")),

                new EvalScenario("understable-midrange",
                        "an understable midrange to hyzer-flip on tight wooded lines",
                        DiscSlot.MIDRANGE, StabilityClass.UNDERSTABLE,
                        new FlightProfile(5, 5, -2, 1),
                        profile(SkillLevel.INTERMEDIATE, ThrowingStyle.BACKHAND, CourseType.WOODED),
                        WeatherCondition.NORMAL,
                        d -> labelled(d, List.of("Midrange"), UNDERSTABLE),
                        List.of("Comet", "Stratus", "Buzzz SS")),

                new EvalScenario("straight-midrange",
                        "a straight, stable midrange for tunnel shots",
                        DiscSlot.MIDRANGE, StabilityClass.STABLE,
                        new FlightProfile(5, 4.5, -1, 2),
                        profile(SkillLevel.INTERMEDIATE, ThrowingStyle.BOTH, CourseType.WOODED),
                        WeatherCondition.NORMAL,
                        d -> labelled(d, List.of("Midrange"), List.of("Stable")),
                        List.of("Buzzz", "Mako3", "Roc", "MD3")),

                new EvalScenario("overstable-fairway",
                        "an overstable fairway driver that holds a forehand flex line into a headwind",
                        DiscSlot.FAIRWAY_DRIVER, StabilityClass.OVERSTABLE,
                        new FlightProfile(8, 4, 0, 3),
                        profile(SkillLevel.ADVANCED, ThrowingStyle.FOREHAND, CourseType.OPEN),
                        WeatherCondition.WINDY,
                        d -> labelled(d, FAIRWAY, OVERSTABLE),
                        List.of("Firebird", "Felon", "Predator")),

                new EvalScenario("understable-distance",
                        "an understable distance driver a slower arm can turn over for extra carry",
                        DiscSlot.DISTANCE_DRIVER, StabilityClass.UNDERSTABLE,
                        new FlightProfile(10, 5, -2, 1),
                        profile(SkillLevel.BEGINNER, ThrowingStyle.BACKHAND, CourseType.OPEN),
                        WeatherCondition.NORMAL,
                        d -> labelled(d, List.of("Distance Driver"), UNDERSTABLE),
                        List.of("Sidewinder", "Mamba", "Roadrunner")),

                new EvalScenario("very-overstable-utility",
                        "a very overstable utility disc for spike hyzers and getting out of trouble",
                        DiscSlot.FAIRWAY_DRIVER, StabilityClass.VERY_OVERSTABLE,
                        new FlightProfile(8, 3, 0, 4),
                        profile(SkillLevel.ADVANCED, ThrowingStyle.FOREHAND, CourseType.WOODED),
                        WeatherCondition.WINDY,
                        d -> "Very Overstable".equals(d.stabilityLabel()),
                        List.of("Firebird", "Zone", "Harp")),

                new EvalScenario("stable-distance",
                        "a straight, stable distance driver for maximum distance in the open",
                        DiscSlot.DISTANCE_DRIVER, StabilityClass.STABLE,
                        new FlightProfile(11, 5.5, -1, 2),
                        profile(SkillLevel.ADVANCED, ThrowingStyle.BACKHAND, CourseType.OPEN),
                        WeatherCondition.NORMAL,
                        d -> labelled(d, List.of("Distance Driver"), List.of("Stable")),
                        List.of("Wraith", "Destroyer", "Sheriff")));
    }
}
