package com.machining.quote.service.u9;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.machining.quote.dto.u9.U9ApiResult;
import com.machining.quote.dto.u9.U9ResultDTO;
import com.machining.quote.dto.u9.U9SalesOrderHeadDTO;
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
    @Value("${erp.u9.connect-timeout-ms:5000}")
    private Integer connectTimeoutMs;
    @Value("${erp.u9.read-timeout-ms:30000}")
    private Integer readTimeoutMs;

    @Autowired
    private U9TokenService tokenService;

    public U9ApiResult<U9ResultDTO> create(List<U9SalesOrderHeadDTO> orders) {
        if (isBlank(u9BaseUrl)) {
            throw new IllegalStateException("ERP_U9_BASE_URL is required for U9 sales order sync");
        }
        if (orders == null || orders.isEmpty()) {
            throw new IllegalArgumentException("U9 sales order request cannot be empty");
        }
        String token = tokenService.fetchToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("token", token);

        String url = trimRight(u9BaseUrl) + "/webapi/SO/Create";
        HttpEntity<List<U9SalesOrderHeadDTO>> entity = new HttpEntity<>(orders, headers);
        try {
            ResponseEntity<String> response = restTemplate().exchange(url, HttpMethod.POST, entity, String.class);
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

    private RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout(connectTimeoutMs, 5000));
        requestFactory.setReadTimeout(timeout(readTimeoutMs, 30000));
        return new RestTemplate(requestFactory);
    }

    private int timeout(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private String trimRight(String value) {
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
