package com.discgolfbagtips.api.analysis;

import com.discgolfbagtips.api.catalog.DiscSlot;
import com.discgolfbagtips.api.catalog.DiscWeight;
import com.discgolfbagtips.api.catalog.StabilityClass;
import com.discgolfbagtips.api.catalog.WearState;
import com.discgolfbagtips.api.player.CourseType;
import com.discgolfbagtips.api.player.PlayerProfile;
import com.discgolfbagtips.api.player.SkillLevel;
import com.discgolfbagtips.api.player.ThrowingStyle;
import com.discgolfbagtips.api.player.WeatherCondition;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Scores the bag against the (slot x stability) cells that matter for this player, in these
 * conditions. The output is deterministic and fully inspectable — it is the half of the
 * explainability payload that does not depend on a language model.
 */
@Component
public class BagAnalyzer {

    private static final int MAX_GAPS = 4;

    private final RedundancyAnalyzer redundancyAnalyzer;

    public BagAnalyzer(RedundancyAnalyzer redundancyAnalyzer) {
        this.redundancyAnalyzer = redundancyAnalyzer;
    }

    /** Cells worth owning, and their baseline importance in a general-purpose bag. */
    private static final Map<DiscSlot, Map<StabilityClass, Double>> BASE_WEIGHTS = baseWeights();

    /** A representative flight line for each stability class, used as the retrieval target. */
    private static final Map<StabilityClass, double[]> TARGET_TURN_FADE = Map.of(
            StabilityClass.VERY_UNDERSTABLE, new double[] {-3.0, 1.0},
            StabilityClass.UNDERSTABLE, new double[] {-2.0, 1.0},
            StabilityClass.STABLE, new double[] {-1.0, 2.0},
            StabilityClass.OVERSTABLE, new double[] {0.0, 3.0},
            StabilityClass.VERY_OVERSTABLE, new double[] {0.0, 4.0});

    public BagAnalysis analyze(List<BagDisc> bag, PlayerProfile profile, WeatherCondition weather,
            List<String> unresolved) {

        Map<String, Integer> coverage = new LinkedHashMap<>();
        Map<DiscSlot, Integer> perSlot = new EnumMap<>(DiscSlot.class);
        for (BagDisc bagDisc : bag) {
            coverage.merge(cellKey(bagDisc.slot(), bagDisc.effectiveStability()), 1, Integer::sum);
            perSlot.merge(bagDisc.slot(), 1, Integer::sum);
        }

        List<BagGap> gaps = new ArrayList<>();
        for (Map.Entry<DiscSlot, Map<StabilityClass, Double>> slotEntry : BASE_WEIGHTS.entrySet()) {
            DiscSlot slot = slotEntry.getKey();
            for (Map.Entry<StabilityClass, Double> cell : slotEntry.getValue().entrySet()) {
                StabilityClass stability = cell.getKey();
                int covered = coverage.getOrDefault(cellKey(slot, stability), 0)
                        + adjacentCoverage(coverage, slot, stability);
                if (covered > 0 && perSlot.getOrDefault(slot, 0) > 1) {
                    continue;
                }
                BagGap.GapKind kind = covered > 0 ? BagGap.GapKind.THIN : BagGap.GapKind.MISSING;
                double severity = severity(cell.getValue(), kind, slot, stability, profile, weather, bag.size());
                if (severity <= 0.05) {
                    continue;
                }
                gaps.add(buildGap(slot, stability, severity, kind, profile, weather, bag));
            }
        }

        if (gaps.isEmpty()) {
            gaps.add(refinementGap(coverage, perSlot, profile, weather, bag));
        }
        gaps.sort(Comparator.comparingDouble(BagGap::severity).reversed());
        List<BagGap> topGaps = gaps.size() > MAX_GAPS ? List.copyOf(gaps.subList(0, MAX_GAPS)) : List.copyOf(gaps);

        List<BagRedundancy> redundancies = redundancyAnalyzer.analyze(bag, profile);
        List<String> notes = notes(bag, profile, weather, perSlot);
        if (!redundancies.isEmpty()) {
            long loud = redundancies.stream()
                    .filter(r -> r.level() != BagRedundancy.Level.FYI).count();
            if (loud > 0) {
                notes.add("%d place%s in this bag where discs overlap enough to free a slot."
                        .formatted(loud, loud == 1 ? "" : "s"));
            }
        }

        return new BagAnalysis(List.copyOf(bag), topGaps, List.copyOf(gaps), List.copyOf(redundancies),
                coverage, coverageScore(coverage, bag.size()), notes, List.copyOf(unresolved));
    }

