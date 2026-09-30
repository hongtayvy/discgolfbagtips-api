package com.discgolfbagtips.api.catalog;

import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DiscRepository extends JpaRepository<Disc, String> {

    List<Disc> findAllByActiveTrue();

    List<Disc> findAllByIdInAndActiveTrue(Collection<String> ids);

    /**
     * Type-ahead ranking: exact prefix on the mold name first, then a prefix on the brand, then any
     * substring match. Ordered inside SQL so the front end can render results as they arrive.
     */
    @Query("""
            select d from Disc d
            where d.active = true
              and (lower(d.name) like lower(concat('%', :term, '%'))
                   or lower(d.brand) like lower(concat('%', :term, '%')))
            order by
              case
                when lower(d.name) = lower(:term) then 0
                when lower(d.name) like lower(concat(:term, '%')) then 1
                when lower(d.brand) like lower(concat(:term, '%')) then 2
                else 3
              end,
              d.name asc, d.brand asc
            """)
    List<Disc> search(@Param("term") String term, Limit limit);

    @Query("select d from Disc d where d.active = true and lower(d.name) = lower(:name) and lower(d.brand) = lower(:brand)")
    List<Disc> findByNameAndBrandIgnoreCase(@Param("name") String name, @Param("brand") String brand);

    @Query("select d from Disc d where d.active = true and lower(d.name) = lower(:name) order by d.brand asc")
    List<Disc> findByNameIgnoreCase(@Param("name") String name);

    long countByActiveTrue();
}
