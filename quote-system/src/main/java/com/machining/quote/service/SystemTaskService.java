package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.config.AuthContext;
import com.machining.quote.entity.SystemTask;
import com.machining.quote.mapper.SystemTaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Service
public class SystemTaskService {
    private static final Logger log = LoggerFactory.getLogger(SystemTaskService.class);
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";

    @Autowired
    private SystemTaskMapper taskMapper;
    @Autowired
    private EngineerService engineerService;
    @Autowired
    private ExcelExportService excelExportService;
    @Autowired
    @Qualifier("systemTaskExecutor")
    private ThreadPoolTaskExecutor taskExecutor;
    @Autowired
    private AuditService auditService;
    @Value("${erp.task.output-dir}")
    private String taskOutputDir;

    public SystemTask submitRefreshQuotesTask(Integer rfqId) {
        if (rfqId == null) {
            throw new RuntimeException("订单 ID 不能为空");
        }
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("rfqId", rfqId);
        SystemTask task = createTask("REFRESH_ORDER_QUOTES", request);
        recordTaskSubmitAudit("QUOTE_REFRESH_TASK_SUBMIT", "ENGINEER", task, request);
        taskExecutor.execute(() -> runTask(task.getId(), () -> engineerService.refreshOrderQuotes(rfqId)));
        return taskMapper.selectById(task.getId());
    }

    public SystemTask submitQuoteExportTask(Integer rfqId, String exportType) {
        if (rfqId == null) {
            throw new RuntimeException("订单 ID 不能为空");
        }
        String safeExportType = exportType == null || exportType.trim().isEmpty() ? "DETAIL" : exportType.trim().toUpperCase();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("rfqId", rfqId);
        request.put("exportType", safeExportType);
        SystemTask task = createTask("EXPORT_QUOTE", request);
        recordTaskSubmitAudit("QUOTE_EXPORT_TASK_SUBMIT", "MARKET", task, request);
        taskExecutor.execute(() -> runTask(task.getId(), () -> {
            Path file;
            try {
                file = excelExportService.exportQuoteSheetFile(rfqId, taskOutputDirectory(), safeExportType);
            } catch (Exception e) {
                throw new RuntimeException("报价单导出失败：" + e.getMessage(), e);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("fileName", file.getFileName().toString());
            result.put("exportType", safeExportType);
            return new TaskRunResult(result, file.toAbsolutePath().normalize().toString());
        }));
        return taskMapper.selectById(task.getId());
    }

    public SystemTask getTask(Long taskId) {
        if (taskId == null) {
            throw new RuntimeException("任务 ID 不能为空");
        }
        SystemTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new RuntimeException("任务不存在");
        }
        return task;
    }

    public Path getTaskFile(Long taskId) {
        SystemTask task = getTask(taskId);
        if (task.getFilePath() == null || task.getFilePath().trim().isEmpty()) {
            throw new RuntimeException("该任务没有可下载文件");
        }
        Path baseDir = taskOutputDirectory();
        Path file = Path.of(task.getFilePath()).toAbsolutePath().normalize();
        if (!file.startsWith(baseDir) || !Files.exists(file) || !Files.isRegularFile(file)) {
            throw new RuntimeException("任务文件不存在或路径不合法");
        }
        return file;
    }

    public List<SystemTask> recentTasks(String taskType, Integer limit) {
        QueryWrapper<SystemTask> qw = new QueryWrapper<>();
        if (taskType != null && !taskType.trim().isEmpty()) {
            qw.eq("task_type", taskType.trim());
        }
        int safeLimit = limit == null || limit < 1 ? 20 : Math.min(limit, 100);
        qw.orderByDesc("id").last("LIMIT " + safeLimit);
        return taskMapper.selectList(qw);
    }

    private SystemTask createTask(String taskType, Object requestJson) {
        AuthContext.SessionUser current = AuthContext.get();
        SystemTask task = new SystemTask();
        task.setTaskType(taskType);
        task.setStatus(STATUS_PENDING);
        task.setProgress(0);
        task.setTotal(1);
        task.setRequestJson(requestJson);
        task.setCreatedBy(current == null ? null : current.username());
        task.setCreatedAt(LocalDateTime.now());
        taskMapper.insert(task);
        log.info("System task submitted taskId={} taskType={} request={}",
                task.getId(), task.getTaskType(), safeRequestSummary(requestJson));
        return task;
    }

    private void recordTaskSubmitAudit(String action, String module, SystemTask task, Map<String, Object> request) {
        if (auditService == null) {
            return;
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("taskId", task.getId());
        summary.put("taskType", task.getTaskType());
        summary.put("status", task.getStatus());
        summary.put("request", request);
        auditService.record(
                action,
                "system_task",
                task.getId(),
                null,
                summary,
                module,
                "SUCCESS",
                action + " taskId=" + task.getId(),
                null
        );
    }

    private void runTask(Long taskId, Supplier<Object> work) {
        SystemTask task = taskMapper.selectById(taskId);
        String taskType = task == null ? "" : task.getTaskType();
        long startedAt = System.currentTimeMillis();
        log.info("System task started taskId={} taskType={}", taskId, taskType);
        try {
            markRunning(taskId);
            Object result = work.get();
            String filePath = null;
            if (result instanceof TaskRunResult taskResult) {
                filePath = taskResult.filePath();
                markSuccess(taskId, taskResult.resultJson(), filePath);
            } else {
                markSuccess(taskId, result, null);
            }
            log.info("System task completed taskId={} taskType={} durationMs={} filePath={}",
                    taskId, taskType, System.currentTimeMillis() - startedAt, filePath);
        } catch (Throwable error) {
            log.error("System task failed taskId={} taskType={} durationMs={}",
                    taskId, taskType, System.currentTimeMillis() - startedAt, error);
            markFailed(taskId, error);
        }
    }

    private void markRunning(Long taskId) {
        SystemTask update = new SystemTask();
        update.setId(taskId);
        update.setStatus(STATUS_RUNNING);
        update.setStartedAt(LocalDateTime.now());
        taskMapper.updateById(update);
    }

    private void markSuccess(Long taskId, Object resultJson, String filePath) {
        SystemTask update = new SystemTask();
        update.setId(taskId);
        update.setStatus(STATUS_SUCCESS);
        update.setProgress(1);
        update.setTotal(1);
        update.setResultJson(resultJson);
        update.setFilePath(filePath);
        update.setFinishedAt(LocalDateTime.now());
        taskMapper.updateById(update);
    }

    private void markFailed(Long taskId, Throwable error) {
        SystemTask update = new SystemTask();
        update.setId(taskId);
        update.setStatus(STATUS_FAILED);
        update.setErrorMessage(error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
        update.setFinishedAt(LocalDateTime.now());
        taskMapper.updateById(update);
    }

    private Path taskOutputDirectory() {
        return Path.of(taskOutputDir).toAbsolutePath().normalize();
    }

    private Object safeRequestSummary(Object requestJson) {
        if (requestJson instanceof Map<?, ?> rawMap) {
            Map<String, Object> summary = new LinkedHashMap<>();
            rawMap.forEach((key, value) -> summary.put(String.valueOf(key), safeValue(value)));
            return summary;
        }
        return safeValue(requestJson);
    }

    private Object safeValue(Object value) {
        if (value == null || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        String text = String.valueOf(value);
        return text.length() <= 120 ? text : text.substring(0, 120) + "...";
    }

    private record TaskRunResult(Object resultJson, String filePath) {
    }
}
