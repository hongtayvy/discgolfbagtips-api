package com.discgolfbagtips.api.catalog.sync;

import java.time.Duration;
import java.time.Instant;

public record SyncReport(
        Instant startedAt,
        long durationMs,
        int fetched,
        int created,
        int updated,
        int deactivated,
        int skipped,
        int duplicates,
        String status,
        String message) {

    public static SyncReport success(Instant startedAt, int fetched, int created, int updated, int deactivated,
            int skipped, int duplicates) {
        return new SyncReport(startedAt, Duration.between(startedAt, Instant.now()).toMillis(), fetched, created,
                updated, deactivated, skipped, duplicates, "SUCCESS", "Catalog sync completed");
    }

    public static SyncReport failure(Instant startedAt, String message) {
        return new SyncReport(startedAt, Duration.between(startedAt, Instant.now()).toMillis(), 0, 0, 0, 0, 0, 0,
                "FAILED", message);
    }

    public static SyncReport skipped(String message) {
        Instant now = Instant.now();
        return new SyncReport(now, 0, 0, 0, 0, 0, 0, 0, "SKIPPED", message);
    }
}
