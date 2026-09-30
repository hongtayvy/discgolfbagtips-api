package com.discgolfbagtips.api.common;

import org.springframework.http.HttpStatus;

/** Base class for errors that carry an intended HTTP status all the way to the client. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String reason;

    public ApiException(HttpStatus status, String reason, String message) {
        super(message);
        this.status = status;
        this.reason = reason;
    }

    public ApiException(HttpStatus status, String reason, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.reason = reason;
    }

    public HttpStatus status() {
        return status;
    }

    public String reason() {
        return reason;
    }
}
