package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.machining.quote.dto.SystemIntegrationConfigDTO;
import com.machining.quote.dto.SystemIntegrationConfigSaveReq;
import com.machining.quote.entity.SystemExternalIntegrationConfig;
import com.machining.quote.integration.ExternalIntegrationConfigService;
import com.machining.quote.integration.ExternalIntegrationTestResult;
import com.machining.quote.integration.ExternalIntegrationTestService;
import com.machining.quote.mapper.SystemExternalIntegrationConfigMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.SystemIntegrationConfigManageService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SystemIntegrationConfigManageServiceTest {

    @Test
    void controllerExposesIntegrationConfigEndpointsWithExpectedPermissions() throws Exception {
        String source = Files.readString(Path.of(
                "src", "main", "java", "com", "machining", "quote", "controller", "SystemIntegrationConfigController.java"
        ), StandardCharsets.UTF_8);

        assertThat(source)
                .contains("@RequestMapping(\"/api/system/integration-configs\")")
                .contains("@GetMapping")
                .contains("@GetMapping(\"/active\")")
                .contains("@PostMapping")
                .contains("@PutMapping(\"/{id}\")")
                .contains("@PostMapping(\"/{id}/test\")")
                .contains("@PutMapping(\"/{id}/activate\")")
                .contains("@PutMapping(\"/{id}/disable\")")
                .contains("@RequirePermission(\"system:config:view\")")
                .contains("@RequirePermission(\"system:config:integration:update\")")
                .contains("@RequirePermission(\"system:config:integration:test\")")
                .contains("@RequirePermission(\"system:config:integration:activate\")");
    }

    @Test
    void listConfigsSupportsOptionalSystemFilterAndRedactsSecrets() {
        SystemExternalIntegrationConfigMapper mapper = mock(SystemExternalIntegrationConfigMapper.class);
        SystemIntegrationConfigManageService service = service(mapper, mock(ExternalIntegrationTestService.class), mock(AuditService.class));
        when(mapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(mesConfig(2L, "plain-token")));

        List<SystemIntegrationConfigDTO> configs = service.listConfigs("mes");

        assertThat(configs).hasSize(1);
        assertThat(configs.get(0).getSystemCode()).isEqualTo("MES");
        assertThat(configs.get(0).getAuthToken()).isEqualTo("******");
        assertThat(configs.toString()).doesNotContain("plain-token");
    }

    @Test
    void createU9ConfigRedactsResponseAndRecordsSecretSafeAuditSummary() {
        SystemExternalIntegrationConfigMapper mapper = mock(SystemExternalIntegrationConfigMapper.class);
        AuditService auditService = mock(AuditService.class);
        SystemIntegrationConfigManageService service = service(mapper, mock(ExternalIntegrationTestService.class), auditService);
        doAnswer(invocation -> {
            SystemExternalIntegrationConfig entity = invocation.getArgument(0);
            entity.setId(8L);
            return 1;
        }).when(mapper).insert(any(SystemExternalIntegrationConfig.class));

        SystemIntegrationConfigDTO result = service.saveConfig(null, u9Req("u9-secret"));

        assertThat(result.getId()).isEqualTo(8L);
        assertThat(result.getClientSecret()).isEqualTo("******");
        ArgumentCaptor<SystemExternalIntegrationConfig> entityCaptor = ArgumentCaptor.forClass(SystemExternalIntegrationConfig.class);
        verify(mapper).insert(entityCaptor.capture());
        assertThat(String.valueOf(entityCaptor.getValue().getConfigJson())).contains("u9-secret");
        ArgumentCaptor<Object> afterCaptor = ArgumentCaptor.forClass(Object.class);
        verify(auditService).record(eq("SYSTEM_INTEGRATION_CONFIG_CREATE"), eq("system_external_integration_config"), eq(8L), isNull(), afterCaptor.capture());
        assertThat(String.valueOf(afterCaptor.getValue()))
                .contains("clientSecretChanged=true")
                .doesNotContain("u9-secret");
    }

    @Test
    void updateKeepsExistingSecretWhenBlankOrMaskedAndResetsTestStatusAfterConnectionChange() {
        SystemExternalIntegrationConfigMapper mapper = mock(SystemExternalIntegrationConfigMapper.class);
        AuditService auditService = mock(AuditService.class);
        SystemIntegrationConfigManageService service = service(mapper, mock(ExternalIntegrationTestService.class), auditService);
        SystemExternalIntegrationConfig existing = u9Config(9L, "old-secret");
        existing.setLastTestStatus("SUCCESS");
        existing.setLastTestMessage("previous ok");
        existing.setLastTestAt(LocalDateTime.now().minusDays(1));
        when(mapper.selectById(9L)).thenReturn(existing);
        SystemIntegrationConfigSaveReq req = u9Req("******");
        req.setBaseUrl("http://u9-new");

        SystemIntegrationConfigDTO result = service.saveConfig(9L, req);

        assertThat(result.getClientSecret()).isEqualTo("******");
        ArgumentCaptor<SystemExternalIntegrationConfig> entityCaptor = ArgumentCaptor.forClass(SystemExternalIntegrationConfig.class);
        verify(mapper).updateById(entityCaptor.capture());
        SystemExternalIntegrationConfig updated = entityCaptor.getValue();
        assertThat(String.valueOf(updated.getConfigJson())).contains("old-secret", "u9-new");
        assertThat(updated.getLastTestStatus()).isNull();
        assertThat(updated.getLastTestMessage()).isNull();
        assertThat(updated.getLastTestAt()).isNull();
        ArgumentCaptor<Object> beforeCaptor = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> afterCaptor = ArgumentCaptor.forClass(Object.class);
        verify(auditService).record(eq("SYSTEM_INTEGRATION_CONFIG_UPDATE"), eq("system_external_integration_config"), eq(9L), beforeCaptor.capture(), afterCaptor.capture());
        assertThat(String.valueOf(beforeCaptor.getValue())).doesNotContain("old-secret");
        assertThat(String.valueOf(afterCaptor.getValue())).doesNotContain("old-secret");
    }

    @Test
    void testConfigPersistsSuccessOrSanitizedFailureAndRecordsAudit() {
        SystemExternalIntegrationConfigMapper mapper = mock(SystemExternalIntegrationConfigMapper.class);
        ExternalIntegrationTestService testService = mock(ExternalIntegrationTestService.class);
        AuditService auditService = mock(AuditService.class);
        SystemIntegrationConfigManageService service = service(mapper, testService, auditService);
        SystemExternalIntegrationConfig entity = mesConfig(3L, "plain-token");
        when(mapper.selectById(3L)).thenReturn(entity);
        when(testService.test(any())).thenReturn(ExternalIntegrationTestResult.success("ok plain-token", 200));

        Map<String, Object> result = service.testConfig(3L);

        assertThat(result).containsEntry("success", true);
        assertThat(String.valueOf(result)).doesNotContain("plain-token");
        ArgumentCaptor<SystemExternalIntegrationConfig> updateCaptor = ArgumentCaptor.forClass(SystemExternalIntegrationConfig.class);
        verify(mapper).updateById(updateCaptor.capture());
        assertThat(updateCaptor.getValue().getLastTestStatus()).isEqualTo("SUCCESS");
        assertThat(updateCaptor.getValue().getLastTestMessage()).doesNotContain("plain-token");
        verify(auditService).record(eq("SYSTEM_INTEGRATION_CONFIG_TEST"), eq("system_external_integration_config"), eq(3L), isNull(), any());
    }

    @Test
    void testConfigFailurePersistsFailedStatusAndRecordsFailedAuditWithoutSecrets() {
        SystemExternalIntegrationConfigMapper mapper = mock(SystemExternalIntegrationConfigMapper.class);
        ExternalIntegrationTestService testService = mock(ExternalIntegrationTestService.class);
        AuditService auditService = mock(AuditService.class);
        SystemIntegrationConfigManageService service = service(mapper, testService, auditService);
        SystemExternalIntegrationConfig entity = mesConfig(7L, "plain-token");
        when(mapper.selectById(7L)).thenReturn(entity);
        when(testService.test(any())).thenReturn(ExternalIntegrationTestResult.failed("cannot authToken=plain-token", 401));

        Map<String, Object> result = service.testConfig(7L);

        assertThat(result).containsEntry("success", false);
        assertThat(String.valueOf(result)).doesNotContain("plain-token");
        ArgumentCaptor<SystemExternalIntegrationConfig> updateCaptor = ArgumentCaptor.forClass(SystemExternalIntegrationConfig.class);
        verify(mapper).updateById(updateCaptor.capture());
        assertThat(updateCaptor.getValue().getLastTestStatus()).isEqualTo("FAILED");
        verify(auditService).record(
                eq("SYSTEM_INTEGRATION_CONFIG_TEST_FAILED"),
                eq("system_external_integration_config"),
                eq(7L),
                isNull(),
                any(),
                eq("SYSTEM"),
                eq("FAILED"),
                eq("Integration config test failed"),
                any()
        );
    }

    @Test
    void activateRequiresSuccessfulTestAndSwitchesOnlySameSystemActiveConfig() {
        SystemExternalIntegrationConfigMapper mapper = mock(SystemExternalIntegrationConfigMapper.class);
        AuditService auditService = mock(AuditService.class);
        SystemIntegrationConfigManageService service = service(mapper, mock(ExternalIntegrationTestService.class), auditService);
        SystemExternalIntegrationConfig entity = mesConfig(4L, "plain-token");
        entity.setEnabled(true);
        entity.setActive(false);
        entity.setLastTestStatus("SUCCESS");
        when(mapper.selectById(4L)).thenReturn(entity);

        SystemIntegrationConfigDTO result = service.activateConfig(4L);

        assertThat(result.getActive()).isTrue();
        ArgumentCaptor<UpdateWrapper<SystemExternalIntegrationConfig>> wrapperCaptor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(mapper).update(any(SystemExternalIntegrationConfig.class), wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment()).contains("system_code");
        verify(mapper).updateById(any(SystemExternalIntegrationConfig.class));
        verify(auditService).record(eq("SYSTEM_INTEGRATION_CONFIG_ACTIVATE"), eq("system_external_integration_config"), eq(4L), isNull(), any());
    }

    @Test
    void activateRejectsUntestedConfigAndDisableRejectsActiveConfig() {
        SystemExternalIntegrationConfigMapper mapper = mock(SystemExternalIntegrationConfigMapper.class);
        SystemIntegrationConfigManageService service = service(mapper, mock(ExternalIntegrationTestService.class), mock(AuditService.class));
        SystemExternalIntegrationConfig untested = mesConfig(5L, "plain-token");
        untested.setLastTestStatus("FAILED");
        when(mapper.selectById(5L)).thenReturn(untested);

        assertThatThrownBy(() -> service.activateConfig(5L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("test");

        SystemExternalIntegrationConfig active = mesConfig(6L, "plain-token");
        active.setActive(true);
        when(mapper.selectById(6L)).thenReturn(active);

        assertThatThrownBy(() -> service.disableConfig(6L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("active");
        verify(mapper, never()).updateById(any(SystemExternalIntegrationConfig.class));
    }

    @Test
    void auditActionsExposeSystemModuleAndReadableLabels() {
        assertThat(AuditService.inferModule("SYSTEM_INTEGRATION_CONFIG_CREATE")).isEqualTo("SYSTEM");
        assertThat(AuditService.labelForAction("SYSTEM_INTEGRATION_CONFIG_CREATE")).contains("出站接口");
        assertThat(AuditService.labelForAction("SYSTEM_INTEGRATION_CONFIG_UPDATE")).contains("出站接口");
        assertThat(AuditService.labelForAction("SYSTEM_INTEGRATION_CONFIG_TEST")).contains("出站接口");
        assertThat(AuditService.labelForAction("SYSTEM_INTEGRATION_CONFIG_TEST_FAILED")).contains("出站接口");
        assertThat(AuditService.labelForAction("SYSTEM_INTEGRATION_CONFIG_ACTIVATE")).contains("出站接口");
        assertThat(AuditService.labelForAction("SYSTEM_INTEGRATION_CONFIG_DISABLE")).contains("出站接口");
    }

    private SystemIntegrationConfigManageService service(
            SystemExternalIntegrationConfigMapper mapper,
            ExternalIntegrationTestService testService,
            AuditService auditService) {
        SystemIntegrationConfigManageService service = new SystemIntegrationConfigManageService();
        ReflectionTestUtils.setField(service, "configMapper", mapper);
        ReflectionTestUtils.setField(service, "configService", new ExternalIntegrationConfigService(mapper));
        ReflectionTestUtils.setField(service, "testService", testService);
        ReflectionTestUtils.setField(service, "auditService", auditService);
        return service;
    }

    private SystemExternalIntegrationConfig mesConfig(Long id, String token) {
        SystemExternalIntegrationConfig entity = new SystemExternalIntegrationConfig();
        entity.setId(id);
        entity.setName("MES");
        entity.setSystemCode("MES");
        entity.setAuthType("BEARER_TOKEN");
        entity.setActive(false);
        entity.setEnabled(true);
        entity.setConfigJson(new LinkedHashMap<>(Map.of(
                "baseUrl", "http://mes.internal",
                "materialPath", "/api/mes/materials/resolve",
                "authToken", token,
                "timeoutMs", 10000
        )));
        return entity;
    }

    private SystemExternalIntegrationConfig u9Config(Long id, String secret) {
        SystemExternalIntegrationConfig entity = new SystemExternalIntegrationConfig();
        entity.setId(id);
        entity.setName("U9");
        entity.setSystemCode("U9");
        entity.setAuthType("U9_AUTH_LOGIN");
        entity.setActive(false);
        entity.setEnabled(true);
        entity.setConfigJson(new LinkedHashMap<>(Map.of(
                "baseUrl", "http://u9.internal",
                "authLoginPath", "/webapi/OAuth2/AuthLogin",
                "salesOrderCreatePath", "/webapi/SO/Create",
                "userCode", "qpo",
                "entCode", "ent",
                "orgCode", "org",
                "clientId", "client",
                "clientSecret", secret,
                "connectTimeoutMs", 5000,
                "readTimeoutMs", 30000
        )));
        return entity;
    }

    private SystemIntegrationConfigSaveReq u9Req(String secret) {
        SystemIntegrationConfigSaveReq req = new SystemIntegrationConfigSaveReq();
        req.setName("U9");
        req.setSystemCode("U9");
        req.setAuthType("U9_AUTH_LOGIN");
        req.setBaseUrl("http://u9.internal");
        req.setAuthLoginPath("/webapi/OAuth2/AuthLogin");
        req.setSalesOrderCreatePath("/webapi/SO/Create");
        req.setUserCode("qpo");
        req.setEntCode("ent");
        req.setOrgCode("org");
        req.setClientId("client");
        req.setClientSecret(secret);
        req.setConnectTimeoutMs(5000);
        req.setReadTimeoutMs(30000);
        req.setEnabled(true);
        return req;
    }
}
