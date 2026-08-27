package com.machining.quote.service.mes;

import com.machining.quote.dto.MesMasterDataSyncScheduleConfigDTO;
import com.machining.quote.dto.MesMasterDataSyncScheduleConfigReq;
import com.machining.quote.entity.MesMasterDataSyncScheduleConfig;
import com.machining.quote.mapper.MesMasterDataSyncScheduleConfigMapper;
import com.machining.quote.service.AuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Service
public class MesMasterDataSyncScheduleConfigService {
    private static final long DEFAULT_STARTUP_DELAY_MS = 30_000L;
    private static final long DEFAULT_FIXED_DELAY_MS = 1_800_000L;
    private static final long MAX_STARTUP_DELAY_MS = 86_400_000L;
    private static final long MIN_FIXED_DELAY_MS = 60_000L;
    private static final long MAX_FIXED_DELAY_MS = 604_800_000L;
    private static final Set<String> SUPPORTED_SYNC_TYPES = Set.of(
            MesMasterDataSyncService.SYNC_TYPE_MATERIAL,
            MesMasterDataSyncService.SYNC_TYPE_PROCESS_TYPE
    );

    @Autowired
    MesMasterDataSyncScheduleConfigMapper configMapper;

    @Autowired
    AuditService auditService;

    @Value("${erp.mes.master-data-sync.enabled:false}")
    boolean fallbackEnabled;

    @Value("${erp.mes.master-data-sync.startup-delay-ms:30000}")
    long fallbackStartupDelayMs = DEFAULT_STARTUP_DELAY_MS;

    @Value("${erp.mes.master-data-sync.fixed-delay-ms:1800000}")
    long fallbackFixedDelayMs = DEFAULT_FIXED_DELAY_MS;

    Clock clock = Clock.system(ZoneId.of("Asia/Shanghai"));

    public MesMasterDataSyncScheduleConfigDTO getSetting(String syncType) {
        LoadedConfig loaded = loadConfigOrFallback(syncType);
        return toDTO(loaded.config(), loaded.fallback());
    }

    public MesMasterDataSyncScheduleConfigDTO updateSetting(String syncType, MesMasterDataSyncScheduleConfigReq req) {
        String normalizedType = validateSyncType(syncType);
        MesMasterDataSyncScheduleConfig before = configMapper.selectById(normalizedType);
        MesMasterDataSyncScheduleConfig config = before == null ? defaultConfigFromFallback(normalizedType) : copy(before);
        applyRequest(config, req);
        config.setUpdatedAt(now());
        if (before == null) {
            configMapper.insert(config);
        } else {
            configMapper.updateById(config);
        }
        auditService.record(
                "DICT_MES_MASTER_DATA_SYNC_SCHEDULE_UPDATE",
                "mes_master_data_sync_schedule_config",
                normalizedType,
                before == null ? null : summary(before),
                summary(config),
                "DICT",
                "SUCCESS",
                "更新 MES 主数据定时同步策略",
                null
        );
        return toDTO(config, false);
    }

