package com.discgolfbagtips.api.recommendation.dto;

import com.discgolfbagtips.api.retrieval.RetrievalFilters;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Optional constraints on what may be recommended — brand loyalty, or what the local shop actually
 * stocks. Entirely optional; omitting the block searches the whole catalog.
 *
 * @param brands        recommend only these manufacturers (case-insensitive)
 * @param excludeBrands never recommend these manufacturers
 * @param maxSpeed      a ceiling on top of the one the player's skill already implies
 */
public record RecommendationFilters(
        @Size(max = 30, message = "at most 30 brands") List<@Size(max = 120) String> brands,
        @Size(max = 30, message = "at most 30 excluded brands") List<@Size(max = 120) String> excludeBrands,
        @DecimalMin(value = "1.0") @DecimalMax(value = "20.0") Double maxSpeed) {

    public static final RecommendationFilters NONE = new RecommendationFilters(List.of(), List.of(), null);

    public RetrievalFilters toRetrievalFilters() {
        return new RetrievalFilters(brands, excludeBrands, maxSpeed);
    }
}
