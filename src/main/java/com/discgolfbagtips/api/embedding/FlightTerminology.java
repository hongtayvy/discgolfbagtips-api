package com.discgolfbagtips.api.embedding;

import com.discgolfbagtips.api.catalog.DiscSlot;
import com.discgolfbagtips.api.catalog.StabilityClass;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Turns raw flight numbers into the language players actually use.
 *
 * <p>This is the heart of the retrieval quality argument. Embedding "12 5 -1 3" gives a model four
 * tokens with no meaning; embedding "an overstable distance driver that needs a fast arm, holds a
 * forehand flex line and fights a headwind" gives it a paragraph that sits near the way a player
 * describes the hole they keep bogeying. Comparable apps index the numbers alone.
 */
public final class FlightTerminology {

    private FlightTerminology() {
    }

    public static String speedPhrase(double speed) {
        if (speed <= 3) {
            return "very low speed, thrown at putting and short-approach pace";
        }
        if (speed <= 4.4) {
            return "low speed, comfortable at approach and touch-shot pace";
        }
        if (speed <= 6.4) {
            return "moderate speed, reachable by almost any arm";
        }
        if (speed <= 9.4) {
            return "control-driver speed, reachable with a clean reachback and modest power";
        }
        if (speed <= 11.4) {
            return "high speed, needs a developed arm to reach its rated flight";
        }
        return "very high speed, only flies as rated for a fast, well-timed arm";
    }

    public static String glidePhrase(double glide) {
        if (glide <= 2) {
            return "low glide, drops out of the air quickly and lands where it is aimed";
        }
        if (glide <= 3.5) {
            return "modest glide, predictable descent, easy to control distance with";
        }
        if (glide <= 4.5) {
            return "good glide, carries well without floating away";
        }
        return "high glide, floats and stretches distance, forgiving for slower arms but harder to control in wind";
    }

    public static String turnPhrase(double turn) {
        if (turn <= -3) {
            return "extreme turn, flips over easily and will roll if over-powered";
        }
        if (turn <= -2) {
            return "strong turn, wants to bank right for a right-hand backhand throw";
        }
        if (turn <= -1) {
            return "noticeable turn, flips up out of hyzer and holds a gentle right-hand-backhand turnover";
        }
        if (turn < 0) {
            return "slight turn, straightens out of a hyzer release without flipping over";
        }
        return "no turn, resists flipping over even at full power";
    }

    public static String fadePhrase(double fade) {
        if (fade <= 1) {
            return "minimal fade, finishes nearly straight and holds its line to the ground";
        }
        if (fade <= 2) {
            return "moderate fade, a gentle predictable finish left for a right-hand backhand";
        }
        if (fade <= 3) {
            return "strong fade, a dependable hook finish and a hard skip on hard ground";
        }
        return "very strong fade, dumps hard left for a right-hand backhand and spike-hyzers on command";
    }

    /** Shot shapes the disc is actually reached for, driven by the stability class and slot. */
    public static List<String> shotShapes(DiscSlot slot, StabilityClass stability) {
        Set<String> shapes = new LinkedHashSet<>();
        switch (stability) {
            case VERY_UNDERSTABLE -> shapes.addAll(List.of("rollers", "big turnovers", "anhyzer flex lines",
                    "hyzer-flip to flat for maximum distance"));
            case UNDERSTABLE -> shapes.addAll(List.of("hyzer flips", "turnover shots", "gentle right-to-left-to-right"
                    + " lines for a right-hand backhand", "tailwind shots"));
            case STABLE -> shapes.addAll(List.of("straight tunnel shots", "point-and-shoot lines",
                    "long straight drives", "flat releases that hold their line"));
            case OVERSTABLE -> shapes.addAll(List.of("hyzer lines", "forehand flex shots", "headwind drives",
                    "reliable fade finishes around a guarded pin"));
            case VERY_OVERSTABLE -> shapes.addAll(List.of("spike hyzers", "utility shots out of trouble",
                    "hard forehand approaches", "strong headwind lines", "skip shots off hardpan"));
        }
        switch (slot) {
            case PUTT_AND_APPROACH -> shapes.addAll(List.of("putting", "short upshots", "jump putts"));
            case MIDRANGE -> shapes.addAll(List.of("controlled approaches", "wooded gap shots"));
            case FAIRWAY_DRIVER -> shapes.addAll(List.of("shaped fairway drives", "controlled placement off the tee"));
            case DISTANCE_DRIVER -> shapes.addAll(List.of("open-field distance", "long shaped bombs"));
        }
        return List.copyOf(shapes);
    }

    /** How the disc behaves once the air stops being still. */
    public static String windPhrase(StabilityClass stability) {
        return switch (stability) {
            case VERY_UNDERSTABLE -> "unusable in a headwind, turns over immediately; excellent downwind";
            case UNDERSTABLE -> "struggles into a headwind, rewards a tailwind, flips early if released flat";
            case STABLE -> "workable in light wind, will turn over if a headwind gets strong";
            case OVERSTABLE -> "wind resistant, holds its intended line into a headwind";
            case VERY_OVERSTABLE -> "the headwind and crosswind answer, will not turn over regardless of power";
        };
    }

    /** Which players tend to reach for this. */
    public static List<String> playerFit(DiscSlot slot, StabilityClass stability, double speed) {
        List<String> fit = new ArrayList<>();
        if (speed >= 11 && (stability == StabilityClass.OVERSTABLE || stability == StabilityClass.VERY_OVERSTABLE)) {
            fit.add("advanced and professional arm speeds only");
        }
        if (stability == StabilityClass.UNDERSTABLE || stability == StabilityClass.VERY_UNDERSTABLE) {
            fit.add("beginner friendly");
            fit.add("rewards a slower arm with extra distance");
        }
        if (stability == StabilityClass.OVERSTABLE || stability == StabilityClass.VERY_OVERSTABLE) {
            fit.add("a forehand player's staple");
        }
        if (slot == DiscSlot.MIDRANGE || slot == DiscSlot.FAIRWAY_DRIVER) {
            fit.add("suits technical wooded courses where placement beats distance");
        }
        if (slot == DiscSlot.DISTANCE_DRIVER) {
            fit.add("suits wide open courses with room to let it fly");
        }
        if (slot == DiscSlot.PUTT_AND_APPROACH) {
            fit.add("scoring disc, used on nearly every hole");
        }
        return List.copyOf(fit);
    }

    /** Weather adjustments, spelled out because players describe conditions, not stability indices. */
    public static String conditionEffect(String condition) {
        return switch (condition == null ? "" : condition.toUpperCase(java.util.Locale.ROOT)) {
            case "HOT" -> "In hot, thin air discs fly slightly further and slightly more understable, "
                    + "so a touch more stability than usual is welcome.";
            case "COLD" -> "In cold, dense air discs fly shorter and noticeably more overstable, "
                    + "so a lower-stability or lighter option keeps the same line.";
            case "RAINY" -> "In the rain, grip is the limiting factor: tacky or gummy plastics and "
                    + "lower-speed, higher-glide discs hold up better than hard premium blends.";
            case "WINDY" -> "In wind, understable discs become unusable into a headwind while overstable "
                    + "discs stay predictable; crosswinds punish high glide.";
            default -> "In calm, normal conditions a disc flies close to its published numbers.";
        };
    }
}
