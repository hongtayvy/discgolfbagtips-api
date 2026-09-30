package com.discgolfbagtips.api.bag;

import java.math.BigDecimal;

/** A bag as the catalog and the fitting endpoint present it. */
public record BagSummary(
        Long id,
        String brand,
        String model,
        Integer capacityMin,
        int capacityMax,
        Integer emptyWeightGrams,
        Double emptyWeightPounds,
        BigDecimal heightIn,
        BigDecimal widthIn,
        BigDecimal depthIn,
        Boolean carryOnCompliant,
        String buildTier,
        String bagType,
        String source,
        String notes) {

    public static BagSummary from(BagModel bag) {
        return new BagSummary(
                bag.id(), bag.brand(), bag.model(), bag.discCapacityMin(), bag.discCapacityMax(),
                bag.emptyWeightGrams(),
                bag.emptyWeightGrams() == null ? null : CarryWeight.gramsToPounds(bag.emptyWeightGrams()),
                bag.heightIn(), bag.widthIn(), bag.depthIn(), bag.carryOnCompliant(),
                bag.buildTier(), bag.bagType(), bag.source(), bag.notes());
    }
}
