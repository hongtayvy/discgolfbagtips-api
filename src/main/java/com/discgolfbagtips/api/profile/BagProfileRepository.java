package com.discgolfbagtips.api.profile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BagProfileRepository extends JpaRepository<BagProfile, UUID> {

    List<BagProfile> findAllByOwnerKeyOrderByUpdatedAtDesc(String ownerKey);

    Optional<BagProfile> findByIdAndOwnerKey(UUID id, String ownerKey);

    Optional<BagProfile> findByOwnerKeyAndNameIgnoreCase(String ownerKey, String name);

    long countByOwnerKey(String ownerKey);

    /**
     * Moves everything an anonymous session accumulated onto a newly signed-in user.
     *
     * <p>{@code updatedAt} is bound rather than written as {@code current_timestamp}: HQL's version
     * of that yields a {@code java.sql.Timestamp}, which Hibernate refuses to assign to an
     * {@code Instant} field.
     */
    @Modifying
    @Query("update BagProfile p set p.ownerKey = :userKey, p.updatedAt = :movedAt "
            + "where p.ownerKey = :sessionKey")
    int reassignOwner(@Param("sessionKey") String sessionKey, @Param("userKey") String userKey,
            @Param("movedAt") java.time.Instant movedAt);
}
