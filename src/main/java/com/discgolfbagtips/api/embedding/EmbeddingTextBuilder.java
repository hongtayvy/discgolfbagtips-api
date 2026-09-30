package com.discgolfbagtips.api.embedding;

import com.discgolfbagtips.api.analysis.BagAnalysis;
import com.discgolfbagtips.api.analysis.BagDisc;
import com.discgolfbagtips.api.analysis.BagGap;
import com.discgolfbagtips.api.catalog.Disc;
import com.discgolfbagtips.api.catalog.DiscSlot;
import com.discgolfbagtips.api.catalog.PlasticType;
import com.discgolfbagtips.api.catalog.StabilityClass;
import com.discgolfbagtips.api.player.PlayerProfile;
import com.discgolfbagtips.api.player.WeatherCondition;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Builds the passages that get embedded — both the per-disc documents and the per-request query.
 *
 * <p>The design bet of this project: a disc's vector should be built from numbers <em>plus</em> the
 * plastic's flight effect <em>plus</em> the vocabulary players use to describe shots, all in one
 * passage. A query is written in that same register ("I need something understable I can hyzer-flip
 * on a tight wooded hole"), so the two land near each other in vector space. Indexing the four
 * flight numbers alone — which is what comparable tools appear to do — throws away the only signal
 * that makes semantic search better than a SQL filter.
 */
@Component
public class EmbeddingTextBuilder {

    /** Prefix on document passages, so a stored vector is never mistaken for a query vector. */
    private static final String DOCUMENT_PREFIX = "Disc: ";
    private static final String QUERY_PREFIX = "Bag gap: ";

    public String forDisc(Disc disc, PlasticType plastic) {
        DiscSlot slot = disc.slot();
        double stabilityIndex = disc.stabilityIndex() + (plastic == null ? 0.0 : plastic.stabilityShift());
        StabilityClass stability = StabilityClass.forIndex(stabilityIndex);

        StringBuilder passage = new StringBuilder(DOCUMENT_PREFIX);
        passage.append(disc.brand()).append(' ').append(disc.name())
                .append(", a ").append(disc.category().toLowerCase(Locale.ROOT))
                .append(plastic == null ? "" : " in " + plastic.name() + " plastic")
                .append(". ");

        passage.append("Flight numbers: speed ").append(number(disc.speed()))
                .append(", glide ").append(number(disc.glide()))
                .append(", turn ").append(number(disc.turn()))
                .append(", fade ").append(number(disc.fade()))
                .append(" (stability index ").append(number(stabilityIndex))
                .append(", ").append(stability.label()).append("). ");

        passage.append("Speed: ").append(FlightTerminology.speedPhrase(disc.speed())).append(". ");
        passage.append("Glide: ").append(FlightTerminology.glidePhrase(disc.glide())).append(". ");
        passage.append("Turn: ").append(FlightTerminology.turnPhrase(disc.turn())).append(". ");
        passage.append("Fade: ").append(FlightTerminology.fadePhrase(disc.fade())).append(". ");
        passage.append("Stability: ").append(stability.label()).append(" — ")
                .append(stability.behaviour()).append(". ");

        if (plastic != null) {
            passage.append("Plastic: ").append(plastic.name()).append(" is a ").append(plastic.family().label())
                    .append(" blend (durability ").append(plastic.durability()).append("/5, grip ")
                    .append(plastic.grip()).append("/5). ").append(plastic.description()).append(' ')
                    .append(plastic.family().behaviour()).append(". ")
                    .append(plasticShiftPhrase(plastic.stabilityShift())).append(' ');
        }

        passage.append("Slot: ").append(slot.label()).append(", used for ").append(slot.purpose()).append(". ");
        passage.append("Thrown for: ")
                .append(String.join(", ", FlightTerminology.shotShapes(slot, stability))).append(". ");
        passage.append("In wind: ").append(FlightTerminology.windPhrase(stability)).append(". ");

        List<String> fit = FlightTerminology.playerFit(slot, stability, disc.speed());
        if (!fit.isEmpty()) {
            passage.append("Suits: ").append(String.join(", ", fit)).append(". ");
        }
        if (disc.stabilityLabel() != null && !disc.stabilityLabel().isBlank()) {
            passage.append("Commonly described as ").append(disc.stabilityLabel().toLowerCase(Locale.ROOT))
                    .append(". ");
        }
        passage.append("Keywords: ").append(String.join(", ", descriptors(disc, plastic))).append('.');
        return passage.toString();
    }

    /**
     * The retrieval query. Written from the gap outward: what is missing, who is throwing it, and
     * what the day looks like.
     */
    public String forGap(BagGap gap, BagAnalysis analysis, PlayerProfile profile, WeatherCondition weather) {
        StringBuilder passage = new StringBuilder(QUERY_PREFIX);
        passage.append("looking for a ").append(gap.stabilityClass().label()).append(' ')
                .append(gap.slot().label()).append(" to add to the bag. ");
        passage.append("Target flight numbers around speed ").append(number(gap.target().speed()))
                .append(", glide ").append(number(gap.target().glide()))
                .append(", turn ").append(number(gap.target().turn()))
                .append(", fade ").append(number(gap.target().fade())).append(". ");
        passage.append("This disc is for ").append(gap.slot().purpose()).append(". ");
        passage.append("It should behave ").append(gap.stabilityClass().behaviour()).append(". ");
        passage.append("Thrown for: ")
                .append(String.join(", ", FlightTerminology.shotShapes(gap.slot(), gap.stabilityClass())))
                .append(". ");
        passage.append("In wind: ").append(FlightTerminology.windPhrase(gap.stabilityClass())).append(". ");
        passage.append(profile.describe()).append(' ');
        passage.append(profile.skillLevel().guidance()).append(". ");
        passage.append(profile.throwingStyle().guidance()).append(". ");
        passage.append(profile.courseType().guidance()).append(". ");
        passage.append(weather.guidance()).append(' ');
        if (weather.gripCritical()) {
            passage.append("Grippy, tacky or gummy plastic is preferred over hard premium plastic. ");
        }
        passage.append("Preferred weight ").append(gap.targetWeight().format()).append(". ")
                .append(gap.targetWeight().rationale()).append(' ');
        if (gap.nearestInBag() != null && gap.delta() != null) {
            passage.append("The closest disc already bagged is ").append(gap.nearestInBag().label())
                    .append(" at ").append(gap.nearestInBag().flight().format())
                    .append("; the new disc should differ by roughly ").append(gap.delta().format())
                    .append(" in speed/glide/turn/fade. ");
        }
        if (!analysis.bag().isEmpty()) {
            passage.append("Already covered in the bag: ").append(coveredSummary(analysis)).append(". ");
        }
        passage.append("Keywords: ").append(String.join(", ",
                queryDescriptors(gap, profile, weather))).append('.');
        return passage.toString();
    }

    /** Short, human-readable tags — surfaced in the explainability payload as "why this matched". */
    public List<String> descriptors(Disc disc, PlasticType plastic) {
        DiscSlot slot = disc.slot();
        double stabilityIndex = disc.stabilityIndex() + (plastic == null ? 0.0 : plastic.stabilityShift());
        StabilityClass stability = StabilityClass.forIndex(stabilityIndex);

        Set<String> tags = new LinkedHashSet<>();
        tags.add(stability.label() + " " + slot.label());
        tags.addAll(FlightTerminology.shotShapes(slot, stability));
        tags.addAll(FlightTerminology.playerFit(slot, stability, disc.speed()));
        if (disc.glide() >= 5) {
            tags.add("high glide");
        }
        if (disc.speed() <= 4) {
            tags.add("low speed control");
        }
        if (plastic != null) {
            tags.add(plastic.family().label() + " plastic");
            if (plastic.grip() >= 4) {
                tags.add("grippy in wet conditions");
            }
            if (plastic.durability() >= 4) {
                tags.add("durable, holds its flight");
            }
        }
        return List.copyOf(tags);
    }

    private List<String> queryDescriptors(BagGap gap, PlayerProfile profile, WeatherCondition weather) {
        Set<String> tags = new LinkedHashSet<>();
        tags.add(gap.stabilityClass().label() + " " + gap.slot().label());
        tags.addAll(FlightTerminology.shotShapes(gap.slot(), gap.stabilityClass()));
        tags.add(profile.courseType().name().toLowerCase(Locale.ROOT) + " course");
        tags.add(profile.throwingStyle().name().toLowerCase(Locale.ROOT) + " dominant");
        tags.add(profile.skillLevel().name().toLowerCase(Locale.ROOT) + " skill level");
        tags.add(weather.description());
        if (weather.gripCritical()) {
            tags.add("grippy in wet conditions");
        }
        return List.copyOf(tags);
    }

    private String coveredSummary(BagAnalysis analysis) {
        List<String> covered = new ArrayList<>();
        for (BagDisc bagDisc : analysis.bag()) {
            // Effective, not published: a beat-in overstable driver no longer covers the overstable cell.
            String entry = bagDisc.effectiveStability().label() + " " + bagDisc.slot().label();
            if (!covered.contains(entry)) {
                covered.add(entry);
            }
        }
        return String.join(", ", covered);
    }

    private String plasticShiftPhrase(double shift) {
        if (shift >= 0.5) {
            return "In this plastic the mold plays noticeably more overstable than its published numbers.";
        }
        if (shift > 0) {
            return "In this plastic the mold plays slightly more overstable than its published numbers.";
        }
        if (shift <= -0.5) {
            return "In this plastic the mold plays noticeably more understable than its published numbers "
                    + "and seasons in quickly.";
        }
        if (shift < 0) {
            return "In this plastic the mold plays slightly more understable than its published numbers.";
        }
        return "This plastic flies close to the mold's published numbers.";
    }

    private static String number(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value)
                : String.valueOf(Math.round(value * 10.0) / 10.0);
    }
}
