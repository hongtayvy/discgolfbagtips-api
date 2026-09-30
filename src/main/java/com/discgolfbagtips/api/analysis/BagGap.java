package com.discgolfbagtips.api.analysis;

import com.discgolfbagtips.api.catalog.DiscSlot;
import com.discgolfbagtips.api.catalog.DiscWeight;
import com.discgolfbagtips.api.catalog.StabilityClass;

/**
 * A hole in the bag: one (slot, stability) cell that is empty or thin, scored for how much this
 * particular player would feel it.
 *
 * @param nearestInBag the bagged disc closest to {@code target} in flight-number space, or null for an empty bag
 * @param delta        {@code target - nearestInBag}, the flight-number distance the gap represents
 * @param targetWeight the weight window to buy the replacement in, given the player and the wind
 */
public record BagGap(
        DiscSlot slot,
        StabilityClass stabilityClass,
        double severity,
        GapKind kind,
        String reason,
        FlightProfile target,
        BagDisc nearestInBag,
        FlightProfile delta,
        DiscWeight.WeightAdvice targetWeight) {

    public enum GapKind {
        /** Nothing in the bag covers this slot and stability at all. */
        MISSING,
        /** Something covers it, but only just — a single disc carrying a whole slot. */
        THIN
    }

    public String describe() {
        return "%s / %s (%s): %s".formatted(slot.label(), stabilityClass.label(), kind.name().toLowerCase(), reason);
    }
}
