package com.discgolfbagtips.api.retrieval;

import java.util.List;

/**
 * @param mode which path produced the candidates — the response says so rather than pretending
 *             every recommendation came from a vector search
 */
public record RetrievalResult(
        RetrievalMode mode,
        String queryText,
        String model,
        boolean stubbedEmbedding,
        List<RetrievedDisc> candidates,
        long latencyMs,
        String degradedReason,
        String appliedFilters) {

    public enum RetrievalMode {
        /** Cosine similarity over the embedded description passages. */
        VECTOR_SIMILARITY,
        /** Nearest neighbour in raw flight-number space; used when embeddings are unavailable. */
        FLIGHT_NUMBER_FALLBACK
    }

    public boolean degraded() {
        return mode == RetrievalMode.FLIGHT_NUMBER_FALLBACK;
    }
}
