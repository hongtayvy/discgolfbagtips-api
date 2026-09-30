package com.discgolfbagtips.api.bag;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BagModelRepository extends JpaRepository<BagModel, Long> {

    @Query("select b from BagModel b where b.active = true order by b.brand asc, b.discCapacityMax asc")
    List<BagModel> findAllActive();

    /**
     * Bags that can hold at least this many discs. Ordered by the tightest fit first, so the result
     * leads with bags sized for the lineup rather than the largest cart on the list.
     *
     * <p>{@code brand} must already be lower-cased by the caller. Applying {@code lower()} to the
     * bind parameter instead fails on PostgreSQL when the value is null, because an untyped null
     * binds as bytea and there is no {@code lower(bytea)}.
     */
    @Query("""
            select b from BagModel b
            where b.active = true
              and b.discCapacityMax >= :discCount
              and (:brand is null or lower(b.brand) = :brand)
              and (:bagType is null or b.bagType = :bagType)
            order by b.discCapacityMax asc, b.brand asc
            """)
    List<BagModel> findFitting(@Param("discCount") int discCount, @Param("brand") String brand,
            @Param("bagType") String bagType);

    Optional<BagModel> findByIdAndActiveTrue(Long id);

    @Query("select distinct b.brand from BagModel b where b.active = true order by b.brand")
    List<String> findDistinctBrands();
}
