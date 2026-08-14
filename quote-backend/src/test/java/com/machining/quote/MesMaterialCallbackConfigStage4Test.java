package com.machining.quote;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.controller.MesIntegrationCallbackController;
import com.machining.quote.dto.mes.MesMaterialCallbackReq;
import com.machining.quote.dto.mes.MesMaterialCallbackResp;
import com.machining.quote.entity.SystemInboundCallbackConfig;
import com.machining.quote.integration.InboundCallbackCode;
import com.machining.quote.mapper.SystemInboundCallbackConfigMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.SystemInboundCallbackConfigService;
import com.machining.quote.service.mes.MesMaterialSyncService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class MesMaterialCallbackConfigStage4Test {

    @Test
    void controllerUsesInboundCallbackConfigServiceInsteadOfDirectEnvTokenFields() throws Exception {
        String source = Files.readString(Path.of(
                "src", "main", "java", "com", "machining", "quote", "controller", "MesIntegrationCallbackController.java"
        ), StandardCharsets.UTF_8);

        assertThat(source)
                .contains("SystemInboundCallbackConfigService")
                .contains("hasValidBearerToken(InboundCallbackCode.MES_MATERIAL_CALLBACK, authorization)")
                .contains("handleMaterialBatchCallback(req)")
                .doesNotContain("handleMaterialCallback(req)")
                .doesNotContain("${erp.mes.callback-token")
                .doesNotContain("private String callbackToken")
                .doesNotContain("private boolean callbackTokenEnabled");
    }

    @Test
    void controllerRejectsWrongTokenBeforeCallingBusinessService() {
        MesMaterialSyncService syncService = mock(MesMaterialSyncService.class);
        SystemInboundCallbackConfigService configService = mock(SystemInboundCallbackConfigService.class);
        MesIntegrationCallbackController controller = controller(syncService, configService);
        when(configService.hasValidBearerToken(any(), eq("Bearer wrong"))).thenReturn(false);

        ResponseEntity<MesMaterialCallbackResp> response = controller.materialCallback("Bearer wrong", callback("MAT-100"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getSuccess()).isFalse();
        verifyNoInteractions(syncService);
        ArgumentCaptor<InboundCallbackCode> codeCaptor = ArgumentCaptor.forClass(InboundCallbackCode.class);
        verify(configService).hasValidBearerToken(codeCaptor.capture(), eq("Bearer wrong"));
        assertThat(codeCaptor.getValue().name()).isEqualTo("MES_MATERIAL_CALLBACK");
    }

    @Test
    void controllerAcceptsPageConfiguredTokenWhenConfigServiceValidatesIt() {
        MesMaterialSyncService syncService = mock(MesMaterialSyncService.class);
        SystemInboundCallbackConfigService configService = mock(SystemInboundCallbackConfigService.class);
        MesIntegrationCallbackController controller = controller(syncService, configService);
        MesMaterialCallbackResp expected = new MesMaterialCallbackResp();
        expected.setSuccess(true);
        expected.setMesRequestId("MES-REQ-123");
        when(configService.hasValidBearerToken(any(), eq("Bearer page-generated-token"))).thenReturn(true);
        when(syncService.handleMaterialBatchCallback(any(MesMaterialCallbackReq.class))).thenReturn(expected);

        ResponseEntity<MesMaterialCallbackResp> response = controller.materialCallback("Bearer page-generated-token", batchCallback("MAT-100"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(expected);
        verify(syncService).handleMaterialBatchCallback(any(MesMaterialCallbackReq.class));
    }

    @Test
    void serviceFallsBackToEnvTokenWhenPageConfigDoesNotExist() {
        SystemInboundCallbackConfigMapper mapper = mock(SystemInboundCallbackConfigMapper.class);
        SystemInboundCallbackConfigService service = service(mapper, "env-token", true);
        when(mapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        assertThat(service.hasValidBearerToken(InboundCallbackCode.MES_MATERIAL_CALLBACK, "Bearer env-token")).isTrue();
        assertThat(service.hasValidBearerToken(InboundCallbackCode.MES_MATERIAL_CALLBACK, "Bearer wrong")).isFalse();
        assertThat(service.hasValidBearerToken(InboundCallbackCode.MES_MATERIAL_CALLBACK, null)).isFalse();
    }

    @Test
    void servicePrefersDbTokenOverEnvFallbackWhenPageConfigExists() {
        SystemInboundCallbackConfigMapper mapper = mock(SystemInboundCallbackConfigMapper.class);
        SystemInboundCallbackConfigService service = service(mapper, "env-token", true);
        SystemInboundCallbackConfig entity = config("db-token", true, true);
        when(mapper.selectOne(any(QueryWrapper.class))).thenReturn(entity);

        assertThat(service.hasValidBearerToken(InboundCallbackCode.MES_MATERIAL_CALLBACK, "Bearer db-token")).isTrue();
        assertThat(service.hasValidBearerToken(InboundCallbackCode.MES_MATERIAL_CALLBACK, "Bearer env-token")).isFalse();
    }

    @Test
    void disabledTokenVerificationAllowsCallbackAndLogsLowNoiseWarning(CapturedOutput output) {
        Logger logger = (Logger) LoggerFactory.getLogger(SystemInboundCallbackConfigService.class);
        Level original = logger.getLevel();
        logger.setLevel(Level.WARN);
        try {
            SystemInboundCallbackConfigMapper mapper = mock(SystemInboundCallbackConfigMapper.class);
            SystemInboundCallbackConfigService service = service(mapper, "env-token", true);
            when(mapper.selectOne(any(QueryWrapper.class))).thenReturn(config("db-token", true, false));

            assertThat(service.hasValidBearerToken(InboundCallbackCode.MES_MATERIAL_CALLBACK, null)).isTrue();

            assertThat(output.getOut())
                    .contains("Data center inbound material callback token verification disabled")
                    .doesNotContain("db-token", "env-token", sha256Hex("db-token"));
        } finally {
            logger.setLevel(original);
        }
    }

    private MesIntegrationCallbackController controller(
            MesMaterialSyncService syncService,
            SystemInboundCallbackConfigService configService) {
        MesIntegrationCallbackController controller = new MesIntegrationCallbackController();
        ReflectionTestUtils.setField(controller, "mesMaterialSyncService", syncService);
        ReflectionTestUtils.setField(controller, "inboundCallbackConfigService", configService);
        return controller;
    }

    private SystemInboundCallbackConfigService service(
            SystemInboundCallbackConfigMapper mapper,
            String fallbackToken,
            boolean fallbackTokenEnabled) {
        SystemInboundCallbackConfigService service = new SystemInboundCallbackConfigService();
        ReflectionTestUtils.setField(service, "configMapper", mapper);
        ReflectionTestUtils.setField(service, "auditService", mock(AuditService.class));
        ReflectionTestUtils.setField(service, "fallbackPublicBaseUrl", "http://qpo.example.com");
        ReflectionTestUtils.setField(service, "fallbackCallbackToken", fallbackToken);
        ReflectionTestUtils.setField(service, "fallbackCallbackTokenEnabled", fallbackTokenEnabled);
        return service;
    }

    private SystemInboundCallbackConfig config(String token, boolean enabled, boolean tokenEnabled) {
        SystemInboundCallbackConfig entity = new SystemInboundCallbackConfig();
        entity.setId(1L);
        entity.setCallbackCode("MES_MATERIAL_CALLBACK");
        entity.setSourceSystemCode("MES");
        entity.setName("MES物料号回调");
        entity.setEnabled(enabled);
        entity.setTokenEnabled(tokenEnabled);
        entity.setPublicBaseUrl("http://qpo.example.com");
        entity.setCallbackPath("/api/integrations/mes/material-callback");
        entity.setTokenHash(sha256Hex(token));
        entity.setTokenMask("***oken");
        return entity;
    }

    private MesMaterialCallbackReq callback(String materialNo) {
        MesMaterialCallbackReq req = new MesMaterialCallbackReq();
        req.setMesRequestId("MES-REQ-123");
        req.setStatus("SUCCESS");
        req.setMaterialNo(materialNo);
        return req;
    }

    private MesMaterialCallbackReq batchCallback(String productCode) {
        MesMaterialCallbackReq req = new MesMaterialCallbackReq();
        req.setMesRequestId("QPO-MAT-20260812-01J5ABCDEF");
        MesMaterialCallbackReq.Item item = new MesMaterialCallbackReq.Item();
        item.setTaskNo("MP202608120001");
        item.setDrawingNo("P-100");
        item.setDrawingVersion("A");
        item.setStatus("SUCCESS");
        item.setProductCode(productCode);
        req.setItems(List.of(item));
        return req;
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
