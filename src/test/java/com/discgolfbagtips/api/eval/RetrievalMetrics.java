package com.discgolfbagtips.api.eval;

import java.util.List;
import java.util.function.Predicate;

/**
 * Standard ranked-retrieval metrics, plus the one that actually matters for judging a model here:
 * lift over the base rate. A scenario whose relevant set is 23% of the catalog gives 0.23 precision
 * to a coin flip, so a raw precision of 0.4 means much less than it looks.
 */
public record RetrievalMetrics(
        double precisionAt5,
        double precisionAt10,
        double recallAt10,
        double meanReciprocalRank,
        double baseRate) {

    public double liftAt5() {
        return baseRate == 0 ? 0 : round(precisionAt5 / baseRate);
    }

    public static <T> RetrievalMetrics of(List<T> ranked, Predicate<T> relevant, int totalRelevant,
            int catalogSize) {

        int hits5 = 0;
        int hits10 = 0;
        double reciprocalRank = 0;

        for (int i = 0; i < ranked.size(); i++) {
            if (!relevant.test(ranked.get(i))) {
                continue;
            }
            if (i < 5) {
                hits5++;
            }
            if (i < 10) {
                hits10++;
            }
            if (reciprocalRank == 0) {
                reciprocalRank = 1.0 / (i + 1);
            }
        }

        int denom5 = Math.min(5, ranked.size());
        int denom10 = Math.min(10, ranked.size());
        return new RetrievalMetrics(
                denom5 == 0 ? 0 : round((double) hits5 / denom5),
                denom10 == 0 ? 0 : round((double) hits10 / denom10),
                totalRelevant == 0 ? 0 : round((double) hits10 / Math.min(10, totalRelevant)),
                round(reciprocalRank),
                catalogSize == 0 ? 0 : round((double) totalRelevant / catalogSize));
    }

    public static RetrievalMetrics mean(List<RetrievalMetrics> all) {
        if (all.isEmpty()) {
            return new RetrievalMetrics(0, 0, 0, 0, 0);
        }
        return new RetrievalMetrics(
                round(all.stream().mapToDouble(RetrievalMetrics::precisionAt5).average().orElse(0)),
                round(all.stream().mapToDouble(RetrievalMetrics::precisionAt10).average().orElse(0)),
                round(all.stream().mapToDouble(RetrievalMetrics::recallAt10).average().orElse(0)),
                round(all.stream().mapToDouble(RetrievalMetrics::meanReciprocalRank).average().orElse(0)),
                round(all.stream().mapToDouble(RetrievalMetrics::baseRate).average().orElse(0)));
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }
}
