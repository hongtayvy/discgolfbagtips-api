package com.discgolfbagtips.api.config;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.savedrequest.NullRequestCache;

/**
 * Accounts are optional. Every endpoint stays public; a request that carries a Supabase access token
 * is simply attributed to that user instead of to the session cookie. Only {@code /api/v1/account}
 * requires a signed-in user, because it has no meaning for an anonymous visitor.
 *
 * <p>A bearer token that is present but invalid or expired is rejected with 401 even on a public
 * endpoint, rather than silently downgraded to anonymous: saving a bag "as the session" while the
 * user believes they are signed in would lose it the moment the cookie expires.
 */
@Configuration
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    public SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        AuthenticationEntryPoint entryPoint = problemEntryPoint();
        http
                // Matches the behaviour before Spring Security arrived. Bearer-token requests cannot be
                // forged cross-site; the cookie session was already unprotected and still is — a
                // known gap for the anonymous path, not a regression.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                // Spring Security would otherwise stash a rejected request in the HTTP session,
                // creating sessions for callers who never asked for one.
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/account/**").authenticated()
                        .anyRequest().permitAll())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(entryPoint))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(entryPoint));
        return http.build();
    }

    /**
     * Verifies against the project's published JWKS, so rotating keys in the Supabase dashboard needs
     * no redeploy. Supabase signs with ES256 by default and offers RS256; the legacy HS256 shared
     * secret is deliberately not supported, as it would mean holding a signing secret here.
     */
    @Bean
    public JwtDecoder jwtDecoder(BagTipsProperties properties) {
        BagTipsProperties.Auth auth = properties.auth();
        if (!auth.enabled()) {
            log.info("bagtips.auth.supabase-url is not set: accounts are disabled, sessions only");
            return token -> {
                throw new BadJwtException("Sign-in is not configured on this server.");
            };
        }

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(auth.jwkSetUri())
                .jwsAlgorithms(algorithms -> {
                    algorithms.add(SignatureAlgorithm.ES256);
                    algorithms.add(SignatureAlgorithm.RS256);
                })
                .build();

        OAuth2TokenValidator<Jwt> audience = new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                aud -> aud != null && aud.contains(auth.audience()));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(auth.issuer()), audience));
        return decoder;
    }

    /**
     * The standard bearer entry point sets {@code WWW-Authenticate} but leaves the body empty; the
     * front end shows the API's own problem detail, so give it one in the same RFC 9457 shape the
     * rest of the API uses.
     */
    private static AuthenticationEntryPoint problemEntryPoint() {
        BearerTokenAuthenticationEntryPoint bearer = new BearerTokenAuthenticationEntryPoint();
        return (request, response, exception) -> {
            bearer.commence(request, response, exception);
            // A token was sent and refused, versus no token on an endpoint that needs one.
            String detail = exception instanceof OAuth2AuthenticationException
                    ? "Your sign-in has expired or is invalid. Sign in again."
                    : "Sign in to use this endpoint.";
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("""
                    {"type":"https://discgolfbagtips.com/problems/unauthorized","title":"Unauthorized",\
                    "status":401,"detail":"%s"}""".formatted(detail));
        };
    }
}
