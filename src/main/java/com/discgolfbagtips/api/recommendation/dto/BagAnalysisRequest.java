package com.discgolfbagtips.api.recommendation.dto;

import com.discgolfbagtips.api.player.CourseConditions;
import com.discgolfbagtips.api.player.PlayerProfile;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record BagAnalysisRequest(
        @NotNull @Size(max = 30, message = "a bag may hold at most 30 discs")
        List<@Valid BagDiscRequest> bag,

        @NotNull @Valid PlayerProfile profile,

        @NotNull @Valid CourseConditions conditions,

        /* Optional. Omit to search the whole catalog. */
        @Valid RecommendationFilters filters,

        /* Optional. The physical bag the player carries, for carry-weight and fitting analysis. */
        @Valid CarriedBag carriedBag) {

    /**
     * Identifies the bag itself. Either an id from {@code GET /api/v1/bags}, or free text the API
     * resolves — the same both-ways approach the disc entries use.
     */
    public record CarriedBag(
            Long bagModelId,
            @jakarta.validation.constraints.Size(max = 120) String brand,
            @jakarta.validation.constraints.Size(max = 160) String model) {

        public boolean identifiesSomething() {
            return bagModelId != null || (model != null && !model.isBlank());
        }
    }

    public List<BagDiscRequest> bagOrEmpty() {
        return bag == null ? List.of() : bag;
    }

    public RecommendationFilters filtersOrNone() {
        return filters == null ? RecommendationFilters.NONE : filters;
    }

    public CarriedBag carriedBagOrNull() {
        return carriedBag != null && carriedBag.identifiesSomething() ? carriedBag : null;
    }
}
