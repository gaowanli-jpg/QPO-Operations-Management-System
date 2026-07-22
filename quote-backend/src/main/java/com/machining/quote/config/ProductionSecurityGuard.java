package com.machining.quote.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ProductionSecurityGuard {
    private static final String DEFAULT_JWT_SECRET = "change-this-secret-before-production";

    @Value("${erp.auth.enabled:true}")
    private boolean authEnabled;

    @Value("${erp.auth.jwt-secret:}")
    private String jwtSecret;

    @PostConstruct
    public void validateSecurityConfig() {
        if (!authEnabled) {
            return;
        }
        String secret = jwtSecret == null ? "" : jwtSecret.trim();
        if (secret.isEmpty() || DEFAULT_JWT_SECRET.equals(secret) || secret.length() < 32) {
            throw new IllegalStateException("ERP_JWT_SECRET must be changed to a strong random value with at least 32 characters before enabling authentication.");
        }
    }
}
