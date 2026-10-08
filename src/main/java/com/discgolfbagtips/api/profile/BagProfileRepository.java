package com.discgolfbagtips.api.profile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BagProfileRepository extends JpaRepository<BagProfile, UUID> {

    List<BagProfile> findAllByOwnerKeyOrderByUpdatedAtDesc(String ownerKey);

    Optional<BagProfile> findByIdAndOwnerKey(UUID id, String ownerKey);

    Optional<BagProfile> findByOwnerKeyAndNameIgnoreCase(String ownerKey, String name);

    long countByOwnerKey(String ownerKey);
}
