package com.machining.quote.service.mes;

import org.springframework.http.HttpStatus;

public class MesMaterialCallbackException extends RuntimeException {
    private final HttpStatus status;

    public MesMaterialCallbackException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