    public boolean shouldRun(String syncType, LocalDateTime schedulerStartedAt) {
        LoadedConfig loaded = loadConfigOrFallback(syncType);
        MesMasterDataSyncScheduleConfig config = loaded.config();
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            return false;
        }
        LocalDateTime current = now();
        LocalDateTime base = config.getLastScheduledRunAt() == null ? schedulerStartedAt : config.getLastScheduledRunAt();
        long delay = config.getLastScheduledRunAt() == null ? config.getStartupDelayMs() : config.getFixedDelayMs();
        return base == null || !current.isBefore(base.plusNanos(delay * 1_000_000L));
    }

    public void updateScheduledResult(String syncType, MesMasterDataSyncService.SyncResult result) {
        LoadedConfig loaded = loadConfigOrFallback(syncType);
        MesMasterDataSyncScheduleConfig config = loaded.fallback() ? defaultConfigFromFallback(validateSyncType(syncType)) : copy(loaded.config());
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

    private LoadedConfig loadConfigOrFallback(String syncType) {
        String normalizedType = validateSyncType(syncType);
        MesMasterDataSyncScheduleConfig existing = configMapper.selectById(normalizedType);
        if (existing == null) {
            return new LoadedConfig(defaultConfigFromFallback(normalizedType), true);
        }
        return new LoadedConfig(normalize(existing), false);
    }

    private MesMasterDataSyncScheduleConfig defaultConfigFromFallback(String syncType) {
        MesMasterDataSyncScheduleConfig config = new MesMasterDataSyncScheduleConfig();
        config.setSyncType(validateSyncType(syncType));
        config.setEnabled(fallbackEnabled);
        config.setStartupDelayMs(clamp(fallbackStartupDelayMs, 0L, MAX_STARTUP_DELAY_MS, DEFAULT_STARTUP_DELAY_MS));
        config.setFixedDelayMs(clamp(fallbackFixedDelayMs, MIN_FIXED_DELAY_MS, MAX_FIXED_DELAY_MS, DEFAULT_FIXED_DELAY_MS));
        config.setLastScheduledStatus("NEVER");
        config.setUpdatedAt(now());
        return config;
    }

    private void applyRequest(MesMasterDataSyncScheduleConfig config, MesMasterDataSyncScheduleConfigReq req) {
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
            throw new IllegalArgumentException("startup delay must be between 0 seconds and 24 hours");
        }
    }

    private void validateFixedDelay(long value) {
        if (value < MIN_FIXED_DELAY_MS || value > MAX_FIXED_DELAY_MS) {
            throw new IllegalArgumentException("fixed delay must be between 1 minute and 7 days");
        }
    }

    private MesMasterDataSyncScheduleConfig normalize(MesMasterDataSyncScheduleConfig config) {
        config.setSyncType(validateSyncType(config.getSyncType()));
        if (config.getEnabled() == null) config.setEnabled(false);
        if (config.getStartupDelayMs() == null) config.setStartupDelayMs(DEFAULT_STARTUP_DELAY_MS);
        if (config.getFixedDelayMs() == null) config.setFixedDelayMs(DEFAULT_FIXED_DELAY_MS);
        if (isBlank(config.getLastScheduledStatus())) config.setLastScheduledStatus("NEVER");
        if (config.getUpdatedAt() == null) config.setUpdatedAt(now());
        return config;
    }

    private MesMasterDataSyncScheduleConfig copy(MesMasterDataSyncScheduleConfig source) {
        MesMasterDataSyncScheduleConfig copy = new MesMasterDataSyncScheduleConfig();
        copy.setSyncType(source.getSyncType());
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

    private MesMasterDataSyncScheduleConfigDTO toDTO(MesMasterDataSyncScheduleConfig config, boolean fallback) {
        MesMasterDataSyncScheduleConfig normalized = normalize(copy(config));
        MesMasterDataSyncScheduleConfigDTO dto = new MesMasterDataSyncScheduleConfigDTO();
        dto.setSyncType(normalized.getSyncType());
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

    private Map<String, Object> summary(MesMasterDataSyncScheduleConfig config) {
        MesMasterDataSyncScheduleConfig normalized = normalize(copy(config));
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("syncType", normalized.getSyncType());
        summary.put("enabled", Boolean.TRUE.equals(normalized.getEnabled()));
        summary.put("startupDelayMs", normalized.getStartupDelayMs());
        summary.put("fixedDelayMs", normalized.getFixedDelayMs());
        summary.put("lastScheduledStatus", normalized.getLastScheduledStatus());
        return summary;
    }

    private String scheduleMessage(MesMasterDataSyncService.SyncResult result) {
        if (result == null) {
            return "scheduled sync returned no result";
        }
        if (!result.success()) {
            return truncate(isBlank(result.message()) ? "MES master data scheduled sync failed" : result.message(), 500);
        }
        return truncate("OK fetched=" + result.fetchedCount()
                + " valid=" + result.validCount()
                + " invalid=" + result.invalidCount()
                + " duplicate=" + result.duplicateCount()
                + " upserted=" + result.upsertedCount()
                + " disabled=" + result.disabledCount()
                + " deleted=" + result.deletedCount(), 500);
    }

    private String validateSyncType(String syncType) {
        if (syncType == null || !SUPPORTED_SYNC_TYPES.contains(syncType.trim())) {
            throw new IllegalArgumentException("Unsupported MES sync type: " + syncType);
        }
        return syncType.trim();
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

    private record LoadedConfig(MesMasterDataSyncScheduleConfig config, boolean fallback) {
    }
}
