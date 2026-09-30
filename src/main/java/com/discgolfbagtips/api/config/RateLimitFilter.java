package com.discgolfbagtips.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UriUtils;

/**
 * Applies the token buckets before anything expensive happens. Callers are keyed by session id when
 * one exists (the app is session-based, so this is the closest thing to a user) and by client IP
 * otherwise.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RateLimitFilter extends OncePerRequestFilter {

    /** The endpoints that spend embedding and reasoning-model quota rather than just reading. */
    private static final String[] EXPENSIVE_PATHS = {"/api/v1/recommendations", "/api/v1/lineup"};

    private final RateLimitService rateLimitService;

    public RateLimitFilter(RateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/") || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        RateLimitService.Tier tier = isExpensive(request)
                ? RateLimitService.Tier.RECOMMENDATION
                : RateLimitService.Tier.GENERAL;

        TokenBucket.Verdict verdict = rateLimitService.check(clientKey(request), tier);

        if (verdict.limit() >= 0) {
            response.setHeader("X-RateLimit-Limit", String.valueOf(verdict.limit()));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, verdict.remaining())));
        }

        if (!verdict.allowed()) {
            long retryAfterSeconds = Math.max(1, verdict.retryAfter().toSeconds());
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write("""
                    {"type":"https://discgolfbagtips.com/problems/rate-limited",\
                    "title":"Rate limit exceeded","status":429,\
                    "detail":"Too many requests. Retry in %d second(s).",\
                    "retryAfterSeconds":%d}""".formatted(retryAfterSeconds, retryAfterSeconds));
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean isExpensive(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        for (String path : EXPENSIVE_PATHS) {
            if (request.getRequestURI().startsWith(path)) {
                return true;
            }
        }
        return false;
    }

    private String clientKey(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            return "session:" + session.getId();
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = forwarded != null && !forwarded.isBlank()
                ? forwarded.split(",")[0].trim()
                : request.getRemoteAddr();
        return "ip:" + UriUtils.encode(ip == null ? "unknown" : ip, java.nio.charset.StandardCharsets.UTF_8);
    }
}
