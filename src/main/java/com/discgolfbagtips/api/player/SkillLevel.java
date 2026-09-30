package com.discgolfbagtips.api.player;

/** Self-rated skill, used to weight gaps and to keep the recommendation throwable. */
public enum SkillLevel {

    BEGINNER(1, 250, "a beginner still building form",
            "needs lower speeds, higher glide and understable-to-stable discs; high-speed overstable molds "
                    + "will only fade out early and cost distance"),
    INTERMEDIATE(2, 330, "an intermediate player with repeatable form",
            "can handle fairway drivers and moderately stable distance drivers, and benefits most from "
                    + "filling shot-shape holes rather than adding raw speed"),
    ADVANCED(3, 400, "an advanced player with consistent power and shot shaping",
            "can throw the full speed range and gains most from specialised utility and stability gaps"),
    PROFESSIONAL(4, 450, "a touring-level player",
            "already covers the common lines; gains come from narrow utility slots and wind-specific tools");

    private final int rank;
    private final int typicalDriveFeet;
    private final String description;
    private final String guidance;

    SkillLevel(int rank, int typicalDriveFeet, String description, String guidance) {
        this.rank = rank;
        this.typicalDriveFeet = typicalDriveFeet;
        this.description = description;
        this.guidance = guidance;
    }

    public int rank() {
        return rank;
    }

    public int typicalDriveFeet() {
        return typicalDriveFeet;
    }

    public String description() {
        return description;
    }

    public String guidance() {
        return guidance;
    }

    /** The fastest disc this player can reasonably make fly as rated. */
    public double maxUsefulSpeed() {
        return switch (this) {
            case BEGINNER -> 9.0;
            case INTERMEDIATE -> 12.0;
            case ADVANCED, PROFESSIONAL -> 15.0;
        };
    }
}
