package com.discgolfbagtips.api.catalog;

/**
 * Speed-based slots. A bag is analysed slot by slot, because a hole in "understable fairway" is a
 * real gap even when the bag already holds fourteen discs.
 */
public enum DiscSlot {

    PUTT_AND_APPROACH(0.0, 4.4, "putter / approach",
            "inside-the-circle putting, short upshots and touch approaches"),
    MIDRANGE(4.5, 6.4, "midrange",
            "150-300 foot control shots, tunnel lines and safe approaches"),
    FAIRWAY_DRIVER(6.5, 9.4, "fairway driver",
            "controlled 250-380 foot drives and shaped gap shots"),
    DISTANCE_DRIVER(9.5, 20.0, "distance driver",
            "maximum-distance open-field bombs and long shaped lines");

    private final double minSpeed;
    private final double maxSpeed;
    private final String label;
    private final String purpose;

    DiscSlot(double minSpeed, double maxSpeed, String label, String purpose) {
        this.minSpeed = minSpeed;
        this.maxSpeed = maxSpeed;
        this.label = label;
        this.purpose = purpose;
    }

    public static DiscSlot forSpeed(double speed) {
        for (DiscSlot slot : values()) {
            if (speed <= slot.maxSpeed) {
                return slot;
            }
        }
        return DISTANCE_DRIVER;
    }

    public double minSpeed() {
        return minSpeed;
    }

    public double maxSpeed() {
        return maxSpeed;
    }

    public String label() {
        return label;
    }

    public String purpose() {
        return purpose;
    }
}
