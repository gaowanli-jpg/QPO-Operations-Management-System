package com.machining.quote.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "erp.auth.data-center-sso")
public class DataCenterSsoProperties {
    private boolean enabled = true;
    private String appCode = "QPO";
    private String sharedSecret = "";
    private int timestampSkewSeconds = 120;
    private int nonceTtlMinutes = 10;
    private String defaultRedirect = "/dashboard";
    private List<String> allowedRedirectPrefixes = new ArrayList<>(List.of("/", "/dashboard", "/orders"));

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getAppCode() {
        return appCode;
    }

    public void setAppCode(String appCode) {
        this.appCode = appCode;
    }

    public String getSharedSecret() {
        return sharedSecret;
    }

    public void setSharedSecret(String sharedSecret) {
        this.sharedSecret = sharedSecret;
    }

    public int getTimestampSkewSeconds() {
        return timestampSkewSeconds;
    }

    public void setTimestampSkewSeconds(int timestampSkewSeconds) {
        this.timestampSkewSeconds = timestampSkewSeconds;
    }

    public int getNonceTtlMinutes() {
        return nonceTtlMinutes;
    }

    public void setNonceTtlMinutes(int nonceTtlMinutes) {
        this.nonceTtlMinutes = nonceTtlMinutes;
    }

    public String getDefaultRedirect() {
        return defaultRedirect;
    }

    public void setDefaultRedirect(String defaultRedirect) {
        this.defaultRedirect = defaultRedirect;
    }

    public List<String> getAllowedRedirectPrefixes() {
        return allowedRedirectPrefixes;
    }

    public void setAllowedRedirectPrefixes(List<String> allowedRedirectPrefixes) {
        this.allowedRedirectPrefixes = allowedRedirectPrefixes;
    }
}
