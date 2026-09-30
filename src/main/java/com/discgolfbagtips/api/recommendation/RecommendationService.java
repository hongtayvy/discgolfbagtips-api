package com.discgolfbagtips.api.recommendation;

import com.discgolfbagtips.api.analysis.BagAnalysis;
import com.discgolfbagtips.api.analysis.BagAnalyzer;
import com.discgolfbagtips.api.analysis.BagDisc;
import com.discgolfbagtips.api.analysis.BagGap;
import com.discgolfbagtips.api.common.ApiException;
import com.discgolfbagtips.api.config.BagTipsProperties;
import com.discgolfbagtips.api.generation.ChatModelClient;
import com.discgolfbagtips.api.generation.GeneratedRecommendation;
import com.discgolfbagtips.api.generation.GenerationService;
import com.discgolfbagtips.api.recommendation.dto.AlternativeDisc;
import com.discgolfbagtips.api.recommendation.dto.BagAnalysisRequest;
import com.discgolfbagtips.api.recommendation.dto.BagAnalysisSummary;
import com.discgolfbagtips.api.recommendation.dto.Explainability;
import com.discgolfbagtips.api.recommendation.dto.RecommendationResponse;
import com.discgolfbagtips.api.recommendation.dto.RecommendedDisc;
import com.discgolfbagtips.api.retrieval.RetrievalResult;
import com.discgolfbagtips.api.retrieval.RetrievalService;
import com.discgolfbagtips.api.retrieval.RetrievedDisc;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * The orchestration layer named in the design: controller in, three sub-layers, assembled response
 * out. It owns none of the AI logic itself — it decides <em>what</em> the pipeline does with the
 * gap analysis, and each sub-layer owns <em>how</em>.
 */
@Service
public class RecommendationService {

    private final BagResolver bagResolver;
    private final BagAnalyzer bagAnalyzer;
    private final RetrievalService retrievalService;
    private final GenerationService generationService;
    private final ExplainabilityAssembler explainabilityAssembler;
    private final ChatModelClient chatModelClient;
    private final BagTipsProperties.Retrieval retrievalConfig;

    public RecommendationService(BagResolver bagResolver, BagAnalyzer bagAnalyzer,
            RetrievalService retrievalService, GenerationService generationService,
            ExplainabilityAssembler explainabilityAssembler, ChatModelClient chatModelClient,
            BagTipsProperties properties) {
        this.bagResolver = bagResolver;
        this.bagAnalyzer = bagAnalyzer;
        this.retrievalService = retrievalService;
        this.generationService = generationService;
        this.explainabilityAssembler = explainabilityAssembler;
        this.chatModelClient = chatModelClient;
        this.retrievalConfig = properties.retrieval();
    }

