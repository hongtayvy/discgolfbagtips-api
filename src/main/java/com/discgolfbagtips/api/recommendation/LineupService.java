package com.discgolfbagtips.api.recommendation;

import com.discgolfbagtips.api.analysis.BagAnalysis;
import com.discgolfbagtips.api.analysis.BagAnalyzer;
import com.discgolfbagtips.api.analysis.BagDisc;
import com.discgolfbagtips.api.analysis.BagGap;
import com.discgolfbagtips.api.analysis.FlightProfile;
import com.discgolfbagtips.api.bag.BagFit;
import com.discgolfbagtips.api.bag.BagFittingService;
import com.discgolfbagtips.api.bag.BagModel;
import com.discgolfbagtips.api.bag.BagSummary;
import com.discgolfbagtips.api.bag.CarryWeight;
import com.discgolfbagtips.api.bag.CarryWeightCalculator;
import com.discgolfbagtips.api.catalog.DiscSlot;
import com.discgolfbagtips.api.catalog.StabilityClass;
import com.discgolfbagtips.api.config.BagTipsProperties;
import com.discgolfbagtips.api.player.PlayerProfile;
import com.discgolfbagtips.api.player.WeatherCondition;
import com.discgolfbagtips.api.recommendation.dto.BagAnalysisSummary;
import com.discgolfbagtips.api.recommendation.dto.BagLineupResponse;
import com.discgolfbagtips.api.recommendation.dto.Explainability;
import com.discgolfbagtips.api.recommendation.dto.SlotLineup;
import com.discgolfbagtips.api.retrieval.RetrievalResult;
import com.discgolfbagtips.api.retrieval.RetrievalService;
import com.discgolfbagtips.api.retrieval.RetrievedDisc;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Builds the slot-by-slot view of a bag.
 *
 * <p>Where {@link RecommendationService} answers "what should I buy next" with one disc and a
 * written case for it, this answers "how complete is my bag" across all four speed slots at once.
 * Same analysis and same retrieval underneath; different question, so a different shape.
 */
@Service
public class LineupService {

    /** Gaps below this barely register against a coverage grid, and each one costs a retrieval. */
    private static final double SEVERITY_FLOOR = 0.2;
    /** Enough choice per hole to be useful without turning one request into thirty searches. */
    private static final int SUGGESTIONS_PER_GAP = 3;
    private static final int MAX_RETRIEVALS = 10;

    private final BagResolver bagResolver;
    private final BagAnalyzer bagAnalyzer;
    private final RetrievalService retrievalService;
    private final com.discgolfbagtips.api.embedding.EmbeddingService embeddingService;
    private final BagFittingService bagFittingService;
    private final CarryWeightCalculator carryWeightCalculator;

    public LineupService(BagResolver bagResolver, BagAnalyzer bagAnalyzer, RetrievalService retrievalService,
            com.discgolfbagtips.api.embedding.EmbeddingService embeddingService,
            BagFittingService bagFittingService, CarryWeightCalculator carryWeightCalculator,
            BagTipsProperties properties) {
        this.bagResolver = bagResolver;
        this.bagAnalyzer = bagAnalyzer;
        this.retrievalService = retrievalService;
        this.embeddingService = embeddingService;
        this.bagFittingService = bagFittingService;
        this.carryWeightCalculator = carryWeightCalculator;
    }

