package com.discgolfbagtips.api.bag;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Answers "which bags actually fit what I carry" — a capacity-matching problem rather than a ranking
 * of premium against budget.
 *
 * <p>Results are not ordered by weight alone. A lighter bag is not automatically a better swap: an
 * Innova Adventure Pack is lighter than a GRIPeq AX6 and built to a different standard, so the build
 * tier travels with every suggestion and the comparison text says when a saving comes with a
 * downgrade rather than implying lightest wins.
 */
@Service
public class BagFittingService {

    private static final int MAX_SUGGESTIONS = 8;

    private final BagModelRepository bagModelRepository;

    public BagFittingService(BagModelRepository bagModelRepository) {
        this.bagModelRepository = bagModelRepository;
    }

    @Transactional(readOnly = true)
    public List<BagSummary> catalog() {
        return bagModelRepository.findAllActive().stream().map(BagSummary::from).toList();
    }

    @Transactional(readOnly = true)
    public List<String> brands() {
        return bagModelRepository.findDistinctBrands();
    }

    @Transactional(readOnly = true)
    public Optional<BagModel> byId(Long id) {
        return id == null ? Optional.empty() : bagModelRepository.findByIdAndActiveTrue(id);
    }

    /** Resolves a bag the caller named by free text, so the request does not have to carry an id. */
    @Transactional(readOnly = true)
    public Optional<BagModel> resolve(String brand, String model) {
        if (model == null || model.isBlank()) {
            return Optional.empty();
        }
        String wanted = model.trim().toLowerCase(java.util.Locale.ROOT);
        return bagModelRepository.findAllActive().stream()
                .filter(bag -> brand == null || brand.isBlank() || bag.brand().equalsIgnoreCase(brand.trim()))
                .filter(bag -> bag.model().toLowerCase(java.util.Locale.ROOT).contains(wanted)
                        || bag.slug().equalsIgnoreCase(wanted.replace(' ', '-')))
                .findFirst();
    }

    @Transactional(readOnly = true)
    public List<BagFit> fitting(int discCount, int discWeightGrams, String brand, String bagType,
            BagModel current) {

        String normalisedBrand = brand == null || brand.isBlank()
                ? null : brand.trim().toLowerCase(java.util.Locale.ROOT);
        String normalisedType = bagType == null || bagType.isBlank()
                ? null : bagType.trim().toUpperCase(java.util.Locale.ROOT);

        List<BagFit> fits = new ArrayList<>();
        for (BagModel bag : bagModelRepository.findFitting(discCount, normalisedBrand, normalisedType)) {
            if (current != null && bag.id().equals(current.id())) {
                continue;
            }
            Integer total = bag.emptyWeightGrams() == null ? null : bag.emptyWeightGrams() + discWeightGrams;
            fits.add(new BagFit(
                    BagSummary.from(bag),
                    bag.discCapacityMax() - discCount,
                    total,
                    total == null ? null : CarryWeight.gramsToPounds(total),
                    compare(bag, current)));
            if (fits.size() >= MAX_SUGGESTIONS) {
                break;
            }
        }
        return List.copyOf(fits);
    }

    private String compare(BagModel candidate, BagModel current) {
        if (current == null) {
            return "Holds %d discs%s.".formatted(candidate.discCapacityMax(),
                    candidate.hasPublishedWeight()
                            ? " and weighs %.1f lb empty".formatted(
                                    CarryWeight.gramsToPounds(candidate.emptyWeightGrams()))
                            : "; empty weight not published");
        }
        if (!candidate.hasPublishedWeight() || !current.hasPublishedWeight()) {
            return "Holds %d discs against your %d. Weight comparison needs a published figure for both."
                    .formatted(candidate.discCapacityMax(), current.discCapacityMax());
        }

        int delta = candidate.emptyWeightGrams() - current.emptyWeightGrams();
        String weight = delta == 0
                ? "the same weight as"
                : "%.1f lb %s than".formatted(Math.abs(CarryWeight.gramsToPounds(Math.abs(delta))),
                        delta < 0 ? "lighter" : "heavier");
        String caveat = delta < 0 && tierRank(candidate) < tierRank(current)
                ? " — but it is a %s build against your %s one, so the saving is not free."
                        .formatted(candidate.buildTier().toLowerCase(java.util.Locale.ROOT),
                                current.buildTier().toLowerCase(java.util.Locale.ROOT))
                : ".";
        return "%s %s, holding %d discs against your %d%s".formatted(weight, current.displayName(),
                candidate.discCapacityMax(), current.discCapacityMax(), caveat);
    }

    private int tierRank(BagModel bag) {
        return switch (bag.buildTier()) {
            case "BUDGET" -> 1;
            case "PREMIUM" -> 3;
            default -> 2;
        };
    }
}
