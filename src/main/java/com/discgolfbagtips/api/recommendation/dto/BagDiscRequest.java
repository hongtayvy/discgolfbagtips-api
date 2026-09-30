package com.discgolfbagtips.api.recommendation.dto;

import com.discgolfbagtips.api.catalog.DiscWeight;
import com.discgolfbagtips.api.catalog.WearState;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * One disc in the player's bag. The front end sends {@code discId} when the disc was picked from
 * the type-ahead, and falls back to free text when the player typed a bag list by hand.
 *
 * <p>{@code plastic}, {@code weightGrams} and {@code wear} are the three per-instance axes. All are
 * optional — a player who does not know what their disc weighs still gets an answer — but each one
 * supplied sharpens the analysis.
 *
 * @param weightGrams grams; PDGA's ceiling is 200 g and anything under 100 g is not a golf disc
 * @param wear        defaults to {@link WearState#NEW} when omitted
 */
public record BagDiscRequest(
        @Size(max = 64) String discId,
        @Size(max = 120) String name,
        @Size(max = 120) String brand,
        @Size(max = 120) String plastic,
        @Min(value = DiscWeight.MIN_GRAMS, message = "a disc weight below {value} g is not a golf disc")
        @Max(value = DiscWeight.MAX_GRAMS, message = "the PDGA weight limit is {value} g")
        Integer weightGrams,
        WearState wear) {

    public BagDiscRequest {
        wear = wear == null ? WearState.NEW : wear;
    }

    @AssertTrue(message = "each bag entry needs either a discId or a name")
    public boolean isIdentifiable() {
        return (discId != null && !discId.isBlank()) || (name != null && !name.isBlank());
    }

    public String describe() {
        if (discId != null && !discId.isBlank()) {
            return discId;
        }
        return ((brand == null || brand.isBlank()) ? "" : brand + " ") + name;
    }
}
