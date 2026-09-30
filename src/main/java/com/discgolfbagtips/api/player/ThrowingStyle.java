package com.discgolfbagtips.api.player;

public enum ThrowingStyle {

    BACKHAND("throws mostly backhand",
            "backhand-dominant players need understable and stable discs for turnover and straight lines, "
                    + "and an overstable disc for hyzers and headwind"),
    FOREHAND("throws mostly forehand",
            "forehand-dominant players put extra off-axis torque on the disc, so they need more overstable "
                    + "molds and more durable plastics than the numbers alone suggest"),
    BOTH("throws both backhand and forehand confidently",
            "a two-sided player can cover most lines with fewer discs, so gaps are usually about stability "
                    + "extremes rather than shot shape");

    private final String description;
    private final String guidance;

    ThrowingStyle(String description, String guidance) {
        this.description = description;
        this.guidance = guidance;
    }

    public String description() {
        return description;
    }

    public String guidance() {
        return guidance;
    }
}