    public RecommendationResponse recommend(BagAnalysisRequest request, String sessionId) {
        long startedAt = System.nanoTime();

        // Sub-layer 0: resolve the request against our own catalog.
        long analyzeStart = System.nanoTime();
        BagResolver.ResolvedBag resolved = bagResolver.resolve(request.bagOrEmpty());
        BagAnalysis analysis = bagAnalyzer.analyze(resolved.bag(), request.profile(),
                request.conditions().weather(), resolved.unresolved());
        long analyzeMs = elapsedMs(analyzeStart);

        BagGap gap = analysis.primaryGap();
        if (gap == null) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "no-gap-found",
                    "The bag could not be analysed into a recommendable gap.");
        }

        // Sub-layers 1 and 2: embed the gap, then retrieve the closest discs.
        RetrievalResult retrieval = retrievalService.retrieve(gap, analysis, request.profile(),
                request.conditions().weather(), request.filtersOrNone().toRetrievalFilters());
        if (retrieval.candidates().isEmpty()) {
            // Distinguish "your filter matched nothing" from "the catalog is not ready" — the first is
            // a 422 the caller can fix by relaxing the filter, the second is our problem.
            if (!request.filtersOrNone().toRetrievalFilters().isEmpty()) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "no-match-for-filters",
                        "No disc matches the requested filters (%s) for the %s / %s gap. Try relaxing them."
                                .formatted(request.filtersOrNone().toRetrievalFilters().describe(),
                                        gap.slot().label(), gap.stabilityClass().label()));
            }
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "catalog-not-ready",
                    "No candidate discs are available yet. The catalog sync or embedding backfill may "
                            + "still be running.");
        }

        // Sub-layer 3: reason over the retrieved discs.
        GeneratedRecommendation generated = generationService.generate(analysis, gap, request.profile(),
                request.conditions().weather(), retrieval.candidates());

        RetrievedDisc chosen = retrieval.candidates().stream()
                .filter(candidate -> candidate.discId().equals(generated.discId()))
                .findFirst()
                .orElseGet(() -> retrieval.candidates().getFirst());

        long totalMs = elapsedMs(startedAt);
        Explainability explainability = explainabilityAssembler.assemble(analysis, gap, retrieval, generated,
                chatModelClient.provider(), analyzeMs, totalMs);

        return new RecommendationResponse(
                sessionId,
                Instant.now(),
                toRecommendedDisc(chosen, generated),
                alternatives(retrieval.candidates(), chosen, gap),
                toSummary(analysis),
                explainability);
    }

    private RecommendedDisc toRecommendedDisc(RetrievedDisc disc, GeneratedRecommendation generated) {
        return new RecommendedDisc(
                disc.discId(),
                disc.name(),
                disc.brand(),
                disc.category(),
                disc.speed(),
                disc.glide(),
                disc.turn(),
                disc.fade(),
                disc.stabilityClass().label(),
                disc.slot().name(),
                disc.imageUrl(),
                disc.sourceUrl(),
                generated.headline(),
                generated.summary(),
                generated.reasoning(),
                generated.whenToThrowIt(),
                generated.plasticAdvice(),
                generated.weightAdvice(),
                generated.suggestedWeightRange(),
                generated.confidence());
    }

    private List<AlternativeDisc> alternatives(List<RetrievedDisc> candidates, RetrievedDisc chosen, BagGap gap) {
        List<AlternativeDisc> alternatives = new ArrayList<>();
        for (RetrievedDisc candidate : candidates) {
            if (candidate.discId().equals(chosen.discId())) {
                continue;
            }
            alternatives.add(new AlternativeDisc(
                    candidate.discId(),
                    candidate.name(),
                    candidate.brand(),
                    candidate.speed(),
                    candidate.glide(),
                    candidate.turn(),
                    candidate.fade(),
                    candidate.stabilityClass().label(),
                    candidate.imageUrl(),
                    candidate.similarity(),
                    candidate.rank(),
                    "Retrieved at rank %d for the %s / %s gap; flies %s against a %s target."
                            .formatted(candidate.rank(), gap.slot().label(), gap.stabilityClass().label(),
                                    candidate.flight().format(), gap.target().format())));
            if (alternatives.size() >= retrievalConfig.alternativeCount()) {
                break;
            }
        }
        return List.copyOf(alternatives);
    }

    private BagAnalysisSummary toSummary(BagAnalysis analysis) {
        List<BagAnalysisSummary.ResolvedBagDisc> resolved = new ArrayList<>();
        for (BagDisc bagDisc : analysis.bag()) {
            var breakdown = bagDisc.stabilityBreakdown();
            resolved.add(new BagAnalysisSummary.ResolvedBagDisc(
                    bagDisc.disc().id(),
                    bagDisc.disc().name(),
                    bagDisc.disc().brand(),
                    bagDisc.plastic() == null ? bagDisc.requestedPlastic() : bagDisc.plastic().name(),
                    bagDisc.weightGrams(),
                    bagDisc.weightClass(),
                    bagDisc.wear().label(),
                    bagDisc.disc().speed(),
                    bagDisc.disc().glide(),
                    bagDisc.disc().turn(),
                    bagDisc.disc().fade(),
                    bagDisc.slot().name(),
                    breakdown.effectiveClass().label(),
                    breakdown.publishedClass().label(),
                    breakdown.plastic(),
                    breakdown.weight(),
                    breakdown.wear(),
                    breakdown.effective(),
                    breakdown.explain()));
        }
        return new BagAnalysisSummary(
                analysis.bag().size(),
                analysis.coverageScore(),
                analysis.coverage(),
                analysis.notes(),
                analysis.unresolved(),
                List.copyOf(resolved),
                analysis.redundancies().stream()
                        .map(com.discgolfbagtips.api.recommendation.dto.RedundancyReport::from).toList());
    }

    private static long elapsedMs(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000;
    }
}
