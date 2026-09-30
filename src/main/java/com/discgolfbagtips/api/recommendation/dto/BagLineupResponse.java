package com.discgolfbagtips.api.recommendation.dto;

import com.discgolfbagtips.api.bag.BagFit;
import com.discgolfbagtips.api.bag.BagSummary;
import com.discgolfbagtips.api.bag.CarryWeight;
import java.time.Instant;
import java.util.List;

/**
 * The whole bag laid out slot by slot, rather than a single "buy this next" answer.
 *
 * <p>Deliberately does not call the reasoning model. This view's value is structural — what is
 * covered, what is missing, what would fill it — and answering it needs one retrieval per gap
 * rather than one per request. Adding a language model would multiply cost and latency by roughly
 * the number of gaps to produce prose the grid already conveys. The narrative belongs on
 * {@code POST /recommendations}, which answers one question well.
 *
 * @param coverageScore 0-100 across the whole bag
 * @param slots         always all four slots, in throwing order, so the UI can render stable tabs
 */
public record BagLineupResponse(
        String sessionId,
        Instant generatedAt,
        int coverageScore,
        int discCount,
        List<SlotLineup> slots,
        /* Null unless the request named a bag the catalog could resolve. */
        BagSummary carriedBag,
        CarryWeight carryWeight,
        /* Bags that would hold this lineup; empty when the current bag already fits comfortably. */
        List<BagFit> betterFittingBags,
        List<String> notes,
        List<String> unresolvedEntries,
        LineupExplainability explainability) {

    /**
     * @param retrievalMode  VECTOR_SIMILARITY or FLIGHT_NUMBER_FALLBACK, as for a recommendation
     * @param gapsRetrieved  how many gaps actually ran a retrieval, after the severity cut-off
     */
    public record LineupExplainability(
            String retrievalMode,
            String appliedFilters,
            Explainability.EmbeddingInfo embedding,
            int gapsConsidered,
            int gapsRetrieved,
            List<String> degradations,
            long totalMs) {
    }
}
