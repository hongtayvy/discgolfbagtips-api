package com.discgolfbagtips.api.analysis;

import com.discgolfbagtips.api.catalog.DiscSlot;
import com.discgolfbagtips.api.catalog.StabilityClass;
import java.util.List;

/**
 * Two or more discs doing the same job.
 *
 * <p>Whether that is a problem depends entirely on who is throwing them. A touring pro carrying
 * three Destroyers at different wear levels is not carrying one disc three times — they are carrying
 * three distinct flights that happen to share a mold name. A beginner with the same three is carrying
 * one disc three times. The same observation, opposite conclusions, so this is reported at a
 * {@link Level} scaled to the player rather than as a flat warning.
 *
 * @param separation  how far apart the closest pair actually flies once plastic, weight and wear are
 *                    applied; near zero means genuinely interchangeable
 * @param level       how forcefully to present it
 */
public record BagRedundancy(
        DiscSlot slot,
        StabilityClass stabilityClass,
        List<BagDisc> discs,
        double separation,
        double severity,
        Level level,
        String reason) {

    public enum Level {
        /** Genuinely duplicated for this player; a slot could be freed. */
        WARNING,
        /** Worth a look, but defensible. */
        NOTE,
        /** Mentioned only for completeness — the player can likely tell these apart. */
        FYI
    }

    public List<String> discNames() {
        return discs.stream().map(disc -> disc.disc().displayName()).toList();
    }
}
