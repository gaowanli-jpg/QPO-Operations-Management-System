package com.machining.quote;

import com.machining.quote.controller.CustomerMasterController;
import com.machining.quote.dto.U9CustomerSyncScheduleConfigDTO;
import com.machining.quote.dto.U9CustomerSyncScheduleConfigReq;
import com.machining.quote.entity.U9CustomerSyncScheduleConfig;
import com.machining.quote.mapper.U9CustomerSyncScheduleConfigMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.u9.U9CustomerSyncScheduleConfigService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class U9CustomerSyncScheduleConfigTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Test
    void missingConfigUsesEnvironmentFallbackDefaults() {
        U9CustomerSyncScheduleConfigMapper mapper = mock(U9CustomerSyncScheduleConfigMapper.class);
        U9CustomerSyncScheduleConfigService service = service(mapper, mock(AuditService.class));
        ReflectionTestUtils.setField(service, "fallbackEnabled", true);
        ReflectionTestUtils.setField(service, "fallbackStartupDelayMs", 45_000L);
        ReflectionTestUtils.setField(service, "fallbackFixedDelayMs", 1_800_000L);

        U9CustomerSyncScheduleConfigDTO setting = service.getSetting();

        assertThat(setting.isEnabled()).isTrue();
        assertThat(setting.getStartupDelayMs()).isEqualTo(45_000L);
        assertThat(setting.getFixedDelayMs()).isEqualTo(1_800_000L);
        assertThat(setting.getLastScheduledStatus()).isEqualTo("NEVER");
        assertThat(setting.isFallback()).isTrue();
    }

    @Test
    void updatePersistsDbPolicyAndRecordsSafeAuditSummary() {
        U9CustomerSyncScheduleConfigMapper mapper = mock(U9CustomerSyncScheduleConfigMapper.class);
        AuditService auditService = mock(AuditService.class);
        U9CustomerSyncScheduleConfig before = config(false, 30_000L, 1_800_000L, null, "NEVER");
        when(mapper.selectById(1)).thenReturn(before);
        U9CustomerSyncScheduleConfigService service = service(mapper, auditService);

        U9CustomerSyncScheduleConfigReq req = new U9CustomerSyncScheduleConfigReq();
        req.setEnabled(true);
        req.setStartupDelayMs(120_000L);
        req.setFixedDelayMs(3_600_000L);

        U9CustomerSyncScheduleConfigDTO updated = service.updateSetting(req);

        assertThat(updated.isEnabled()).isTrue();
        assertThat(updated.getStartupDelayMs()).isEqualTo(120_000L);
        assertThat(updated.getFixedDelayMs()).isEqualTo(3_600_000L);
        ArgumentCaptor<U9CustomerSyncScheduleConfig> captor = ArgumentCaptor.forClass(U9CustomerSyncScheduleConfig.class);
        verify(mapper).updateById(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(1);
        assertThat(captor.getValue().getLastScheduledStatus()).isEqualTo("NEVER");
        verify(auditService).record(
                eq("U9_CUSTOMER_SYNC_SCHEDULE_UPDATE"),
                eq("u9_customer_sync_schedule_config"),
                eq(1),
                any(Map.class),
                any(Map.class),
                eq("SYSTEM"),
                eq("SUCCESS"),
                eq("更新 U9 客户定时同步策略"),
                eq(null)
        );
    }

    @Test
    void updateRejectsIntervalsThatWouldHammerU9() {
        U9CustomerSyncScheduleConfigService service = service(mock(U9CustomerSyncScheduleConfigMapper.class), mock(AuditService.class));
        U9CustomerSyncScheduleConfigReq req = new U9CustomerSyncScheduleConfigReq();
        req.setFixedDelayMs(30_000L);

        assertThatThrownBy(() -> service.updateSetting(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("同步完成后间隔");
    }

    @Test
    void shouldRunWaitsForStartupDelayWhenNoScheduledRunExists() {
        U9CustomerSyncScheduleConfigMapper mapper = mock(U9CustomerSyncScheduleConfigMapper.class);
        when(mapper.selectById(1)).thenReturn(config(true, 120_000L, 1_800_000L, null, "NEVER"));
        U9CustomerSyncScheduleConfigService service = service(mapper, mock(AuditService.class));

        assertThat(service.shouldRun(LocalDateTime.of(2026, 8, 12, 11, 59))).isFalse();
        assertThat(service.shouldRun(LocalDateTime.of(2026, 8, 12, 11, 58))).isTrue();
    }

    @Test
    void shouldRunUsesFixedDelayAfterLastScheduledCompletion() {
        U9CustomerSyncScheduleConfigMapper mapper = mock(U9CustomerSyncScheduleConfigMapper.class);
        when(mapper.selectById(1)).thenReturn(config(
                true,
                30_000L,
                1_800_000L,
                LocalDateTime.of(2026, 8, 12, 11, 40),
                "SUCCESS"));
        U9CustomerSyncScheduleConfigService service = service(mapper, mock(AuditService.class));

        assertThat(service.shouldRun(LocalDateTime.of(2026, 8, 12, 8, 0))).isFalse();

        when(mapper.selectById(1)).thenReturn(config(
                true,
                30_000L,
                1_800_000L,
                LocalDateTime.of(2026, 8, 12, 11, 29),
                "SUCCESS"));
        assertThat(service.shouldRun(LocalDateTime.of(2026, 8, 12, 8, 0))).isTrue();
    }

    @Test
    void controllerExposesScheduleEndpointsBehindCustomerSyncPermission() throws Exception {
        Method get = CustomerMasterController.class.getMethod("customerSyncSchedule");
        Method put = CustomerMasterController.class.getMethod("updateCustomerSyncSchedule", U9CustomerSyncScheduleConfigReq.class);

        assertThat(get.getAnnotation(com.machining.quote.config.RequirePermission.class).value())
                .containsExactly("system:customer:sync");
        assertThat(put.getAnnotation(com.machining.quote.config.RequirePermission.class).value())
                .containsExactly("system:customer:sync");
    }

    @Test
    void auditActionUsesSystemModuleAndReadableLabel() {
        assertThat(AuditService.inferModule("U9_CUSTOMER_SYNC_SCHEDULE_UPDATE")).isEqualTo("SYSTEM");
        assertThat(AuditService.labelForAction("U9_CUSTOMER_SYNC_SCHEDULE_UPDATE"))
                .isEqualTo("更新 U9 客户定时同步策略");
    }

    @Test
    void migrationDefinesSingleScheduleConfigTableAndSeed() throws Exception {
        String schema = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/resources/db/migration/V5__u9_customer_sync_schedule_config.sql"));

        assertThat(schema).contains("CREATE TABLE IF NOT EXISTS u9_customer_sync_schedule_config");
        assertThat(schema).contains("startup_delay_ms");
        assertThat(schema).contains("fixed_delay_ms");
        assertThat(schema).contains("INSERT INTO u9_customer_sync_schedule_config");
    }

    private U9CustomerSyncScheduleConfigService service(U9CustomerSyncScheduleConfigMapper mapper, AuditService auditService) {
        U9CustomerSyncScheduleConfigService service = new U9CustomerSyncScheduleConfigService();
        ReflectionTestUtils.setField(service, "configMapper", mapper);
        ReflectionTestUtils.setField(service, "auditService", auditService);
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(Instant.parse("2026-08-12T04:00:00Z"), ZONE));
        return service;
    }

    private U9CustomerSyncScheduleConfig config(
            boolean enabled,
            long startupDelayMs,
            long fixedDelayMs,
            LocalDateTime lastRunAt,
            String status) {
        U9CustomerSyncScheduleConfig config = new U9CustomerSyncScheduleConfig();
        config.setId(1);
        config.setEnabled(enabled);
        config.setStartupDelayMs(startupDelayMs);
        config.setFixedDelayMs(fixedDelayMs);
        config.setLastScheduledRunAt(lastRunAt);
        config.setLastScheduledStatus(status);
        config.setUpdatedAt(LocalDateTime.of(2026, 8, 12, 12, 0));
        return config;
    }
}