    public BagLineupResponse buildLineup(com.discgolfbagtips.api.recommendation.dto.BagAnalysisRequest request,
            String sessionId) {

        long startedAt = System.nanoTime();
        BagResolver.ResolvedBag resolved = bagResolver.resolve(request.bagOrEmpty());
        PlayerProfile profile = request.profile();
        WeatherCondition weather = request.conditions().weather();
        BagAnalysis analysis = bagAnalyzer.analyze(resolved.bag(), profile, weather, resolved.unresolved());

        // Retrieval is the expensive part, so spend it on the gaps that matter and stop.
        List<BagGap> worthRetrieving = analysis.allGaps().stream()
                .filter(gap -> gap.severity() >= SEVERITY_FLOOR)
                .limit(MAX_RETRIEVALS)
                .toList();

        Set<String> degradations = new LinkedHashSet<>();
        String retrievalMode = null;
        List<SlotLineup> slots = new ArrayList<>();

        for (DiscSlot slot : DiscSlot.values()) {
            List<BagDisc> inSlot = analysis.bagForSlot(slot);
            List<SlotLineup.SlotGap> slotGaps = new ArrayList<>();

            for (BagGap gap : analysis.gapsForSlot(slot)) {
                List<SlotLineup.SuggestedDisc> suggestions = List.of();
                if (worthRetrieving.contains(gap)) {
                    RetrievalResult result = retrievalService.retrieve(gap, analysis, profile, weather,
                            request.filtersOrNone().toRetrievalFilters());
                    retrievalMode = retrievalMode == null ? result.mode().name() : retrievalMode;
                    if (result.degraded()) {
                        degradations.add("retrieval: fell back to flight-number search (%s)"
                                .formatted(result.degradedReason()));
                    }
                    if (result.stubbedEmbedding()) {
                        degradations.add("embedding: vectors came from the local hashing vectorizer");
                    }
                    suggestions = toSuggestions(result.candidates(), gap.target());
                }
                slotGaps.add(new SlotLineup.SlotGap(
                        gap.stabilityClass().name(),
                        gap.stabilityClass().label(),
                        gap.stabilityClass().behaviour(),
                        gap.severity(),
                        gap.reason(),
                        flight(gap.target()),
                        gap.targetWeight().format(),
                        suggestions));
            }

            slots.add(buildSlot(slot, inSlot, slotGaps, analysis));
        }

        // The physical bag: what it weighs loaded, and whether anything holds the lineup better.
        BagModel carried = resolveCarriedBag(request);
        CarryWeight carryWeight = carryWeightCalculator.calculate(analysis.bag(), carried);
        List<BagFit> betterFitting = suggestBags(analysis, carried, carryWeight);

        long totalMs = (System.nanoTime() - startedAt) / 1_000_000;
        return new BagLineupResponse(
                sessionId,
                Instant.now(),
                analysis.coverageScore(),
                analysis.bag().size(),
                List.copyOf(slots),
                carried == null ? null : BagSummary.from(carried),
                carryWeight,
                betterFitting,
                analysis.notes(),
                analysis.unresolved(),
                new BagLineupResponse.LineupExplainability(
                        retrievalMode == null ? "NONE" : retrievalMode,
                        request.filtersOrNone().toRetrievalFilters().describe(),
                        new Explainability.EmbeddingInfo(embeddingService.provider(), embeddingService.model(),
                                embeddingService.dimensions(), embeddingService.stubbed()),
                        analysis.allGaps().size(),
                        worthRetrieving.size(),
                        List.copyOf(degradations),
                        totalMs));
    }

    private BagModel resolveCarriedBag(
            com.discgolfbagtips.api.recommendation.dto.BagAnalysisRequest request) {
        var carried = request.carriedBagOrNull();
        if (carried == null) {
            return null;
        }
        if (carried.bagModelId() != null) {
            return bagFittingService.byId(carried.bagModelId()).orElse(null);
        }
        return bagFittingService.resolve(carried.brand(), carried.model()).orElse(null);
    }

    /**
     * Only suggests alternatives when there is a reason to: the current bag is overpacked, or the
     * player named no bag at all. Offering a shopping list to someone whose bag already fits is
     * noise, and this view is meant to be read at a glance.
     */
    private List<BagFit> suggestBags(BagAnalysis analysis, BagModel carried, CarryWeight carryWeight) {
        if (analysis.bag().isEmpty()) {
            return List.of();
        }
        boolean worthSuggesting = carried == null || carryWeight.overpacked();
        if (!worthSuggesting) {
            return List.of();
        }
        return bagFittingService.fitting(analysis.bag().size(), carryWeight.discWeightGrams(),
                null, null, carried);
    }

