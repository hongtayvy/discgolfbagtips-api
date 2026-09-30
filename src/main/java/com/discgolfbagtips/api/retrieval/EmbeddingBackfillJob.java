package com.discgolfbagtips.api.retrieval;

import com.discgolfbagtips.api.catalog.Disc;
import com.discgolfbagtips.api.catalog.DiscRepository;
import com.discgolfbagtips.api.common.ApiException;
import com.discgolfbagtips.api.config.BagTipsProperties;
import com.discgolfbagtips.api.embedding.EmbeddingService;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Keeps the vector table in step with the catalog. Deliberately incremental: a free Hugging Face
 * account will not embed 2,400 passages in one go, and there is no reason to — a disc that gained a
 * vector yesterday does not need another one today.
 */
@Component
public class EmbeddingBackfillJob {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingBackfillJob.class);

    private final DiscRepository discRepository;
    private final EmbeddingService embeddingService;
    private final DiscVectorStore vectorStore;
    private final BagTipsProperties properties;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<BackfillProgress> progress;

    public EmbeddingBackfillJob(DiscRepository discRepository, EmbeddingService embeddingService,
            DiscVectorStore vectorStore, BagTipsProperties properties) {
        this.discRepository = discRepository;
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
        this.properties = properties;
        this.progress = new AtomicReference<>(BackfillProgress.idle(embeddingService.model()));
    }

    @Scheduled(cron = "${bagtips.embedding.backfill-cron:0 45 3 * * *}", zone = "UTC")
    public void scheduledBackfill() {
        backfill(properties.embedding().maxPerRun());
    }

    public BackfillProgress progress() {
        return progress.get();
    }

    /**
     * Claims the single backfill slot. Separate from {@link #runFullBackfill()} so the caller learns
     * immediately whether it won the race, rather than discovering it inside an async method whose
     * result nobody is waiting on.
     */
    public boolean tryClaim() {
        if (!properties.embedding().backfillEnabled()) {
            return false;
        }
        if (!running.compareAndSet(false, true)) {
            return false;
        }
        // Publish RUNNING here rather than inside the async method: the caller reads progress
        // immediately after dispatch and would otherwise be told IDLE for a job it just started.
        progress.set(BackfillProgress.claimed(embeddingService.model()));
        return true;
    }

    /**
     * Embeds every disc still lacking a current vector, batch after batch, until none are left.
     *
     * <p>Runs asynchronously because embedding the whole catalog outlasts any sensible HTTP timeout;
     * poll {@link #progress()} instead of waiting. Safe to re-run after a failure: what has already
     * been embedded is detected by content hash and skipped, so a rate-limited or interrupted run
     * resumes where it stopped rather than starting over.
     */
    @Async
    public void runFullBackfill() {
        String model = embeddingService.model();
        try {
            int remaining = vectorStore.findDiscIdsNeedingEmbedding(model, Integer.MAX_VALUE).size();
            progress.set(BackfillProgress.starting(model, remaining));
            log.info("Full embedding backfill starting: {} disc(s) need a vector from {}", remaining, model);

            long throttleMs = properties.embedding().backfillThrottle().toMillis();
            int chunk = Math.max(1, properties.embedding().maxPerRun());

            while (true) {
                List<String> pending = vectorStore.findDiscIdsNeedingEmbedding(model, chunk);
                if (pending.isEmpty()) {
                    break;
                }
                List<Disc> discs = discRepository.findAllByIdInAndActiveTrue(pending);
                if (discs.isEmpty()) {
                    break;
                }
                List<EmbeddingService.DiscEmbedding> embeddings = embeddingService.embedDiscs(discs, null);
                for (int i = 0; i < embeddings.size(); i++) {
                    vectorStore.upsert(embeddings.get(i), discs.get(i).contentHash());
                }
                int stillLeft = vectorStore.findDiscIdsNeedingEmbedding(model, Integer.MAX_VALUE).size();
                progress.updateAndGet(current -> current.advanced(embeddings.size(), stillLeft));
                log.info("Backfill progress: {} embedded, {} remaining", progress.get().embedded(), stillLeft);

                if (throttleMs > 0 && stillLeft > 0) {
                    Thread.sleep(java.time.Duration.ofMillis(throttleMs));
                }
            }
            progress.updateAndGet(BackfillProgress::completed);
            log.info("Full embedding backfill complete: {} disc(s) in {} ms",
                    progress.get().embedded(), progress.get().elapsedMs());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            progress.updateAndGet(current -> current.failed("Interrupted", current.remaining()));
        } catch (RuntimeException ex) {
            log.warn("Full embedding backfill stopped: {}", ex.getMessage());
            int left = safeRemaining(model);
            progress.updateAndGet(current -> current.failed(ex.getMessage(), left));
        } finally {
            running.set(false);
        }
    }

    private int safeRemaining(String model) {
        try {
            return vectorStore.findDiscIdsNeedingEmbedding(model, Integer.MAX_VALUE).size();
        } catch (RuntimeException ex) {
            return -1;
        }
    }

    public BackfillReport backfill(int limit) {
        if (!properties.embedding().backfillEnabled()) {
            return new BackfillReport(0, 0, "Backfill is disabled (bagtips.embedding.backfill-enabled=false)");
        }
        int capped = Math.clamp(limit, 1, properties.embedding().maxPerRun());
        List<String> pending = vectorStore.findDiscIdsNeedingEmbedding(embeddingService.model(), capped);
        if (pending.isEmpty()) {
            return new BackfillReport(0, 0, "Every active disc already has a current embedding");
        }

        List<Disc> discs = discRepository.findAllByIdInAndActiveTrue(pending);
        int embedded = 0;
        int failed = 0;
        try {
            // The stored passage is plastic-agnostic: a mold gets one vector, and the plastic the
            // player names is applied at analysis time rather than multiplying the index by blend.
            List<EmbeddingService.DiscEmbedding> embeddings = embeddingService.embedDiscs(discs, null);
            for (int i = 0; i < embeddings.size(); i++) {
                vectorStore.upsert(embeddings.get(i), discs.get(i).contentHash());
                embedded++;
            }
        } catch (ApiException ex) {
            failed = discs.size() - embedded;
            log.warn("Embedding backfill stopped after {} disc(s): {}", embedded, ex.getMessage());
            return new BackfillReport(embedded, failed, "Stopped early: " + ex.getMessage());
        }
        log.info("Embedded {} disc(s) with model {}", embedded, embeddingService.model());
        return new BackfillReport(embedded, failed, "Backfill complete");
    }

    public record BackfillReport(int embedded, int failed, String message) {
    }
}
