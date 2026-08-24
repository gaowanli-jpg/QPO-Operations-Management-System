package com.machining.quote.service.u9;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class U9OrgCodeResolver {
    private static final String DEFAULT_ORG_CODE = "101";

    private final U9RuntimeConfigService configService;

    @Autowired
    public U9OrgCodeResolver(U9RuntimeConfigService configService) {
        this.configService = configService;
    }

    public String effectiveOrgCode() {
        return firstNonBlank(configService == null ? null : configService.effectiveOrgCode(), DEFAULT_ORG_CODE);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                return trimmed;
            }
        }
        return DEFAULT_ORG_CODE;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
