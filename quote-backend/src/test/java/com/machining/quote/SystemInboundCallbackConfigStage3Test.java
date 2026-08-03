package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.dto.SystemInboundCallbackConfigDTO;
import com.machining.quote.dto.SystemInboundCallbackConfigSaveReq;
import com.machining.quote.dto.SystemInboundCallbackTokenResp;
import com.machining.quote.entity.SystemInboundCallbackConfig;
import com.machining.quote.integration.InboundCallbackCode;
import com.machining.quote.mapper.SystemInboundCallbackConfigMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.SystemInboundCallbackConfigService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SystemInboundCallbackConfigStage3Test {
    private static final String OLD_TOKEN = "old-callback-token";
    private static final String OLD_HASH = sha256Hex(OLD_TOKEN);

    @Test
    void controllerExposesInboundCallbackEndpointsWithExpectedPermissions() throws Exception {
        String source = Files.readString(Path.of(
                "src", "main", "java", "com", "machining", "quote", "controller", "SystemInboundCallbackConfigController.java"
        ), StandardCharsets.UTF_8);

        assertThat(source)
                .contains("@RequestMapping(\"/api/system/inbound-callback-configs\")")
                .contains("@GetMapping")
                .contains("@GetMapping(\"/mes-material-callback\")")
                .contains("@PutMapping(\"/mes-material-callback\")")
                .contains("@PostMapping(\"/mes-material-callback/token\")")
                .contains("@PostMapping(\"/mes-material-callback/validate\")")
                .contains("@RequirePermission(\"system:config:view\")")
                .contains("@RequirePermission(\"system:config:inbound:update\")")
                .contains("@RequirePermission(\"system:config:inbound:token-rotate\")");
    }

    @Test
    void queryDtosDoNotExposePlainTokenOrTokenHash() {
        SystemInboundCallbackConfigMapper mapper = mock(SystemInboundCallbackConfigMapper.class);
        SystemInboundCallbackConfigService service = service(mapper, mock(AuditService.class));
        SystemInboundCallbackConfig entity = config(11L);
        when(mapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(entity));
        when(mapper.selectOne(any(QueryWrapper.class))).thenReturn(entity);

        List<SystemInboundCallbackConfigDTO> configs = service.listConfigs();
        SystemInboundCallbackConfigDTO detail = service.getMesMaterialCallbackConfig();

        assertThat(configs).hasSize(1);
        assertThat(detail.getCallbackCode()).isEqualTo("MES_MATERIAL_CALLBACK");
        assertThat(detail.getTokenConfigured()).isTrue();
        assertThat(detail.getTokenMask()).isEqualTo("***oken");
        assertThat(detail.getTokenUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 8, 3, 10, 30));
        assertThat(String.valueOf(configs)).doesNotContain(OLD_TOKEN, OLD_HASH);
        assertThat(String.valueOf(detail)).doesNotContain(OLD_TOKEN, OLD_HASH);
        assertThat(SystemInboundCallbackConfigDTO.class.getDeclaredFields())
                .extracting("name")
                .doesNotContain("token", "tokenHash", "authorization");
    }

    @Test
    void saveConfigUpdatesSafeFieldsAndRecordsSecretSafeAudit() {
        SystemInboundCallbackConfigMapper mapper = mock(SystemInboundCallbackConfigMapper.class);
        AuditService auditService = mock(AuditService.class);
        SystemInboundCallbackConfigService service = service(mapper, auditService);
        SystemInboundCallbackConfig existing = config(12L);
        when(mapper.selectOne(any(QueryWrapper.class))).thenReturn(existing);

        SystemInboundCallbackConfigSaveReq req = new SystemInboundCallbackConfigSaveReq();
        req.setPublicBaseUrl(" http://qpo.example.com ");
        req.setEnabled(false);
        req.setTokenEnabled(true);
        req.setRemark("  MES callback entry  ");

        SystemInboundCallbackConfigDTO dto = service.saveMesMaterialCallbackConfig(req);

        assertThat(dto.getPublicBaseUrl()).isEqualTo("http://qpo.example.com");
        assertThat(dto.getTokenConfigured()).isTrue();
        ArgumentCaptor<SystemInboundCallbackConfig> updateCaptor = ArgumentCaptor.forClass(SystemInboundCallbackConfig.class);
        verify(mapper).updateById(updateCaptor.capture());
        SystemInboundCallbackConfig updated = updateCaptor.getValue();
        assertThat(updated.getCallbackCode()).isEqualTo("MES_MATERIAL_CALLBACK");
        assertThat(updated.getSourceSystemCode()).isEqualTo("MES");
        assertThat(updated.getCallbackPath()).isEqualTo("/api/integrations/mes/material-callback");
        assertThat(updated.getTokenHash()).isEqualTo(OLD_HASH);
        assertThat(updated.getTokenMask()).isEqualTo("***oken");

        ArgumentCaptor<Object> beforeCaptor = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> afterCaptor = ArgumentCaptor.forClass(Object.class);
        verify(auditService).record(eq("SYSTEM_INBOUND_CALLBACK_CONFIG_UPDATE"), eq("system_inbound_callback_config"), eq(12L), beforeCaptor.capture(), afterCaptor.capture());
        assertThat(String.valueOf(beforeCaptor.getValue())).doesNotContain(OLD_TOKEN, OLD_HASH, "Authorization");
        assertThat(String.valueOf(afterCaptor.getValue())).doesNotContain(OLD_TOKEN, OLD_HASH, "Authorization");
    }

    @Test
    void rotateTokenReturnsPlainTextOnceStoresHashAndInvalidatesOldToken() {
        SystemInboundCallbackConfigMapper mapper = mock(SystemInboundCallbackConfigMapper.class);
        AuditService auditService = mock(AuditService.class);
        SystemInboundCallbackConfigService service = service(mapper, auditService);
        SystemInboundCallbackConfig existing = config(13L);
        when(mapper.selectOne(any(QueryWrapper.class))).thenReturn(existing);

        SystemInboundCallbackTokenResp resp = service.rotateMesMaterialCallbackToken();

        assertThat(resp.getCallbackCode()).isEqualTo("MES_MATERIAL_CALLBACK");
        assertThat(resp.getToken()).isNotBlank();
        assertThat(resp.getTokenMask()).startsWith("***");
        ArgumentCaptor<SystemInboundCallbackConfig> updateCaptor = ArgumentCaptor.forClass(SystemInboundCallbackConfig.class);
        verify(mapper).updateById(updateCaptor.capture());
        SystemInboundCallbackConfig updated = updateCaptor.getValue();
        assertThat(updated.getTokenHash()).isEqualTo(sha256Hex(resp.getToken()));
        assertThat(updated.getTokenHash()).isNotEqualTo(OLD_HASH);
        assertThat(updated.getTokenHash()).doesNotContain(resp.getToken());

        when(mapper.selectOne(any(QueryWrapper.class))).thenReturn(updated);
        assertThat(service.hasValidBearerToken(InboundCallbackCode.MES_MATERIAL_CALLBACK, "Bearer " + OLD_TOKEN)).isFalse();
        assertThat(service.hasValidBearerToken(InboundCallbackCode.MES_MATERIAL_CALLBACK, "Bearer " + resp.getToken())).isTrue();
        assertThat(String.valueOf(service.getMesMaterialCallbackConfig())).doesNotContain(resp.getToken(), updated.getTokenHash());

        ArgumentCaptor<Object> afterCaptor = ArgumentCaptor.forClass(Object.class);
        verify(auditService).record(eq("SYSTEM_INBOUND_CALLBACK_TOKEN_ROTATE"), eq("system_inbound_callback_config"), eq(13L), isNull(), afterCaptor.capture());
        assertThat(String.valueOf(afterCaptor.getValue())).doesNotContain(resp.getToken(), updated.getTokenHash(), "Authorization");
    }

    @Test
    void validateConfigPersistsStatusWithoutCallingBusinessCallback() {
        SystemInboundCallbackConfigMapper mapper = mock(SystemInboundCallbackConfigMapper.class);
        AuditService auditService = mock(AuditService.class);
        SystemInboundCallbackConfigService service = service(mapper, auditService);
        SystemInboundCallbackConfig entity = config(14L);
        entity.setPublicBaseUrl("");
        entity.setTokenHash(null);
        when(mapper.selectOne(any(QueryWrapper.class))).thenReturn(entity);

        Map<String, Object> result = service.validateMesMaterialCallbackConfig();

        assertThat(result).containsEntry("success", false);
        assertThat(String.valueOf(result)).contains("publicBaseUrl");
        ArgumentCaptor<SystemInboundCallbackConfig> updateCaptor = ArgumentCaptor.forClass(SystemInboundCallbackConfig.class);
        verify(mapper).updateById(updateCaptor.capture());
        assertThat(updateCaptor.getValue().getLastValidateStatus()).isEqualTo("FAILED");
        assertThat(updateCaptor.getValue().getLastValidateMessage()).doesNotContain("token=", OLD_TOKEN, OLD_HASH, "Authorization");
        verify(auditService).record(eq("SYSTEM_INBOUND_CALLBACK_CONFIG_VALIDATE"), eq("system_inbound_callback_config"), eq(14L), isNull(), any());
    }

    @Test
    void auditActionsExposeSystemModuleAndReadableLabels() {
        assertThat(AuditService.inferModule("SYSTEM_INBOUND_CALLBACK_CONFIG_UPDATE")).isEqualTo("SYSTEM");
        assertThat(AuditService.labelForAction("SYSTEM_INBOUND_CALLBACK_CONFIG_UPDATE")).contains("入站");
        assertThat(AuditService.labelForAction("SYSTEM_INBOUND_CALLBACK_TOKEN_ROTATE")).contains("回调");
        assertThat(AuditService.labelForAction("SYSTEM_INBOUND_CALLBACK_CONFIG_VALIDATE")).contains("入站");

        AuditService auditService = new AuditService();
        Object redacted = ReflectionTestUtils.invokeMethod(auditService, "redact", Map.of(
                "tokenHash", OLD_HASH,
                "authorizationHeader", "Bearer " + OLD_TOKEN,
                "callbackCode", "MES_MATERIAL_CALLBACK"
        ));
        assertThat(String.valueOf(redacted)).doesNotContain(OLD_TOKEN, OLD_HASH);
    }

    private SystemInboundCallbackConfigService service(SystemInboundCallbackConfigMapper mapper, AuditService auditService) {
        SystemInboundCallbackConfigService service = new SystemInboundCallbackConfigService();
        ReflectionTestUtils.setField(service, "configMapper", mapper);
        ReflectionTestUtils.setField(service, "auditService", auditService);
        ReflectionTestUtils.setField(service, "fallbackPublicBaseUrl", "http://env-qpo.example.com");
        ReflectionTestUtils.setField(service, "fallbackCallbackToken", "env-token");
        ReflectionTestUtils.setField(service, "fallbackCallbackTokenEnabled", true);
        return service;
    }

    private SystemInboundCallbackConfig config(Long id) {
        SystemInboundCallbackConfig entity = new SystemInboundCallbackConfig();
        entity.setId(id);
        entity.setCallbackCode("MES_MATERIAL_CALLBACK");
        entity.setSourceSystemCode("MES");
        entity.setName("MES物料号回调");
        entity.setEnabled(true);
        entity.setTokenEnabled(true);
        entity.setPublicBaseUrl("http://qpo.internal");
        entity.setCallbackPath("/api/integrations/mes/material-callback");
        entity.setTokenHash(OLD_HASH);
        entity.setTokenMask("***oken");
        entity.setTokenUpdatedAt(LocalDateTime.of(2026, 8, 3, 10, 30));
        entity.setRemark("callback config");
        return entity;
    }

    private static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
