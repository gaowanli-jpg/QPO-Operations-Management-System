package com.machining.quote.service.mes;

import com.machining.quote.dto.mes.master.MesMaterialPageResp;
import com.machining.quote.dto.mes.master.MesProcessTypeListResp;
import com.machining.quote.service.DataCenterEndpointRegistry;
import com.machining.quote.service.DataCenterIntegrationClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class MesMasterDataClient {
    private static final Pattern AUTHORIZATION_FIELD = Pattern.compile(
            "(?i)authorization[^,;\\s}]*\\s*[:=]\\s*\\\"?Bearer\\s+[^\\s,\\\";}]+\\\"?"
    );
    private static final Pattern BEARER_VALUE = Pattern.compile("(?i)Bearer\\s+[^\\s,;}]+");
    private static final Pattern SENSITIVE_ASSIGNMENT = Pattern.compile(
            "(?i)\\b(token|access_token|refresh_token|client[_-]?secret|clientsecret|password|cookie)\\s*[:=]\\s*[^\\s,;}]+"
    );
    private static final Pattern RESPONSE_BODY = Pattern.compile("(?i)(response|body)\\s*[:=]\\s*\\{.*");

    @Autowired
    private DataCenterIntegrationClient dataCenterIntegrationClient;

    public List<MesProcessTypeListResp.Row> listProcessTypes() {
        MesProcessTypeListResp resp;
        try {
            resp = dataCenterIntegrationClient.callGet(
                    DataCenterEndpointRegistry.MES_PROCESS_TYPE_LIST,
                    Map.of(),
                    MesProcessTypeListResp.class);
        } catch (RuntimeException error) {
            throw failure("MES process type list", null, error.getMessage());
        }
        ensureSuccess("MES process type list", resp == null ? null : resp.getCode(), resp == null ? null : resp.getSuccess(),
                resp == null ? null : firstNonBlank(resp.getMessage(), resp.getException()));
        return resp.rows();
    }

    public MesMaterialPageResp.Data pageMaterials(int pageNo, int pageSize) {
        if (pageNo <= 0 || pageSize <= 0) {
            throw new IllegalArgumentException("MES material pageNo and pageSize must be positive");
        }
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("pageNo", pageNo);
        query.put("pageSize", pageSize);
        MesMaterialPageResp resp;
        try {
            resp = dataCenterIntegrationClient.callGet(
                    DataCenterEndpointRegistry.MES_MATERIAL_PAGE,
                    query,
                    MesMaterialPageResp.class);
        } catch (RuntimeException error) {
            throw failure("MES material page", null, error.getMessage());
        }
        ensureSuccess("MES material page", resp == null ? null : resp.getCode(), resp == null ? null : resp.getSuccess(),
                resp == null ? null : firstNonBlank(resp.getMessage(), resp.getException()));
        return resp.toData();
    }

    private void ensureSuccess(String purpose, Object code, Boolean success, String message) {
        if (code == null && success == null) {
            return;
        }
        if (Boolean.FALSE.equals(success) || !isSuccessCode(code)) {
            throw failure(purpose, code, message);
        }
    }

    private boolean isSuccessCode(Object code) {
        if (code == null) {
            return true;
        }
        if (code instanceof Number number) {
            int value = number.intValue();
            return value == 0 || value == 200;
        }
        String value = String.valueOf(code).trim();
        if (value.isEmpty()) {
            return true;
        }
        String normalized = value.toUpperCase(Locale.ROOT);
        return "0".equals(normalized) || "200".equals(normalized) || "SUCCESS".equals(normalized) || "OK".equals(normalized);
    }

    private IllegalStateException failure(String purpose, Object code, String message) {
        StringBuilder builder = new StringBuilder(purpose).append(" failed");
        if (code != null) {
            builder.append(" code=").append(code);
        }
        builder.append(" message=").append(sanitize(message));
        return new IllegalStateException(builder.toString());
    }

    private String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "MES master data unavailable";
        }
        String text = value.replaceAll("\\s+", " ").trim();
        text = RESPONSE_BODY.matcher(text).replaceAll("[REDACTED]");
        text = AUTHORIZATION_FIELD.matcher(text).replaceAll("[REDACTED]");
        text = BEARER_VALUE.matcher(text).replaceAll("[REDACTED]");
        text = SENSITIVE_ASSIGNMENT.matcher(text).replaceAll("[REDACTED]");
        return text.length() <= 200 ? text : text.substring(0, 200) + "...";
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
