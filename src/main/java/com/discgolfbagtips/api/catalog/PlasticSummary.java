package com.discgolfbagtips.api.catalog;

public record PlasticSummary(
        Long id,
        String brand,
        String name,
        String slug,
        String family,
        double stabilityShift,
        int durability,
        int grip,
        String description) {

    public static PlasticSummary from(PlasticType plastic) {
        return new PlasticSummary(
                plastic.id(),
                plastic.brand(),
                plastic.name(),
                plastic.slug(),
                plastic.family().label(),
                plastic.stabilityShift(),
                plastic.durability(),
                plastic.grip(),
                plastic.description());
    }
}
