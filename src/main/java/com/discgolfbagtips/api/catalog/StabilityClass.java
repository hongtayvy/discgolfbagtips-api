package com.discgolfbagtips.api.catalog;

/**
 * Stability derived from {@code turn + fade} rather than from the upstream text label, so that gap
 * analysis and retrieval always agree on what a disc is.
 */
public enum StabilityClass {

    VERY_UNDERSTABLE("very understable", "flips to a roller or a long turnover; the beginner's distance disc"),
    UNDERSTABLE("understable", "turns right for a right-hand backhand and finishes gently; hyzer-flip and turnover shots"),
    STABLE("stable", "holds the line it is put on and finishes with a mild fade; the straight workhorse"),
    OVERSTABLE("overstable", "resists turn and finishes reliably left for a right-hand backhand; wind and forehand"),
    VERY_OVERSTABLE("very overstable", "hard, dependable finish; headwind shots, forehand flex lines and utility spike hyzers");

    private final String label;
    private final String behaviour;

    StabilityClass(String label, String behaviour) {
        this.label = label;
        this.behaviour = behaviour;
    }

    /** @param stabilityIndex {@code turn + fade}. */
    public static StabilityClass forIndex(double stabilityIndex) {
        if (stabilityIndex < -1.5) {
            return VERY_UNDERSTABLE;
        }
        if (stabilityIndex < -0.5) {
            return UNDERSTABLE;
        }
        if (stabilityIndex < 1.5) {
            return STABLE;
        }
        if (stabilityIndex < 3.5) {
            return OVERSTABLE;
        }
        return VERY_OVERSTABLE;
    }

    public String label() {
        return label;
    }

    public String behaviour() {
        return behaviour;
    }
}
