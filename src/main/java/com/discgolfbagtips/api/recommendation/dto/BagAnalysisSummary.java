package com.discgolfbagtips.api.recommendation.dto;

import java.util.List;
import java.util.Map;

/**
 * @param unresolvedEntries bag entries the catalog could not match, echoed back so the UI can ask
 */
public record BagAnalysisSummary(
        int discCount,
        int coverageScore,
        Map<String, Integer> coverage,
        List<String> notes,
        List<String> unresolvedEntries,
        List<ResolvedBagDisc> resolvedBag,
        /* The other half of the analysis: discs already doubling up, not just holes. */
        List<RedundancyReport> redundancies) {

    /**
     * One bag entry as the analyzer understood it, including the three per-instance axes and the
     * stability arithmetic they produced.
     *
     * @param stability     the class this disc actually flies, not the one the catalog publishes
     * @param publishedStability what the catalog says, shown when wear or weight has moved it
     */
    public record ResolvedBagDisc(
            String discId,
            String name,
            String brand,
            String plastic,
            Integer weightGrams,
            String weightClass,
            String wear,
            double speed,
            double glide,
            double turn,
            double fade,
            String slot,
            String stability,
            String publishedStability,
            double plasticStabilityShift,
            double weightStabilityShift,
            double wearStabilityShift,
            double effectiveStabilityIndex,
            String stabilityExplanation) {
    }
}
