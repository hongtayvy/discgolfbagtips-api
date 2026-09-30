package com.discgolfbagtips.api.profile;

import com.discgolfbagtips.api.common.ApiException;
import com.discgolfbagtips.api.common.NotFoundException;
import com.discgolfbagtips.api.recommendation.dto.BagAnalysisRequest;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Named bag setups, owned by a session today and by a user account once auth lands. */
@Service
public class BagProfileService {

    /** A guard against a single anonymous session filling the table, not a product limit. */
    private static final int MAX_PROFILES_PER_OWNER = 20;

    private static final Logger log = LoggerFactory.getLogger(BagProfileService.class);

    private final BagProfileRepository repository;
    private final ObjectMapper objectMapper;

    public BagProfileService(BagProfileRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /** {@code session:<id>} now, {@code user:<uuid>} once accounts exist. */
    public static String sessionOwnerKey(String sessionId) {
        return "session:" + sessionId;
    }

    public static String userOwnerKey(String userId) {
        return "user:" + userId;
    }

    @Transactional(readOnly = true)
    public List<BagProfileSummary> list(String ownerKey) {
        return repository.findAllByOwnerKeyOrderByUpdatedAtDesc(ownerKey).stream()
                .map(BagProfileSummary::from).toList();
    }

    @Transactional
    public BagProfileDetail save(String ownerKey, String name, String description,
            BagAnalysisRequest request) {

        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid-profile-name",
                    "A profile needs a name.");
        }

        String payload = objectMapper.writeValueAsString(request);
        int discCount = request.bagOrEmpty().size();
        Long bagModelId = request.carriedBagOrNull() == null ? null : request.carriedBagOrNull().bagModelId();

        // Saving under an existing name replaces it, which is what "save" means to someone who just
        // edited their bag and typed the same name again.
        var existing = repository.findByOwnerKeyAndNameIgnoreCase(ownerKey, trimmed);
        if (existing.isPresent()) {
            BagProfile profile = existing.get();
            profile.update(trimmed, description, payload, discCount, bagModelId);
            return BagProfileDetail.from(profile, request);
        }

        if (repository.countByOwnerKey(ownerKey) >= MAX_PROFILES_PER_OWNER) {
            throw new ApiException(HttpStatus.CONFLICT, "too-many-profiles",
                    "This session already holds %d saved bags. Delete one before adding another."
                            .formatted(MAX_PROFILES_PER_OWNER));
        }

        BagProfile profile = repository.save(
                new BagProfile(ownerKey, trimmed, description, payload, discCount, bagModelId));
        return BagProfileDetail.from(profile, request);
    }

    @Transactional
    public BagProfileDetail load(String ownerKey, UUID id) {
        BagProfile profile = repository.findByIdAndOwnerKey(id, ownerKey)
                .orElseThrow(() -> new NotFoundException("No saved bag with id " + id));
        profile.markViewed();
        return BagProfileDetail.from(profile, parse(profile));
    }

    @Transactional
    public void delete(String ownerKey, UUID id) {
        BagProfile profile = repository.findByIdAndOwnerKey(id, ownerKey)
                .orElseThrow(() -> new NotFoundException("No saved bag with id " + id));
        repository.delete(profile);
    }

    /**
     * Hands an anonymous session's saved bags to a user who has just signed in. Unused until auth
     * exists, but the reason {@code ownerKey} is a string rather than a foreign key.
     */
    @Transactional
    public int claimSessionProfiles(String sessionId, String userId) {
        int moved = repository.reassignOwner(sessionOwnerKey(sessionId), userOwnerKey(userId),
                java.time.Instant.now());
        if (moved > 0) {
            log.info("Moved {} saved bag(s) from session {} to user {}", moved, sessionId, userId);
        }
        return moved;
    }

    /** A payload written by an older build may no longer parse; report it rather than 500. */
    private BagAnalysisRequest parse(BagProfile profile) {
        try {
            return objectMapper.readValue(profile.payload(), BagAnalysisRequest.class);
        } catch (JacksonException ex) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "unreadable-profile",
                    "Saved bag '%s' was written in an older format and can no longer be read."
                            .formatted(profile.name()));
        }
    }
}
