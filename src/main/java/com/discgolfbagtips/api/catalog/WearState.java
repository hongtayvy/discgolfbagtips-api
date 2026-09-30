package com.discgolfbagtips.api.catalog;

/**
 * How much use a disc has seen. Wear only ever moves a disc toward understable — scuffs and dings
 * round the flight plate and the disc loses its resistance to turning over.
 *
 * <p>The state describes <em>usage</em>, not appearance, and how far that usage moved the flight
 * depends on the plastic: a season of rounds turns a DX driver into a roller and barely touches a
 * Champion one. That interaction is why {@link #stabilityShift(Integer)} takes the blend's
 * durability rather than returning a flat number, and it is what lets the API tell a
 * "beat-in Firebird" (still overstable, just less hard) apart from a "beat-in Leopard" (a flipper).
 */
public enum WearState {

    NEW(0.0, "brand new", "out of the box, flying its published numbers"),
    SEASONED(-0.4, "seasoned", "a few dozen rounds in, just starting to lose its edge"),
    BEAT_IN(-1.0, "beat in", "well used, noticeably straighter and more willing to turn than new"),
    WELL_WORN(-1.8, "well worn", "heavily used and flippy, held onto for turnovers and rollers");

    /** Applied to the stability index at reference durability; always zero or negative. */
    private final double baseShift;
    private final String label;
    private final String description;

    WearState(double baseShift, String label, String description) {
        this.baseShift = baseShift;
        this.label = label;
        this.description = description;
    }

    public double baseShift() {
        return baseShift;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    /**
     * @param durability the plastic's 1-5 durability, or null when the plastic is unknown
     * @return how far this much use moved the disc's stability index, damped by the blend
     */
    public double stabilityShift(Integer durability) {
        return round(baseShift * durabilityDamping(durability));
    }

    /**
     * Base plastic beats in faster than it "should" and premium plastic resists; 3 is the neutral
     * mid-grade blend, and an unknown plastic is treated as mid-grade rather than guessed at.
     */
    static double durabilityDamping(Integer durability) {
        if (durability == null) {
            return 1.0;
        }
        return switch (Math.clamp(durability, 1, 5)) {
            case 1 -> 1.4;
            case 2 -> 1.2;
            case 3 -> 1.0;
            case 4 -> 0.75;
            default -> 0.5;
        };
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
