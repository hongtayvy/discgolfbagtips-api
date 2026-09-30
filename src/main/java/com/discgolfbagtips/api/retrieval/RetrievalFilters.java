package com.discgolfbagtips.api.retrieval;

import java.util.List;
import java.util.Locale;

/**
 * Optional hard constraints applied alongside the vector search — brand loyalty, what the local shop
 * stocks, a self-imposed speed ceiling.
 *
 * <p>These are applied <em>inside</em> the SQL rather than by filtering the results afterwards. That
 * distinction matters: post-filtering an over-fetched top-40 for "Innova only" can easily leave
 * nothing, whereas constraining the query lets similarity rank within the allowed set. Same reason
 * the slot's speed window is already a WHERE clause rather than a Java filter.
 *
 * @param brands        include only these manufacturers; empty means no restriction
 * @param excludeBrands never recommend these manufacturers
 * @param maxSpeed      a ceiling on top of the one the player's skill already implies
 */
public record RetrievalFilters(List<String> brands, List<String> excludeBrands, Double maxSpeed) {

    public static RetrievalFilters none() {
        return new RetrievalFilters(List.of(), List.of(), null);
    }

    public RetrievalFilters {
        brands = normalize(brands);
        excludeBrands = normalize(excludeBrands);
    }

    private static List<String> normalize(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(v -> v != null && !v.isBlank())
                .map(v -> v.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    public boolean isEmpty() {
        return brands.isEmpty() && excludeBrands.isEmpty() && maxSpeed == null;
    }

    public boolean restrictsBrands() {
        return !brands.isEmpty();
    }

    /** A short human-readable form for the explainability payload. */
    public String describe() {
        if (isEmpty()) {
            return "none";
        }
        StringBuilder description = new StringBuilder();
        if (!brands.isEmpty()) {
            description.append("brands in [").append(String.join(", ", brands)).append(']');
        }
        if (!excludeBrands.isEmpty()) {
            description.append(description.isEmpty() ? "" : "; ")
                    .append("excluding [").append(String.join(", ", excludeBrands)).append(']');
        }
        if (maxSpeed != null) {
            description.append(description.isEmpty() ? "" : "; ").append("speed <= ").append(maxSpeed);
        }
        return description.toString();
    }
}
