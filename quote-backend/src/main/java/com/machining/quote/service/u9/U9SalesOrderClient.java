package com.machining.quote.service.u9;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.machining.quote.dto.u9.U9ApiResult;
import com.machining.quote.dto.u9.U9ResultDTO;
import com.machining.quote.dto.u9.U9SalesOrderHeadDTO;
import com.machining.quote.integration.ExternalAuthType;
import com.machining.quote.integration.ExternalIntegrationConfig;
import com.machining.quote.integration.ExternalIntegrationConfigService;
import com.machining.quote.integration.ExternalSystemCode;
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

import java.util.List;

@Service
public class U9SalesOrderClient {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${erp.u9.base-url:}")
    private String u9BaseUrl;
    @Value("${erp.u9.sales-order-create-path:/webapi/SO/Create}")
    private String u9SalesOrderCreatePath;
    @Value("${erp.u9.connect-timeout-ms:5000}")
    private Integer connectTimeoutMs;
    @Value("${erp.u9.read-timeout-ms:30000}")
    private Integer readTimeoutMs;

    @Autowired
    private U9TokenService tokenService;

    @Autowired(required = false)
    private ExternalIntegrationConfigService externalIntegrationConfigService;

    public U9ApiResult<U9ResultDTO> create(List<U9SalesOrderHeadDTO> orders) {
        ExternalIntegrationConfig config = runtimeConfig();
        if (isBlank(config.baseUrl())) {
            throw new IllegalStateException("ERP_U9_BASE_URL is required for U9 sales order sync");
        }
        if (orders == null || orders.isEmpty()) {
            throw new IllegalArgumentException("U9 sales order request cannot be empty");
        }
        String token = tokenService.fetchToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("token", token);

        String url = trimRight(config.baseUrl()) + normalizePath(config.salesOrderCreatePath());
        HttpEntity<List<U9SalesOrderHeadDTO>> entity = new HttpEntity<>(orders, headers);
        try {
            ResponseEntity<String> response = restTemplate(config).exchange(url, HttpMethod.POST, entity, String.class);
            return objectMapper.readValue(response.getBody(), new TypeReference<>() {});
        } catch (HttpStatusCodeException error) {
            throw new RuntimeException("U9 sales order create failed status=" + error.getStatusCode().value()
                    + " message=" + abbreviate(error.getResponseBodyAsString()), error);
        } catch (ResourceAccessException error) {
            throw new RuntimeException("U9 sales order create failed message=" + abbreviate(error.getMessage()), error);
        } catch (Exception error) {
            throw new RuntimeException("U9 sales order create response parse failed message=" + abbreviate(error.getMessage()), error);
        }
    }

    private ExternalIntegrationConfig runtimeConfig() {
        if (externalIntegrationConfigService != null) {
            return externalIntegrationConfigService.activeConfig(ExternalSystemCode.U9);
        }
        return new ExternalIntegrationConfig(
                null,
                "U9 legacy fields",
                ExternalSystemCode.U9,
                false,
                true,
                ExternalAuthType.U9_AUTH_LOGIN,
                trimRight(u9BaseUrl),
                null,
                null,
                null,
                null,
                null,
                normalizePath(u9SalesOrderCreatePath),
                null,
                null,
                null,
                null,
                null,
                timeout(connectTimeoutMs, 5000),
                timeout(readTimeoutMs, 30000),
                null
        );
    }

    private RestTemplate restTemplate(ExternalIntegrationConfig config) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout(config.connectTimeoutMs(), 5000));
        requestFactory.setReadTimeout(timeout(config.readTimeoutMs(), 30000));
        return new RestTemplate(requestFactory);
    }

    private int timeout(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private String normalizePath(String value) {
        String path = isBlank(value) ? "/webapi/SO/Create" : value.trim();
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

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
