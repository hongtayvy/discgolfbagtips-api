package com.discgolfbagtips.api.profile;

import java.time.Instant;
import java.util.UUID;

/** A saved bag as the list endpoint shows it — no payload, so a list stays small. */
public record BagProfileSummary(
        UUID id,
        String name,
        String description,
        int discCount,
        Long bagModelId,
        Instant createdAt,
        Instant updatedAt,
        Instant lastViewedAt) {

    public static BagProfileSummary from(BagProfile profile) {
        return new BagProfileSummary(profile.id(), profile.name(), profile.description(),
                profile.discCount(), profile.bagModelId(), profile.createdAt(), profile.updatedAt(),
                profile.lastViewedAt());
    }
}