    private SlotLineup buildSlot(DiscSlot slot, List<BagDisc> inSlot, List<SlotLineup.SlotGap> gaps,
            BagAnalysis analysis) {

        SlotLineup.Status status = inSlot.isEmpty()
                ? SlotLineup.Status.EMPTY
                : gaps.isEmpty() ? SlotLineup.Status.COMPLETE : SlotLineup.Status.THIN;

        List<SlotLineup.StabilityCell> cells = new ArrayList<>();
        for (StabilityClass stability : StabilityClass.values()) {
            int count = analysis.coverage().getOrDefault(slot.name() + "/" + stability.name(), 0);
            cells.add(new SlotLineup.StabilityCell(stability.name(), stability.label(), count, count > 0));
        }

        return new SlotLineup(
                slot.name(),
                slot.label(),
                slot.purpose(),
                slot.minSpeed(),
                slot.maxSpeed(),
                status,
                inSlot.size(),
                inSlot.stream().map(this::toResolved).toList(),
                List.copyOf(cells),
                List.copyOf(gaps),
                analysis.redundanciesForSlot(slot).stream()
                        .map(com.discgolfbagtips.api.recommendation.dto.RedundancyReport::from).toList(),
                summarise(slot, status, inSlot, analysis.redundanciesForSlot(slot), gaps));
    }

    /** Deterministic prose — no language model is involved in this view. */
    private String summarise(DiscSlot slot, SlotLineup.Status status, List<BagDisc> inSlot,
            List<com.discgolfbagtips.api.analysis.BagRedundancy> redundancies,
            List<SlotLineup.SlotGap> gaps) {

        String base = switch (status) {
            case EMPTY -> "Nothing in the bag covers the %s slot, which is where %s."
                    .formatted(slot.label(), slot.purpose());
            case COMPLETE -> "%d disc%s covering this slot, with no stability class missing."
                    .formatted(inSlot.size(), inSlot.size() == 1 ? "" : "s");
            case THIN -> "%d disc%s here, but no %s option for %s."
                    .formatted(inSlot.size(), inSlot.size() == 1 ? "" : "s",
                            gaps.getFirst().label(), slot.purpose());
        };
        // A slot can be simultaneously short of a stability class and carrying two of another.
        long loud = redundancies.stream()
                .filter(r -> r.level() != com.discgolfbagtips.api.analysis.BagRedundancy.Level.FYI).count();
        return loud == 0 ? base
                : base + " %d disc%s here overlap%s enough to free a slot.".formatted(
                        loud, loud == 1 ? "" : "s", loud == 1 ? "s" : "");
    }

    private List<SlotLineup.SuggestedDisc> toSuggestions(List<RetrievedDisc> candidates, FlightProfile target) {
        List<SlotLineup.SuggestedDisc> suggestions = new ArrayList<>();
        for (RetrievedDisc candidate : candidates) {
            FlightProfile delta = candidate.flight().minus(target);
            suggestions.add(new SlotLineup.SuggestedDisc(
                    candidate.discId(), candidate.name(), candidate.brand(),
                    candidate.speed(), candidate.glide(), candidate.turn(), candidate.fade(),
                    candidate.stabilityClass().label(), candidate.imageUrl(), candidate.similarity(),
                    new Explainability.FlightDelta(delta.speed(), delta.glide(), delta.turn(), delta.fade())));
            if (suggestions.size() >= SUGGESTIONS_PER_GAP) {
                break;
            }
        }
        return List.copyOf(suggestions);
    }

    private BagAnalysisSummary.ResolvedBagDisc toResolved(BagDisc bagDisc) {
        var breakdown = bagDisc.stabilityBreakdown();
        return new BagAnalysisSummary.ResolvedBagDisc(
                bagDisc.disc().id(), bagDisc.disc().name(), bagDisc.disc().brand(),
                bagDisc.plastic() == null ? bagDisc.requestedPlastic() : bagDisc.plastic().name(),
                bagDisc.weightGrams(), bagDisc.weightClass(), bagDisc.wear().label(),
                bagDisc.disc().speed(), bagDisc.disc().glide(), bagDisc.disc().turn(), bagDisc.disc().fade(),
                bagDisc.slot().name(), breakdown.effectiveClass().label(), breakdown.publishedClass().label(),
                breakdown.plastic(), breakdown.weight(), breakdown.wear(), breakdown.effective(),
                breakdown.explain());
    }

    private Explainability.FlightNumbers flight(FlightProfile profile) {
        return new Explainability.FlightNumbers(profile.speed(), profile.glide(), profile.turn(), profile.fade());
    }
}