    /**
     * A bag with every cell covered still deserves an answer. Fall back to the thinnest slot and the
     * stability class within it that this player leans on most, flagged as a refinement rather than a
     * hole.
     */
    private BagGap refinementGap(Map<String, Integer> coverage, Map<DiscSlot, Integer> perSlot,
            PlayerProfile profile, WeatherCondition weather, List<BagDisc> bag) {

        DiscSlot thinnestSlot = BASE_WEIGHTS.keySet().stream()
                .min(Comparator.comparingInt(slot -> perSlot.getOrDefault(slot, 0)))
                .orElse(DiscSlot.MIDRANGE);
        StabilityClass thinnestCell = BASE_WEIGHTS.get(thinnestSlot).keySet().stream()
                .min(Comparator.comparingInt((StabilityClass stability) ->
                                coverage.getOrDefault(cellKey(thinnestSlot, stability), 0))
                        .thenComparing(Comparator.comparingDouble(
                                (StabilityClass stability) -> BASE_WEIGHTS.get(thinnestSlot).get(stability))
                                .reversed()))
                .orElse(StabilityClass.STABLE);

        FlightProfile target = targetFlight(thinnestSlot, thinnestCell, profile, weather);
        BagDisc nearest = bag.stream()
                .min(Comparator.comparingDouble(candidate -> candidate.flight().distanceTo(target)))
                .orElse(null);
        String reason = "This bag has no outright hole; the %s slot is the thinnest, and a %s option there "
                .formatted(thinnestSlot.label(), thinnestCell.label())
                + "is the most useful addition for how this player throws";
        return new BagGap(thinnestSlot, thinnestCell, 0.25, BagGap.GapKind.THIN, reason, target, nearest,
                nearest == null ? null : target.minus(nearest.flight()),
                DiscWeight.recommend(thinnestSlot, profile.skillLevel().rank(),
                        weather == WeatherCondition.WINDY));
    }

    /**
     * A stable disc half-covers the neutral end of the understable cell and vice versa; without this
     * every bag would report a wall of gaps.
     */
    private int adjacentCoverage(Map<String, Integer> coverage, DiscSlot slot, StabilityClass stability) {
        StabilityClass neighbour = switch (stability) {
            case VERY_UNDERSTABLE -> StabilityClass.UNDERSTABLE;
            case VERY_OVERSTABLE -> StabilityClass.OVERSTABLE;
            default -> null;
        };
        return neighbour == null ? 0 : coverage.getOrDefault(cellKey(slot, neighbour), 0);
    }

    private double severity(double baseWeight, BagGap.GapKind kind, DiscSlot slot, StabilityClass stability,
            PlayerProfile profile, WeatherCondition weather, int bagSize) {

        double severity = baseWeight * (kind == BagGap.GapKind.MISSING ? 1.0 : 0.45);

        // A disc the player cannot make fly as rated is not a gap worth filling.
        if (slot.minSpeed() > profile.skillLevel().maxUsefulSpeed()) {
            severity *= 0.15;
        }
        if (profile.skillLevel() == SkillLevel.BEGINNER && slot == DiscSlot.DISTANCE_DRIVER) {
            severity *= 0.4;
        }

        severity *= styleMultiplier(profile.throwingStyle(), stability);
        severity *= courseMultiplier(profile.courseType(), slot);
        severity *= weatherMultiplier(weather, stability);

        // A four-disc bag needs its core covered long before it needs a utility slot.
        if (bagSize <= 5) {
            boolean core = (slot == DiscSlot.PUTT_AND_APPROACH || slot == DiscSlot.MIDRANGE)
                    && (stability == StabilityClass.STABLE || stability == StabilityClass.OVERSTABLE);
            severity *= core ? 1.6 : 0.8;
        }
        return Math.round(severity * 1000.0) / 1000.0;
    }

