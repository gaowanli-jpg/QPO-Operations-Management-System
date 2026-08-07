package com.machining.quote.service;

public record MaterialNoReuseResult(Status status) {
    public enum Status {
        COPIED,
        IDEMPOTENT,
        NOT_APPLICABLE
    }
}
