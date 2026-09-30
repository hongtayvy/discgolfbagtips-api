package com.discgolfbagtips.api.bag;

/**
 * A bag that can hold the player's lineup, with the weight it would add.
 *
 * @param headroom   spare capacity beyond the current disc count
 * @param comparison how this bag differs from the one they already carry, when they named one
 */
public record BagFit(BagSummary bag, int headroom, Integer totalCarryGrams, Double totalCarryPounds,
        String comparison) {
}
