package com.discgolfbagtips.api.catalog;

import com.discgolfbagtips.api.common.NotFoundException;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DiscCatalogService {

    private static final int MAX_SEARCH_RESULTS = 50;

    private final DiscRepository discRepository;

    public DiscCatalogService(DiscRepository discRepository) {
        this.discRepository = discRepository;
    }

    @Transactional(readOnly = true)
    public List<DiscSummary> search(String query, int limit) {
        String term = query == null ? "" : query.trim();
        if (term.length() < 2) {
            return List.of();
        }
        int capped = Math.clamp(limit, 1, MAX_SEARCH_RESULTS);
        return discRepository.search(term, Limit.of(capped)).stream().map(DiscSummary::from).toList();
    }

    @Transactional(readOnly = true)
    public DiscSummary byId(String id) {
        return discRepository.findById(id)
                .map(DiscSummary::from)
                .orElseThrow(() -> new NotFoundException("No disc with id '%s'".formatted(id)));
    }

    @Transactional(readOnly = true)
    public Optional<Disc> entityById(String id) {
        return discRepository.findById(id);
    }

    /**
     * Resolves a bag entry that arrived as free text rather than as a catalog id — the front end
     * allows both so a player can paste a bag list without picking every disc from the dropdown.
     */
    @Transactional(readOnly = true)
    public Optional<Disc> resolveByName(String name, String brand) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        if (brand != null && !brand.isBlank()) {
            List<Disc> exact = discRepository.findByNameAndBrandIgnoreCase(name.trim(), brand.trim());
            if (!exact.isEmpty()) {
                return Optional.of(exact.getFirst());
            }
        }
        List<Disc> byName = discRepository.findByNameIgnoreCase(name.trim());
        if (!byName.isEmpty()) {
            return Optional.of(byName.getFirst());
        }
        List<Disc> fuzzy = discRepository.search(name.trim(), Limit.of(1));
        return fuzzy.isEmpty() ? Optional.empty() : Optional.of(fuzzy.getFirst());
    }

    @Transactional(readOnly = true)
    public long activeDiscCount() {
        return discRepository.countByActiveTrue();
    }
}
