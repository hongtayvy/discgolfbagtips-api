package com.discgolfbagtips.api.account;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.discgolfbagtips.api.profile.BagProfileService;
import com.discgolfbagtips.api.profile.ClaimResult;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * The real security filter chain in front of the real controllers: who owns a request, what an
 * anonymous or badly-signed caller gets back, and that the browser can preflight a DELETE.
 *
 * <p>Auth is unconfigured in this profile, so any real bearer token is refused — the {@code jwt()}
 * post-processor stands in for a verified Supabase token.
 */
@SpringBootTest
@ActiveProfiles("contexttest")
class AccountSecurityTest {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private BagProfileService profileService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        when(profileService.list(anyString())).thenReturn(List.of());
    }

    @Test
    void anAnonymousVisitorsBagsBelongToTheirSession() throws Exception {
        mockMvc.perform(get("/api/v1/profiles")).andExpect(status().isOk());

        verify(profileService).list(argThat(key -> key.startsWith("session:")));
    }

    @Test
    void aSignedInUsersBagsBelongToTheirAccount() throws Exception {
        mockMvc.perform(get("/api/v1/profiles").with(jwt().jwt(token -> token.subject("user-123"))))
                .andExpect(status().isOk());

        verify(profileService).list("user:user-123");
    }

    @Test
    void theAccountEndpointNeedsASignedInUser() throws Exception {
        mockMvc.perform(get("/api/v1/account"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", startsWith("Bearer")))
                .andExpect(jsonPath("$.detail").value("Sign in to use this endpoint."));
    }

    @Test
    void theAccountEndpointNamesTheUser() throws Exception {
        mockMvc.perform(get("/api/v1/account")
                        .with(jwt().jwt(token -> token.subject("user-123").claim("email", "a@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("user-123"))
                .andExpect(jsonPath("$.email").value("a@example.com"));
    }

    /** A stale token must not quietly fall back to the session; the user would lose what they save. */
    @Test
    void anInvalidTokenIsRefusedEvenOnAPublicEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/profiles").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value(containsString("expired or is invalid")));
    }

    @Test
    void claimingMovesTheSessionsBagsToTheSignedInUser() throws Exception {
        when(profileService.claimSessionProfiles(anyString(), eq("user-123")))
                .thenReturn(new ClaimResult(2, 1, 0));

        mockMvc.perform(post("/api/v1/account/claim-session")
                        .with(jwt().jwt(token -> token.subject("user-123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moved").value(2))
                .andExpect(jsonPath("$.renamed").value(1));
    }

    @Test
    void aCrossOriginFrontEndMayPreflightADelete() throws Exception {
        mockMvc.perform(options("/api/v1/profiles/00000000-0000-0000-0000-000000000000")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "DELETE")
                        .header("Access-Control-Request-Headers", "authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("DELETE")))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }
}
