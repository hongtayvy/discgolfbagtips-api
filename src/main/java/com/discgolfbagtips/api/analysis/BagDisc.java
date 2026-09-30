package com.discgolfbagtips.api.analysis;

import com.discgolfbagtips.api.catalog.Disc;
import com.discgolfbagtips.api.catalog.DiscSlot;
import com.discgolfbagtips.api.catalog.DiscWeight;
import com.discgolfbagtips.api.catalog.PlasticType;
import com.discgolfbagtips.api.catalog.StabilityClass;
import com.discgolfbagtips.api.catalog.WearState;
import java.util.Optional;

/**
 * A disc in the player's bag once the mold has been matched to the catalog and the three
 * per-instance axes — plastic, weight and wear — have been resolved.
 *
 * <p>None of those three are catalog data: the upstream source publishes one set of numbers per
 * mold. They are what makes the player's Champion 175 g Teebird a different disc from their
 * beat-in DX 165 g one, so they are modelled here rather than in {@link Disc}.
 *
 * @param weightGrams the disc's weight, or null when the player did not say
 * @param wear        how much use it has seen; never null, defaulting to {@link WearState#NEW}
 */
public record BagDisc(Disc disc, PlasticType plastic, String requestedPlastic, Integer weightGrams,
        WearState wear) {

    public BagDisc {
        wear = wear == null ? WearState.NEW : wear;
    }

    /** Convenience for callers that only care about the mold. */
    public static BagDisc of(Disc disc, PlasticType plastic, String requestedPlastic) {
        return new BagDisc(disc, plastic, requestedPlastic, null, WearState.NEW);
    }

    public FlightProfile flight() {
        return new FlightProfile(disc.speed(), disc.glide(), disc.turn(), disc.fade());
    }

    public double plasticShift() {
        return plastic == null ? 0.0 : plastic.stabilityShift();
    }

    public double weightShift() {
        return DiscWeight.stabilityShift(slot(), weightGrams);
    }

    public double wearShift() {
        return wear.stabilityShift(plastic == null ? null : plastic.durability());
    }

    public StabilityBreakdown stabilityBreakdown() {
        return new StabilityBreakdown(disc.stabilityIndex(), plasticShift(), weightShift(), wearShift());
    }

    public double effectiveStabilityIndex() {
        return stabilityBreakdown().effective();
    }

    public StabilityClass effectiveStability() {
        return stabilityBreakdown().effectiveClass();
    }

    public DiscSlot slot() {
        return disc.slot();
    }

    public Optional<PlasticType> plasticOrEmpty() {
        return Optional.ofNullable(plastic);
    }

    public String weightClass() {
        return DiscWeight.classify(weightGrams);
    }

    /** e.g. {@code Discraft Buzzz (ESP, 177 g, beat in)}. */
    public String label() {
        String plasticName = plastic != null ? plastic.name()
                : (requestedPlastic == null || requestedPlastic.isBlank() ? "unspecified plastic" : requestedPlastic);
        StringBuilder label = new StringBuilder(disc.brand()).append(' ').append(disc.name())
                .append(" (").append(plasticName);
        if (weightGrams != null) {
            label.append(", ").append(weightGrams).append(" g");
        }
        if (wear != WearState.NEW) {
            label.append(", ").append(wear.label());
        }
        return label.append(')').toString();
    }
}
