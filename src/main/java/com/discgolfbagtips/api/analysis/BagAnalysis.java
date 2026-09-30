package com.discgolfbagtips.api.analysis;

import java.util.List;
import java.util.Map;

/**
 * @param coverage   count of discs per "SLOT/STABILITY" cell
 * @param gaps       the highest-severity gaps, which is what a single recommendation acts on
 * @param allGaps    every gap found, in severity order — the slot-by-slot lineup needs the full set
 * @param redundancies discs duplicating each other's job — the other half of a bag analysis, and
 *                     the reason the output is not purely "what is missing"
 * @param unresolved bag entries the catalog could not match, echoed back so the UI can flag them
 */
public record BagAnalysis(
        List<BagDisc> bag,
        List<BagGap> gaps,
        List<BagGap> allGaps,
        List<BagRedundancy> redundancies,
        Map<String, Integer> coverage,
        int coverageScore,
        List<String> notes,
        List<String> unresolved) {

    public BagGap primaryGap() {
        return gaps.isEmpty() ? null : gaps.getFirst();
    }

    /** Every gap the analyzer scored for one slot, highest severity first. */
    public List<BagGap> gapsForSlot(com.discgolfbagtips.api.catalog.DiscSlot slot) {
        return allGaps.stream().filter(gap -> gap.slot() == slot).toList();
    }

    public List<BagDisc> bagForSlot(com.discgolfbagtips.api.catalog.DiscSlot slot) {
        return bag.stream().filter(disc -> disc.slot() == slot).toList();
    }

    public List<BagRedundancy> redundanciesForSlot(com.discgolfbagtips.api.catalog.DiscSlot slot) {
        return redundancies.stream().filter(r -> r.slot() == slot).toList();
    }
}
