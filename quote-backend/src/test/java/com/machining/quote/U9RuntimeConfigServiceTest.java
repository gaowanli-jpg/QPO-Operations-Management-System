package com.machining.quote;

import com.machining.quote.dto.U9RuntimeConfigDTO;
import com.machining.quote.dto.U9RuntimeConfigReq;
import com.machining.quote.entity.U9RuntimeConfig;
import com.machining.quote.mapper.U9RuntimeConfigMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.u9.U9RuntimeConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class U9RuntimeConfigServiceTest {

    @Test
    void getSettingUsesDatabaseOrgCodeBeforeEnvAndDefault() {
        U9RuntimeConfigMapper mapper = mock(U9RuntimeConfigMapper.class);
        U9RuntimeConfigService service = service(mapper, mock(AuditService.class), "105");
        U9RuntimeConfig config = new U9RuntimeConfig();
        config.setId(1);
        config.setOrgCode(" 106 ");
        config.setUpdatedAt(LocalDateTime.of(2026, 8, 24, 9, 0));
        when(mapper.selectById(1)).thenReturn(config);

        U9RuntimeConfigDTO setting = service.getSetting();

        assertThat(setting.getOrgCode()).isEqualTo("106");
        assertThat(setting.getSource()).isEqualTo("DB");
        assertThat(setting.isFallback()).isFalse();
    }

    @Test
    void getSettingFallsBackToEnvThenDefault101() {
        U9RuntimeConfigMapper mapper = mock(U9RuntimeConfigMapper.class);
        when(mapper.selectById(1)).thenReturn(null);

        assertThat(service(mapper, mock(AuditService.class), " 105 ").getSetting().getOrgCode()).isEqualTo("105");
        assertThat(service(mapper, mock(AuditService.class), " ").getSetting().getOrgCode()).isEqualTo("101");
    }

    @Test
    void updateSettingOnlyPersistsOrgCodeAndRecordsAudit() {
        U9RuntimeConfigMapper mapper = mock(U9RuntimeConfigMapper.class);
        AuditService auditService = mock(AuditService.class);
        U9RuntimeConfigService service = service(mapper, auditService, "101");
        U9RuntimeConfig existing = new U9RuntimeConfig();
        existing.setId(1);
        existing.setOrgCode("101");
        when(mapper.selectById(1)).thenReturn(existing);
        U9RuntimeConfigReq req = new U9RuntimeConfigReq();
        req.setOrgCode(" 106 ");

        U9RuntimeConfigDTO updated = service.updateSetting(req);

        assertThat(updated.getOrgCode()).isEqualTo("106");
        verify(mapper).updateById(any(U9RuntimeConfig.class));
        verify(auditService).record(
                eq("SYSTEM_U9_ORG_CONFIG_UPDATE"),
                eq("u9_runtime_config"),
                eq(1),
                eq(Map.of("orgCode", "101")),
                eq(Map.of("orgCode", "106"))
        );
    }

    @Test
    void updateSettingRejectsBlankOrgCode() {
        U9RuntimeConfigReq req = new U9RuntimeConfigReq();
        req.setOrgCode(" ");

        assertThatThrownBy(() -> service(mock(U9RuntimeConfigMapper.class), mock(AuditService.class), "101").updateSetting(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("orgCode");
    }

    @Test
    void controllerExposesOnlyRuntimeOrgCodeEndpointsWithExistingPermissions() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src", "main", "java", "com", "machining", "quote", "controller", "U9RuntimeConfigController.java"
        ));

        assertThat(source)
                .contains("@RequestMapping(\"/api/system/u9-runtime-config\")")
                .contains("@GetMapping")
                .contains("@PutMapping")
                .contains("@RequirePermission(\"system:config:view\")")
                .contains("@RequirePermission(\"system:config:integration:update\")");
    }

    private U9RuntimeConfigService service(U9RuntimeConfigMapper mapper, AuditService auditService, String envOrgCode) {
        U9RuntimeConfigService service = new U9RuntimeConfigService();
        ReflectionTestUtils.setField(service, "configMapper", mapper);
        ReflectionTestUtils.setField(service, "auditService", auditService);
        ReflectionTestUtils.setField(service, "envOrgCode", envOrgCode);
        return service;
    }
}
