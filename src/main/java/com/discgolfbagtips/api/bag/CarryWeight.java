package com.discgolfbagtips.api.bag;

import java.util.List;

/**
 * What the player is actually carrying, and whether their bag can hold it.
 *
 * @param estimated     true when at least one disc weight was assumed rather than given — the number
 *                      is still useful, but it should not be presented as measured
 * @param assumedWeights how many discs fell back to a slot default
 * @param overpacked    the lineup exceeds the bag's published capacity
 */
public record CarryWeight(
        Integer bagWeightGrams,
        int discWeightGrams,
        Integer totalGrams,
        double totalPounds,
        boolean estimated,
        int assumedWeights,
        int discCount,
        Integer capacity,
        boolean overpacked,
        List<String> notes) {

    public String describe() {
        if (totalGrams == null) {
            return "%d discs weigh about %.1f lb; total carry weight needs a bag with a published empty weight."
                    .formatted(discCount, gramsToPounds(discWeightGrams));
        }
        return "%d discs plus the bag come to about %.1f lb.".formatted(discCount, totalPounds);
    }

    public static double gramsToPounds(int grams) {
        return Math.round(grams / 453.592 * 10.0) / 10.0;
    }
}
