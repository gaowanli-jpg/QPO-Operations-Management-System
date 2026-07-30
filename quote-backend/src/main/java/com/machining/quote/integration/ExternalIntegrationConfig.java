package com.machining.quote.integration;

public record ExternalIntegrationConfig(
        Long id,
        String name,
        ExternalSystemCode systemCode,
        boolean active,
        boolean enabled,
        ExternalAuthType authType,
        String baseUrl,
        String materialPath,
        String allocationPath,
        String authToken,
        Integer timeoutMs,
        String authLoginPath,
        String salesOrderCreatePath,
        String userCode,
        String entCode,
        String orgCode,
        String clientId,
        String clientSecret,
        Integer connectTimeoutMs,
        Integer readTimeoutMs,
        String remark
) {
}
