package com.discgolfbagtips.api.recommendation.dto;

import java.time.Instant;
import java.util.List;

public record RecommendationResponse(
        String sessionId,
        Instant generatedAt,
        RecommendedDisc recommendation,
        List<AlternativeDisc> alternatives,
        BagAnalysisSummary analysis,
        Explainability explainability) {
}
