package com.discgolfbagtips.api.recommendation;

import com.discgolfbagtips.api.analysis.BagDisc;
import com.discgolfbagtips.api.catalog.Disc;
import com.discgolfbagtips.api.catalog.DiscCatalogService;
import com.discgolfbagtips.api.catalog.PlasticResolver;
import com.discgolfbagtips.api.catalog.PlasticType;
import com.discgolfbagtips.api.recommendation.dto.BagDiscRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Turns the loosely-typed bag on the wire into resolved molds and plastics. Entries that cannot be
 * matched are collected rather than rejected — one mistyped disc should not fail the whole request.
 */
@Component
public class BagResolver {

    private final DiscCatalogService discCatalogService;
    private final PlasticResolver plasticResolver;

    public BagResolver(DiscCatalogService discCatalogService, PlasticResolver plasticResolver) {
        this.discCatalogService = discCatalogService;
        this.plasticResolver = plasticResolver;
    }

    public ResolvedBag resolve(List<BagDiscRequest> requested) {
        List<BagDisc> resolved = new ArrayList<>();
        List<String> unresolved = new ArrayList<>();

        for (BagDiscRequest entry : requested) {
            Optional<Disc> disc = entry.discId() != null && !entry.discId().isBlank()
                    ? discCatalogService.entityById(entry.discId())
                    : discCatalogService.resolveByName(entry.name(), entry.brand());

            if (disc.isEmpty()) {
                unresolved.add(entry.describe());
                continue;
            }
            // Repeated molds are kept. They used to be dropped as uninformative, which was true
            // while the analysis only looked for holes — but two copies of one mold is precisely what
            // redundancy analysis exists to evaluate, and whether it is duplication or a deliberate
            // wear spread depends on the plastic and wear recorded against each copy.
            PlasticType plastic = plasticResolver.resolve(disc.get().brand(), entry.plastic()).orElse(null);
            resolved.add(new BagDisc(disc.get(), plastic, entry.plastic(), entry.weightGrams(), entry.wear()));
        }
        return new ResolvedBag(List.copyOf(resolved), List.copyOf(unresolved));
    }

    public record ResolvedBag(List<BagDisc> bag, List<String> unresolved) {
    }
}
