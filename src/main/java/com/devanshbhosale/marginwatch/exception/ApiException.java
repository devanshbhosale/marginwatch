package com.devanshbhosale.marginwatch.exception;

import org.springframework.http.HttpStatus;

/**
 * Base exception carrying the HTTP status the handler should return.
 */
public class ApiException extends RuntimeException {

    private final int status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status.value();
    }

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
