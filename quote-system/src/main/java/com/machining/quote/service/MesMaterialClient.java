package com.machining.quote.service;

import com.machining.quote.dto.MesMaterialResolveReq;
import com.machining.quote.dto.MesMaterialResolveResp;
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

    public MesMaterialResolveResp resolveMaterial(MesMaterialResolveReq req) {
        if (isBlank(mesBaseUrl)) {
            throw new RuntimeException("MES base URL is not configured");
        }
        int timeout = mesTimeoutMs == null || mesTimeoutMs <= 0 ? 10000 : mesTimeoutMs;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        RestTemplate restTemplate = new RestTemplate(requestFactory);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (!isBlank(mesAuthToken)) {
            headers.set("Authorization", "Bearer " + mesAuthToken.trim());
        }
        HttpEntity<MesMaterialResolveReq> entity = new HttpEntity<>(req, headers);
        String url = trimRight(mesBaseUrl) + normalizePath(mesMaterialPath);
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
        }
    }

    private String normalizePath(String value) {
        String path = isBlank(value) ? "/api/mes/materials/resolve" : value.trim();
        return path.startsWith("/") ? path : "/" + path;
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
