package com.discgolfbagtips.api.recommendation;

import com.discgolfbagtips.api.analysis.BagAnalysis;
import com.discgolfbagtips.api.analysis.BagDisc;
import com.discgolfbagtips.api.analysis.BagGap;
import com.discgolfbagtips.api.analysis.FlightProfile;
import com.discgolfbagtips.api.embedding.EmbeddingService;
import com.discgolfbagtips.api.generation.GeneratedRecommendation;
import com.discgolfbagtips.api.recommendation.dto.Explainability;
import com.discgolfbagtips.api.retrieval.RetrievalResult;
import com.discgolfbagtips.api.retrieval.RetrievedDisc;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Builds the audit trail. Every claim here is derived from something the pipeline actually did:
 * the cosine similarities pgvector returned, the descriptor phrases shared by the query and the
 * matched passage, and the flight-number arithmetic the analyzer performed.
 */
@Component
public class ExplainabilityAssembler {

    private static final int EXCERPT_CHARS = 280;

    private final EmbeddingService embeddingService;

    public ExplainabilityAssembler(EmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    public Explainability assemble(BagAnalysis analysis, BagGap selectedGap, RetrievalResult retrieval,
            GeneratedRecommendation generated, String generationProvider, long analyzeMs, long totalMs) {

        List<Explainability.VectorMatch> matches = new ArrayList<>();
        for (RetrievedDisc candidate : retrieval.candidates()) {
            matches.add(new Explainability.VectorMatch(
                    candidate.rank(),
                    candidate.discId(),
                    candidate.name(),
                    candidate.brand(),
                    candidate.similarity(),
                    candidate.distance(),
                    matchedDescriptors(candidate, retrieval.queryText()),
                    delta(selectedGap.target(), candidate.flight()),
                    candidate.passageExcerpt(EXCERPT_CHARS),
                    candidate.discId().equals(generated.discId())));
        }

        List<Explainability.FlightGapExplanation> gaps = new ArrayList<>();
        for (BagGap gap : analysis.gaps()) {
            gaps.add(new Explainability.FlightGapExplanation(
                    gap.slot().name(),
                    gap.stabilityClass().name(),
                    gap.kind().name(),
                    gap.severity(),
                    gap.reason(),
                    flight(gap.target()),
                    gap.targetWeight().format(),
                    gap.targetWeight().rationale(),
                    nearest(gap.nearestInBag()),
                    gap.delta() == null ? null : new Explainability.FlightDelta(
                            gap.delta().speed(), gap.delta().glide(), gap.delta().turn(), gap.delta().fade()),
                    gap == selectedGap));
        }

        List<String> degradations = new ArrayList<>();
        if (retrieval.degraded()) {
            degradations.add("retrieval: fell back to flight-number nearest neighbour (%s)"
                    .formatted(retrieval.degradedReason()));
        }
        if (retrieval.stubbedEmbedding()) {
            degradations.add("embedding: vectors came from the local hashing vectorizer, not a hosted model");
        }
        if (!generated.fromModel()) {
            degradations.add("generation: explanation written by the deterministic fallback");
        }
        if (generated.note() != null && !generated.note().isBlank()) {
            degradations.add("generation: " + generated.note());
        }

        return new Explainability(
                retrieval.mode().name(),
                retrieval.queryText(),
                retrieval.appliedFilters(),
                new Explainability.EmbeddingInfo(embeddingService.provider(), retrieval.model(),
                        embeddingService.dimensions(), retrieval.stubbedEmbedding()),
                new Explainability.GenerationInfo(generationProvider, generated.model(), generated.source().name(),
                        generated.note(), !generated.fromModel(), generated.transientFailure()),
                List.copyOf(matches),
                List.copyOf(gaps),
                citations(selectedGap, matches, generated),
                new Explainability.Timings(analyzeMs, retrieval.latencyMs(), generated.latencyMs(), totalMs),
                List.copyOf(degradations));
    }

    /**
     * The specific reasons a match is a match: descriptor phrases the candidate's passage and the
     * generated query have in common.
     */
    private List<String> matchedDescriptors(RetrievedDisc candidate, String queryText) {
        if (candidate.descriptors().isEmpty() || queryText == null) {
            return List.of();
        }
        String haystack = queryText.toLowerCase(Locale.ROOT);
        return candidate.descriptors().stream()
                .filter(descriptor -> haystack.contains(descriptor.toLowerCase(Locale.ROOT)))
                .toList();
    }

    private List<String> citations(BagGap gap, List<Explainability.VectorMatch> matches,
            GeneratedRecommendation generated) {

        List<String> citations = new ArrayList<>();
        citations.add("flight-gap:%s/%s severity %.2f — %s".formatted(gap.slot().name(),
                gap.stabilityClass().name(), gap.severity(), gap.reason()));
        if (gap.nearestInBag() != null && gap.delta() != null) {
            citations.add("flight-delta:%s vs %s = %s".formatted(gap.target().format(),
                    gap.nearestInBag().label(), gap.delta().format()));
        }
        for (Explainability.VectorMatch match : matches) {
            citations.add("vector-match#%d:%s %s — cosine similarity %.4f%s%s".formatted(
                    match.rank(),
                    match.brand(),
                    match.name(),
                    match.similarity(),
                    match.matchedDescriptors().isEmpty() ? ""
                            : " — shared terms: " + String.join(", ", match.matchedDescriptors()),
                    match.chosen() ? " [chosen]" : ""));
        }
        citations.add("generation:%s via %s".formatted(generated.source().name(), generated.model()));
        return List.copyOf(citations);
    }

    private Explainability.FlightDelta delta(FlightProfile target, FlightProfile actual) {
        FlightProfile difference = actual.minus(target);
        return new Explainability.FlightDelta(difference.speed(), difference.glide(), difference.turn(),
                difference.fade());
    }

    private Explainability.FlightNumbers flight(FlightProfile profile) {
        return new Explainability.FlightNumbers(profile.speed(), profile.glide(), profile.turn(), profile.fade());
    }

    private Explainability.NearestBagDisc nearest(BagDisc bagDisc) {
        if (bagDisc == null) {
            return null;
        }
        return new Explainability.NearestBagDisc(
                bagDisc.disc().id(),
                bagDisc.disc().name(),
                bagDisc.disc().brand(),
                bagDisc.plastic() == null ? bagDisc.requestedPlastic() : bagDisc.plastic().name(),
                bagDisc.weightGrams(),
                bagDisc.wear().label(),
                flight(bagDisc.flight()),
                bagDisc.effectiveStability().label(),
                bagDisc.stabilityBreakdown().explain());
    }
}
