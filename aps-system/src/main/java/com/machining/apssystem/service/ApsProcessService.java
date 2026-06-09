package com.machining.apssystem.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.machining.apssystem.dto.ApsProcessDTO;
import com.machining.apssystem.dto.ErpProcessDTO;
import com.machining.apssystem.entity.ApsProcess;
import com.machining.apssystem.mapper.ApsProcessMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ApsProcessService {
    private static final String WORK_HOUR_PROCESS = "WORK_HOUR";
    private static final String NON_WORK_HOUR_PROCESS = "NON_WORK_HOUR";

    @Autowired
    private ApsProcessMapper processMapper;

    @Value("${aps.erp.base-url:http://localhost:8080}")
    private String erpBaseUrl;
    @Value("${aps.erp.auth-token:}")
    private String erpAuthToken;

    private final RestTemplate restTemplate = new RestTemplate();

    public List<ApsProcessDTO> listProcesses(String processType) {
        String safeType = normalizeProcessType(processType, true);
        LambdaQueryWrapper<ApsProcess> wrapper = new LambdaQueryWrapper<>();
        if (!safeType.isEmpty()) {
            wrapper.eq(ApsProcess::getProcessType, safeType);
        }
        wrapper.orderByAsc(ApsProcess::getProcessType)
                .orderByAsc(ApsProcess::getSortNo)
                .orderByAsc(ApsProcess::getProcessName)
                .orderByAsc(ApsProcess::getId);
        return processMapper.selectList(wrapper).stream()
                .map(ApsProcessDTO::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> syncFromErp(String processType) {
        String safeType = normalizeProcessType(processType, true);
        ErpProcessDTO[] rows = fetchErpProcesses(safeType);
        int received = rows == null ? 0 : rows.length;
        int imported = 0;
        if (rows != null) {
            for (ErpProcessDTO row : rows) {
                if (row == null || text(row.getProcessName()).isEmpty()) {
                    continue;
                }
                String rowType = normalizeProcessType(row.getProcessType(), false);
                if (!safeType.isEmpty() && !safeType.equals(rowType)) {
                    continue;
                }
                processMapper.upsertFromErp(toProcess(row, rowType));
                imported++;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("receivedCount", received);
        result.put("importedCount", imported);
        result.put("processType", safeType);
        return result;
    }

    private ApsProcess toProcess(ErpProcessDTO row, String processType) {
        ApsProcess process = new ApsProcess();
        process.setProcessName(text(row.getProcessName()));
        process.setProcessType(processType);
        process.setCalcType(text(row.getCalcType()));
        process.setErpSourceId(row.getSourceId());
        process.setSortNo(row.getSortNo());
        return process;
    }

    private ErpProcessDTO[] fetchErpProcesses(String processType) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(trimTrailingSlash(erpBaseUrl) + "/api/aps/source/processes");
        if (!processType.isEmpty()) {
            builder.queryParam("processType", processType);
        }
        URI uri = builder.build(true).toUri();
        HttpHeaders headers = new HttpHeaders();
        if (!text(erpAuthToken).isEmpty()) {
            headers.setBearerAuth(erpAuthToken.trim());
        }
        try {
            ResponseEntity<ErpProcessDTO[]> response = restTemplate.exchange(
                    uri, HttpMethod.GET, new HttpEntity<>(headers), ErpProcessDTO[].class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new IllegalStateException("ERP process sync request failed: " + response.getStatusCode());
            }
            return response.getBody();
        } catch (RestClientResponseException ex) {
            String hint = ex.getStatusCode().value() == 401
                    ? " Check APS_ERP_AUTH_TOKEN and ERP_APS_SOURCE_TOKEN."
                    : "";
            throw new IllegalStateException("ERP process sync request failed: "
                    + ex.getStatusCode().value() + " " + ex.getStatusText() + "." + hint, ex);
        }
    }

    private String normalizeProcessType(String value, boolean allowEmpty) {
        String text = text(value).toUpperCase();
        if (text.isEmpty() && allowEmpty) {
            return "";
        }
        if (!WORK_HOUR_PROCESS.equals(text) && !NON_WORK_HOUR_PROCESS.equals(text)) {
            throw new IllegalArgumentException("processType must be WORK_HOUR or NON_WORK_HOUR.");
        }
        return text;
    }

    private String trimTrailingSlash(String value) {
        String text = text(value);
        if (text.isEmpty()) {
            text = "http://localhost:8080";
        }
        while (text.endsWith("/")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
