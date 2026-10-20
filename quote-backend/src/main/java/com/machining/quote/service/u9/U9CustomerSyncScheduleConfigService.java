package com.machining.quote.service.u9;

import com.machining.quote.dto.U9CustomerSyncScheduleConfigDTO;
import com.machining.quote.dto.U9CustomerSyncScheduleConfigReq;
import com.machining.quote.entity.U9CustomerSyncScheduleConfig;
import com.machining.quote.mapper.U9CustomerSyncScheduleConfigMapper;
import com.machining.quote.service.AuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class U9CustomerSyncScheduleConfigService {
    private static final int CONFIG_ID = 1;
    private static final long DEFAULT_STARTUP_DELAY_MS = 30_000L;
    private static final long DEFAULT_FIXED_DELAY_MS = 1_800_000L;
    private static final long MAX_STARTUP_DELAY_MS = 86_400_000L;
    private static final long MIN_FIXED_DELAY_MS = 60_000L;
    private static final long MAX_FIXED_DELAY_MS = 604_800_000L;

    @Autowired
    U9CustomerSyncScheduleConfigMapper configMapper;

    @Autowired
    AuditService auditService;

    @Value("${erp.u9.customer-sync.enabled:false}")
    boolean fallbackEnabled;

    @Value("${erp.u9.customer-sync.startup-delay-ms:30000}")
    long fallbackStartupDelayMs = DEFAULT_STARTUP_DELAY_MS;

    @Value("${erp.u9.customer-sync.fixed-delay-ms:1800000}")
    long fallbackFixedDelayMs = DEFAULT_FIXED_DELAY_MS;

    Clock clock = Clock.system(ZoneId.of("Asia/Shanghai"));

    public U9CustomerSyncScheduleConfigDTO getSetting() {
        LoadedConfig loaded = loadConfigOrFallback();
        return toDTO(loaded.config(), loaded.fallback());
    }

    public U9CustomerSyncScheduleConfigDTO updateSetting(U9CustomerSyncScheduleConfigReq req) {
        U9CustomerSyncScheduleConfig before = configMapper.selectById(CONFIG_ID);
        U9CustomerSyncScheduleConfig config = before == null ? defaultConfigFromFallback() : copy(before);
        applyRequest(config, req);
        config.setUpdatedAt(now());
        if (before == null) {
            configMapper.insert(config);
        } else {
            configMapper.updateById(config);
        }
        auditService.record(
                "U9_CUSTOMER_SYNC_SCHEDULE_UPDATE",
                "u9_customer_sync_schedule_config",
                CONFIG_ID,
                before == null ? null : summary(before),
                summary(config),
                "SYSTEM",
                "SUCCESS",
                "更新 U9 客户定时同步策略",
                null
        );
        return toDTO(config, false);
    }

    public boolean shouldRun(LocalDateTime schedulerStartedAt) {
        LoadedConfig loaded = loadConfigOrFallback();
        U9CustomerSyncScheduleConfig config = loaded.config();
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            return false;
        }
        LocalDateTime current = now();
        LocalDateTime base = config.getLastScheduledRunAt() == null ? schedulerStartedAt : config.getLastScheduledRunAt();
        long delay = config.getLastScheduledRunAt() == null ? config.getStartupDelayMs() : config.getFixedDelayMs();
        return base == null || !current.isBefore(base.plusNanos(delay * 1_000_000L));
    }

    public void updateScheduledResult(U9MasterDataSyncService.SyncResult result) {
        LoadedConfig loaded = loadConfigOrFallback();
        U9CustomerSyncScheduleConfig config = loaded.fallback() ? defaultConfigFromFallback() : copy(loaded.config());
        config.setLastScheduledRunAt(now());
        config.setLastScheduledSyncRunId(result == null ? null : result.syncRunId());
        config.setLastScheduledStatus(result != null && result.success() ? "SUCCESS" : "FAILED");
        config.setLastScheduledMessage(scheduleMessage(result));
        config.setUpdatedAt(now());
        if (loaded.fallback()) {
            configMapper.insert(config);
        } else {
            configMapper.updateById(config);
        }
    }

    private LoadedConfig loadConfigOrFallback() {
        U9CustomerSyncScheduleConfig existing = configMapper.selectById(CONFIG_ID);
        if (existing == null) {
            return new LoadedConfig(defaultConfigFromFallback(), true);
        }
        return new LoadedConfig(normalize(existing), false);
    }

    private U9CustomerSyncScheduleConfig defaultConfigFromFallback() {
        U9CustomerSyncScheduleConfig config = new U9CustomerSyncScheduleConfig();
        config.setId(CONFIG_ID);
        config.setEnabled(fallbackEnabled);
        config.setStartupDelayMs(clamp(fallbackStartupDelayMs, 0L, MAX_STARTUP_DELAY_MS, DEFAULT_STARTUP_DELAY_MS));
        config.setFixedDelayMs(clamp(fallbackFixedDelayMs, MIN_FIXED_DELAY_MS, MAX_FIXED_DELAY_MS, DEFAULT_FIXED_DELAY_MS));
        config.setLastScheduledStatus("NEVER");
        config.setUpdatedAt(now());
        return config;
    }

    private void applyRequest(U9CustomerSyncScheduleConfig config, U9CustomerSyncScheduleConfigReq req) {
        if (req == null) {
            return;
        }
        if (req.getEnabled() != null) {
            config.setEnabled(req.getEnabled());
        }
        if (req.getStartupDelayMs() != null) {
            validateStartupDelay(req.getStartupDelayMs());
            config.setStartupDelayMs(req.getStartupDelayMs());
        }
        if (req.getFixedDelayMs() != null) {
            validateFixedDelay(req.getFixedDelayMs());
            config.setFixedDelayMs(req.getFixedDelayMs());
        }
    }

    private void validateStartupDelay(long value) {
        if (value < 0 || value > MAX_STARTUP_DELAY_MS) {
            throw new IllegalArgumentException("启动后首次同步延迟必须在 0 秒到 24 小时之间");
        }
    }

    private void validateFixedDelay(long value) {
        if (value < MIN_FIXED_DELAY_MS || value > MAX_FIXED_DELAY_MS) {
            throw new IllegalArgumentException("同步完成后间隔必须在 1 分钟到 7 天之间");
        }
    }

    private U9CustomerSyncScheduleConfig normalize(U9CustomerSyncScheduleConfig config) {
        if (config.getId() == null) config.setId(CONFIG_ID);
        if (config.getEnabled() == null) config.setEnabled(false);
        if (config.getStartupDelayMs() == null) config.setStartupDelayMs(DEFAULT_STARTUP_DELAY_MS);
        if (config.getFixedDelayMs() == null) config.setFixedDelayMs(DEFAULT_FIXED_DELAY_MS);
        if (isBlank(config.getLastScheduledStatus())) config.setLastScheduledStatus("NEVER");
        if (config.getUpdatedAt() == null) config.setUpdatedAt(now());
        return config;
    }

    private U9CustomerSyncScheduleConfig copy(U9CustomerSyncScheduleConfig source) {
        U9CustomerSyncScheduleConfig copy = new U9CustomerSyncScheduleConfig();
        copy.setId(source.getId());
        copy.setEnabled(source.getEnabled());
        copy.setStartupDelayMs(source.getStartupDelayMs());
        copy.setFixedDelayMs(source.getFixedDelayMs());
        copy.setLastScheduledRunAt(source.getLastScheduledRunAt());
        copy.setLastScheduledSyncRunId(source.getLastScheduledSyncRunId());
        copy.setLastScheduledStatus(source.getLastScheduledStatus());
        copy.setLastScheduledMessage(source.getLastScheduledMessage());
        copy.setUpdatedAt(source.getUpdatedAt());
        return normalize(copy);
    }

    private U9CustomerSyncScheduleConfigDTO toDTO(U9CustomerSyncScheduleConfig config, boolean fallback) {
        U9CustomerSyncScheduleConfig normalized = normalize(copy(config));
        U9CustomerSyncScheduleConfigDTO dto = new U9CustomerSyncScheduleConfigDTO();
        dto.setEnabled(Boolean.TRUE.equals(normalized.getEnabled()));
        dto.setStartupDelayMs(normalized.getStartupDelayMs());
        dto.setFixedDelayMs(normalized.getFixedDelayMs());
        dto.setLastScheduledRunAt(normalized.getLastScheduledRunAt());
        dto.setLastScheduledSyncRunId(normalized.getLastScheduledSyncRunId());
        dto.setLastScheduledStatus(normalized.getLastScheduledStatus());
        dto.setLastScheduledMessage(normalized.getLastScheduledMessage());
        dto.setUpdatedAt(normalized.getUpdatedAt());
        dto.setFallback(fallback);
        return dto;
    }

    private Map<String, Object> summary(U9CustomerSyncScheduleConfig config) {
        U9CustomerSyncScheduleConfig normalized = normalize(copy(config));
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("enabled", Boolean.TRUE.equals(normalized.getEnabled()));
        summary.put("startupDelayMs", normalized.getStartupDelayMs());
        summary.put("fixedDelayMs", normalized.getFixedDelayMs());
        summary.put("lastScheduledStatus", normalized.getLastScheduledStatus());
        return summary;
    }

    private String scheduleMessage(U9MasterDataSyncService.SyncResult result) {
        if (result == null) {
            return "定时同步未返回结果";
        }
        if (!result.success()) {
            return truncate(isBlank(result.message()) ? "U9 客户定时同步失败" : result.message(), 500);
        }
        return truncate("OK fetched=" + result.fetchedCount()
                + " upserted=" + result.upsertedCount()
                + " disabled=" + result.disabledCount(), 500);
    }

    private long clamp(long value, long min, long max, long defaultValue) {
        if (value < min || value > max) {
            return defaultValue;
        }
        return value;
    }

    private String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private record LoadedConfig(U9CustomerSyncScheduleConfig config, boolean fallback) {
    }
}
