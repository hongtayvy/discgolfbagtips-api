package com.discgolfbagtips.api.recommendation.dto;

import java.util.List;

/**
 * The part of the response that makes the recommendation auditable: exactly which vector matches
 * and which flight-number deltas produced it, and which model produced each half.
 */
public record Explainability(
        String retrievalMode,
        String retrievalQuery,
        String appliedFilters,
        EmbeddingInfo embedding,
        GenerationInfo generation,
        List<VectorMatch> vectorMatches,
        List<FlightGapExplanation> flightGaps,
        List<String> citations,
        Timings timings,
        List<String> degradations) {

    public record EmbeddingInfo(String provider, String model, int dimensions, boolean stubbed) {
    }

    /**
     * @param transientFailure true only when the model was reachable-in-principle but failed right
     *                         now. A missing API key is a settled configuration, not a blip, so it
     *                         does not set this — which is what lets the cache keep such a response.
     */
    public record GenerationInfo(String provider, String model, String source, String note, boolean stubbed,
            boolean transientFailure) {
    }

    /** One retrieved disc, with the evidence for why it came back. */
    public record VectorMatch(
            int rank,
            String discId,
            String name,
            String brand,
            double similarity,
            double distance,
            List<String> matchedDescriptors,
            FlightDelta deltaVsGapTarget,
            String passageExcerpt,
            boolean chosen) {
    }

    /** A gap the analyzer found, and the flight-number arithmetic behind it. */
    public record FlightGapExplanation(
            String slot,
            String stabilityClass,
            String kind,
            double severity,
            String reason,
            FlightNumbers target,
            String targetWeightRange,
            String targetWeightRationale,
            NearestBagDisc nearestInBag,
            FlightDelta delta,
            boolean selected) {
    }

    public record FlightNumbers(double speed, double glide, double turn, double fade) {
    }

    public record FlightDelta(double speed, double glide, double turn, double fade) {
    }

    /**
     * @param plays    the stability this disc actually flies, after plastic, weight and wear
     * @param stability the arithmetic that got there, e.g. "2 published -0.3 plastic -0.4 weight -1.05 wear = 0.25"
     */
    public record NearestBagDisc(
            String discId,
            String name,
            String brand,
            String plastic,
            Integer weightGrams,
            String wear,
            FlightNumbers flight,
            String plays,
            String stability) {
    }

    public record Timings(long analyzeMs, long retrieveMs, long generateMs, long totalMs) {
    }
}
