package com.machining.quote;

import com.machining.quote.dto.MesMasterDataSyncScheduleConfigDTO;
import com.machining.quote.dto.MesMasterDataSyncScheduleConfigReq;
import com.machining.quote.entity.MesMasterDataSyncScheduleConfig;
import com.machining.quote.mapper.MesMasterDataSyncScheduleConfigMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.mes.MesMasterDataSyncScheduleConfigService;
import com.machining.quote.service.mes.MesMasterDataSyncService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MesMasterDataSyncScheduleConfigTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Test
    void missingConfigUsesFallbackDefaultsPerMesSyncType() {
        MesMasterDataSyncScheduleConfigMapper mapper = mock(MesMasterDataSyncScheduleConfigMapper.class);
        MesMasterDataSyncScheduleConfigService service = service(mapper, mock(AuditService.class));
        ReflectionTestUtils.setField(service, "fallbackEnabled", true);
        ReflectionTestUtils.setField(service, "fallbackStartupDelayMs", 45_000L);
        ReflectionTestUtils.setField(service, "fallbackFixedDelayMs", 3_600_000L);

        MesMasterDataSyncScheduleConfigDTO material = service.getSetting("MES_MATERIAL");
        MesMasterDataSyncScheduleConfigDTO process = service.getSetting("MES_PROCESS_TYPE");

        assertThat(material.getSyncType()).isEqualTo("MES_MATERIAL");
        assertThat(process.getSyncType()).isEqualTo("MES_PROCESS_TYPE");
        assertThat(material.isEnabled()).isTrue();
        assertThat(material.getStartupDelayMs()).isEqualTo(45_000L);
        assertThat(material.getFixedDelayMs()).isEqualTo(3_600_000L);
        assertThat(material.getLastScheduledStatus()).isEqualTo("NEVER");
        assertThat(material.isFallback()).isTrue();
    }

    @Test
    void updatePersistsMaterialPolicyAndRecordsSafeAuditSummary() {
        MesMasterDataSyncScheduleConfigMapper mapper = mock(MesMasterDataSyncScheduleConfigMapper.class);
        AuditService auditService = mock(AuditService.class);
        MesMasterDataSyncScheduleConfig before = config("MES_MATERIAL", false, 30_000L, 1_800_000L, null, "NEVER");
        when(mapper.selectById("MES_MATERIAL")).thenReturn(before);
        MesMasterDataSyncScheduleConfigService service = service(mapper, auditService);

        MesMasterDataSyncScheduleConfigReq req = new MesMasterDataSyncScheduleConfigReq();
        req.setEnabled(true);
        req.setStartupDelayMs(120_000L);
        req.setFixedDelayMs(7_200_000L);

        MesMasterDataSyncScheduleConfigDTO updated = service.updateSetting("MES_MATERIAL", req);

        assertThat(updated.isEnabled()).isTrue();
        assertThat(updated.getStartupDelayMs()).isEqualTo(120_000L);
        assertThat(updated.getFixedDelayMs()).isEqualTo(7_200_000L);
        ArgumentCaptor<MesMasterDataSyncScheduleConfig> captor = ArgumentCaptor.forClass(MesMasterDataSyncScheduleConfig.class);
        verify(mapper).updateById(captor.capture());
        assertThat(captor.getValue().getSyncType()).isEqualTo("MES_MATERIAL");
        assertThat(captor.getValue().getLastScheduledStatus()).isEqualTo("NEVER");
        verify(auditService).record(
                eq("DICT_MES_MASTER_DATA_SYNC_SCHEDULE_UPDATE"),
                eq("mes_master_data_sync_schedule_config"),
                eq("MES_MATERIAL"),
                any(Map.class),
                any(Map.class),
                eq("DICT"),
                eq("SUCCESS"),
                eq("更新 MES 主数据定时同步策略"),
                eq(null)
        );
    }

    @Test
    void updateRejectsUnknownTypeAndTooShortFixedDelay() {
        MesMasterDataSyncScheduleConfigService service = service(mock(MesMasterDataSyncScheduleConfigMapper.class), mock(AuditService.class));
        MesMasterDataSyncScheduleConfigReq req = new MesMasterDataSyncScheduleConfigReq();
        req.setFixedDelayMs(30_000L);

        assertThatThrownBy(() -> service.getSetting("UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported MES sync type");
        assertThatThrownBy(() -> service.updateSetting("MES_MATERIAL", req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 minute and 7 days");
    }

    @Test
    void shouldRunIsCalculatedIndependentlyForEachSyncType() {
        MesMasterDataSyncScheduleConfigMapper mapper = mock(MesMasterDataSyncScheduleConfigMapper.class);
        when(mapper.selectById("MES_MATERIAL")).thenReturn(config("MES_MATERIAL", true, 120_000L, 1_800_000L, null, "NEVER"));
        when(mapper.selectById("MES_PROCESS_TYPE")).thenReturn(config(
                "MES_PROCESS_TYPE",
                true,
                30_000L,
                1_800_000L,
                LocalDateTime.of(2026, 8, 13, 11, 40),
                "SUCCESS"));
        MesMasterDataSyncScheduleConfigService service = service(mapper, mock(AuditService.class));

        assertThat(service.shouldRun("MES_MATERIAL", LocalDateTime.of(2026, 8, 13, 11, 59))).isFalse();
        assertThat(service.shouldRun("MES_MATERIAL", LocalDateTime.of(2026, 8, 13, 11, 58))).isTrue();
        assertThat(service.shouldRun("MES_PROCESS_TYPE", LocalDateTime.of(2026, 8, 13, 8, 0))).isFalse();

        when(mapper.selectById("MES_PROCESS_TYPE")).thenReturn(config(
                "MES_PROCESS_TYPE",
                true,
                30_000L,
                1_800_000L,
                LocalDateTime.of(2026, 8, 13, 11, 29),
                "SUCCESS"));
        assertThat(service.shouldRun("MES_PROCESS_TYPE", LocalDateTime.of(2026, 8, 13, 8, 0))).isTrue();
    }

    @Test
    void scheduledResultCreatesFallbackRowAndStoresCounters() {
        MesMasterDataSyncScheduleConfigMapper mapper = mock(MesMasterDataSyncScheduleConfigMapper.class);
        MesMasterDataSyncScheduleConfigService service = service(mapper, mock(AuditService.class));
        MesMasterDataSyncService.SyncResult result = new MesMasterDataSyncService.SyncResult(
                true, 991L, 20, 20, 0, 0, 20, 0, 2, null);

        service.updateScheduledResult("MES_MATERIAL", result);

        ArgumentCaptor<MesMasterDataSyncScheduleConfig> captor = ArgumentCaptor.forClass(MesMasterDataSyncScheduleConfig.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getSyncType()).isEqualTo("MES_MATERIAL");
        assertThat(captor.getValue().getLastScheduledSyncRunId()).isEqualTo(991L);
        assertThat(captor.getValue().getLastScheduledStatus()).isEqualTo("SUCCESS");
        assertThat(captor.getValue().getLastScheduledMessage()).contains("fetched=20", "valid=20", "upserted=20", "deleted=2");
    }

    @Test
    void disabledPolicyDoesNotRunOrUpdateRows() {
        MesMasterDataSyncScheduleConfigMapper mapper = mock(MesMasterDataSyncScheduleConfigMapper.class);
        when(mapper.selectById("MES_MATERIAL")).thenReturn(config("MES_MATERIAL", false, 0L, 60_000L, null, "NEVER"));
        MesMasterDataSyncScheduleConfigService service = service(mapper, mock(AuditService.class));

        assertThat(service.shouldRun("MES_MATERIAL", LocalDateTime.of(2026, 8, 13, 8, 0))).isFalse();
        verify(mapper, never()).updateById(any());
    }

    @Test
    void auditActionUsesDictModuleAndReadableLabel() {
        assertThat(AuditService.inferModule("DICT_MES_MASTER_DATA_SYNC_SCHEDULE_UPDATE")).isEqualTo("DICT");
        assertThat(AuditService.labelForAction("DICT_MES_MASTER_DATA_SYNC_SCHEDULE_UPDATE"))
                .isEqualTo("更新 MES 主数据定时同步策略");
    }

    @Test
    void migrationDefinesMesScheduleTableAndSeedsBothTypes() throws Exception {
        String schema = Files.readString(Path.of("src/main/resources/db/migration/V9__mes_master_data_sync_schedule_config.sql"));

        assertThat(schema).contains("CREATE TABLE IF NOT EXISTS mes_master_data_sync_schedule_config");
        assertThat(schema).contains("sync_type character varying(40)");
        assertThat(schema).contains("MES_MATERIAL");
        assertThat(schema).contains("MES_PROCESS_TYPE");
        assertThat(schema).contains("startup_delay_ms");
        assertThat(schema).contains("fixed_delay_ms");
    }

    @Test
    void schedulerMethodExistsOnMesSyncService() throws Exception {
        Method method = MesMasterDataSyncService.class.getMethod("syncMesMasterDataScheduled");

        assertThat(method.getAnnotation(org.springframework.scheduling.annotation.Scheduled.class)).isNotNull();
    }

    private MesMasterDataSyncScheduleConfigService service(MesMasterDataSyncScheduleConfigMapper mapper, AuditService auditService) {
        MesMasterDataSyncScheduleConfigService service = new MesMasterDataSyncScheduleConfigService();
        ReflectionTestUtils.setField(service, "configMapper", mapper);
        ReflectionTestUtils.setField(service, "auditService", auditService);
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(Instant.parse("2026-08-13T04:00:00Z"), ZONE));
        return service;
    }

    private MesMasterDataSyncScheduleConfig config(
            String syncType,
            boolean enabled,
            long startupDelayMs,
            long fixedDelayMs,
            LocalDateTime lastRunAt,
            String status) {
        MesMasterDataSyncScheduleConfig config = new MesMasterDataSyncScheduleConfig();
        config.setSyncType(syncType);
        config.setEnabled(enabled);
        config.setStartupDelayMs(startupDelayMs);
        config.setFixedDelayMs(fixedDelayMs);
        config.setLastScheduledRunAt(lastRunAt);
        config.setLastScheduledStatus(status);
        config.setUpdatedAt(LocalDateTime.of(2026, 8, 13, 12, 0));
        return config;
    }
}
