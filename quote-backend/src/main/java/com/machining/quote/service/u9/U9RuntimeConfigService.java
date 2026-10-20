package com.machining.quote.service.u9;

import com.machining.quote.dto.U9RuntimeConfigDTO;
import com.machining.quote.dto.U9RuntimeConfigReq;
import com.machining.quote.entity.U9RuntimeConfig;
import com.machining.quote.mapper.U9RuntimeConfigMapper;
import com.machining.quote.service.AuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class U9RuntimeConfigService {
    private static final int CONFIG_ID = 1;
    private static final String DEFAULT_ORG_CODE = "101";

    @Autowired
    private U9RuntimeConfigMapper configMapper;
    @Autowired
    private AuditService auditService;

    @Value("${erp.u9.org-code:}")
    private String envOrgCode;

    public U9RuntimeConfigDTO getSetting() {
        U9RuntimeConfig config = configMapper == null ? null : configMapper.selectById(CONFIG_ID);
        String dbOrgCode = config == null ? null : trimToNull(config.getOrgCode());
        if (dbOrgCode != null) {
            return dto(dbOrgCode, "DB", config.getUpdatedAt(), false);
        }
        String env = trimToNull(envOrgCode);
        if (env != null) {
            return dto(env, "ENV", null, true);
        }
        return dto(DEFAULT_ORG_CODE, "DEFAULT", null, true);
    }

    public String effectiveOrgCode() {
        return getSetting().getOrgCode();
    }

    @Transactional
    public U9RuntimeConfigDTO updateSetting(U9RuntimeConfigReq req) {
        String orgCode = requireOrgCode(req == null ? null : req.getOrgCode());
        U9RuntimeConfig existing = configMapper.selectById(CONFIG_ID);
        Map<String, Object> before = Map.of("orgCode", existing == null ? getSetting().getOrgCode() : firstNonBlank(existing.getOrgCode(), DEFAULT_ORG_CODE));

        U9RuntimeConfig entity = existing == null ? new U9RuntimeConfig() : existing;
        entity.setId(CONFIG_ID);
        entity.setOrgCode(orgCode);
        entity.setUpdatedAt(LocalDateTime.now());
        if (existing == null) {
            configMapper.insert(entity);
        } else {
            configMapper.updateById(entity);
        }
        Map<String, Object> after = Map.of("orgCode", orgCode);
        if (auditService != null) {
            auditService.record("SYSTEM_U9_ORG_CONFIG_UPDATE", "u9_runtime_config", CONFIG_ID, before, after);
        }
        return dto(orgCode, "DB", entity.getUpdatedAt(), false);
    }

    private U9RuntimeConfigDTO dto(String orgCode, String source, LocalDateTime updatedAt, boolean fallback) {
        U9RuntimeConfigDTO dto = new U9RuntimeConfigDTO();
        dto.setOrgCode(orgCode);
        dto.setSource(source);
        dto.setUpdatedAt(updatedAt);
        dto.setFallback(fallback);
        return dto;
    }

    private String requireOrgCode(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException("U9 orgCode cannot be blank");
        }
        if (normalized.length() > 40) {
            throw new IllegalArgumentException("U9 orgCode cannot exceed 40 characters");
        }
        return normalized;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = trimToNull(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return DEFAULT_ORG_CODE;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
