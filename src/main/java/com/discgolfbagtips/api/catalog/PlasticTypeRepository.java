package com.discgolfbagtips.api.catalog;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlasticTypeRepository extends JpaRepository<PlasticType, Long> {

    List<PlasticType> findAllByBrandIgnoreCaseOrderByNameAsc(String brand);

    @Query("""
            select p from PlasticType p
            where lower(p.slug) = lower(:slug)
              and (lower(p.brand) = lower(:brand) or p.brand = '*')
            order by case when p.brand = '*' then 1 else 0 end
            """)
    List<PlasticType> findBySlugPreferringBrand(@Param("slug") String slug, @Param("brand") String brand);

    Optional<PlasticType> findFirstBySlugIgnoreCase(String slug);
}
