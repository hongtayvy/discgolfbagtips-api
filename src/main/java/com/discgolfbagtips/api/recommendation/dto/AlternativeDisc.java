package com.discgolfbagtips.api.recommendation.dto;

/** Runners-up from the same retrieval, so the UI can offer a second and third option. */
public record AlternativeDisc(
        String discId,
        String name,
        String brand,
        double speed,
        double glide,
        double turn,
        double fade,
        String stability,
        String imageUrl,
        double similarity,
        int rank,
        String whyItWasConsidered) {
}
