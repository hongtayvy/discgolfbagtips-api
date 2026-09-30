package com.discgolfbagtips.api.retrieval;

import com.discgolfbagtips.api.analysis.BagAnalysis;
import com.discgolfbagtips.api.analysis.BagDisc;
import com.discgolfbagtips.api.analysis.BagGap;
import com.discgolfbagtips.api.common.ApiException;
import com.discgolfbagtips.api.config.BagTipsProperties;
import com.discgolfbagtips.api.embedding.EmbeddingService;
import com.discgolfbagtips.api.player.PlayerProfile;
import com.discgolfbagtips.api.player.WeatherCondition;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Sub-layer 2 of the RAG pipeline: given a gap, find the discs worth talking about.
 *
 * <p>Retrieval is hybrid on purpose. A speed window derived from the gap's slot (and capped by what
 * the player can actually throw) narrows the catalog structurally; cosine similarity over the
 * description passages ranks what is left. Pure vector search across 2,000+ molds happily returns a
 * speed-13 driver for a putter-shaped question.
 */
@Service
public class RetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalService.class);

    private final EmbeddingService embeddingService;
    private final DiscVectorStore vectorStore;
    private final BagTipsProperties.Retrieval config;

    public RetrievalService(EmbeddingService embeddingService, DiscVectorStore vectorStore,
            BagTipsProperties properties) {
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
        this.config = properties.retrieval();
    }

    public RetrievalResult retrieve(BagGap gap, BagAnalysis analysis, PlayerProfile profile,
            WeatherCondition weather) {
        return retrieve(gap, analysis, profile, weather, RetrievalFilters.none());
    }

    public RetrievalResult retrieve(BagGap gap, BagAnalysis analysis, PlayerProfile profile,
            WeatherCondition weather, RetrievalFilters filters) {

        long startedAt = System.nanoTime();
        double minSpeed = Math.max(0, gap.slot().minSpeed() - 0.5);
        double maxSpeed = Math.min(gap.slot().maxSpeed() + 0.5, profile.skillLevel().maxUsefulSpeed() + 1.0);
        Set<String> exclusions = exclusions(analysis);

        try {
            EmbeddingService.QueryEmbedding query = embeddingService.embedGap(gap, analysis, profile, weather);
            List<RetrievedDisc> raw =
                    vectorStore.search(query.vector(), minSpeed, maxSpeed, filters, config.overFetch());
            List<RetrievedDisc> candidates = shortlist(raw, exclusions, true);
            if (candidates.isEmpty()) {
                // A filter that excludes everything is the caller's choice, not a system failure, so
                // say so plainly rather than falling back and quietly ignoring what they asked for.
                if (filters.restrictsBrands() && raw.isEmpty()) {
                    return new RetrievalResult(RetrievalResult.RetrievalMode.VECTOR_SIMILARITY, query.text(),
                            embeddingService.model(), embeddingService.stubbed(), List.of(),
                            elapsedMs(startedAt),
                            "No disc matched the filter (%s) for this gap".formatted(filters.describe()),
                            filters.describe());
                }
                return flightNumberFallback(gap, analysis, minSpeed, maxSpeed, exclusions, startedAt,
                        "No embedded disc cleared the similarity floor for this gap", filters);
            }
            return new RetrievalResult(RetrievalResult.RetrievalMode.VECTOR_SIMILARITY, query.text(),
                    embeddingService.model(), embeddingService.stubbed(), candidates, elapsedMs(startedAt),
                    null, filters.describe());
        } catch (ApiException ex) {
            log.warn("Vector retrieval unavailable ({}); falling back to flight-number search", ex.getMessage());
            return flightNumberFallback(gap, analysis, minSpeed, maxSpeed, exclusions, startedAt,
                    ex.getMessage(), filters);
        }
    }

    private RetrievalResult flightNumberFallback(BagGap gap, BagAnalysis analysis, double minSpeed, double maxSpeed,
            Set<String> exclusions, long startedAt, String reason, RetrievalFilters filters) {

        List<RetrievedDisc> raw = vectorStore.searchByFlightNumbers(
                gap.target().speed(), gap.target().glide(), gap.target().turn(), gap.target().fade(),
                minSpeed, maxSpeed, filters, config.overFetch());
        List<RetrievedDisc> candidates = shortlist(raw, exclusions, false);
        String queryText = "Flight-number nearest neighbour to %s in the %s slot"
                .formatted(gap.target().format(), gap.slot().label());
        return new RetrievalResult(RetrievalResult.RetrievalMode.FLIGHT_NUMBER_FALLBACK, queryText,
                "flight-numbers", false, candidates, elapsedMs(startedAt), reason, filters.describe());
    }

    /** Drops discs the player already owns, applies the similarity floor, and re-ranks from 1. */
    private List<RetrievedDisc> shortlist(List<RetrievedDisc> raw, Set<String> exclusions, boolean applyFloor) {
        List<RetrievedDisc> shortlisted = new ArrayList<>();
        for (RetrievedDisc candidate : raw) {
            if (exclusions.contains(candidate.discId()) || exclusions.contains(moldKey(candidate.name()))) {
                continue;
            }
            if (applyFloor && candidate.similarity() < config.minimumSimilarity()) {
                continue;
            }
            shortlisted.add(candidate.withRank(shortlisted.size() + 1));
            if (shortlisted.size() >= config.candidateLimit()) {
                break;
            }
        }
        return List.copyOf(shortlisted);
    }

    /** Both the catalog id and the mold name, so "the Buzzz I already own" is excluded in any plastic. */
    private Set<String> exclusions(BagAnalysis analysis) {
        Set<String> exclusions = new HashSet<>();
        for (BagDisc bagDisc : analysis.bag()) {
            exclusions.add(bagDisc.disc().id());
            exclusions.add(moldKey(bagDisc.disc().name()));
        }
        return exclusions;
    }

    private static String moldKey(String name) {
        return "mold:" + (name == null ? "" : name.trim().toLowerCase(Locale.ROOT));
    }

    private static long elapsedMs(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000;
    }
}
