package com.machining.quote.service;

import com.machining.quote.config.AuthContext;
import com.machining.quote.dto.ProductionItemAutoDispatchConfigDTO;
import com.machining.quote.dto.ProductionItemAutoDispatchConfigReq;
import com.machining.quote.entity.ProductionItemAutoDispatchConfig;
import com.machining.quote.mapper.ProductionItemAutoDispatchConfigMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Service
public class ProductionItemAutoDispatchConfigService {
    public static final String CONFIG_ID = "MES_PRODUCTION_AUTO_DISPATCH";
    public static final String SCHEDULE_MODE_DAILY_TIME = "DAILY_TIME";

    static final LocalTime DEFAULT_DAILY_RUN_TIME = LocalTime.of(17, 0);
    static final int DEFAULT_BATCH_SIZE = 200;
    static final int DEFAULT_BATCH_INTERVAL_MS = 3000;
    static final int DEFAULT_MAX_ROUNDS = 50;
    static final long DEFAULT_WARN_DURATION_MS = 3_600_000L;
    static final int DEFAULT_FAILURE_BREAKER = 3;

    static final int MIN_BATCH_SIZE = 100;
    static final int MAX_BATCH_SIZE = 1000;
    static final int MIN_BATCH_INTERVAL_MS = 1000;
    static final int MAX_BATCH_INTERVAL_MS = 60_000;
    static final int MIN_MAX_ROUNDS = 1;
    static final int MAX_MAX_ROUNDS = 500;
    static final long MIN_WARN_DURATION_MS = 60_000L;
    static final long MAX_WARN_DURATION_MS = 28_800_000L;
    static final int MIN_FAILURE_BREAKER = 1;
    static final int MAX_FAILURE_BREAKER = 10;

    private static final Set<String> SUPPORTED_SCHEDULE_MODES = Set.of(SCHEDULE_MODE_DAILY_TIME);

    @Autowired
    ProductionItemAutoDispatchConfigMapper configMapper;

    @Autowired(required = false)
    AuditService auditService;

    Clock clock = Clock.system(ZoneId.of("Asia/Shanghai"));

    public ProductionItemAutoDispatchConfigDTO getSetting() {
        return toDTO(loadConfig());
    }

    public ProductionItemAutoDispatchConfigDTO updateSetting(ProductionItemAutoDispatchConfigReq req) {
        ProductionItemAutoDispatchConfig before = configMapper.selectById(CONFIG_ID);
        ProductionItemAutoDispatchConfig config = before == null ? defaultConfig() : copy(before);
        applyRequest(config, req);
        normalize(config);
        config.setUpdatedAt(now());
        config.setUpdatedBy(currentUsername());
        if (before == null) {
            configMapper.insert(config);
        } else {
            configMapper.updateById(config);
        }
        recordAudit("PRODUCTION_ITEM_AUTO_DISPATCH_CONFIG_UPDATE", before, config);
        return toDTO(config);
    }

    public boolean isEnabled() {
        return Boolean.TRUE.equals(loadConfig().getEnabled());
    }

