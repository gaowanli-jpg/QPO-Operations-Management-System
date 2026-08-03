package com.machining.quote.service.mes;

import com.machining.quote.dto.mes.MesProductionDispatchReq;
import com.machining.quote.dto.mes.MesProductionDispatchResp;
import com.machining.quote.integration.ExternalAuthType;
import com.machining.quote.integration.ExternalIntegrationConfig;
import com.machining.quote.integration.ExternalIntegrationConfigService;
import com.machining.quote.integration.ExternalSystemCode;
import com.machining.quote.service.DataCenterEndpointRegistry;
import com.machining.quote.service.DataCenterIntegrationClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@Service
public class MesProductionDispatchClient {
    private static final Logger log = LoggerFactory.getLogger(MesProductionDispatchClient.class);

    @Value("${erp.mes.base-url:}")
    private String mesBaseUrl;
    @Value("${erp.mes.auth-token:}")
    private String mesAuthToken;
    @Value("${erp.mes.production-path:/api/mes/production/submit}")
    private String mesProductionPath;
    @Value("${erp.mes.timeout-ms:10000}")
    private Integer mesTimeoutMs;
    @Value("${erp.data-center.enabled:true}")
    private Boolean dataCenterEnabled = true;

    @Autowired(required = false)
    private DataCenterIntegrationClient dataCenterIntegrationClient;

    @Autowired(required = false)
    private ExternalIntegrationConfigService externalIntegrationConfigService;

    public MesProductionDispatchResp dispatch(MesProductionDispatchReq req) {
        if (useDataCenter()) {
            log.debug("MES production dispatch data center request allocationId={} ticketId={} requestId={} productionQty={} ticketQty={}",
                    req == null ? null : req.getAllocationId(),
                    req == null ? null : req.getTicketId(),
                    req == null ? null : req.getRequestId(),
                    req == null ? null : req.getProductionQty(),
                    req == null ? null : req.getTicketQty());
            return dataCenterIntegrationClient.call(
                    DataCenterEndpointRegistry.MES_PRODUCTION_DISPATCH,
                    req,
                    MesProductionDispatchResp.class
            );
        }
        ExternalIntegrationConfig config = runtimeConfig();
        if (isBlank(config.baseUrl())) {
            throw new RuntimeException("MES base URL is not configured");
        }
        int timeout = timeout(config.timeoutMs(), 10000);
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        RestTemplate restTemplate = new RestTemplate(requestFactory);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (!isBlank(config.authToken())) {
            headers.set("Authorization", "Bearer " + config.authToken().trim());
        }
        HttpEntity<MesProductionDispatchReq> entity = new HttpEntity<>(req, headers);
        String url = trimRight(config.baseUrl()) + normalizePath(mesProductionPath);
        log.debug("MES production dispatch request allocationId={} ticketId={} requestId={} productionQty={} ticketQty={}",
                req == null ? null : req.getAllocationId(),
                req == null ? null : req.getTicketId(),
                req == null ? null : req.getRequestId(),
                req == null ? null : req.getProductionQty(),
                req == null ? null : req.getTicketQty());
        try {
            ResponseEntity<MesProductionDispatchResp> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    MesProductionDispatchResp.class
            );
            return response.getBody();
        } catch (HttpStatusCodeException error) {
            throw new RuntimeException("MES production dispatch failed status=" + error.getStatusCode().value()
                    + " message=" + abbreviate(error.getResponseBodyAsString()), error);
        } catch (ResourceAccessException error) {
            throw new RuntimeException("MES production dispatch failed message=" + abbreviate(error.getMessage()), error);
        }
    }

    private boolean useDataCenter() {
        return Boolean.TRUE.equals(dataCenterEnabled) && dataCenterIntegrationClient != null;
    }

    private ExternalIntegrationConfig runtimeConfig() {
        if (externalIntegrationConfigService != null) {
            ExternalIntegrationConfig config = externalIntegrationConfigService.activeConfig(ExternalSystemCode.MES);
            return new ExternalIntegrationConfig(
                    config.id(),
                    config.name(),
                    config.systemCode(),
                    config.active(),
                    config.enabled(),
                    config.authType(),
                    trimRight(config.baseUrl()),
                    config.materialPath(),
                    config.allocationPath(),
                    config.authToken(),
                    timeout(config.timeoutMs(), 10000),
                    config.authLoginPath(),
                    config.salesOrderCreatePath(),
                    config.userCode(),
                    config.entCode(),
                    config.orgCode(),
                    config.clientId(),
                    config.clientSecret(),
                    config.connectTimeoutMs(),
                    config.readTimeoutMs(),
                    config.remark()
            );
        }
        return new ExternalIntegrationConfig(
                null,
                "MES production legacy fields",
                ExternalSystemCode.MES,
                false,
                true,
                isBlank(mesAuthToken) ? ExternalAuthType.NONE : ExternalAuthType.BEARER_TOKEN,
                trimRight(mesBaseUrl),
                null,
                null,
                mesAuthToken,
                timeout(mesTimeoutMs, 10000),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private String normalizePath(String value) {
        String path = isBlank(value) ? "/api/mes/production/submit" : value.trim();
        return path.startsWith("/") ? path : "/" + path;
    }

    private String trimRight(String value) {
        if (isBlank(value)) {
            return null;
        }
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private String abbreviate(String value) {
        if (value == null) {
            return "";
        }
        String text = value.replaceAll("\\s+", " ").trim();
        return text.length() <= 200 ? text : text.substring(0, 200) + "...";
    }

    private int timeout(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
