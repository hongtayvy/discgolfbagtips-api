package com.discgolfbagtips.api.recommendation.dto;

import com.discgolfbagtips.api.analysis.BagRedundancy;
import java.util.List;

/**
 * Discs duplicating each other's job, as the API reports them.
 *
 * @param level      WARNING, NOTE or FYI — scaled to the player rather than a flat judgement
 * @param separation how far apart the closest pair actually flies; near zero is interchangeable
 */
public record RedundancyReport(
        String slot,
        String stabilityClass,
        String level,
        double severity,
        double separation,
        List<Disc> discs,
        String reason) {

    public record Disc(String discId, String name, String brand, String plastic, Integer weightGrams,
            String wear, String plays) {
    }

    public static RedundancyReport from(BagRedundancy redundancy) {
        List<Disc> discs = redundancy.discs().stream()
                .map(bagDisc -> new Disc(
                        bagDisc.disc().id(),
                        bagDisc.disc().name(),
                        bagDisc.disc().brand(),
                        bagDisc.plastic() == null ? bagDisc.requestedPlastic() : bagDisc.plastic().name(),
                        bagDisc.weightGrams(),
                        bagDisc.wear().label(),
                        bagDisc.effectiveStability().label()))
                .toList();
        return new RedundancyReport(
                redundancy.slot().name(),
                redundancy.stabilityClass().name(),
                redundancy.level().name(),
                redundancy.severity(),
                redundancy.separation(),
                discs,
                redundancy.reason());
    }
}
