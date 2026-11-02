package com.machining.quote.service.mes;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.machining.quote.dto.mes.MesDemandDeleteReq;
import com.machining.quote.dto.mes.MesDemandDeleteResp;
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
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

/**
 * 调用 MES 删除需求任务（{@code POST /api/v1/mes/openapi/demand/delete}）。
 *
 * <p>优先走数据中台端点 {@code MES_DEMAND_DELETE}；数据中心关闭时按 legacy 配置直连。
 * 失败原因（明确失败 / 结果未知）由 {@code DataCenterCallException#getCertainty()} 区分，
 * 供上层决定是否用同一 bizId 重发。
 */
@Service
public class MesDemandDeleteClient {
    private static final Logger log = LoggerFactory.getLogger(MesDemandDeleteClient.class);

    @Value("${erp.mes.base-url:}")
    private String mesBaseUrl;
    @Value("${erp.mes.auth-token:}")
    private String mesAuthToken;
    @Value("${erp.mes.demand-delete-path:/api/v1/mes/openapi/demand/delete}")
    private String mesDemandDeletePath;
    @Value("${erp.mes.timeout-ms:15000}")
    private Integer mesTimeoutMs;
    @Value("${erp.data-center.enabled:true}")
    private Boolean dataCenterEnabled = true;

    @Autowired(required = false)
    private DataCenterIntegrationClient dataCenterIntegrationClient;

    public MesDemandDeleteResp delete(String bizId, Long demandId) {
        MesDemandDeleteReq req = new MesDemandDeleteReq();
        req.setBizId(bizId);
        req.setId(demandId);
        if (useDataCenter()) {
            log.debug("MES demand delete data center request bizId={} id={}", bizId, demandId);
            return dataCenterIntegrationClient.call(
                    DataCenterEndpointRegistry.MES_DEMAND_DELETE,
                    req,
                    MesDemandDeleteResp.class
            );
        }
        return deleteDirect(req);
    }

    private boolean useDataCenter() {
        return Boolean.TRUE.equals(dataCenterEnabled) && dataCenterIntegrationClient != null;
    }

    private MesDemandDeleteResp deleteDirect(MesDemandDeleteReq req) {
        if (isBlank(mesBaseUrl)) {
            throw new RuntimeException("MES base URL is not configured");
        }
        int timeout = mesTimeoutMs == null || mesTimeoutMs <= 0 ? 15000 : mesTimeoutMs;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        RestTemplate restTemplate = new RestTemplate(requestFactory);
        for (HttpMessageConverter<?> converter : restTemplate.getMessageConverters()) {
            if (converter instanceof MappingJackson2HttpMessageConverter jsonConverter) {
                jsonConverter.setObjectMapper(JsonMapper.builder()
                        .addModule(new JavaTimeModule())
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .build());
            }
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        if (!isBlank(mesAuthToken)) {
            headers.setBearerAuth(mesAuthToken.trim());
        }
        HttpEntity<MesDemandDeleteReq> entity = new HttpEntity<>(req, headers);
        String url = trimRight(mesBaseUrl) + normalizePath(mesDemandDeletePath);
        log.debug("MES demand delete request bizId={} id={}", req.getBizId(), req.getId());
        try {
            ResponseEntity<MesDemandDeleteResp> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    MesDemandDeleteResp.class
            );
            return response.getBody();
        } catch (HttpStatusCodeException error) {
            throw new RuntimeException("MES demand delete failed status=" + error.getStatusCode().value()
                    + " message=" + abbreviate(error.getResponseBodyAsString()), error);
        } catch (ResourceAccessException error) {
            throw new RuntimeException("MES demand delete failed message=" + abbreviate(error.getMessage()), error);
        }
    }

    private String normalizePath(String value) {
        String path = isBlank(value) ? "/api/v1/mes/openapi/demand/delete" : value.trim();
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
