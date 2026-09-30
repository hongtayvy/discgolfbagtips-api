package com.discgolfbagtips.api.analysis;

import com.discgolfbagtips.api.catalog.DiscSlot;
import com.discgolfbagtips.api.catalog.StabilityClass;
import com.discgolfbagtips.api.player.PlayerProfile;
import com.discgolfbagtips.api.player.SkillLevel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Finds discs that duplicate each other's job, and decides how loudly to say so.
 *
 * <p>The naive version — flag any two discs in the same (slot, stability) cell — is wrong in both
 * directions. It misses duplicates split across a cell boundary, and it shouts at pro bags where
 * repeated molds are deliberate. Two corrections:
 *
 * <ol>
 *   <li><b>Separation, not grouping.</b> Redundancy is measured as how far the closest pair actually
 *       flies apart once plastic, weight and wear are applied. Two Destroyers at different wear
 *       levels separate; two identical new ones do not.</li>
 *   <li><b>Scaled to the thrower.</b> A subtle flight difference only exists if the player can
 *       produce it. Sensitivity drops with skill, and drops further when wear data shows the
 *       duplication is intentional.</li>
 * </ol>
 */
@Component
public class RedundancyAnalyzer {

    /** Below this, two discs are doing the same job by any reasonable reading. */
    private static final double INTERCHANGEABLE = 0.5;
    /** Above this, the pair is distinct enough that calling it redundant would be wrong. */
    private static final double CLEARLY_DISTINCT = 1.6;

    public List<BagRedundancy> analyze(List<BagDisc> bag, PlayerProfile profile) {
        if (bag.size() < 2) {
            return List.of();
        }

        // Grouped by slot, not by (slot, stability) cell. Cell boundaries are arbitrary lines through
        // a continuum: a 150 g and a 175 g Destroyer fall either side of one while remaining the same
        // disc in two weights. Separation decides redundancy; the cell only labels it afterwards.
        Map<DiscSlot, List<BagDisc>> bySlot = new LinkedHashMap<>();
        for (BagDisc disc : bag) {
            bySlot.computeIfAbsent(disc.slot(), key -> new ArrayList<>()).add(disc);
        }

        List<BagRedundancy> found = new ArrayList<>();
        for (List<BagDisc> slotDiscs : bySlot.values()) {
            found.addAll(clustersWithin(slotDiscs, profile));
        }
        found.sort(Comparator.comparingDouble(BagRedundancy::severity).reversed());
        return List.copyOf(found);
    }

    /**
     * Groups discs within a slot into clusters that fly close enough together to count as duplicating
     * each other, using single-linkage: a disc joins a cluster if it is near <em>any</em> member. That
     * keeps a chain of three progressively-worn copies together instead of splitting it into pairs.
     */
    private List<BagRedundancy> clustersWithin(List<BagDisc> slotDiscs, PlayerProfile profile) {
        if (slotDiscs.size() < 2) {
            return List.of();
        }
        boolean[] assigned = new boolean[slotDiscs.size()];
        List<BagRedundancy> found = new ArrayList<>();

        for (int i = 0; i < slotDiscs.size(); i++) {
            if (assigned[i]) {
                continue;
            }
            List<BagDisc> cluster = new ArrayList<>();
            cluster.add(slotDiscs.get(i));
            assigned[i] = true;

            boolean grew = true;
            while (grew) {
                grew = false;
                for (int j = 0; j < slotDiscs.size(); j++) {
                    if (assigned[j]) {
                        continue;
                    }
                    for (BagDisc member : cluster) {
                        if (separation(member, slotDiscs.get(j)) <= CLEARLY_DISTINCT) {
                            cluster.add(slotDiscs.get(j));
                            assigned[j] = true;
                            grew = true;
                            break;
                        }
                    }
                }
            }

            if (cluster.size() >= 2) {
                found.add(describe(cluster, closestPairSeparation(cluster), profile));
            }
        }
        return found;
    }

