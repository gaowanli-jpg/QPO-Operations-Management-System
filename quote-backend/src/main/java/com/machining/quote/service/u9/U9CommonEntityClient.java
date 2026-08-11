package com.machining.quote.service.u9;

import com.machining.quote.dto.u9.U9CommonEntityQueryReq;
import com.machining.quote.dto.u9.U9CommonEntityResp;
import com.machining.quote.dto.u9.U9CommonEntityRowDTO;
import com.machining.quote.service.DataCenterEndpointRegistry;
import com.machining.quote.service.DataCenterIntegrationClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class U9CommonEntityClient {
    private static final Pattern AUTHORIZATION_FIELD = Pattern.compile("(?i)authorization\\s*[:=]\\s*Bearer\\s+[^\\s,;}]++");
    private static final Pattern SENSITIVE_ASSIGNMENT = Pattern.compile(
            "(?i)\\b(token|client[_-]?secret|clientsecret|password|cookie)\\s*[:=]\\s*[^\\s,;}]+"
    );

    @Autowired
    private DataCenterIntegrationClient dataCenterIntegrationClient;

    public List<U9CommonEntityRowDTO> query(U9CommonEntityQueryReq req) {
        U9CommonEntityResp resp;
        try {
            resp = dataCenterIntegrationClient.call(
                    DataCenterEndpointRegistry.U9_COMMON_ENTITY_QUERY,
                    req,
                    U9CommonEntityResp.class
            );
        } catch (RuntimeException error) {
            throw new IllegalStateException("U9 common entity query failed message=" + sanitize(error.getMessage()));
        }
        if (resp == null) {
            throw new IllegalStateException("U9 common entity query failed message=empty response");
        }
        if (!Boolean.TRUE.equals(resp.getSuccess()) || (resp.getResCode() != null && resp.getResCode() != 0)) {
            throw new IllegalStateException("U9 common entity query failed resCode="
                    + (resp.getResCode() == null ? "unknown" : resp.getResCode())
                    + " message=" + sanitize(resp.getResMsg()));
        }
        Set<String> allowedFields = req == null || req.getReturnFields() == null
                ? Set.of()
                : req.getReturnFields().stream().filter(field -> field != null && !field.isBlank()).collect(Collectors.toSet());
        return safeList(resp.getData()).stream()
                .map(row -> whitelist(row, allowedFields))
                .toList();
    }

    private U9CommonEntityRowDTO whitelist(U9CommonEntityRowDTO row, Set<String> allowedFields) {
        U9CommonEntityRowDTO result = new U9CommonEntityRowDTO();
        Map<String, Object> values = new LinkedHashMap<>();
        if (row != null && row.getValues() != null) {
            for (String field : allowedFields) {
                Object value = row.value(field);
                if (value != null) {
                    values.put(field, value);
                }
            }
        }
        result.setValues(values);
        return result;
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    public static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "U9 common entity unavailable";
        }
        String text = value.replaceAll("\\s+", " ").trim();
        text = AUTHORIZATION_FIELD.matcher(text).replaceAll("[REDACTED]");
        text = Pattern.compile("(?i)Bearer\\s+[^\\s,;}]+").matcher(text).replaceAll("[REDACTED]");
        text = SENSITIVE_ASSIGNMENT.matcher(text).replaceAll("[REDACTED]");
        return text.length() <= 200 ? text : text.substring(0, 200) + "...";
    }
}
