package com.machining.quote.service.auth;

import java.util.List;

public record DataCenterSsoRuntimeConfig(
        String source,
        boolean enabled,
        String appCode,
        String sharedSecret,
        int timestampSkewSeconds,
        int nonceTtlMinutes,
        String defaultRedirect,
        List<String> allowedRedirectPrefixes
) {
}
