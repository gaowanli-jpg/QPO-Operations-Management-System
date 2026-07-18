package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.dto.AuditLogRetentionConfigDTO;
import com.machining.quote.dto.AuditLogRetentionConfigReq;
import com.machining.quote.dto.AuditLogRetentionRunDTO;
import com.machining.quote.entity.AuditLog;
import com.machining.quote.entity.AuditLogRetentionConfig;
import com.machining.quote.mapper.AuditLogMapper;
import com.machining.quote.mapper.AuditLogRetentionConfigMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class AuditLogRetentionService {
    private static final Logger log = LoggerFactory.getLogger(AuditLogRetentionService.class);
    private static final int CONFIG_ID = 1;
    private static final int DEFAULT_RETENTION_DAYS = 180;
    private static final int CLEANUP_BATCH_LIMIT = 5000;
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter ARCHIVE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    @Autowired
    private AuditLogRetentionConfigMapper configMapper;

    @Autowired
    private AuditLogMapper auditLogMapper;

    @Autowired
    private AuditService auditService;

    @Value("${erp.audit-log.archive-dir:}")
    private String archiveRoot;

    @Value("${erp.backup-dir:D:/ERP_Files/backup/}")
    private String backupDir;

    Clock clock = Clock.system(ZoneId.of("Asia/Shanghai"));

    public AuditLogRetentionConfigDTO getSetting() {
        return toDTO(loadConfigOrDefault());
    }

    public AuditLogRetentionConfigDTO updateSetting(AuditLogRetentionConfigReq req) {
        AuditLogRetentionConfig before = configMapper.selectById(CONFIG_ID);
        AuditLogRetentionConfig config = before == null ? defaultConfig() : copyConfig(before);
        applyRequest(config, req);
        config.setUpdatedAt(now());
        if (before == null) {
            configMapper.insert(config);
        } else {
            configMapper.updateById(config);
        }
        auditService.record(
                "AUDIT_RETENTION_CONFIG_UPDATE",
                "audit_log_retention_config",
                CONFIG_ID,
                before == null ? null : summary(before),
                summary(config),
                "SYSTEM",
                "SUCCESS",
                "更新操作日志保留策略",
                null
        );
        return toDTO(config);
    }

    public AuditLogRetentionRunDTO runCleanupNow() {
        return runCleanup(true);
    }

    @Scheduled(fixedDelayString = "${erp.audit-log.retention-check-ms:60000}")
    public void runScheduledCleanupIfDue() {
        AuditLogRetentionConfig config = loadConfigOrDefault();
        if (!Boolean.TRUE.equals(config.getEnabled()) || !isCleanupTimeDue(config)) {
            return;
        }
        runCleanup(false);
    }

    private AuditLogRetentionRunDTO runCleanup(boolean manual) {
        AuditLogRetentionConfig config = loadConfigOrDefault();
        LocalDateTime runAt = now();
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            AuditLogRetentionRunDTO skipped = runResult("DISABLED", 0, "自动清理未开启", null, runAt);
            updateLastRun(config, skipped);
            if (manual) {
                recordCleanupAudit(skipped, config, "SUCCESS", null);
            }
            return skipped;
        }

        try {
            LocalDateTime cutoff = runAt.minusDays(config.getRetentionDays());
            List<AuditLog> expiredLogs = findExpiredLogs(cutoff);
            if (expiredLogs.isEmpty()) {
                AuditLogRetentionRunDTO result = runResult("SUCCESS", 0, "没有到期日志需要清理", null, runAt);
                updateLastRun(config, result);
                if (manual) {
                    recordCleanupAudit(result, config, "SUCCESS", null);
                }
                return result;
            }

            String archiveFile = Boolean.TRUE.equals(config.getArchiveBeforeCleanup())
                    ? writeArchive(expiredLogs, cutoff, runAt)
                    : null;
            List<Integer> ids = expiredLogs.stream().map(AuditLog::getId).toList();
            int deleted = auditLogMapper.deleteBatchIds(ids);
            AuditLogRetentionRunDTO result = runResult("SUCCESS", deleted, "已清理到期操作日志", archiveFile, runAt);
            updateLastRun(config, result);
            recordCleanupAudit(result, config, "SUCCESS", null);
            return result;
        } catch (Exception e) {
            AuditLogRetentionRunDTO result = runResult("FAILED", 0, "操作日志清理失败：" + e.getMessage(), null, runAt);
            updateLastRun(config, result);
            recordCleanupAudit(result, config, "FAILED", e.getMessage());
            return result;
        }
    }

    private List<AuditLog> findExpiredLogs(LocalDateTime cutoff) {
        QueryWrapper<AuditLog> query = new QueryWrapper<>();
        query.lt("created_at", cutoff)
                .orderByAsc("id")
                .last("LIMIT " + CLEANUP_BATCH_LIMIT);
        return auditLogMapper.selectList(query);
    }

    private String writeArchive(List<AuditLog> logs, LocalDateTime cutoff, LocalDateTime runAt) throws IOException {
        Path dir = archiveDirectory(runAt);
        Files.createDirectories(dir);
        String fileName = "audit_log_before_" + cutoff.format(ARCHIVE_STAMP) + ".csv";
        Path archiveFile = dir.resolve(fileName).normalize();
        Files.write(archiveFile, csvBytes(logs));
        return archiveFile.toAbsolutePath().normalize().toString();
    }

    private Path archiveDirectory(LocalDateTime runAt) {
        String root = archiveRoot == null || archiveRoot.trim().isEmpty()
                ? Path.of(backupDir).resolve("audit-logs").toString()
                : archiveRoot;
        return Path.of(root)
                .toAbsolutePath()
                .normalize()
                .resolve(runAt.format(DateTimeFormatter.ofPattern("yyyyMMdd")))
                .normalize();
    }

    private byte[] csvBytes(List<AuditLog> logs) {
        StringBuilder csv = new StringBuilder("\uFEFF");
        appendCsvRow(csv, List.of(
                "日志ID",
                "时间",
                "操作人",
                "角色",
                "模块",
                "动作",
                "结果",
                "对象类型",
                "对象ID",
                "摘要",
                "错误信息",
                "Request ID",
                "IP",
                "方法",
                "路径",
                "User-Agent",
                "操作前数据",
                "操作后数据"
        ));
        for (AuditLog log : logs) {
            appendCsvRow(csv, List.of(
                    text(log.getId()),
                    formatDateTime(log.getCreatedAt()),
                    text(log.getUsername()),
                    text(log.getRole()),
                    text(log.getModule()),
                    text(log.getAction()),
                    text(log.getResult()),
                    text(log.getTargetType()),
                    text(log.getTargetId()),
                    text(log.getMessage()),
                    text(log.getErrorMessage()),
                    text(log.getRequestId()),
                    text(log.getIpAddress()),
                    text(log.getRequestMethod()),
                    text(log.getRequestPath()),
                    text(log.getUserAgent()),
                    text(log.getBeforeData()),
                    text(log.getAfterData())
            ));
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void updateLastRun(AuditLogRetentionConfig config, AuditLogRetentionRunDTO result) {
        AuditLogRetentionConfig update = copyConfig(config);
        update.setLastRunAt(result.getRunAt());
        update.setLastRunDeletedCount(result.getDeletedCount());
        update.setLastRunStatus(result.getStatus());
        update.setLastRunMessage(result.getMessage());
        update.setLastArchiveFile(result.getArchiveFile());
        update.setUpdatedAt(now());
        configMapper.updateById(update);
    }

    private void recordCleanupAudit(AuditLogRetentionRunDTO result, AuditLogRetentionConfig config, String auditResult, String errorMessage) {
        String action = "FAILED".equals(auditResult) ? "AUDIT_LOG_CLEANUP_FAILED" : "AUDIT_LOG_CLEANUP_RUN";
        auditService.record(
                action,
                "audit_log_retention_config",
                CONFIG_ID,
                null,
                Map.of(
                        "enabled", Boolean.TRUE.equals(config.getEnabled()),
                        "retentionDays", config.getRetentionDays(),
                        "archiveBeforeCleanup", Boolean.TRUE.equals(config.getArchiveBeforeCleanup()),
                        "deletedCount", result.getDeletedCount(),
                        "archiveFile", result.getArchiveFile() == null ? "" : result.getArchiveFile(),
                        "status", result.getStatus()
                ),
                "SYSTEM",
                auditResult,
                "执行操作日志自动清理",
                errorMessage
        );
    }

    private boolean isCleanupTimeDue(AuditLogRetentionConfig config) {
        LocalTime configuredTime = parseCleanupTime(config.getCleanupTime());
        LocalDateTime current = now();
        if (current.getHour() != configuredTime.getHour() || current.getMinute() != configuredTime.getMinute()) {
            return false;
        }
        LocalDate today = current.toLocalDate();
        return config.getLastRunAt() == null || !today.equals(config.getLastRunAt().toLocalDate());
    }

    private AuditLogRetentionConfig loadConfigOrDefault() {
        AuditLogRetentionConfig existing = configMapper.selectById(CONFIG_ID);
        return existing == null ? defaultConfig() : normalize(existing);
    }

    private AuditLogRetentionConfig defaultConfig() {
        AuditLogRetentionConfig config = new AuditLogRetentionConfig();
        config.setId(CONFIG_ID);
        config.setEnabled(false);
        config.setRetentionDays(DEFAULT_RETENTION_DAYS);
        config.setArchiveBeforeCleanup(true);
        config.setCleanupTime("02:00");
        config.setLastRunDeletedCount(0);
        config.setLastRunStatus("NEVER");
        config.setUpdatedAt(now());
        return config;
    }

    private AuditLogRetentionConfig copyConfig(AuditLogRetentionConfig source) {
        AuditLogRetentionConfig copy = new AuditLogRetentionConfig();
        copy.setId(source.getId());
        copy.setEnabled(source.getEnabled());
        copy.setRetentionDays(source.getRetentionDays());
        copy.setArchiveBeforeCleanup(source.getArchiveBeforeCleanup());
        copy.setCleanupTime(source.getCleanupTime());
        copy.setLastRunAt(source.getLastRunAt());
        copy.setLastRunDeletedCount(source.getLastRunDeletedCount());
        copy.setLastRunStatus(source.getLastRunStatus());
        copy.setLastRunMessage(source.getLastRunMessage());
        copy.setLastArchiveFile(source.getLastArchiveFile());
        copy.setUpdatedAt(source.getUpdatedAt());
        return copy;
    }

    private AuditLogRetentionConfig normalize(AuditLogRetentionConfig config) {
        if (config.getId() == null) config.setId(CONFIG_ID);
        if (config.getEnabled() == null) config.setEnabled(false);
        if (config.getRetentionDays() == null) config.setRetentionDays(DEFAULT_RETENTION_DAYS);
        if (config.getArchiveBeforeCleanup() == null) config.setArchiveBeforeCleanup(true);
        if (isBlank(config.getCleanupTime())) config.setCleanupTime("02:00");
        if (config.getLastRunDeletedCount() == null) config.setLastRunDeletedCount(0);
        if (isBlank(config.getLastRunStatus())) config.setLastRunStatus("NEVER");
        return config;
    }

    private void applyRequest(AuditLogRetentionConfig config, AuditLogRetentionConfigReq req) {
        if (req == null) {
            return;
        }
        if (req.getEnabled() != null) {
            config.setEnabled(req.getEnabled());
        }
        if (req.getRetentionDays() != null) {
            if (req.getRetentionDays() < 1 || req.getRetentionDays() > 3650) {
                throw new IllegalArgumentException("日志保留天数必须在 1 到 3650 天之间");
            }
            config.setRetentionDays(req.getRetentionDays());
        }
        if (req.getArchiveBeforeCleanup() != null) {
            config.setArchiveBeforeCleanup(req.getArchiveBeforeCleanup());
        }
        if (!isBlank(req.getCleanupTime())) {
            config.setCleanupTime(formatCleanupTime(parseCleanupTime(req.getCleanupTime())));
        }
    }

    private AuditLogRetentionConfigDTO toDTO(AuditLogRetentionConfig config) {
        AuditLogRetentionConfig normalized = normalize(config);
        AuditLogRetentionConfigDTO dto = new AuditLogRetentionConfigDTO();
        dto.setEnabled(Boolean.TRUE.equals(normalized.getEnabled()));
        dto.setRetentionDays(normalized.getRetentionDays());
        dto.setArchiveBeforeCleanup(Boolean.TRUE.equals(normalized.getArchiveBeforeCleanup()));
        dto.setCleanupTime(normalized.getCleanupTime());
        dto.setLastRunAt(normalized.getLastRunAt());
        dto.setLastRunDeletedCount(normalized.getLastRunDeletedCount());
        dto.setLastRunStatus(normalized.getLastRunStatus());
        dto.setLastRunMessage(normalized.getLastRunMessage());
        dto.setLastArchiveFile(normalized.getLastArchiveFile());
        dto.setUpdatedAt(normalized.getUpdatedAt());
        return dto;
    }

    private Map<String, Object> summary(AuditLogRetentionConfig config) {
        AuditLogRetentionConfig normalized = normalize(copyConfig(config));
        return Map.of(
                "enabled", Boolean.TRUE.equals(normalized.getEnabled()),
                "retentionDays", normalized.getRetentionDays(),
                "archiveBeforeCleanup", Boolean.TRUE.equals(normalized.getArchiveBeforeCleanup()),
                "cleanupTime", normalized.getCleanupTime(),
                "lastRunStatus", normalized.getLastRunStatus()
        );
    }

    private AuditLogRetentionRunDTO runResult(String status, int deletedCount, String message, String archiveFile, LocalDateTime runAt) {
        AuditLogRetentionRunDTO dto = new AuditLogRetentionRunDTO();
        dto.setStatus(status);
        dto.setDeletedCount(deletedCount);
        dto.setMessage(message);
        dto.setArchiveFile(archiveFile);
        dto.setRunAt(runAt);
        return dto;
    }

    private LocalTime parseCleanupTime(String value) {
        try {
            return LocalTime.parse(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("清理执行时间必须使用 HH:mm 格式，例如 02:00");
        }
    }

    private String formatCleanupTime(LocalTime time) {
        return time.format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    private void appendCsvRow(StringBuilder csv, List<String> values) {
        List<String> safeValues = new ArrayList<>(values);
        for (int i = 0; i < safeValues.size(); i++) {
            if (i > 0) {
                csv.append(',');
            }
            csv.append(csvCell(safeValues.get(i)));
        }
        csv.append('\n');
    }

    private String csvCell(String value) {
        String safe = value == null ? "" : value;
        String escaped = safe.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n") || escaped.contains("\r")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? "" : DATE_TIME.format(value);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
