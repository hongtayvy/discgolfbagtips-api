package com.discgolfbagtips.api.recommendation;

import com.discgolfbagtips.api.cache.AnalysisCache;
import com.discgolfbagtips.api.cache.AnalysisCacheKey;
import com.discgolfbagtips.api.common.NotFoundException;
import com.discgolfbagtips.api.recommendation.dto.BagAnalysisRequest;
import com.discgolfbagtips.api.recommendation.dto.BagLineupResponse;
import com.discgolfbagtips.api.recommendation.dto.RecommendationResponse;
import com.discgolfbagtips.api.session.SessionState;
import com.discgolfbagtips.api.session.SessionStateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Recommendations", description = "Bag gap analysis and the next disc to add")
public class RecommendationController {

    private final RecommendationService recommendationService;
    private final LineupService lineupService;
    private final SessionStateService sessionStateService;
    private final AnalysisCache analysisCache;

    public RecommendationController(RecommendationService recommendationService, LineupService lineupService,
            SessionStateService sessionStateService, AnalysisCache analysisCache) {
        this.recommendationService = recommendationService;
        this.lineupService = lineupService;
        this.sessionStateService = sessionStateService;
        this.analysisCache = analysisCache;
    }

    @PostMapping("/recommendations")
    @Operation(summary = "Analyse a bag and recommend the next disc",
            description = "Runs the full retrieval-augmented pipeline: gap analysis, embedding, pgvector "
                    + "retrieval and a grounded generation step. The response carries the explainability "
                    + "payload naming the vector matches and flight-number deltas behind the pick.")
    public RecommendationResponse recommend(@Valid @RequestBody BagAnalysisRequest request, HttpSession session) {
        String sessionId = sessionStateService.sessionId(session);
        var cached = analysisCache.get(
                AnalysisCacheKey.of("recommendations", request),
                () -> recommendationService.recommend(request, sessionId),
                // A transient failure is served but never stored: a brief model outage must not become
                // a day of quietly degraded recommendations. A missing API key is a settled state
                // rather than a blip, so those responses do cache.
                result -> result.explainability() == null
                        || !result.explainability().generation().transientFailure());

        RecommendationResponse response = cached.value();
        if (cached.fromCache()) {
            // The cached body carries whichever session first computed it; correct that on the way out.
            response = new RecommendationResponse(sessionId, response.generatedAt(), response.recommendation(),
                    response.alternatives(), response.analysis(), response.explainability());
        }
        sessionStateService.recordRecommendation(session, request, response);
        return response;
    }

    @PostMapping("/lineup")
    @Operation(summary = "Analyse the bag slot by slot",
            description = "The whole bag as four tabs — putter/approach, midrange, fairway, distance — "
                    + "each with the discs already in it, which stability classes that leaves "
                    + "uncovered, and suggestions for each hole. Unlike /recommendations this does not "
                    + "call the reasoning model: the answer is structural, and one language-model call "
                    + "per gap would multiply cost and latency for prose the grid already shows.")
    public BagLineupResponse lineup(@Valid @RequestBody BagAnalysisRequest request, HttpSession session) {
        String sessionId = sessionStateService.sessionId(session);
        var cached = analysisCache.get(
                AnalysisCacheKey.of("lineup", request),
                () -> lineupService.buildLineup(request, sessionId),
                result -> result.explainability() == null
                        || !result.explainability().retrievalMode().equals("FLIGHT_NUMBER_FALLBACK"));

        BagLineupResponse response = cached.value();
        if (cached.fromCache()) {
            response = new BagLineupResponse(sessionId, response.generatedAt(), response.coverageScore(),
                    response.discCount(), response.slots(), response.carriedBag(), response.carryWeight(),
                    response.betterFittingBags(), response.notes(), response.unresolvedEntries(),
                    response.explainability());
        }
        sessionStateService.recordRequest(session, request);
        return response;
    }

    @GetMapping("/recommendations/latest")
    @Operation(summary = "Re-read the last recommendation from this session")
    public RecommendationResponse latest(HttpSession session) {
        SessionState state = sessionStateService.current(session);
        if (state.lastRecommendation() == null) {
            throw new NotFoundException("This session has no recommendation yet.");
        }
        return state.lastRecommendation();
    }

    @GetMapping("/session")
    @Operation(summary = "Inspect what this session has stored")
    public SessionSnapshot session(HttpSession session) {
        SessionState state = sessionStateService.current(session);
        return new SessionSnapshot(session.getId(), state.hasSavedBag(), state.lastRecommendation() != null,
                state.updatedAt());
    }

    @PostMapping("/session/bag")
    @Operation(summary = "Save a bag without asking for a recommendation")
    public SessionSnapshot saveBag(@Valid @RequestBody BagAnalysisRequest request, HttpSession session) {
        sessionStateService.recordRequest(session, request);
        SessionState state = sessionStateService.current(session);
        return new SessionSnapshot(session.getId(), true, state.lastRecommendation() != null, state.updatedAt());
    }

    @DeleteMapping("/session")
    @Operation(summary = "Forget everything stored for this session")
    public ResponseEntity<Void> clear(HttpSession session) {
        sessionStateService.clear(session);
        session.invalidate();
        return ResponseEntity.noContent().build();
    }

    public record SessionSnapshot(String sessionId, boolean hasSavedBag, boolean hasRecommendation,
            Instant updatedAt) {
    }
}
