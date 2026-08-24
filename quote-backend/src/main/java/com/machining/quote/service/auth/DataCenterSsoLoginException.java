package com.machining.quote.service.auth;

import org.springframework.http.HttpStatus;

public class DataCenterSsoLoginException extends RuntimeException {
    private final HttpStatus status;

    public DataCenterSsoLoginException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status == null ? HttpStatus.BAD_REQUEST : status;
    }

    public HttpStatus status() {
        return status;
    }
}
