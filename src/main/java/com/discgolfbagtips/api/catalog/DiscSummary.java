package com.discgolfbagtips.api.catalog;

/** The catalog projection the front end's type-ahead picker renders. */
public record DiscSummary(
        String id,
        String name,
        String brand,
        String category,
        double speed,
        double glide,
        double turn,
        double fade,
        String stability,
        String slot,
        String imageUrl) {

    public static DiscSummary from(Disc disc) {
        return new DiscSummary(
                disc.id(),
                disc.name(),
                disc.brand(),
                disc.category(),
                disc.speed(),
                disc.glide(),
                disc.turn(),
                disc.fade(),
                disc.stabilityClass().label(),
                disc.slot().name(),
                disc.imageUrl());
    }
}
