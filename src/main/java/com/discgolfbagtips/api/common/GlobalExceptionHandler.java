package com.discgolfbagtips.api.common;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Arrays;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import tools.jackson.databind.exc.InvalidFormatException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Translates everything the pipeline can throw into RFC 9457 problem details. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String TYPE_PREFIX = "https://discgolfbagtips.com/problems/";

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ProblemDetail> handleRateLimit(RateLimitExceededException ex) {
        ProblemDetail problem = problem(ex.status(), ex.reason(), "Rate limit exceeded", ex.getMessage());
        problem.setProperty("retryAfterSeconds", Math.max(1, ex.retryAfter().toSeconds()));
        return ResponseEntity.status(ex.status())
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, ex.retryAfter().toSeconds())))
                .body(problem);
    }

    @ExceptionHandler(UpstreamServiceException.class)
    public ResponseEntity<ProblemDetail> handleUpstream(UpstreamServiceException ex) {
        log.warn("Upstream service '{}' failed: {}", ex.service(), ex.getMessage());
        ProblemDetail problem = problem(ex.status(), ex.reason(), "Upstream service unavailable", ex.getMessage());
        problem.setProperty("service", ex.service());
        return ResponseEntity.status(ex.status()).body(problem);
    }

    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<ProblemDetail> handleOpenCircuit(CallNotPermittedException ex) {
        log.warn("Circuit breaker open: {}", ex.getMessage());
        ProblemDetail problem = problem(HttpStatus.SERVICE_UNAVAILABLE, "circuit-open",
                "Dependency temporarily disabled",
                "A downstream dependency is failing and its circuit breaker is open. Try again shortly.");
        problem.setProperty("circuitBreaker", ex.getCausingCircuitBreakerName());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(problem);
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApi(ApiException ex) {
        return ResponseEntity.status(ex.status())
                .body(problem(ex.status(), ex.reason(), ex.status().getReasonPhrase(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new TreeMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        ex.getBindingResult().getGlobalErrors()
                .forEach(error -> fieldErrors.putIfAbsent(error.getObjectName(), error.getDefaultMessage()));
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "validation-failed", "Request validation failed",
                "One or more fields are invalid.");
        problem.setProperty("errors", fieldErrors);
        return ResponseEntity.badRequest().body(problem);
    }

    /**
     * A body Jackson could not read at all: a misspelled enum, a string where a number belongs, or
     * truncated JSON. That is the caller's mistake, so it must not surface as a 500 — and naming the
     * offending field (and the values we would have accepted) saves a round of guessing.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadableBody(HttpMessageNotReadableException ex) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "malformed-request", "Malformed request body",
                "The request body could not be read as JSON.");

        if (ex.getCause() instanceof InvalidFormatException invalid) {
            String field = describePath(invalid);
            if (!field.isBlank()) {
                problem.setProperty("field", field);
            }
            Class<?> target = invalid.getTargetType();
            if (target != null && target.isEnum()) {
                problem.setProperty("accepted",
                        Arrays.stream(target.getEnumConstants()).map(Object::toString).sorted().toList());
                problem.setDetail("'%s' is not a valid value for %s."
                        .formatted(String.valueOf(invalid.getValue()), field.isBlank() ? "this field" : field));
            } else {
                problem.setDetail(field.isBlank()
                        ? "A value in the request body has the wrong type."
                        : "The value supplied for '%s' has the wrong type.".formatted(field));
            }
        }
        return ResponseEntity.badRequest().body(problem);
    }

    /** Renders Jackson's path as conventional JSON-path notation, e.g. {@code bag[0].wear}. */
    private String describePath(InvalidFormatException ex) {
        StringBuilder path = new StringBuilder();
        for (var reference : ex.getPath()) {
            String property = reference.getPropertyName();
            if (property == null) {
                path.append('[').append(reference.getIndex()).append(']');
            } else {
                if (!path.isEmpty()) {
                    path.append('.');
                }
                path.append(property);
            }
        }
        return path.toString();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(problem(HttpStatus.BAD_REQUEST, "invalid-request", "Invalid request", ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.internalServerError()
                .body(problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "Unexpected error",
                        "Something went wrong on our side."));
    }

    private ProblemDetail problem(HttpStatus status, String reason, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_PREFIX + reason));
        problem.setTitle(title);
        problem.setProperty("timestamp", Instant.now().toString());
        return problem;
    }
}
