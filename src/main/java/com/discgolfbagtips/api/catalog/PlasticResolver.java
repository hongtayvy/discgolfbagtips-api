package com.discgolfbagtips.api.catalog;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maps the free-text plastic a player types ("Star", "esp", "Champion") onto a curated blend,
 * preferring the blend belonging to the disc's own manufacturer before falling back to a
 * cross-brand entry and finally to an "unspecified" placeholder.
 */
@Service
public class PlasticResolver {

    private final PlasticTypeRepository plasticTypeRepository;

    public PlasticResolver(PlasticTypeRepository plasticTypeRepository) {
        this.plasticTypeRepository = plasticTypeRepository;
    }

    @Transactional(readOnly = true)
    public Optional<PlasticType> resolve(String brand, String plasticName) {
        if (plasticName == null || plasticName.isBlank()) {
            return Optional.empty();
        }
        String slug = Slugs.of(plasticName);
        if (slug.isEmpty()) {
            return Optional.empty();
        }
        List<PlasticType> matches = plasticTypeRepository.findBySlugPreferringBrand(slug, brand == null ? "" : brand);
        if (!matches.isEmpty()) {
            return Optional.of(matches.getFirst());
        }
        return plasticTypeRepository.findFirstBySlugIgnoreCase(slug);
    }

    @Transactional(readOnly = true)
    public List<PlasticType> forBrand(String brand) {
        List<PlasticType> branded = plasticTypeRepository.findAllByBrandIgnoreCaseOrderByNameAsc(brand);
        List<PlasticType> generic =
                plasticTypeRepository.findAllByBrandIgnoreCaseOrderByNameAsc(PlasticType.GENERIC_BRAND);
        return java.util.stream.Stream.concat(branded.stream(), generic.stream()).toList();
    }

    @Transactional(readOnly = true)
    public List<PlasticType> all() {
        return plasticTypeRepository.findAll();
    }
}
