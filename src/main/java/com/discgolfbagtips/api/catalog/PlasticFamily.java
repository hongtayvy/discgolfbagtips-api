package com.discgolfbagtips.api.catalog;

/**
 * Broad plastic families. DiscIt does not publish plastic, so this axis is modelled locally and
 * seeded from a curated table — it is what lets the API reason about "the same mold in a different
 * blend" instead of treating flight numbers as the whole story.
 */
public enum PlasticFamily {

    BASE("base-grade", "grippy but soft; beats in quickly and becomes noticeably more understable with use"),
    MID("mid-grade", "a middle ground between grip and durability; seasons in gradually"),
    PREMIUM("premium", "durable and stable-holding; keeps its original flight for a long time"),
    GRIPPY_PREMIUM("grippy premium", "premium durability with a tacky surface; confidence in the rain and in cold hands"),
    GUMMY("gummy / soft premium", "soft and flexible; grabs the ground on landing and holds grip in wet conditions"),
    GLOW("glow", "glow additive usually stiffens the blend and pushes the flight slightly more overstable"),
    SPECIALTY("specialty", "limited or experimental blend with its own feel and seasoning curve");

    private final String label;
    private final String behaviour;

    PlasticFamily(String label, String behaviour) {
        this.label = label;
        this.behaviour = behaviour;
    }

    public String label() {
        return label;
    }

    public String behaviour() {
        return behaviour;
    }
}
