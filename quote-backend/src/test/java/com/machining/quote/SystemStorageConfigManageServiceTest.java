package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.machining.quote.dto.SystemStorageConfigDTO;
import com.machining.quote.dto.SystemStorageConfigSaveReq;
import com.machining.quote.entity.SystemFileStorageConfig;
import com.machining.quote.mapper.SystemFileStorageConfigMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.SystemStorageConfigManageService;
import com.machining.quote.storage.FileStorageConfigService;
import com.machining.quote.storage.FileStorageTestService;
import com.machining.quote.storage.StorageTestResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
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

class SystemStorageConfigManageServiceTest {

    @Test
    void listConfigsRedactsMinioSecrets() {
        SystemFileStorageConfigMapper mapper = mock(SystemFileStorageConfigMapper.class);
        SystemStorageConfigManageService service = service(mapper, mock(FileStorageTestService.class), mock(AuditService.class));
        when(mapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(minioConfig(2L, "secret-value")));

        List<SystemStorageConfigDTO> configs = service.listConfigs();

        assertThat(configs).hasSize(1);
        assertThat(configs.get(0).getStorageType()).isEqualTo("MINIO");
        assertThat(configs.get(0).getAccessSecret()).isEqualTo("******");
        assertThat(configs.toString()).doesNotContain("secret-value");
    }

    @Test
    void createMinioConfigRequiresSecretRedactsResponseAndRecordsAuditSummary() {
        SystemFileStorageConfigMapper mapper = mock(SystemFileStorageConfigMapper.class);
        AuditService auditService = mock(AuditService.class);
        SystemStorageConfigManageService service = service(mapper, mock(FileStorageTestService.class), auditService);
        doAnswer(invocation -> {
            SystemFileStorageConfig entity = invocation.getArgument(0);
            entity.setId(8L);
            return 1;
        }).when(mapper).insert(any(SystemFileStorageConfig.class));

        SystemStorageConfigDTO result = service.saveConfig(null, minioReq("secret-value"));

        assertThat(result.getId()).isEqualTo(8L);
        assertThat(result.getAccessSecret()).isEqualTo("******");
        ArgumentCaptor<SystemFileStorageConfig> entityCaptor = ArgumentCaptor.forClass(SystemFileStorageConfig.class);
        verify(mapper).insert(entityCaptor.capture());
        assertThat(String.valueOf(entityCaptor.getValue().getConfigJson())).contains("secret-value");

        ArgumentCaptor<Object> afterCaptor = ArgumentCaptor.forClass(Object.class);
        verify(auditService).record(eq("SYSTEM_STORAGE_CONFIG_CREATE"), eq("system_file_storage_config"), eq(8L), isNull(), afterCaptor.capture());
        assertThat(String.valueOf(afterCaptor.getValue()))
                .contains("secretChanged=true")
                .doesNotContain("secret-value");
    }

    @Test
    void updateMinioKeepsExistingSecretWhenBlankAndResetsTestStatusAfterConnectionChange() {
        SystemFileStorageConfigMapper mapper = mock(SystemFileStorageConfigMapper.class);
        AuditService auditService = mock(AuditService.class);
        SystemStorageConfigManageService service = service(mapper, mock(FileStorageTestService.class), auditService);
        SystemFileStorageConfig existing = minioConfig(2L, "old-secret");
        existing.setLastTestStatus("SUCCESS");
        existing.setLastTestMessage("previous ok");
        existing.setLastTestAt(LocalDateTime.now().minusDays(1));
        when(mapper.selectById(2L)).thenReturn(existing);

        SystemStorageConfigSaveReq req = minioReq("");
        req.setEndpoint("http://127.0.0.1:9001");
        SystemStorageConfigDTO result = service.saveConfig(2L, req);

        assertThat(result.getAccessSecret()).isEqualTo("******");
        ArgumentCaptor<SystemFileStorageConfig> entityCaptor = ArgumentCaptor.forClass(SystemFileStorageConfig.class);
        verify(mapper).updateById(entityCaptor.capture());
        SystemFileStorageConfig updated = entityCaptor.getValue();
        assertThat(String.valueOf(updated.getConfigJson())).contains("old-secret").contains("9001");
        assertThat(updated.getLastTestStatus()).isNull();
        assertThat(updated.getLastTestMessage()).isNull();
        assertThat(updated.getLastTestAt()).isNull();
        ArgumentCaptor<Object> beforeCaptor = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> afterCaptor = ArgumentCaptor.forClass(Object.class);
        verify(auditService).record(eq("SYSTEM_STORAGE_CONFIG_UPDATE"), eq("system_file_storage_config"), eq(2L), beforeCaptor.capture(), afterCaptor.capture());
        assertThat(String.valueOf(beforeCaptor.getValue())).contains("9000").doesNotContain("9001", "old-secret");
        assertThat(String.valueOf(afterCaptor.getValue())).contains("9001").doesNotContain("old-secret");
    }

