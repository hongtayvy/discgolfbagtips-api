package com.discgolfbagtips.api.catalog.sync;

import com.discgolfbagtips.api.catalog.DiscRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The catalog is refreshed on a schedule, not per request: DiscIt is a volunteer-run free service
 * and its data changes on the order of weeks.
 */
@Component
public class CatalogSyncJob {

    private static final Logger log = LoggerFactory.getLogger(CatalogSyncJob.class);

    private final DiscCatalogSyncService syncService;
    private final DiscRepository discRepository;

    public CatalogSyncJob(DiscCatalogSyncService syncService, DiscRepository discRepository) {
        this.syncService = syncService;
        this.discRepository = discRepository;
    }

    /** Nightly at 03:15 UTC, off-peak for a hobby project's traffic. */
    @Scheduled(cron = "${bagtips.discit.sync-cron:0 15 3 * * *}", zone = "UTC")
    public void scheduledSync() {
        log.info("Scheduled disc catalog sync starting");
        syncService.sync();
    }

    /** A cold database (first deploy, or a wiped free-tier instance) should not wait until 03:15. */
    @EventListener(ApplicationReadyEvent.class)
    public void syncOnEmptyCatalog() {
        try {
            if (discRepository.countByActiveTrue() == 0) {
                log.info("Disc catalog is empty; running an initial sync");
                syncService.sync();
            }
        } catch (RuntimeException ex) {
            log.warn("Startup catalog check failed: {}", ex.getMessage());
        }
    }
}
