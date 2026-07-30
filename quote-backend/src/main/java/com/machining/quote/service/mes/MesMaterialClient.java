package com.machining.quote.service.mes;

import com.machining.quote.dto.mes.MesMaterialResolveReq;
import com.machining.quote.dto.mes.MesMaterialResolveResp;
import com.machining.quote.integration.ExternalAuthType;
import com.machining.quote.integration.ExternalIntegrationConfig;
import com.machining.quote.integration.ExternalIntegrationConfigService;
import com.machining.quote.integration.ExternalSystemCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class MesMaterialClient {
    private static final Logger log = LoggerFactory.getLogger(MesMaterialClient.class);

    @Value("${erp.mes.base-url:}")
    private String mesBaseUrl;
    @Value("${erp.mes.auth-token:}")
    private String mesAuthToken;
    @Value("${erp.mes.material-path:/api/mes/materials/resolve}")
    private String mesMaterialPath;
    @Value("${erp.mes.timeout-ms:10000}")
    private Integer mesTimeoutMs;

    @Autowired(required = false)
    private ExternalIntegrationConfigService externalIntegrationConfigService;

    public MesMaterialResolveResp resolveMaterial(MesMaterialResolveReq req) {
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
        HttpEntity<MesMaterialResolveReq> entity = new HttpEntity<>(req, headers);
        String url = trimRight(config.baseUrl()) + normalizePath(config.materialPath());
        log.debug("MES material resolve request ticketId={} rfqId={} partNo={}",
                req == null ? null : req.getTicketId(),
                req == null ? null : req.getRfqId(),
                req == null ? null : req.getPartNo());
        try {
            ResponseEntity<MesMaterialResolveResp> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    MesMaterialResolveResp.class
            );
            return response.getBody();
        } catch (HttpStatusCodeException error) {
            throw new RuntimeException("MES material resolve failed status=" + error.getStatusCode().value()
                    + " message=" + abbreviate(error.getResponseBodyAsString()), error);
        } catch (ResourceAccessException error) {
            throw new RuntimeException("MES material resolve failed message=" + abbreviate(error.getMessage()), error);
        }
    }

    private ExternalIntegrationConfig runtimeConfig() {
        if (externalIntegrationConfigService != null) {
            return externalIntegrationConfigService.activeConfig(ExternalSystemCode.MES);
        }
        return new ExternalIntegrationConfig(
                null,
                "MES legacy fields",
                ExternalSystemCode.MES,
                false,
                true,
                isBlank(mesAuthToken) ? ExternalAuthType.NONE : ExternalAuthType.BEARER_TOKEN,
                trimRight(mesBaseUrl),
                normalizePath(mesMaterialPath),
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
        String path = isBlank(value) ? "/api/mes/materials/resolve" : value.trim();
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
