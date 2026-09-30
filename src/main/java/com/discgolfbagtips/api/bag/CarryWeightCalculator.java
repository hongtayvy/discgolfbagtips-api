package com.discgolfbagtips.api.bag;

import com.discgolfbagtips.api.analysis.BagDisc;
import com.discgolfbagtips.api.catalog.DiscWeight;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Turns a resolved bag plus an optional bag model into a carry weight.
 *
 * <p>Disc weight is optional on the wire, so missing entries fall back to the slot's reference
 * weight — the same figure {@link DiscWeight} already uses to reason about stability, so the
 * assumption is at least consistent with the rest of the analysis. Any fallback flips
 * {@code estimated}, because a number presented as a measurement when half of it was assumed is
 * worse than an honest approximation.
 */
@Component
public class CarryWeightCalculator {

    /** Above this, a full bag is a genuine physical complaint rather than a curiosity. */
    private static final double HEAVY_POUNDS = 22.0;

    public CarryWeight calculate(List<BagDisc> bag, BagModel bagModel) {
        int discWeight = 0;
        int assumed = 0;

        for (BagDisc disc : bag) {
            if (disc.weightGrams() != null) {
                discWeight += disc.weightGrams();
            } else {
                discWeight += DiscWeight.referenceGrams(disc.slot());
                assumed++;
            }
        }

        Integer bagWeight = bagModel == null ? null : bagModel.emptyWeightGrams();
        Integer total = bagWeight == null ? null : bagWeight + discWeight;
        double pounds = CarryWeight.gramsToPounds(total == null ? discWeight : total);

        Integer capacity = bagModel == null ? null : bagModel.discCapacityMax();
        boolean overpacked = bagModel != null && bagModel.overpackedBy(bag.size());

        List<String> notes = new ArrayList<>();
        if (assumed > 0) {
            notes.add("%d of %d discs had no weight given; the slot's typical weight was assumed."
                    .formatted(assumed, bag.size()));
        }
        if (bagModel != null && bagWeight == null) {
            notes.add("%s does not publish an empty weight, so only the disc load is counted."
                    .formatted(bagModel.displayName()));
        }
        if (overpacked) {
            notes.add("%d discs exceeds the %d this bag is rated for."
                    .formatted(bag.size(), bagModel.discCapacityMax()));
        } else if (capacity != null && bag.size() >= capacity - 1) {
            notes.add("At %d of %d discs this bag is essentially full.".formatted(bag.size(), capacity));
        }
        if (pounds >= HEAVY_POUNDS) {
            notes.add("About %.1f lb is a heavy round; dropping a disc or two is worth considering."
                    .formatted(pounds));
        }

        return new CarryWeight(bagWeight, discWeight, total, pounds, assumed > 0, assumed, bag.size(),
                capacity, overpacked, List.copyOf(notes));
    }
}
