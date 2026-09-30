package com.discgolfbagtips.api.catalog.sync;

import com.discgolfbagtips.api.catalog.Disc;
import com.discgolfbagtips.api.catalog.DiscRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transactional half of the catalog sync. Kept separate from
 * {@link DiscCatalogSyncService} so the slow HTTP fetch happens outside the database transaction.
 */
@Component
public class DiscCatalogWriter {

    private static final Logger log = LoggerFactory.getLogger(DiscCatalogWriter.class);
    /** Bundled sets are packaging, not molds; they have no meaningful flight numbers. */
    private static final Set<String> EXCLUDED_CATEGORIES = Set.of("Disc Golf Sets");

    private final DiscRepository discRepository;

    public DiscCatalogWriter(DiscRepository discRepository) {
        this.discRepository = discRepository;
    }

    @Transactional
    public SyncReport apply(List<DiscItDisc> upstream, Instant startedAt) {
        Map<String, Disc> existing = new HashMap<>();
        discRepository.findAll().forEach(disc -> existing.put(disc.id(), disc));

        Set<String> seen = new HashSet<>();
        int created = 0;
        int updated = 0;
        int skipped = 0;
        int duplicates = 0;

        for (DiscItDisc source : upstream) {
            if (!source.usable() || EXCLUDED_CATEGORIES.contains(source.category())) {
                skipped++;
                continue;
            }
            // Upstream currently returns every disc twice under one id, differing only by name_slug.
            // Without this the second copy looks new (the `existing` snapshot predates the insert),
            // so the report claimed twice as many creations as there were rows.
            if (!seen.add(source.id())) {
                duplicates++;
                continue;
            }
            Disc disc = existing.get(source.id());
            boolean isNew = disc == null;
            if (isNew) {
                disc = new Disc(source.id());
                disc.setFirstSeenAt(Instant.now());
            }
            String previousHash = disc.contentHash();
            copyInto(source, disc);
            disc.setLastSyncedAt(Instant.now());
            discRepository.save(disc);
            if (isNew) {
                created++;
            } else if (!disc.contentHash().equals(previousHash)) {
                updated++;
            }
        }

        int deactivated = 0;
        for (Disc disc : existing.values()) {
            if (!seen.contains(disc.id()) && disc.active()) {
                disc.setActive(false);
                disc.setLastSyncedAt(Instant.now());
                discRepository.save(disc);
                deactivated++;
            }
        }

        log.info("Catalog sync: fetched={} distinct={} created={} updated={} deactivated={} "
                + "skipped={} duplicates={}",
                upstream.size(), seen.size(), created, updated, deactivated, skipped, duplicates);
        return SyncReport.success(startedAt, upstream.size(), created, updated, deactivated, skipped,
                duplicates);
    }

    private void copyInto(DiscItDisc source, Disc disc) {
        disc.setName(source.name().trim());
        disc.setBrand(source.brand().trim());
        disc.setCategory(source.category() == null ? "Unknown" : source.category().trim());
        disc.setSpeed(source.speedValue());
        disc.setGlide(source.glideValue());
        disc.setTurn(source.turnValue());
        disc.setFade(source.fadeValue());
        disc.setStabilityLabel(source.stability());
        disc.setNameSlug(source.name_slug());
        disc.setBrandSlug(source.brand_slug());
        disc.setImageUrl(source.pic());
        disc.setSourceUrl(source.link());
        disc.setActive(true);
        disc.setContentHash(DiscContentHash.of(disc));
    }
}
