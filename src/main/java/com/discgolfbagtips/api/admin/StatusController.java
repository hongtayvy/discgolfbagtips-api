package com.discgolfbagtips.api.admin;

import com.discgolfbagtips.api.cache.AnalysisCache;
import com.discgolfbagtips.api.catalog.DiscCatalogService;
import com.discgolfbagtips.api.catalog.sync.DiscCatalogSyncService;
import com.discgolfbagtips.api.catalog.sync.SyncReport;
import com.discgolfbagtips.api.embedding.EmbeddingService;
import com.discgolfbagtips.api.generation.ChatModelClient;
import com.discgolfbagtips.api.retrieval.DiscVectorStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public pipeline status. The front end reads this to decide whether to warn that the demo is
 * running on the local vectorizer rather than the hosted models.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Status", description = "Pipeline readiness")
public class StatusController {

    private final DiscCatalogService catalogService;
    private final DiscCatalogSyncService syncService;
    private final EmbeddingService embeddingService;
    private final DiscVectorStore vectorStore;
    private final ChatModelClient chatModelClient;
    private final AnalysisCache analysisCache;

    public StatusController(DiscCatalogService catalogService, DiscCatalogSyncService syncService,
            EmbeddingService embeddingService, DiscVectorStore vectorStore, ChatModelClient chatModelClient,
            AnalysisCache analysisCache) {
        this.catalogService = catalogService;
        this.syncService = syncService;
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
        this.chatModelClient = chatModelClient;
        this.analysisCache = analysisCache;
    }

    @GetMapping("/status")
    @Operation(summary = "Catalog, embedding and generation readiness")
    public PipelineStatus status() {
        DiscVectorStore.VectorStoreStats stats = vectorStore.stats(embeddingService.model());
        return new PipelineStatus(
                catalogService.activeDiscCount(),
                syncService.lastReport(),
                new EmbeddingStatus(embeddingService.provider(), embeddingService.model(),
                        embeddingService.dimensions(), embeddingService.stubbed(), stats.embeddedDiscs(),
                        stats.coverage(), stats.lastEmbeddedAt() == null ? null
                                : stats.lastEmbeddedAt().toString()),
                new GenerationStatus(chatModelClient.provider(), chatModelClient.model(),
                        chatModelClient.available()),
                analysisCache.stats());
    }

    public record PipelineStatus(long activeDiscs, SyncReport lastCatalogSync, EmbeddingStatus embedding,
            GenerationStatus generation, AnalysisCache.Stats cache) {
    }

    public record EmbeddingStatus(String provider, String model, int dimensions, boolean stubbed,
            long embeddedDiscs, double coverage, String lastEmbeddedAt) {
    }

    public record GenerationStatus(String provider, String model, boolean configured) {
    }
}
