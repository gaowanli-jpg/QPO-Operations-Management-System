package com.machining.quote.service.wms;

import com.machining.quote.dto.wms.WmsAllocationReq;
import com.machining.quote.dto.wms.WmsAllocationResp;
import com.machining.quote.integration.ExternalAuthType;
import com.machining.quote.integration.ExternalIntegrationConfig;
import com.machining.quote.integration.ExternalIntegrationConfigService;
import com.machining.quote.integration.ExternalSystemCode;
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
public class WmsAllocationClient {
    private static final Logger log = LoggerFactory.getLogger(WmsAllocationClient.class);

    @Value("${erp.wms.base-url:}")
    private String wmsBaseUrl;
    @Value("${erp.wms.auth-token:}")
    private String wmsAuthToken;
    @Value("${erp.wms.allocation-path:/api/wms/inventory/allocate}")
    private String wmsAllocationPath;
    @Value("${erp.wms.timeout-ms:10000}")
    private Integer wmsTimeoutMs;

    @Autowired(required = false)
    private ExternalIntegrationConfigService externalIntegrationConfigService;

    public WmsAllocationResp allocate(WmsAllocationReq req) {
        ExternalIntegrationConfig config = runtimeConfig();
        if (isBlank(config.baseUrl())) {
            throw new RuntimeException("WMS base URL is not configured");
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
        HttpEntity<WmsAllocationReq> entity = new HttpEntity<>(req, headers);
        String url = trimRight(config.baseUrl()) + normalizePath(config.allocationPath());
        log.debug("WMS allocation request ticketId={} rfqId={} materialNo={} demandQty={}",
                req == null ? null : req.getTicketId(),
                req == null ? null : req.getRfqId(),
                req == null ? null : req.getMaterialNo(),
                req == null ? null : req.getDemandQty());
        try {
            ResponseEntity<WmsAllocationResp> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    WmsAllocationResp.class
            );
            return response.getBody();
        } catch (HttpStatusCodeException error) {
            throw new RuntimeException("WMS allocation failed status=" + error.getStatusCode().value()
                    + " message=" + abbreviate(error.getResponseBodyAsString()), error);
        } catch (ResourceAccessException error) {
            throw new RuntimeException("WMS allocation failed message=" + abbreviate(error.getMessage()), error);
        }
    }

    private ExternalIntegrationConfig runtimeConfig() {
        if (externalIntegrationConfigService != null) {
            return externalIntegrationConfigService.activeConfig(ExternalSystemCode.WMS);
        }
        return new ExternalIntegrationConfig(
                null,
                "WMS legacy fields",
                ExternalSystemCode.WMS,
                false,
                true,
                isBlank(wmsAuthToken) ? ExternalAuthType.NONE : ExternalAuthType.BEARER_TOKEN,
                trimRight(wmsBaseUrl),
                null,
                normalizePath(wmsAllocationPath),
                wmsAuthToken,
                timeout(wmsTimeoutMs, 10000),
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
        String path = isBlank(value) ? "/api/wms/inventory/allocate" : value.trim();
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