    public boolean shouldRun(LocalDateTime current) {
        ProductionItemAutoDispatchConfig config = loadConfig();
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            return false;
        }
        LocalTime dailyRunTime = config.getDailyRunTime() == null ? DEFAULT_DAILY_RUN_TIME : config.getDailyRunTime();
        LocalDateTime todayRunAt = LocalDateTime.of(current.toLocalDate(), dailyRunTime);
        if (current.isBefore(todayRunAt)) {
            return false;
        }
        LocalDate lastRunDate = config.getLastRunDate();
        return lastRunDate == null || lastRunDate.isBefore(current.toLocalDate());
    }

    public boolean claimDailyRun(LocalDateTime current) {
        ensureConfigRow();
        return configMapper.claimDailyRun(CONFIG_ID, current, current.toLocalDate()) > 0;
    }

    public void resetDailyRunClaim() {
        configMapper.resetDailyRunClaim(CONFIG_ID);
    }

    public void updateResult(Long taskId, String status, String message, ProductionItemAutoDispatchResult result) {
        ProductionItemAutoDispatchConfig config = configMapper.selectById(CONFIG_ID);
        boolean exists = config != null;
        if (!exists) {
            config = defaultConfig();
        }
        config.setLastRunAt(now());
        config.setLastTaskId(taskId);
        config.setLastStatus(isBlank(status) ? "NEVER" : status);
        config.setLastMessage(truncate(message, 500));
        if (result != null) {
            config.setLastTotal(result.total());
            config.setLastSuccess(result.success());
            config.setLastFailed(result.failed());
            config.setLastSkipped(result.skipped());
            config.setLastRounds(result.rounds());
            config.setLastDurationMs(result.durationMs());
        }
        config.setUpdatedAt(now());
        if (exists) {
            configMapper.updateById(config);
        } else {
            configMapper.insert(config);
        }
    }

    public ProductionItemAutoDispatchConfig loadConfig() {
        ProductionItemAutoDispatchConfig existing = configMapper.selectById(CONFIG_ID);
        ProductionItemAutoDispatchConfig config = existing == null ? defaultConfig() : copy(existing);
        normalize(config);
        return config;
    }

    public void ensureConfigRow() {
        if (configMapper.selectById(CONFIG_ID) == null) {
            ProductionItemAutoDispatchConfig config = defaultConfig();
            config.setUpdatedAt(now());
            configMapper.insert(config);
        }
    }

    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private ProductionItemAutoDispatchConfig defaultConfig() {
        ProductionItemAutoDispatchConfig config = new ProductionItemAutoDispatchConfig();
        config.setId(CONFIG_ID);
        config.setEnabled(false);
        config.setScheduleMode(SCHEDULE_MODE_DAILY_TIME);
        config.setDailyRunTime(DEFAULT_DAILY_RUN_TIME);
        config.setBatchSize(DEFAULT_BATCH_SIZE);
        config.setBatchIntervalMs(DEFAULT_BATCH_INTERVAL_MS);
        config.setMaxRounds(DEFAULT_MAX_ROUNDS);
        config.setWarnDurationMs(DEFAULT_WARN_DURATION_MS);
        config.setFailureBreaker(DEFAULT_FAILURE_BREAKER);
        config.setIncludeFailed(false);
        config.setLastStatus("NEVER");
        return config;
    }

    private void applyRequest(ProductionItemAutoDispatchConfig config, ProductionItemAutoDispatchConfigReq req) {
        if (req == null) {
            return;
        }
        if (req.getEnabled() != null) {
            config.setEnabled(req.getEnabled());
        }
        if (req.getScheduleMode() != null) {
            config.setScheduleMode(validateScheduleMode(req.getScheduleMode()));
        }
        if (req.getDailyRunTime() != null) {
            config.setDailyRunTime(req.getDailyRunTime());
        }
        if (req.getBatchSize() != null) {
            config.setBatchSize(validateRange("batch size", req.getBatchSize(), MIN_BATCH_SIZE, MAX_BATCH_SIZE));
        }
        if (req.getBatchIntervalMs() != null) {
            config.setBatchIntervalMs(validateRange("batch interval", req.getBatchIntervalMs(),
                    MIN_BATCH_INTERVAL_MS, MAX_BATCH_INTERVAL_MS));
        }
        if (req.getMaxRounds() != null) {
            config.setMaxRounds(validateRange("max rounds", req.getMaxRounds(), MIN_MAX_ROUNDS, MAX_MAX_ROUNDS));
        }
        if (req.getWarnDurationMs() != null) {
            long value = req.getWarnDurationMs();
            if (value < MIN_WARN_DURATION_MS || value > MAX_WARN_DURATION_MS) {
                throw new IllegalArgumentException("warn duration must be between 1 minute and 8 hours");
            }
            config.setWarnDurationMs(value);
        }
        if (req.getFailureBreaker() != null) {
            config.setFailureBreaker(validateRange("failure breaker", req.getFailureBreaker(),
                    MIN_FAILURE_BREAKER, MAX_FAILURE_BREAKER));
        }
    }

    private ProductionItemAutoDispatchConfig normalize(ProductionItemAutoDispatchConfig config) {
        config.setId(CONFIG_ID);
        if (config.getEnabled() == null) {
            config.setEnabled(false);
        }
        config.setScheduleMode(validateScheduleMode(config.getScheduleMode()));
        if (config.getDailyRunTime() == null) {
            config.setDailyRunTime(DEFAULT_DAILY_RUN_TIME);
        }
        config.setBatchSize(clampInt(config.getBatchSize(), MIN_BATCH_SIZE, MAX_BATCH_SIZE, DEFAULT_BATCH_SIZE));
        config.setBatchIntervalMs(clampInt(config.getBatchIntervalMs(), MIN_BATCH_INTERVAL_MS,
                MAX_BATCH_INTERVAL_MS, DEFAULT_BATCH_INTERVAL_MS));
        config.setMaxRounds(clampInt(config.getMaxRounds(), MIN_MAX_ROUNDS, MAX_MAX_ROUNDS, DEFAULT_MAX_ROUNDS));
        config.setWarnDurationMs(clampLong(config.getWarnDurationMs(), MIN_WARN_DURATION_MS,
                MAX_WARN_DURATION_MS, DEFAULT_WARN_DURATION_MS));
        config.setFailureBreaker(clampInt(config.getFailureBreaker(), MIN_FAILURE_BREAKER,
                MAX_FAILURE_BREAKER, DEFAULT_FAILURE_BREAKER));
        if (config.getIncludeFailed() == null) {
            config.setIncludeFailed(false);
        }
        if (isBlank(config.getLastStatus())) {
            config.setLastStatus("NEVER");
        }
        return config;
    }

    private ProductionItemAutoDispatchConfig copy(ProductionItemAutoDispatchConfig source) {
        ProductionItemAutoDispatchConfig copy = new ProductionItemAutoDispatchConfig();
        copy.setId(source.getId());
        copy.setEnabled(source.getEnabled());
        copy.setScheduleMode(source.getScheduleMode());
        copy.setDailyRunTime(source.getDailyRunTime());
        copy.setBatchSize(source.getBatchSize());
        copy.setBatchIntervalMs(source.getBatchIntervalMs());
        copy.setMaxRounds(source.getMaxRounds());
        copy.setWarnDurationMs(source.getWarnDurationMs());
        copy.setFailureBreaker(source.getFailureBreaker());
        copy.setIncludeFailed(source.getIncludeFailed());
        copy.setLastRunAt(source.getLastRunAt());
        copy.setLastRunDate(source.getLastRunDate());
        copy.setLastTaskId(source.getLastTaskId());
        copy.setLastStatus(source.getLastStatus());
        copy.setLastMessage(source.getLastMessage());
        copy.setLastTotal(source.getLastTotal());
        copy.setLastSuccess(source.getLastSuccess());
        copy.setLastFailed(source.getLastFailed());
        copy.setLastSkipped(source.getLastSkipped());
        copy.setLastRounds(source.getLastRounds());
        copy.setLastDurationMs(source.getLastDurationMs());
        copy.setUpdatedBy(source.getUpdatedBy());
        copy.setUpdatedAt(source.getUpdatedAt());
        return copy;
    }

    private ProductionItemAutoDispatchConfigDTO toDTO(ProductionItemAutoDispatchConfig config) {
        ProductionItemAutoDispatchConfig normalized = normalize(copy(config));
        ProductionItemAutoDispatchConfigDTO dto = new ProductionItemAutoDispatchConfigDTO();
        dto.setId(CONFIG_ID);
        dto.setEnabled(Boolean.TRUE.equals(normalized.getEnabled()));
        dto.setScheduleMode(normalized.getScheduleMode());
        dto.setDailyRunTime(normalized.getDailyRunTime());
        dto.setBatchSize(normalized.getBatchSize());
        dto.setBatchIntervalMs(normalized.getBatchIntervalMs());
        dto.setMaxRounds(normalized.getMaxRounds());
        dto.setWarnDurationMs(normalized.getWarnDurationMs());
        dto.setFailureBreaker(normalized.getFailureBreaker());
        dto.setIncludeFailed(Boolean.TRUE.equals(normalized.getIncludeFailed()));
        dto.setLastRunAt(normalized.getLastRunAt());
        dto.setLastRunDate(normalized.getLastRunDate());
        dto.setLastTaskId(normalized.getLastTaskId());
        dto.setLastStatus(normalized.getLastStatus());
        dto.setLastMessage(normalized.getLastMessage());
        dto.setLastTotal(normalized.getLastTotal());
        dto.setLastSuccess(normalized.getLastSuccess());
        dto.setLastFailed(normalized.getLastFailed());
        dto.setLastSkipped(normalized.getLastSkipped());
        dto.setLastRounds(normalized.getLastRounds());
        dto.setLastDurationMs(normalized.getLastDurationMs());
        dto.setUpdatedAt(normalized.getUpdatedAt());
        return dto;
    }

    private void recordAudit(String action, ProductionItemAutoDispatchConfig before,
                             ProductionItemAutoDispatchConfig after) {
        if (auditService == null) {
            return;
        }
        auditService.record(
                action,
                "production_item_auto_dispatch_config",
                CONFIG_ID,
                before == null ? null : summary(before),
                summary(after),
                "DELIVERY",
                "SUCCESS",
                "更新待产零件定时下发策略",
                null
        );
    }

    private Map<String, Object> summary(ProductionItemAutoDispatchConfig config) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("enabled", Boolean.TRUE.equals(config.getEnabled()));
        summary.put("scheduleMode", config.getScheduleMode());
        summary.put("dailyRunTime", config.getDailyRunTime());
        summary.put("batchSize", config.getBatchSize());
        summary.put("batchIntervalMs", config.getBatchIntervalMs());
        summary.put("maxRounds", config.getMaxRounds());
        summary.put("warnDurationMs", config.getWarnDurationMs());
        summary.put("failureBreaker", config.getFailureBreaker());
        return summary;
    }

    private String validateScheduleMode(String scheduleMode) {
        if (isBlank(scheduleMode)) {
            return SCHEDULE_MODE_DAILY_TIME;
        }
        String normalized = scheduleMode.trim().toUpperCase();
        if (!SUPPORTED_SCHEDULE_MODES.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported production auto dispatch schedule mode: " + scheduleMode);
        }
        return normalized;
    }

    private int validateRange(String label, int value, int min, int max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(label + " must be between " + min + " and " + max);
        }
        return value;
    }

    private int clampInt(Integer value, int min, int max, int fallback) {
        if (value == null || value < min || value > max) {
            return fallback;
        }
        return value;
    }

    private long clampLong(Long value, long min, long max, long fallback) {
        if (value == null || value < min || value > max) {
            return fallback;
        }
        return value;
    }

    private String currentUsername() {
        AuthContext.SessionUser current = AuthContext.get();
        return current == null ? null : current.username();
    }

    private String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