    private double styleMultiplier(ThrowingStyle style, StabilityClass stability) {
        return switch (style) {
            case FOREHAND -> switch (stability) {
                case OVERSTABLE, VERY_OVERSTABLE -> 1.35;
                case VERY_UNDERSTABLE -> 0.6;
                default -> 1.0;
            };
            case BACKHAND -> switch (stability) {
                case UNDERSTABLE, VERY_UNDERSTABLE -> 1.15;
                default -> 1.0;
            };
            case BOTH -> 1.0;
        };
    }

    private double courseMultiplier(CourseType courseType, DiscSlot slot) {
        return switch (courseType) {
            case WOODED -> switch (slot) {
                case PUTT_AND_APPROACH, MIDRANGE -> 1.3;
                case FAIRWAY_DRIVER -> 1.15;
                case DISTANCE_DRIVER -> 0.6;
            };
            case OPEN -> switch (slot) {
                case DISTANCE_DRIVER -> 1.3;
                case FAIRWAY_DRIVER -> 1.1;
                case MIDRANGE -> 0.9;
                case PUTT_AND_APPROACH -> 1.0;
            };
            case MIXED -> 1.0;
        };
    }

    private double weatherMultiplier(WeatherCondition weather, StabilityClass stability) {
        return switch (weather) {
            case WINDY -> switch (stability) {
                case OVERSTABLE, VERY_OVERSTABLE -> 1.4;
                case UNDERSTABLE -> 0.6;
                case VERY_UNDERSTABLE -> 0.35;
                case STABLE -> 0.9;
            };
            // Cold, dense air makes everything play more overstable, so the understable hole bites first.
            case COLD -> switch (stability) {
                case UNDERSTABLE, VERY_UNDERSTABLE -> 1.25;
                case VERY_OVERSTABLE -> 0.8;
                default -> 1.0;
            };
            case HOT -> switch (stability) {
                case OVERSTABLE, VERY_OVERSTABLE -> 1.1;
                default -> 1.0;
            };
            case RAINY, NORMAL -> 1.0;
        };
    }

    private BagGap buildGap(DiscSlot slot, StabilityClass stability, double severity, BagGap.GapKind kind,
            PlayerProfile profile, WeatherCondition weather, List<BagDisc> bag) {

        FlightProfile target = targetFlight(slot, stability, profile, weather);
        BagDisc nearest = bag.stream()
                .min(Comparator.comparingDouble(candidate -> candidate.flight().distanceTo(target)))
                .orElse(null);
        FlightProfile delta = nearest == null ? null : target.minus(nearest.flight());

        String reason = kind == BagGap.GapKind.MISSING
                ? "Nothing in the bag covers the %s slot with %s stability, which is where %s"
                        .formatted(slot.label(), stability.label(), slot.purpose())
                : "The %s slot is carried by a single disc, so there is no %s option for %s"
                        .formatted(slot.label(), stability.label(), slot.purpose());
        if (nearest != null && delta != null) {
            reason += ". Closest disc already bagged is %s at %s, a flight delta of %s"
                    .formatted(nearest.label(), nearest.flight().format(), delta.format());
        }

        return new BagGap(slot, stability, severity, kind, reason, target, nearest, delta,
                DiscWeight.recommend(slot, profile.skillLevel().rank(), weather == WeatherCondition.WINDY));
    }

