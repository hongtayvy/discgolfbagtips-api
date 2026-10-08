package com.discgolfbagtips.api.profile;

import com.discgolfbagtips.api.recommendation.dto.BagAnalysisRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Named bag setups — a "wooded / East Coast" bag kept separately from an "open / West Coast" one.
 *
 * <p>Owned by the signed-in user when the request carries a Supabase access token, otherwise by the
 * session cookie. Signing in does not move anything by itself: the front end calls
 * {@code POST /api/v1/account/claim-session} once, which hands the anonymous bags to the account.
 */
@RestController
@RequestMapping("/api/v1/profiles")
@Tag(name = "Bag profiles", description = "Saved bag setups")
public class BagProfileController {

    private final BagProfileService profileService;

    public BagProfileController(BagProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    @Operation(summary = "List the saved bags of the signed-in user, or of this session")
    public List<BagProfileSummary> list(@AuthenticationPrincipal Jwt user, HttpSession session) {
        return profileService.list(ownerKey(user, session));
    }

    @PostMapping
    @Operation(summary = "Save the current bag under a name",
            description = "Saving under a name that already exists replaces it.")
    public BagProfileDetail save(@Valid @RequestBody SaveProfileRequest request,
            @AuthenticationPrincipal Jwt user, HttpSession session) {
        return profileService.save(ownerKey(user, session), request.name(), request.description(),
                request.bag());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Load a saved bag, ready to re-post to /recommendations or /lineup")
    public BagProfileDetail load(@PathVariable("id") UUID id, @AuthenticationPrincipal Jwt user,
            HttpSession session) {
        return profileService.load(ownerKey(user, session), id);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a saved bag")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id, @AuthenticationPrincipal Jwt user,
            HttpSession session) {
        profileService.delete(ownerKey(user, session), id);
        return ResponseEntity.noContent().build();
    }

    /** {@code user} is null for an anonymous request; an invalid token never gets this far. */
    private String ownerKey(Jwt user, HttpSession session) {
        return user != null
                ? BagProfileService.userOwnerKey(user.getSubject())
                : BagProfileService.sessionOwnerKey(session.getId());
    }

    public record SaveProfileRequest(
            @NotNull @Size(min = 1, max = 120) String name,
            @Size(max = 512) String description,
            @NotNull @Valid BagAnalysisRequest bag) {
    }
}
