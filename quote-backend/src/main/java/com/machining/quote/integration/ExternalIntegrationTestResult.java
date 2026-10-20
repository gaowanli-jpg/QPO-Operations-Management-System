package com.machining.quote.integration;

public record ExternalIntegrationTestResult(
        boolean success,
        String message,
        Integer httpStatus
) {
    public static ExternalIntegrationTestResult success(String message, Integer httpStatus) {
        return new ExternalIntegrationTestResult(true, message, httpStatus);
    }

    public static ExternalIntegrationTestResult failed(String message, Integer httpStatus) {
        return new ExternalIntegrationTestResult(false, message, httpStatus);
    }
}
