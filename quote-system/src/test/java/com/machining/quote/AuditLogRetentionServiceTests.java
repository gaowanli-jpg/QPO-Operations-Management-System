package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.dto.AuditLogRetentionConfigDTO;
import com.machining.quote.dto.AuditLogRetentionConfigReq;
import com.machining.quote.dto.AuditLogRetentionRunDTO;
import com.machining.quote.entity.AuditLog;
import com.machining.quote.entity.AuditLogRetentionConfig;
import com.machining.quote.mapper.AuditLogMapper;
import com.machining.quote.mapper.AuditLogRetentionConfigMapper;
import com.machining.quote.service.AuditLogRetentionService;
import com.machining.quote.service.AuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditLogRetentionServiceTests {
    @TempDir
    Path archiveRoot;

    @Test
    void getSettingReturnsDefaultDisabledPolicyWhenMissing() {
        AuditLogRetentionConfigMapper configMapper = mock(AuditLogRetentionConfigMapper.class);
        AuditLogRetentionService service = service(configMapper, mock(AuditLogMapper.class), mock(AuditService.class));

        AuditLogRetentionConfigDTO setting = service.getSetting();

        assertThat(setting.isEnabled()).isFalse();
        assertThat(setting.getRetentionDays()).isEqualTo(180);
        assertThat(setting.isArchiveBeforeCleanup()).isTrue();
        assertThat(setting.getCleanupTime()).isEqualTo("02:00");
        assertThat(setting.getLastRunStatus()).isEqualTo("NEVER");
    }

    @Test
    void updateSettingPersistsPolicyAndRecordsAudit() {
        AuditLogRetentionConfigMapper configMapper = mock(AuditLogRetentionConfigMapper.class);
        AuditService auditService = mock(AuditService.class);
        AuditLogRetentionConfig existing = new AuditLogRetentionConfig();
        existing.setId(1);
        existing.setEnabled(false);
        existing.setRetentionDays(180);
        existing.setArchiveBeforeCleanup(true);
        existing.setCleanupTime("02:00");
        when(configMapper.selectById(1)).thenReturn(existing);
        AuditLogRetentionService service = service(configMapper, mock(AuditLogMapper.class), auditService);

        AuditLogRetentionConfigReq req = new AuditLogRetentionConfigReq();
        req.setEnabled(true);
        req.setRetentionDays(90);
        req.setArchiveBeforeCleanup(true);
        req.setCleanupTime("03:30");
        AuditLogRetentionConfigDTO updated = service.updateSetting(req);

        assertThat(updated.isEnabled()).isTrue();
        assertThat(updated.getRetentionDays()).isEqualTo(90);
        assertThat(updated.getCleanupTime()).isEqualTo("03:30");
        ArgumentCaptor<AuditLogRetentionConfig> captor = ArgumentCaptor.forClass(AuditLogRetentionConfig.class);
        verify(configMapper).updateById(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(1);
        assertThat(captor.getValue().getRetentionDays()).isEqualTo(90);
        verify(auditService).record(
                eq("AUDIT_RETENTION_CONFIG_UPDATE"),
                eq("audit_log_retention_config"),
                eq(1),
                any(Map.class),
                any(Map.class),
                eq("SYSTEM"),
                eq("SUCCESS"),
                eq("更新操作日志保留策略"),
                eq(null)
        );
    }

    @Test
    void runCleanupArchivesExpiredLogsDeletesThemAndUpdatesLastRun() throws Exception {
        AuditLogRetentionConfigMapper configMapper = mock(AuditLogRetentionConfigMapper.class);
        AuditLogMapper auditLogMapper = mock(AuditLogMapper.class);
        AuditService auditService = mock(AuditService.class);
        AuditLogRetentionConfig config = new AuditLogRetentionConfig();
        config.setId(1);
        config.setEnabled(true);
        config.setRetentionDays(30);
        config.setArchiveBeforeCleanup(true);
        config.setCleanupTime("02:00");
        when(configMapper.selectById(1)).thenReturn(config);
        AuditLog expired = new AuditLog();
        expired.setId(10);
        expired.setUsername("market");
        expired.setRole("MARKET");
        expired.setModule("MARKET");
        expired.setAction("RFQ_IMPORT");
        expired.setResult("SUCCESS");
        expired.setMessage("导入 RFQ");
        expired.setCreatedAt(LocalDateTime.of(2026, 5, 1, 9, 0));
        when(auditLogMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(expired));
        when(auditLogMapper.deleteBatchIds(List.of(10))).thenReturn(1);
        AuditLogRetentionService service = service(configMapper, auditLogMapper, auditService);
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(Instant.parse("2026-07-17T02:05:00Z"), ZoneId.of("Asia/Shanghai")));

        AuditLogRetentionRunDTO result = service.runCleanupNow();

        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        assertThat(result.getDeletedCount()).isEqualTo(1);
        assertThat(result.getArchiveFile()).endsWith(".csv");
        Path archiveFile = Path.of(result.getArchiveFile());
        assertThat(Files.exists(archiveFile)).isTrue();
        byte[] bytes = Files.readAllBytes(archiveFile);
        assertThat(bytes).startsWith(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        assertThat(new String(bytes, StandardCharsets.UTF_8)).contains("日志ID", "RFQ_IMPORT");
        verify(auditLogMapper).deleteBatchIds(List.of(10));
        verify(auditService).record(
                eq("AUDIT_LOG_CLEANUP_RUN"),
                eq("audit_log_retention_config"),
                eq(1),
                eq(null),
                any(Map.class),
                eq("SYSTEM"),
                eq("SUCCESS"),
                eq("执行操作日志自动清理"),
                eq(null)
        );
        ArgumentCaptor<AuditLogRetentionConfig> updateCaptor = ArgumentCaptor.forClass(AuditLogRetentionConfig.class);
        verify(configMapper).updateById(updateCaptor.capture());
        assertThat(updateCaptor.getValue().getLastRunStatus()).isEqualTo("SUCCESS");
        assertThat(updateCaptor.getValue().getLastRunDeletedCount()).isEqualTo(1);
    }

    private AuditLogRetentionService service(
            AuditLogRetentionConfigMapper configMapper,
            AuditLogMapper auditLogMapper,
            AuditService auditService) {
        AuditLogRetentionService service = new AuditLogRetentionService();
        ReflectionTestUtils.setField(service, "configMapper", configMapper);
        ReflectionTestUtils.setField(service, "auditLogMapper", auditLogMapper);
        ReflectionTestUtils.setField(service, "auditService", auditService);
        ReflectionTestUtils.setField(service, "archiveRoot", archiveRoot.toString());
        return service;
    }
}
