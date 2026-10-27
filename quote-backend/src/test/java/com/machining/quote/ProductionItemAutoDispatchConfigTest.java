package com.machining.quote;

import com.machining.quote.dto.ProductionItemAutoDispatchConfigDTO;
import com.machining.quote.dto.ProductionItemAutoDispatchConfigReq;
import com.machining.quote.entity.ProductionItemAutoDispatchConfig;
import com.machining.quote.mapper.ProductionItemAutoDispatchConfigMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.ProductionItemAutoDispatchConfigService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductionItemAutoDispatchConfigTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final String CONFIG_ID = ProductionItemAutoDispatchConfigService.CONFIG_ID;

    @Test
    void missingRowFallsBackToDocumentedDefaultsAndStaysDisabled() {
        ProductionItemAutoDispatchConfigMapper mapper = mock(ProductionItemAutoDispatchConfigMapper.class);
        when(mapper.selectById(CONFIG_ID)).thenReturn(null);

        ProductionItemAutoDispatchConfigDTO dto = service(mapper, mock(AuditService.class)).getSetting();

        assertThat(dto.isEnabled()).isFalse();
        assertThat(dto.getScheduleMode()).isEqualTo("DAILY_TIME");
        assertThat(dto.getDailyRunTime()).isEqualTo(LocalTime.of(17, 0));
        assertThat(dto.getBatchSize()).isEqualTo(200);
        assertThat(dto.getBatchIntervalMs()).isEqualTo(3000);
        assertThat(dto.getMaxRounds()).isEqualTo(50);
        assertThat(dto.getWarnDurationMs()).isEqualTo(3_600_000L);
        assertThat(dto.getFailureBreaker()).isEqualTo(3);
        assertThat(dto.isIncludeFailed()).isFalse();
        assertThat(dto.getLastStatus()).isEqualTo("NEVER");
    }

    @Test
    void disabledConfigNeverRuns() {
        ProductionItemAutoDispatchConfigMapper mapper = mock(ProductionItemAutoDispatchConfigMapper.class);
        ProductionItemAutoDispatchConfig config = config(false, LocalTime.of(17, 0), null);
        when(mapper.selectById(CONFIG_ID)).thenReturn(config);

        assertThat(service(mapper, mock(AuditService.class))
                .shouldRun(LocalDateTime.of(2026, 10, 27, 18, 0))).isFalse();
    }

    @Test
    void runsAtDailyTimeAndOnlyOncePerDay() {
        ProductionItemAutoDispatchConfigMapper mapper = mock(ProductionItemAutoDispatchConfigMapper.class);
        ProductionItemAutoDispatchConfig config = config(true, LocalTime.of(17, 0), null);
        when(mapper.selectById(CONFIG_ID)).thenReturn(config);
        ProductionItemAutoDispatchConfigService service = service(mapper, mock(AuditService.class));

        assertThat(service.shouldRun(LocalDateTime.of(2026, 10, 27, 16, 59))).isFalse();
        assertThat(service.shouldRun(LocalDateTime.of(2026, 10, 27, 17, 0))).isTrue();

        config.setLastRunDate(LocalDate.of(2026, 10, 27));
        assertThat(service.shouldRun(LocalDateTime.of(2026, 10, 27, 18, 30))).isFalse();

        config.setLastRunDate(LocalDate.of(2026, 10, 26));
        assertThat(service.shouldRun(LocalDateTime.of(2026, 10, 27, 17, 5))).isTrue();
    }

    @Test
    void updateRejectsOutOfRangeParameters() {
        ProductionItemAutoDispatchConfigMapper mapper = mock(ProductionItemAutoDispatchConfigMapper.class);
        when(mapper.selectById(CONFIG_ID)).thenReturn(config(true, LocalTime.of(17, 0), null));
        ProductionItemAutoDispatchConfigService service = service(mapper, mock(AuditService.class));

        assertThatThrownBy(() -> service.updateSetting(reqWithBatchSize(50)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("batch size");
        assertThatThrownBy(() -> service.updateSetting(reqWithBatchSize(1001)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("batch size");
        assertThatThrownBy(() -> service.updateSetting(reqWithInterval(500)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("batch interval");
        assertThatThrownBy(() -> service.updateSetting(reqWithMaxRounds(0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max rounds");
        assertThatThrownBy(() -> service.updateSetting(reqWithBreaker(0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("failure breaker");
    }

    @Test
    void updatePersistsPolicyAndRecordsAudit() {
        ProductionItemAutoDispatchConfigMapper mapper = mock(ProductionItemAutoDispatchConfigMapper.class);
        AuditService auditService = mock(AuditService.class);
        when(mapper.selectById(CONFIG_ID)).thenReturn(config(false, LocalTime.of(17, 0), null));
        ProductionItemAutoDispatchConfigService service = service(mapper, auditService);

        ProductionItemAutoDispatchConfigReq req = new ProductionItemAutoDispatchConfigReq();
        req.setEnabled(true);
        req.setDailyRunTime(LocalTime.of(18, 30));
        req.setBatchSize(300);
        req.setBatchIntervalMs(5000);
        req.setMaxRounds(20);
        req.setWarnDurationMs(1_800_000L);
        req.setFailureBreaker(5);

        ProductionItemAutoDispatchConfigDTO updated = service.updateSetting(req);

        assertThat(updated.isEnabled()).isTrue();
        assertThat(updated.getDailyRunTime()).isEqualTo(LocalTime.of(18, 30));
        assertThat(updated.getBatchSize()).isEqualTo(300);
        assertThat(updated.getBatchIntervalMs()).isEqualTo(5000);
        assertThat(updated.getMaxRounds()).isEqualTo(20);
        assertThat(updated.getWarnDurationMs()).isEqualTo(1_800_000L);
        assertThat(updated.getFailureBreaker()).isEqualTo(5);

        ArgumentCaptor<ProductionItemAutoDispatchConfig> captor =
                ArgumentCaptor.forClass(ProductionItemAutoDispatchConfig.class);
        verify(mapper).updateById(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(CONFIG_ID);
        assertThat(captor.getValue().getDailyRunTime()).isEqualTo(LocalTime.of(18, 30));

        verify(auditService).record(
                eq("PRODUCTION_ITEM_AUTO_DISPATCH_CONFIG_UPDATE"),
                eq("production_item_auto_dispatch_config"),
                eq(CONFIG_ID),
                any(Map.class),
                any(Map.class),
                eq("DELIVERY"),
                eq("SUCCESS"),
                anyString(),
                isNull()
        );
    }

    @Test
    void claimDailyRunUsesConditionalUpdateSoSameDayCannotRunTwice() {
        ProductionItemAutoDispatchConfigMapper mapper = mock(ProductionItemAutoDispatchConfigMapper.class);
        when(mapper.selectById(CONFIG_ID)).thenReturn(config(true, LocalTime.of(17, 0), null));
        when(mapper.claimDailyRun(eq(CONFIG_ID), any(), any())).thenReturn(1, 0);
        ProductionItemAutoDispatchConfigService service = service(mapper, mock(AuditService.class));
        LocalDateTime now = LocalDateTime.of(2026, 10, 27, 17, 0);

        assertThat(service.claimDailyRun(now)).isTrue();
        assertThat(service.claimDailyRun(now)).isFalse();
    }

    @Test
    void resetDailyRunClaimClearsClaimForRetry() {
        ProductionItemAutoDispatchConfigMapper mapper = mock(ProductionItemAutoDispatchConfigMapper.class);
        ProductionItemAutoDispatchConfigService service = service(mapper, mock(AuditService.class));

        service.resetDailyRunClaim();

        verify(mapper).resetDailyRunClaim(CONFIG_ID);
    }

    @Test
    void auditLabelsAreRegisteredForBothNewActions() {
        assertThat(AuditService.inferModule("PRODUCTION_ITEM_MES_PRODUCTION_AUTO_SUBMIT")).isEqualTo("DELIVERY");
        assertThat(AuditService.inferModule("PRODUCTION_ITEM_AUTO_DISPATCH_CONFIG_UPDATE")).isEqualTo("DELIVERY");
        assertThat(AuditService.labelForAction("PRODUCTION_ITEM_MES_PRODUCTION_AUTO_SUBMIT"))
                .isEqualTo("提交待产零件定时下发");
        assertThat(AuditService.labelForAction("PRODUCTION_ITEM_AUTO_DISPATCH_CONFIG_UPDATE"))
                .isEqualTo("更新待产零件定时下发策略");
    }

    @Test
    void migrationCreatesConfigTableAndRegistersSchedulePermission() throws Exception {
        String sql = Files.readString(Path.of(
                "src/main/resources/db/migration/V66__production_item_auto_dispatch_config.sql"));

        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS production_item_auto_dispatch_config");
        assertThat(sql).contains("batch_size");
        assertThat(sql).contains("batch_interval_ms");
        assertThat(sql).contains("max_rounds");
        assertThat(sql).contains("warn_duration_ms");
        assertThat(sql).contains("failure_breaker");
        assertThat(sql).doesNotContain("daily_max_rows");
        assertThat(sql).contains("MES_PRODUCTION_AUTO_DISPATCH");
        assertThat(sql).contains("market:production-item:mes-production-schedule");
        assertThat(sql).contains("DEFAULT FALSE");
    }

    @Test
    void candidateQueriesShareTheSameSkipReasonAlignedPredicate() throws Exception {
        String mapper = Files.readString(Path.of(
                "src/main/java/com/machining/quote/mapper/OrderDeliveryTicketAllocationMapper.java"));

        // 关联到别的销售订单的错位行会被上下文加载器判为 error 并整批跳过，跳过又不回写状态，
        // 一旦被选进候选就会一直占住候选窗口，所以候选查询必须在 SQL 层就排除它们。
        assertThat(mapper)
                .as("候选行查询必须排除关联到其他销售订单的错位行")
                .contains("AND (alloc.source_order_id IS NULL OR item.sales_order_id = alloc.source_order_id)");

        // 列表查询和剩余数统计必须同一口径，否则 remaining 与实际候选不一致，状态会被误判。
        long predicateCount = mapper.lines()
                .filter(line -> line.contains("item.sales_order_id = alloc.source_order_id"))
                .count();
        assertThat(predicateCount)
                .as("selectAutoDispatchCandidateIds 与 countAutoDispatchCandidates 必须共用同一口径")
                .isEqualTo(2);
    }

    private ProductionItemAutoDispatchConfigReq reqWithBatchSize(int batchSize) {
        ProductionItemAutoDispatchConfigReq req = new ProductionItemAutoDispatchConfigReq();
        req.setBatchSize(batchSize);
        return req;
    }

    private ProductionItemAutoDispatchConfigReq reqWithInterval(int intervalMs) {
        ProductionItemAutoDispatchConfigReq req = new ProductionItemAutoDispatchConfigReq();
        req.setBatchIntervalMs(intervalMs);
        return req;
    }

    private ProductionItemAutoDispatchConfigReq reqWithMaxRounds(int maxRounds) {
        ProductionItemAutoDispatchConfigReq req = new ProductionItemAutoDispatchConfigReq();
        req.setMaxRounds(maxRounds);
        return req;
    }

    private ProductionItemAutoDispatchConfigReq reqWithBreaker(int breaker) {
        ProductionItemAutoDispatchConfigReq req = new ProductionItemAutoDispatchConfigReq();
        req.setFailureBreaker(breaker);
        return req;
    }

    private ProductionItemAutoDispatchConfig config(boolean enabled, LocalTime dailyRunTime, LocalDate lastRunDate) {
        ProductionItemAutoDispatchConfig config = new ProductionItemAutoDispatchConfig();
        config.setId(CONFIG_ID);
        config.setEnabled(enabled);
        config.setScheduleMode("DAILY_TIME");
        config.setDailyRunTime(dailyRunTime);
        config.setBatchSize(200);
        config.setBatchIntervalMs(3000);
        config.setMaxRounds(50);
        config.setWarnDurationMs(3_600_000L);
        config.setFailureBreaker(3);
        config.setIncludeFailed(false);
        config.setLastRunDate(lastRunDate);
        config.setLastStatus("NEVER");
        return config;
    }

    private ProductionItemAutoDispatchConfigService service(
            ProductionItemAutoDispatchConfigMapper mapper,
            AuditService auditService) {
        ProductionItemAutoDispatchConfigService service = new ProductionItemAutoDispatchConfigService();
        ReflectionTestUtils.setField(service, "configMapper", mapper);
        ReflectionTestUtils.setField(service, "auditService", auditService);
        LocalDateTime current = LocalDateTime.of(2026, 10, 27, 12, 0);
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(current.atZone(ZONE).toInstant(), ZONE));
        return service;
    }
}
