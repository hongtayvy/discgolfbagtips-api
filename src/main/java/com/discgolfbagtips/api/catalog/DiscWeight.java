package com.discgolfbagtips.api.catalog;

/**
 * Weight as a stability axis.
 *
 * <p>Manufacturers publish one set of flight numbers per mold, and those numbers describe a
 * max-weight run. The same mold in a lighter weight flies more understable and carries further for
 * a slower arm, and is far more exposed to wind. This class turns "165 g" into that difference.
 */
public final class DiscWeight {

    /** PDGA's ceiling is 200 g; anything under 120 g is a mini or a mis-entry. */
    public static final int MIN_GRAMS = 100;
    public static final int MAX_GRAMS = 200;

    /** Stability index points lost per gram below the slot's reference weight. */
    private static final double SHIFT_PER_GRAM = 0.04;
    /** Weight alone never turns an overstable driver into a roller; cap its authority. */
    private static final double MAX_SHIFT = 1.5;

    private DiscWeight() {
    }

    /** The weight the published flight numbers describe, per slot. */
    public static int referenceGrams(DiscSlot slot) {
        return switch (slot) {
            case PUTT_AND_APPROACH -> 174;
            case MIDRANGE -> 178;
            case FAIRWAY_DRIVER -> 174;
            case DISTANCE_DRIVER -> 173;
        };
    }

    /**
     * @return how far this weight moves the mold's stability index; negative below the reference
     *         weight, and zero when no weight was given
     */
    public static double stabilityShift(DiscSlot slot, Integer weightGrams) {
        if (weightGrams == null) {
            return 0.0;
        }
        double shift = (weightGrams - referenceGrams(slot)) * SHIFT_PER_GRAM;
        return Math.round(Math.clamp(shift, -MAX_SHIFT, MAX_SHIFT) * 100.0) / 100.0;
    }

    /** The band a player would name out loud. */
    public static String classify(Integer weightGrams) {
        if (weightGrams == null) {
            return "unspecified weight";
        }
        if (weightGrams < 150) {
            return "very light";
        }
        if (weightGrams < 160) {
            return "150-class";
        }
        if (weightGrams < 170) {
            return "light to mid weight";
        }
        if (weightGrams < 175) {
            return "standard weight";
        }
        return "max weight";
    }

    public static String describe(DiscSlot slot, Integer weightGrams) {
        if (weightGrams == null) {
            return "No weight given, so the published flight numbers are used unadjusted.";
        }
        int delta = weightGrams - referenceGrams(slot);
        if (delta <= -12) {
            return "At %d g this is well under the %d g the numbers describe: it carries further for a "
                    .formatted(weightGrams, referenceGrams(slot))
                    + "slower arm, flies noticeably more understable, and is hard to trust in wind.";
        }
        if (delta <= -5) {
            return "At %d g it is lighter than the %d g the numbers describe, so it flies a little more "
                    .formatted(weightGrams, referenceGrams(slot))
                    + "understable and gives up some wind resistance for distance.";
        }
        if (delta < 0) {
            return "At %d g it is marginally under the %d g the numbers describe and flies close to them."
                    .formatted(weightGrams, referenceGrams(slot));
        }
        return "At %d g this is at or above the %d g the numbers describe: full stability and the best "
                .formatted(weightGrams, referenceGrams(slot))
                + "wind resistance the mold offers.";
    }

    /**
     * The weight to buy a recommended disc in. Lighter for players who need the carry, heavier when
     * the wind will punish a light disc.
     */
    public static WeightAdvice recommend(DiscSlot slot, int skillRank, boolean windy) {
        int reference = referenceGrams(slot);
        int centre = reference;
        StringBuilder rationale = new StringBuilder();

        if (skillRank <= 1) {
            centre -= slot == DiscSlot.PUTT_AND_APPROACH ? 4 : 10;
            rationale.append("A lighter run carries further at this arm speed and is easier to turn over. ");
        } else if (skillRank == 2) {
            centre -= slot == DiscSlot.PUTT_AND_APPROACH ? 2 : 5;
            rationale.append("A slightly under-max run adds carry without giving up much stability. ");
        } else {
            rationale.append("Max weight gives this mold its full rated stability. ");
        }

        if (windy) {
            centre = Math.min(reference, centre + 6);
            rationale.append("The wind argues for the heavier end of that range. ");
        }

        int low = Math.max(MIN_GRAMS, centre - 5);
        int high = Math.min(reference, centre + 5);
        return new WeightAdvice(low, high, rationale.toString().trim());
    }

    /** A suggested weight window, in grams, and why. */
    public record WeightAdvice(int minGrams, int maxGrams, String rationale) {

        public String format() {
            return minGrams == maxGrams ? minGrams + " g" : "%d-%d g".formatted(minGrams, maxGrams);
        }
    }
}
