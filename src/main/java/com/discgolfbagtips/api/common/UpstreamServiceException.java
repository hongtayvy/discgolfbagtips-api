package com.discgolfbagtips.api.common;

import org.springframework.http.HttpStatus;

/** A third-party dependency (DiscIt, Hugging Face, Groq/OpenRouter) failed or was unavailable. */
public class UpstreamServiceException extends ApiException {

    private final String service;

    public UpstreamServiceException(String service, String message) {
        this(service, message, null);
    }

    public UpstreamServiceException(String service, String message, Throwable cause) {
        super(HttpStatus.BAD_GATEWAY, "upstream-unavailable", message, cause);
        this.service = service;
    }

    public String service() {
        return service;
    }
}
