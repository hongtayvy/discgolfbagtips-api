package com.discgolfbagtips.api.admin;

import com.discgolfbagtips.api.catalog.sync.DiscCatalogSyncService;
import com.discgolfbagtips.api.catalog.sync.SyncReport;
import com.discgolfbagtips.api.common.ApiException;
import com.discgolfbagtips.api.config.BagTipsProperties;
import com.discgolfbagtips.api.embedding.EmbeddingService;
import com.discgolfbagtips.api.retrieval.BackfillProgress;
import com.discgolfbagtips.api.retrieval.DiscVectorStore;
import com.discgolfbagtips.api.retrieval.EmbeddingBackfillJob;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manual triggers for the two scheduled pipelines, so a deploy does not have to wait for 03:15.
 * Gated on a shared token; with no token configured the endpoints are simply off.
 */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin", description = "Operational triggers, gated on the X-Admin-Token header")
public class AdminController {

    private final DiscCatalogSyncService syncService;
    private final EmbeddingBackfillJob backfillJob;
    private final DiscVectorStore vectorStore;
    private final EmbeddingService embeddingService;
    private final BagTipsProperties properties;

    public AdminController(DiscCatalogSyncService syncService, EmbeddingBackfillJob backfillJob,
            DiscVectorStore vectorStore, EmbeddingService embeddingService, BagTipsProperties properties) {
        this.syncService = syncService;
        this.backfillJob = backfillJob;
        this.vectorStore = vectorStore;
        this.embeddingService = embeddingService;
        this.properties = properties;
    }

    @PostMapping("/catalog/sync")
    @Operation(summary = "Run the DiscIt catalog sync now")
    public SyncReport sync(@RequestHeader(name = "X-Admin-Token", required = false) String token) {
        authorize(token);
        return syncService.sync();
    }

    @PostMapping("/embeddings/backfill")
    @Operation(summary = "Embed the discs whose vectors are missing or stale")
    public EmbeddingBackfillJob.BackfillReport backfill(
            @RequestHeader(name = "X-Admin-Token", required = false) String token,
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        authorize(token);
        return backfillJob.backfill(limit);
    }

    @PostMapping("/embeddings/backfill/all")
    @Operation(summary = "Embed the entire catalog, resuming until nothing is left",
            description = "Returns immediately with 202; embedding the whole catalog outlasts any "
                    + "sensible request timeout. Poll GET /admin/embeddings/progress. Safe to re-run: "
                    + "already-embedded discs are detected by content hash and skipped.")
    public ResponseEntity<BackfillProgress> backfillAll(
            @RequestHeader(name = "X-Admin-Token", required = false) String token) {
        authorize(token);
        if (!backfillJob.tryClaim()) {
            // Either one is already running, or backfill is switched off entirely.
            return ResponseEntity.status(HttpStatus.CONFLICT).body(backfillJob.progress());
        }
        backfillJob.runFullBackfill();
        return ResponseEntity.accepted().body(backfillJob.progress());
    }

    @GetMapping("/embeddings/progress")
    @Operation(summary = "Progress of the running or most recent full backfill")
    public BackfillProgress progress(
            @RequestHeader(name = "X-Admin-Token", required = false) String token) {
        authorize(token);
        return backfillJob.progress();
    }

    @PostMapping("/embeddings/stats")
    @Operation(summary = "Vector coverage for the active embedding model")
    public DiscVectorStore.VectorStoreStats stats(
            @RequestHeader(name = "X-Admin-Token", required = false) String token) {
        authorize(token);
        return vectorStore.stats(embeddingService.model());
    }

    private void authorize(String token) {
        String expected = properties.adminToken();
        if (expected == null || expected.isBlank()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "admin-disabled",
                    "Admin endpoints are disabled because bagtips.admin-token is not set.");
        }
        if (token == null || !constantTimeEquals(token, expected)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "admin-forbidden", "Invalid admin token.");
        }
    }

    private boolean constantTimeEquals(String provided, String expected) {
        return MessageDigest.isEqual(provided.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8));
    }
}