    @Test
    void testConfigPersistsSuccessOrSanitizedFailureAndRecordsAudit() {
        SystemFileStorageConfigMapper mapper = mock(SystemFileStorageConfigMapper.class);
        FileStorageTestService testService = mock(FileStorageTestService.class);
        AuditService auditService = mock(AuditService.class);
        SystemStorageConfigManageService service = service(mapper, testService, auditService);
        SystemFileStorageConfig entity = minioConfig(3L, "plain-secret");
        when(mapper.selectById(3L)).thenReturn(entity);
        when(testService.test(any())).thenReturn(StorageTestResult.success("ok plain-secret", "probe.txt"));

        Map<String, Object> result = service.testConfig(3L);

        assertThat(result).containsEntry("success", true);
        assertThat(String.valueOf(result)).doesNotContain("plain-secret");
        ArgumentCaptor<SystemFileStorageConfig> updateCaptor = ArgumentCaptor.forClass(SystemFileStorageConfig.class);
        verify(mapper).updateById(updateCaptor.capture());
        assertThat(updateCaptor.getValue().getLastTestStatus()).isEqualTo("SUCCESS");
        assertThat(updateCaptor.getValue().getLastTestMessage()).doesNotContain("plain-secret");
        verify(auditService).record(eq("SYSTEM_STORAGE_CONFIG_TEST"), eq("system_file_storage_config"), eq(3L), isNull(), any());
    }

    @Test
    void testConfigFailurePersistsFailedStatusAndRecordsFailedAudit() {
        SystemFileStorageConfigMapper mapper = mock(SystemFileStorageConfigMapper.class);
        FileStorageTestService testService = mock(FileStorageTestService.class);
        AuditService auditService = mock(AuditService.class);
        SystemStorageConfigManageService service = service(mapper, testService, auditService);
        SystemFileStorageConfig entity = minioConfig(7L, "plain-secret");
        when(mapper.selectById(7L)).thenReturn(entity);
        when(testService.test(any())).thenReturn(StorageTestResult.failed("cannot write plain-secret", "probe.txt"));

        Map<String, Object> result = service.testConfig(7L);

        assertThat(result).containsEntry("success", false);
        assertThat(String.valueOf(result)).doesNotContain("plain-secret");
        ArgumentCaptor<SystemFileStorageConfig> updateCaptor = ArgumentCaptor.forClass(SystemFileStorageConfig.class);
        verify(mapper).updateById(updateCaptor.capture());
        assertThat(updateCaptor.getValue().getLastTestStatus()).isEqualTo("FAILED");
        verify(auditService).record(
                eq("SYSTEM_STORAGE_CONFIG_TEST_FAILED"),
                eq("system_file_storage_config"),
                eq(7L),
                isNull(),
                any(),
                eq("SYSTEM"),
                eq("FAILED"),
                eq("Storage config test failed"),
                any()
        );
    }