    private FlightProfile targetFlight(DiscSlot slot, StabilityClass stability, PlayerProfile profile,
            WeatherCondition weather) {

        double speed = Math.min((slot.minSpeed() + slot.maxSpeed()) / 2.0, profile.skillLevel().maxUsefulSpeed());
        if (slot == DiscSlot.PUTT_AND_APPROACH) {
            speed = 3.0;
        }
        double[] turnFade = TARGET_TURN_FADE.get(stability);
        double glide = switch (stability) {
            case VERY_UNDERSTABLE, UNDERSTABLE -> 5.0;
            case STABLE -> 4.5;
            case OVERSTABLE -> 4.0;
            case VERY_OVERSTABLE -> 3.0;
        };
        if (profile.skillLevel() == SkillLevel.BEGINNER) {
            glide = Math.min(6.0, glide + 0.5);
        }
        if (weather == WeatherCondition.WINDY) {
            glide = Math.max(2.0, glide - 0.5);
        }
        return new FlightProfile(round(speed), round(glide), turnFade[0], turnFade[1]);
    }

    /** 0-100, a blunt "how complete is this bag" number for the UI to render as a bar. */
    private int coverageScore(Map<String, Integer> coverage, int bagSize) {
        int cellsWanted = 0;
        int cellsCovered = 0;
        for (Map.Entry<DiscSlot, Map<StabilityClass, Double>> slotEntry : BASE_WEIGHTS.entrySet()) {
            for (StabilityClass stability : slotEntry.getValue().keySet()) {
                cellsWanted++;
                if (coverage.getOrDefault(cellKey(slotEntry.getKey(), stability), 0) > 0) {
                    cellsCovered++;
                }
            }
        }
        if (cellsWanted == 0) {
            return 0;
        }
        double cellRatio = (double) cellsCovered / cellsWanted;
        double sizeRatio = Math.min(1.0, bagSize / 12.0);
        return (int) Math.round(100 * (0.75 * cellRatio + 0.25 * sizeRatio));
    }

    private List<String> notes(List<BagDisc> bag, PlayerProfile profile, WeatherCondition weather,
            Map<DiscSlot, Integer> perSlot) {

        List<String> notes = new ArrayList<>();
        if (bag.isEmpty()) {
            notes.add("The bag is empty, so the recommendation targets a first disc rather than a gap.");
            return notes;
        }
        if (perSlot.getOrDefault(DiscSlot.PUTT_AND_APPROACH, 0) == 0) {
            notes.add("No putter or approach disc was listed; putters are used on nearly every hole.");
        }
        long fastDiscs = bag.stream().filter(disc -> disc.disc().speed() > profile.skillLevel().maxUsefulSpeed()).count();
        if (fastDiscs > 0) {
            notes.add("%d disc(s) in the bag are faster than this skill level usually flies as rated."
                    .formatted(fastDiscs));
        }
        if (weather.gripCritical()) {
            long slick = bag.stream().filter(disc -> disc.plastic() != null && disc.plastic().grip() <= 2).count();
            if (slick > 0) {
                notes.add("%d disc(s) are in slick plastic, which matters in %s."
                        .formatted(slick, weather.description()));
            }
        }
        double averageShift = bag.stream().mapToDouble(BagDisc::plasticShift).average().orElse(0.0);
        if (Math.abs(averageShift) >= 0.3) {
            notes.add("Plastic choices shift this bag %s overall (average stability shift %.1f)."
                    .formatted(averageShift > 0 ? "more overstable" : "more understable", averageShift));
        }
        notes.addAll(weightNotes(bag, profile));
        notes.addAll(wearNotes(bag));
        return notes;
    }

