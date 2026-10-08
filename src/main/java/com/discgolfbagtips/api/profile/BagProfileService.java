package com.discgolfbagtips.api.profile;

import com.discgolfbagtips.api.common.ApiException;
import com.discgolfbagtips.api.common.NotFoundException;
import com.discgolfbagtips.api.recommendation.dto.BagAnalysisRequest;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Named bag setups, owned by the signed-in user or, for an anonymous visitor, by their session. */
@Service
public class BagProfileService {

    /** A guard against a single anonymous session filling the table, not a product limit. */
    private static final int MAX_PROFILES_PER_OWNER = 20;

    /** {@code bag_profile.name} is {@code varchar(120)}. */
    private static final int MAX_NAME_LENGTH = 120;

    private static final Logger log = LoggerFactory.getLogger(BagProfileService.class);

    private final BagProfileRepository repository;
    private final ObjectMapper objectMapper;

    public BagProfileService(BagProfileRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /** An anonymous visitor's bags; {@link #userOwnerKey} once they sign in. */
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
                    "You already have %d saved bags. Delete one before adding another."
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
     * Hands an anonymous session's saved bags to a user who has just signed in — the reason
     * {@code ownerKey} is a string rather than a foreign key.
     *
     * <p>Row by row rather than one bulk UPDATE, because two things a bulk move cannot handle are
     * normal here: the account already keeping a bag of the same name (someone signing in on a second
     * device), which would violate {@code bag_profile_name_uq}, and the account being near its limit.
     * A clash is renamed rather than overwritten, so nothing the visitor saved is lost; whatever does
     * not fit stays with the session, most recently edited bags claimed first.
     */
    @Transactional
    public ClaimResult claimSessionProfiles(String sessionId, String userId) {
        List<BagProfile> incoming =
                repository.findAllByOwnerKeyOrderByUpdatedAtDesc(sessionOwnerKey(sessionId));
        if (incoming.isEmpty()) {
            return ClaimResult.NOTHING;
        }

        String userKey = userOwnerKey(userId);
        Set<String> taken = new HashSet<>();
        repository.findAllByOwnerKeyOrderByUpdatedAtDesc(userKey)
                .forEach(profile -> taken.add(nameKey(profile.name())));
        long room = MAX_PROFILES_PER_OWNER - (long) taken.size();

        int moved = 0;
        int renamed = 0;
        for (BagProfile profile : incoming) {
            if (moved >= room) {
                break;
            }
            String name = profile.name();
            if (taken.contains(nameKey(name))) {
                name = firstFreeName(name, taken);
                renamed++;
            }
            profile.claimedBy(userKey, name);
            taken.add(nameKey(name));
            moved++;
        }

        int leftBehind = incoming.size() - moved;
        log.info("Moved {} saved bag(s) from session {} to user {} ({} renamed, {} left behind)",
                moved, sessionId, userId, renamed, leftBehind);
        return new ClaimResult(moved, renamed, leftBehind);
    }

    /** Names are unique per owner ignoring case, matching how {@link #save} finds an existing one. */
    private static String nameKey(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    /** "Wooded" becomes "Wooded (2)", or "(3)" if that is taken too, still within the column limit. */
    static String firstFreeName(String name, Set<String> taken) {
        for (int n = 2; ; n++) {
            String suffix = " (" + n + ")";
            String base = name.length() + suffix.length() > MAX_NAME_LENGTH
                    ? name.substring(0, MAX_NAME_LENGTH - suffix.length()).stripTrailing()
                    : name;
            String candidate = base + suffix;
            if (!taken.contains(nameKey(candidate))) {
                return candidate;
            }
        }
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
