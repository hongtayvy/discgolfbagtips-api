package com.discgolfbagtips.api.recommendation.dto;

import java.util.List;

/** The headline answer: one disc, and the case for it. */
public record RecommendedDisc(
        String discId,
        String name,
        String brand,
        String category,
        double speed,
        double glide,
        double turn,
        double fade,
        String stability,
        String slot,
        String imageUrl,
        String sourceUrl,
        String headline,
        String summary,
        List<String> reasoning,
        String whenToThrowIt,
        String plasticAdvice,
        String weightAdvice,
        String suggestedWeightRange,
        double confidence) {
}
