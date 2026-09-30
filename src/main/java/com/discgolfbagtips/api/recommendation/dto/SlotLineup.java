package com.discgolfbagtips.api.recommendation.dto;

import java.util.List;

/**
 * One tab in the lineup view: everything the player owns in a single speed slot, which stability
 * classes that leaves uncovered, and what would fill each hole.
 *
 * @param status        whether this slot is finished, thin, or absent from the bag entirely
 * @param stabilityCoverage the slot's stability classes and how many discs sit in each
 * @param gaps          uncovered or thin cells, highest severity first, each with suggestions
 */
public record SlotLineup(
        String slot,
        String label,
        String purpose,
        double minSpeed,
        double maxSpeed,
        Status status,
        int discCount,
        List<BagAnalysisSummary.ResolvedBagDisc> inBag,
        List<StabilityCell> stabilityCoverage,
        List<SlotGap> gaps,
        List<RedundancyReport> redundancies,
        String summary) {

    public enum Status {
        /** Every stability class worth owning in this slot is covered. */
        COMPLETE,
        /** Discs present, but at least one stability class is missing or carried by a single disc. */
        THIN,
        /** Nothing in the bag falls in this slot at all. */
        EMPTY
    }

    /** One (slot × stability) cell of the coverage matrix, for rendering as a grid or chips. */
    public record StabilityCell(String stabilityClass, String label, int count, boolean covered) {
    }

    /**
     * A hole in this slot and the discs that would fill it.
     *
     * @param suggestions retrieved candidates, best first; empty when the catalog has no vector yet
     */
    public record SlotGap(
            String stabilityClass,
            String label,
            String behaviour,
            double severity,
            String reason,
            Explainability.FlightNumbers target,
            String targetWeightRange,
            List<SuggestedDisc> suggestions) {
    }

    /** A candidate for one slot gap. Deliberately lighter than the headline recommendation. */
    public record SuggestedDisc(
            String discId,
            String name,
            String brand,
            double speed,
            double glide,
            double turn,
            double fade,
            String stability,
            String imageUrl,
            double similarity,
            Explainability.FlightDelta deltaVsTarget) {
    }
}
