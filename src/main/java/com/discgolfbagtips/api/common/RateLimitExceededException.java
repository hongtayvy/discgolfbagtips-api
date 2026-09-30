package com.discgolfbagtips.api.common;

import java.time.Duration;
import org.springframework.http.HttpStatus;

public class RateLimitExceededException extends ApiException {

    private final Duration retryAfter;

    public RateLimitExceededException(Duration retryAfter) {
        super(HttpStatus.TOO_MANY_REQUESTS, "rate-limited",
                "Too many requests. Retry in %d second(s).".formatted(Math.max(1, retryAfter.toSeconds())));
        this.retryAfter = retryAfter;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