    /** Weight problems the flight numbers cannot show on their own. */
    private List<String> weightNotes(List<BagDisc> bag, PlayerProfile profile) {
        List<BagDisc> weighed = bag.stream().filter(disc -> disc.weightGrams() != null).toList();
        if (weighed.isEmpty()) {
            return List.of();
        }
        List<String> notes = new ArrayList<>();

        // A developing arm throwing an all-max-weight bag is leaving distance on the tee.
        if (profile.skillLevel().rank() <= 2) {
            long maxWeight = weighed.stream()
                    .filter(disc -> disc.weightGrams() >= DiscWeight.referenceGrams(disc.slot()) - 2)
                    .count();
            if (maxWeight >= Math.max(3, weighed.size() * 3 / 4)) {
                notes.add(("%d of %d weighed discs are at or near max weight; at this skill level a lighter "
                        + "run in the driver slots would add carry without costing control.")
                        .formatted(maxWeight, weighed.size()));
            }
        }

        long veryLight = weighed.stream().filter(disc -> disc.weightGrams() < 160).count();
        if (veryLight > 0 && profile.skillLevel().rank() >= 3) {
            notes.add(("%d disc(s) are under 160 g, which an arm this fast will turn over and which the wind "
                    + "will punish.").formatted(veryLight));
        }
        return notes;
    }

    /** Wear problems, including discs that have quietly left their published stability class. */
    private List<String> wearNotes(List<BagDisc> bag) {
        List<String> notes = new ArrayList<>();

        List<String> moved = bag.stream()
                .filter(disc -> disc.wear() != WearState.NEW && disc.stabilityBreakdown().movedClass())
                .map(disc -> "%s now plays %s rather than %s (%s)".formatted(
                        disc.label(),
                        disc.stabilityBreakdown().effectiveClass().label(),
                        disc.stabilityBreakdown().publishedClass().label(),
                        disc.stabilityBreakdown().explain()))
                .toList();
        if (!moved.isEmpty()) {
            notes.add("Wear has moved discs out of their published stability: " + String.join("; ", moved) + ".");
        }

        long wornBaseline = bag.stream()
                .filter(disc -> disc.wear() == WearState.WELL_WORN || disc.wear() == WearState.BEAT_IN)
                .count();
        if (wornBaseline >= 3) {
            notes.add(("%d disc(s) are beat in or well worn, so this bag flies more understable overall than "
                    + "its flight numbers suggest.").formatted(wornBaseline));
        }
        return notes;
    }

    private static String cellKey(DiscSlot slot, StabilityClass stability) {
        return slot.name() + "/" + stability.name();
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static Map<DiscSlot, Map<StabilityClass, Double>> baseWeights() {
        Map<DiscSlot, Map<StabilityClass, Double>> weights = new EnumMap<>(DiscSlot.class);
        weights.put(DiscSlot.PUTT_AND_APPROACH, orderedCells(Map.of(
                StabilityClass.STABLE, 1.0,
                StabilityClass.OVERSTABLE, 0.85,
                StabilityClass.UNDERSTABLE, 0.35)));
        weights.put(DiscSlot.MIDRANGE, orderedCells(Map.of(
                StabilityClass.STABLE, 1.0,
                StabilityClass.OVERSTABLE, 0.8,
                StabilityClass.UNDERSTABLE, 0.7)));
        weights.put(DiscSlot.FAIRWAY_DRIVER, orderedCells(Map.of(
                StabilityClass.STABLE, 0.9,
                StabilityClass.OVERSTABLE, 0.85,
                StabilityClass.UNDERSTABLE, 0.7)));
        weights.put(DiscSlot.DISTANCE_DRIVER, orderedCells(Map.of(
                StabilityClass.STABLE, 0.7,
                StabilityClass.OVERSTABLE, 0.75,
                StabilityClass.UNDERSTABLE, 0.55)));
        return Map.copyOf(weights);
    }

    private static Map<StabilityClass, Double> orderedCells(Map<StabilityClass, Double> cells) {
        Map<StabilityClass, Double> ordered = new EnumMap<>(StabilityClass.class);
        ordered.putAll(cells);
        return ordered;
    }
}