    @Test
    void activateRequiresSuccessfulTestAndSwitchesActiveConfigAtomically() {
        SystemFileStorageConfigMapper mapper = mock(SystemFileStorageConfigMapper.class);
        AuditService auditService = mock(AuditService.class);
        SystemStorageConfigManageService service = service(mapper, mock(FileStorageTestService.class), auditService);
        SystemFileStorageConfig entity = minioConfig(4L, "secret-value");
        entity.setEnabled(true);
        entity.setActive(false);
        entity.setLastTestStatus("SUCCESS");
        when(mapper.selectById(4L)).thenReturn(entity);

        SystemStorageConfigDTO result = service.activateConfig(4L);

        assertThat(result.getActive()).isTrue();
        verify(mapper).update(any(SystemFileStorageConfig.class), any(UpdateWrapper.class));
        verify(mapper).updateById(any(SystemFileStorageConfig.class));
        verify(auditService).record(eq("SYSTEM_STORAGE_CONFIG_ACTIVATE"), eq("system_file_storage_config"), eq(4L), isNull(), any());
    }

    @Test
    void activateRejectsUntestedConfigAndDisableRejectsActiveConfig() {
        SystemFileStorageConfigMapper mapper = mock(SystemFileStorageConfigMapper.class);
        SystemStorageConfigManageService service = service(mapper, mock(FileStorageTestService.class), mock(AuditService.class));
        SystemFileStorageConfig untested = minioConfig(5L, "secret-value");
        untested.setLastTestStatus("FAILED");
        when(mapper.selectById(5L)).thenReturn(untested);

        assertThatThrownBy(() -> service.activateConfig(5L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("test");

        SystemFileStorageConfig active = minioConfig(6L, "secret-value");
        active.setActive(true);
        when(mapper.selectById(6L)).thenReturn(active);

        assertThatThrownBy(() -> service.disableConfig(6L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("active");
        verify(mapper, never()).updateById(any(SystemFileStorageConfig.class));
    }

    private SystemStorageConfigManageService service(
            SystemFileStorageConfigMapper mapper,
            FileStorageTestService testService,
            AuditService auditService) {
        SystemStorageConfigManageService service = new SystemStorageConfigManageService();
        ReflectionTestUtils.setField(service, "configMapper", mapper);
        ReflectionTestUtils.setField(service, "configService", new FileStorageConfigService(mapper));
        ReflectionTestUtils.setField(service, "testService", testService);
        ReflectionTestUtils.setField(service, "auditService", auditService);
        return service;
    }

    private SystemFileStorageConfig minioConfig(Long id, String secret) {
        SystemFileStorageConfig entity = new SystemFileStorageConfig();
        entity.setId(id);
        entity.setName("MinIO");
        entity.setStorageType("MINIO");
        entity.setActive(false);
        entity.setEnabled(true);
        entity.setConfigJson(Map.of(
                "endpoint", "http://127.0.0.1:9000",
                "bucket", "qpo-drawings",
                "accessKey", "qpo-service",
                "accessSecret", secret,
                "region", "us-east-1",
                "pathStyle", true,
                "publicReadEnabled", true,
                "publicBaseUrl", "http://127.0.0.1:9000/qpo-drawings"
        ));
        return entity;
    }

    private SystemStorageConfigSaveReq minioReq(String secret) {
        SystemStorageConfigSaveReq req = new SystemStorageConfigSaveReq();
        req.setName("MinIO");
        req.setStorageType("MINIO");
        req.setEndpoint("http://127.0.0.1:9000");
        req.setBucket("qpo-drawings");
        req.setAccessKey("qpo-service");
        req.setAccessSecret(secret);
        req.setRegion("us-east-1");
        req.setPathStyle(true);
        req.setPublicReadEnabled(true);
        req.setPublicBaseUrl("http://127.0.0.1:9000/qpo-drawings");
        req.setRemark("local minio");
        return req;
    }
}
