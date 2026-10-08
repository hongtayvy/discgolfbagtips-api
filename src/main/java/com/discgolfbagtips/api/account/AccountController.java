package com.discgolfbagtips.api.account;

import com.discgolfbagtips.api.config.OpenApiConfig;
import com.discgolfbagtips.api.profile.BagProfileService;
import com.discgolfbagtips.api.profile.ClaimResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in Supabase user. Sign-up, sign-in and password resets happen between the front end and
 * Supabase directly; this API never sees a password, only the resulting access token.
 *
 * <p>Every route here requires a valid bearer token (see {@code SecurityConfig}), so {@code user} is
 * never null.
 */
@RestController
@RequestMapping("/api/v1/account")
@Tag(name = "Account", description = "The signed-in user (Supabase Auth bearer token required)")
@SecurityRequirement(name = OpenApiConfig.SUPABASE_BEARER)
public class AccountController {

    private final BagProfileService profileService;

    public AccountController(BagProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    @Operation(summary = "Who the bearer token belongs to",
            description = "Useful for the front end to confirm the API accepts its token.")
    public Account me(@AuthenticationPrincipal Jwt user) {
        return new Account(user.getSubject(), user.getClaimAsString("email"));
    }

    @PostMapping("/claim-session")
    @Operation(summary = "Move this session's saved bags to the signed-in account",
            description = "Call once after sign-in, with both the session cookie and the bearer token. "
                    + "A bag whose name the account already uses is kept under a \" (2)\"-style name. "
                    + "Safe to repeat: a session with nothing left to claim moves nothing.")
    public ClaimResult claimSession(@AuthenticationPrincipal Jwt user, HttpSession session) {
        return profileService.claimSessionProfiles(session.getId(), user.getSubject());
    }

    /** {@code id} is the Supabase user UUID; {@code email} is absent for phone or anonymous sign-ins. */
    public record Account(String id, String email) {
    }
}