    /**
     * The tightest pair decides the verdict: three discs where two are interchangeable is a
     * redundancy regardless of how distinct the third is.
     */
    private double closestPairSeparation(List<BagDisc> group) {
        double closest = Double.MAX_VALUE;
        for (int i = 0; i < group.size(); i++) {
            for (int j = i + 1; j < group.size(); j++) {
                closest = Math.min(closest, separation(group.get(i), group.get(j)));
            }
        }
        return round(closest);
    }

    /**
     * Distance in published flight numbers, plus the gap the per-instance axes opened up. Two copies
     * of one mold score zero on the first term and rely entirely on the second — which is exactly the
     * case this feature exists to handle.
     */
    private double separation(BagDisc left, BagDisc right) {
        double flight = left.flight().distanceTo(right.flight());
        double effective = Math.abs(left.effectiveStabilityIndex() - right.effectiveStabilityIndex());
        return flight + effective;
    }

    private BagRedundancy describe(List<BagDisc> group, double separation, PlayerProfile profile) {
        DiscSlot slot = group.getFirst().slot();
        StabilityClass stability = group.stream()
                .collect(java.util.stream.Collectors.groupingBy(BagDisc::effectiveStability,
                        java.util.stream.Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(group.getFirst().effectiveStability());

        // Closer pairs are more redundant; the scale runs 1.0 at identical to 0 at clearly distinct.
        double base = Math.clamp(1.0 - (separation / CLEARLY_DISTINCT), 0.0, 1.0);
        // A third copy is worse than a second.
        double crowding = 1.0 + 0.25 * (group.size() - 2);
        double skillFactor = skillFactor(profile.skillLevel());
        double severity = round(base * crowding * skillFactor);

        boolean sameMold = group.stream().map(disc -> disc.disc().name()).distinct().count() == 1;
        boolean wearDiffers = group.stream().map(BagDisc::wear).distinct().count() > 1;
        boolean skilled = profile.skillLevel().rank() >= SkillLevel.ADVANCED.rank();

        BagRedundancy.Level level;
        if (skilled && wearDiffers) {
            // The pro case: repeated molds at different wear levels are a deliberate spread.
            level = BagRedundancy.Level.FYI;
            severity = round(severity * 0.5);
        } else if (severity >= 0.6) {
            level = BagRedundancy.Level.WARNING;
        } else if (severity >= 0.3) {
            level = BagRedundancy.Level.NOTE;
        } else {
            level = BagRedundancy.Level.FYI;
        }

        return new BagRedundancy(slot, stability, List.copyOf(group), separation, severity, level,
                reason(group, slot, stability, separation, sameMold, wearDiffers, skilled, profile));
    }

    /**
     * How much a flight difference this small matters to this player. A beginner cannot reliably
     * produce a half-point stability difference, so for them near-duplicates really are duplicates;
     * a professional shapes shots around exactly that margin.
     */
    private double skillFactor(SkillLevel skill) {
        return switch (skill) {
            case BEGINNER -> 1.0;
            case INTERMEDIATE -> 0.8;
            case ADVANCED -> 0.5;
            case PROFESSIONAL -> 0.3;
        };
    }

    private String reason(List<BagDisc> group, DiscSlot slot, StabilityClass stability, double separation,
            boolean sameMold, boolean wearDiffers, boolean skilled, PlayerProfile profile) {

        String names = String.join(" and ", group.stream().map(BagDisc::label).toList());

        if (skilled && wearDiffers) {
            return "%s share the %s %s role, but sit at different wear levels — at this skill level that "
                    .formatted(names, stability.label(), slot.label())
                    + "is usually a deliberate spread rather than duplication, so this is noted only.";
        }
        if (separation <= INTERCHANGEABLE) {
            String why = sameMold
                    ? "the same mold with nothing separating the copies"
                    : "different molds that fly the same line";
            return "%s are effectively interchangeable (%s). Dropping one frees a slot for a gap."
                    .formatted(names, why);
        }
        if (wearDiffers && profile.skillLevel().rank() <= SkillLevel.INTERMEDIATE.rank()) {
            return "%s overlap in the %s %s role. The wear difference separates them a little, but "
                    .formatted(names, stability.label(), slot.label())
                    + "probably not enough to throw two distinct lines yet.";
        }
        return "%s both cover the %s %s role with only %.1f separating them."
                .formatted(names, stability.label(), slot.label(), separation);
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
