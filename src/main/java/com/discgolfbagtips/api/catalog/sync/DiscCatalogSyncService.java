package com.discgolfbagtips.api.catalog.sync;

import com.discgolfbagtips.api.config.BagTipsProperties;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Pulls the DiscIt catalog into our own tables. Molds that disappear upstream are deactivated
 * rather than deleted, so a bag saved last week still resolves.
 */
@Service
public class DiscCatalogSyncService {

    private static final Logger log = LoggerFactory.getLogger(DiscCatalogSyncService.class);

    private final DiscItClient discItClient;
    private final DiscCatalogWriter writer;
    private final BagTipsProperties properties;
    private final AtomicReference<SyncReport> lastReport =
            new AtomicReference<>(SyncReport.skipped("No sync has run yet"));

    public DiscCatalogSyncService(DiscItClient discItClient, DiscCatalogWriter writer,
            BagTipsProperties properties) {
        this.discItClient = discItClient;
        this.writer = writer;
        this.properties = properties;
    }

    public SyncReport lastReport() {
        return lastReport.get();
    }

    public SyncReport sync() {
        Instant startedAt = Instant.now();
        if (!properties.discit().syncEnabled()) {
            SyncReport report = SyncReport.skipped("Catalog sync is disabled (bagtips.discit.sync-enabled=false)");
            lastReport.set(report);
            return report;
        }
        try {
            SyncReport report = writer.apply(discItClient.fetchAll(), startedAt);
            lastReport.set(report);
            return report;
        } catch (RuntimeException ex) {
            log.error("Disc catalog sync failed", ex);
            SyncReport report = SyncReport.failure(startedAt, ex.getMessage());
            lastReport.set(report);
            return report;
        }
    }
}
