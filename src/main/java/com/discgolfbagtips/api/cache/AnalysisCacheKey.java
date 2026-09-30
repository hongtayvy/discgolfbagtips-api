package com.discgolfbagtips.api.cache;

import com.discgolfbagtips.api.recommendation.dto.BagAnalysisRequest;
import com.discgolfbagtips.api.recommendation.dto.BagDiscRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * A stable fingerprint of everything that can change an analysis.
 *
 * <p>Deliberately not keyed on the session or any user id: two players with the same bag, profile
 * and conditions get the same answer, so they should share the cached one. That is what makes the
 * cache worth having at all on a low-traffic beta, where per-user keys would almost never hit.
 *
 * <p>The bag is sorted before hashing so that reordering the same discs is the same key, and every
 * per-instance axis (plastic, weight, wear) is included because each one changes the result.
 */
public final class AnalysisCacheKey {

    private AnalysisCacheKey() {
    }

    public static String of(String endpoint, BagAnalysisRequest request) {
        StringBuilder canonical = new StringBuilder(endpoint).append('\n');

        List<String> discs = request.bagOrEmpty().stream()
                .map(AnalysisCacheKey::describe)
                .sorted()
                .toList();
        canonical.append(String.join(",", discs)).append('\n');

        canonical.append(request.profile().skillLevel()).append('|')
                .append(request.profile().throwingStyle()).append('|')
                .append(request.profile().courseType()).append('\n');
        canonical.append(request.conditions().weather()).append('\n');

        var filters = request.filtersOrNone().toRetrievalFilters();
        canonical.append(filters.describe()).append('\n');

        var carried = request.carriedBagOrNull();
        canonical.append(carried == null ? "-"
                : "%s|%s|%s".formatted(carried.bagModelId(), lower(carried.brand()), lower(carried.model())));

        return sha256(canonical.toString());
    }

    private static String describe(BagDiscRequest disc) {
        return "%s/%s/%s/%s/%s/%s".formatted(
                lower(disc.discId()), lower(disc.name()), lower(disc.brand()),
                lower(disc.plastic()), disc.weightGrams(), disc.wear());
    }

    private static String lower(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String sha256(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required but unavailable", ex);
        }
    }
}
