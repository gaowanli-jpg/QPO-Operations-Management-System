package com.machining.quote.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "erp.data-center")
public class DataCenterProperties {
    private boolean enabled = true;
    private String baseUrl = "http://192.168.70.250:8080";
    private String tokenUrl = "http://192.168.70.250:8080/realms/api-platform/protocol/openid-connect/token";
    private String clientId = "app_8ed28b2a1faf4a0b";
    private String clientSecret = "";
    private String grantType = "client_credentials";
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 60000;
    private int tokenExpireSkewSeconds = 60;
    private String mesMaterialPath = "/api/v1/mes/openapi/material-preparation/create";
    private String mesProcessTypeListPath = "/api/v1/mes/openapi/process-type/list";
    private String mesMaterialPagePath = "/api/v1/mes/openapi/material/page";
    private String wmsAllocationPath = "";
    private String u9SalesOrderSyncPath = "";
    private String u9CommonEntityQueryPath = "";
    private String mesProductionDispatchPath = "/api/v1/mes/openapi/demand/create";
    private String mesStockPickingOrderCreatePath = "/api/v1/mes/openapi/production-order/create";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getTokenUrl() {
        return tokenUrl;
    }

    public void setTokenUrl(String tokenUrl) {
        this.tokenUrl = tokenUrl;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getGrantType() {
        return grantType;
    }

    public void setGrantType(String grantType) {
        this.grantType = grantType;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    public int getTokenExpireSkewSeconds() {
        return tokenExpireSkewSeconds;
    }

    public void setTokenExpireSkewSeconds(int tokenExpireSkewSeconds) {
        this.tokenExpireSkewSeconds = tokenExpireSkewSeconds;
    }

    public String getMesMaterialPath() {
        return mesMaterialPath;
    }

    public void setMesMaterialPath(String mesMaterialPath) {
        this.mesMaterialPath = mesMaterialPath;
    }

    public String getMesProcessTypeListPath() {
        return mesProcessTypeListPath;
    }

    public void setMesProcessTypeListPath(String mesProcessTypeListPath) {
        this.mesProcessTypeListPath = mesProcessTypeListPath;
    }

    public String getMesMaterialPagePath() {
        return mesMaterialPagePath;
    }

    public void setMesMaterialPagePath(String mesMaterialPagePath) {
        this.mesMaterialPagePath = mesMaterialPagePath;
    }

    public String getWmsAllocationPath() {
        return wmsAllocationPath;
    }

    public void setWmsAllocationPath(String wmsAllocationPath) {
        this.wmsAllocationPath = wmsAllocationPath;
    }

    public String getU9SalesOrderSyncPath() {
        return u9SalesOrderSyncPath;
    }

    public void setU9SalesOrderSyncPath(String u9SalesOrderSyncPath) {
        this.u9SalesOrderSyncPath = u9SalesOrderSyncPath;
    }

    public String getU9CommonEntityQueryPath() {
        return u9CommonEntityQueryPath;
    }

    public void setU9CommonEntityQueryPath(String u9CommonEntityQueryPath) {
        this.u9CommonEntityQueryPath = u9CommonEntityQueryPath;
    }

    public String getMesProductionDispatchPath() {
        return mesProductionDispatchPath;
    }

    public void setMesProductionDispatchPath(String mesProductionDispatchPath) {
        this.mesProductionDispatchPath = mesProductionDispatchPath;
    }

    public String getMesStockPickingOrderCreatePath() {
        return mesStockPickingOrderCreatePath;
    }

    public void setMesStockPickingOrderCreatePath(String mesStockPickingOrderCreatePath) {
        this.mesStockPickingOrderCreatePath = mesStockPickingOrderCreatePath;
    }
}
