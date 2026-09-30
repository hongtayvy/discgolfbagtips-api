package com.discgolfbagtips.api.catalog.sync;

/**
 * One record from the DiscIt API. Every flight number arrives as a string (and can carry a decimal
 * point), so parsing is deliberate rather than implicit.
 */
public record DiscItDisc(
        String id,
        String name,
        String brand,
        String category,
        String speed,
        String glide,
        String turn,
        String fade,
        String stability,
        String link,
        String pic,
        String name_slug,
        String brand_slug,
        String category_slug,
        String stability_slug) {

    public boolean usable() {
        return id != null && !id.isBlank()
                && name != null && !name.isBlank()
                && brand != null && !brand.isBlank()
                && numeric(speed) != null
                && numeric(glide) != null
                && numeric(turn) != null
                && numeric(fade) != null;
    }

    public double speedValue() {
        return required(speed, "speed");
    }

    public double glideValue() {
        return required(glide, "glide");
    }

    public double turnValue() {
        return required(turn, "turn");
    }

    public double fadeValue() {
        return required(fade, "fade");
    }

    private double required(String raw, String field) {
        Double value = numeric(raw);
        if (value == null) {
            throw new IllegalArgumentException("Disc %s has unparseable %s '%s'".formatted(id, field, raw));
        }
        return value;
    }

    private static Double numeric(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Double.valueOf(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
