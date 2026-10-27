package com.machining.quote.service;

import com.machining.quote.entity.ProductionItemAutoDispatchConfig;
import com.machining.quote.entity.SystemTask;
import com.machining.quote.mapper.OrderDeliveryTicketAllocationMapper;
import com.machining.quote.mapper.SystemTaskMapper;
import com.machining.quote.service.mes.MesProductionDispatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 待产零件每日定时自动下发。
 *
 * <p>设计口径：限制单批瞬时负载，不限制当天总量。
 * 每批取 {@code batchSize} 行调用 {@link MesProductionDispatchService#dispatchBatch}，
 * 批间 sleep {@code batchIntervalMs}，循环直到候选为空。
 *
 * <p>提前结束只有两种情况：连续失败批数达到 {@code failureBreaker}（MES/数据中心异常），
 * 或轮次达到 {@code maxRounds}（防死循环兜底）。
 */
@Service
public class ProductionItemAutoDispatchService {
    private static final Logger log = LoggerFactory.getLogger(ProductionItemAutoDispatchService.class);

    public static final String TASK_TYPE = "MES_PRODUCTION_AUTO_DISPATCH";
    public static final String TRIGGER_SCHEDULED = "SCHEDULED";
    public static final String TRIGGER_MANUAL = "MANUAL";

    private static final String TASK_STATUS_PENDING = "PENDING";
    private static final String TASK_STATUS_RUNNING = "RUNNING";
    private static final String TASK_STATUS_SUCCESS = "SUCCESS";
    private static final String TASK_STATUS_FAILED = "FAILED";

    private static final String RESULT_STATUS_SUCCESS = "SUCCESS";
    private static final String RESULT_STATUS_FAILED = "FAILED";
    private static final String RESULT_STATUS_PARTIAL = "PARTIAL";

    @Autowired
    private OrderDeliveryTicketAllocationMapper allocationMapper;
    @Autowired
    private MesProductionDispatchService mesProductionDispatchService;
    @Autowired
    private SystemTaskMapper taskMapper;
    @Autowired
    private ProductionItemAutoDispatchConfigService configService;
    @Autowired(required = false)
    private AuditService auditService;
    @Autowired
    @Qualifier("productionAutoDispatchExecutor")
    private TaskExecutor executor;

    private AtomicBoolean running = new AtomicBoolean(false);

    public boolean isRunning() {
        return running.get();
    }

    /**
     * 提交一次自动下发。立即返回 system_task，实际执行在独立单线程池里。
     */
    public SystemTask submitRun(String trigger) {
        String safeTrigger = TRIGGER_MANUAL.equalsIgnoreCase(trigger) ? TRIGGER_MANUAL : TRIGGER_SCHEDULED;
        if (!running.compareAndSet(false, true)) {
            throw new RuntimeException("待产零件定时下发正在执行中，请稍后再试");
        }
        Long taskId;
        try {
            SystemTask task = createTask(safeTrigger);
            taskId = task.getId();
            recordSubmitAudit(task, safeTrigger);
        } catch (RuntimeException error) {
            running.set(false);
            throw error;
        }
        try {
            executor.execute(() -> {
                try {
                    execute(taskId, safeTrigger);
                } finally {
                    running.set(false);
                }
            });
        } catch (RuntimeException error) {
            running.set(false);
            markTaskFailed(taskId, error);
            configService.updateResult(taskId, RESULT_STATUS_FAILED, safeMessage(error), null);
            throw error;
        }
        return taskMapper.selectById(taskId);
    }

    private void execute(Long taskId, String trigger) {
        long startedAt = System.currentTimeMillis();
        markTaskRunning(taskId);
        log.info("Production auto dispatch started taskId={} trigger={}", taskId, trigger);
        try {
            ProductionItemAutoDispatchConfig config = configService.loadConfig();
            RunOutcome outcome = runBatches(taskId, config, startedAt);
            long durationMs = System.currentTimeMillis() - startedAt;
            Map<String, Object> result = outcome.toResultJson(taskId, trigger, durationMs);
            markTaskSuccess(taskId, result);
            configService.updateResult(taskId, outcome.status(), outcome.message(), outcome.toAutoDispatchResult(durationMs));
            if (durationMs > safeWarnDuration(config)) {
                log.warn("Production auto dispatch slow run taskId={} durationMs={} thresholdMs={}",
                        taskId, durationMs, config.getWarnDurationMs());
            }
            log.info("Production auto dispatch finished taskId={} rounds={} total={} success={} failed={} skipped={} remaining={} durationMs={}",
                    taskId, outcome.rounds(), outcome.total(), outcome.success(), outcome.failed(),
                    outcome.skipped(), outcome.remaining(), durationMs);
        } catch (Throwable error) {
            log.error("Production auto dispatch failed taskId={} durationMs={}",
                    taskId, System.currentTimeMillis() - startedAt, error);
            markTaskFailed(taskId, error);
            configService.updateResult(taskId, RESULT_STATUS_FAILED, safeMessage(error), null);
        }
    }

    private RunOutcome runBatches(Long taskId, ProductionItemAutoDispatchConfig config, long startedAt) {
        int batchSize = positive(config.getBatchSize(), ProductionItemAutoDispatchConfigService.DEFAULT_BATCH_SIZE);
        int intervalMs = positive(config.getBatchIntervalMs(), ProductionItemAutoDispatchConfigService.DEFAULT_BATCH_INTERVAL_MS);
        int maxRounds = positive(config.getMaxRounds(), ProductionItemAutoDispatchConfigService.DEFAULT_MAX_ROUNDS);
        int failureBreaker = positive(config.getFailureBreaker(), ProductionItemAutoDispatchConfigService.DEFAULT_FAILURE_BREAKER);

        int rounds = 0;
        int total = 0;
        int success = 0;
        int failed = 0;
        int skipped = 0;
        int consecutiveFailedRounds = 0;
        boolean breakerTripped = false;
        boolean roundLimitReached = false;

        while (true) {
            if (rounds >= maxRounds) {
                roundLimitReached = true;
                log.warn("Production auto dispatch reached max rounds taskId={} maxRounds={}", taskId, maxRounds);
                break;
            }
            List<Integer> candidateIds = allocationMapper.selectAutoDispatchCandidateIds(batchSize);
            if (candidateIds == null || candidateIds.isEmpty()) {
                break;
            }
            rounds++;
            int roundSuccess = 0;
            int roundFailed = 0;
            int roundSkipped = 0;
            long roundStartedAt = System.currentTimeMillis();
            try {
                Map<String, Object> batchResult = mesProductionDispatchService.dispatchBatch(candidateIds, taskId);
                roundSuccess = intOf(batchResult, "successCount");
                roundFailed = intOf(batchResult, "failedCount");
                roundSkipped = intOf(batchResult, "skippedCount");
                int unknown = intOf(batchResult, "unknownCount");
                if (unknown > 0) {
                    roundFailed += unknown;
                }
            } catch (RuntimeException error) {
                roundFailed = candidateIds.size();
                log.error("Production auto dispatch round failed taskId={} round={} size={}",
                        taskId, rounds, candidateIds.size(), error);
            }
            total += candidateIds.size();
            success += roundSuccess;
            failed += roundFailed;
            skipped += roundSkipped;
            log.info("Production auto dispatch round finished taskId={} round={} size={} success={} failed={} skipped={} durationMs={}",
                    taskId, rounds, candidateIds.size(), roundSuccess, roundFailed, roundSkipped,
                    System.currentTimeMillis() - roundStartedAt);

            // 判定"本批没有任何进展"只看成功数：被跳过的行不会回写 mes_production_status，
            // 候选查询下一轮仍会返回它们，若把跳过也算进展，纯跳过批次会一直空转到 maxRounds，
            // 并且因为排序固定会永远挡住排在后面的可下发行业。
            if (roundSuccess == 0) {
                consecutiveFailedRounds++;
                if (consecutiveFailedRounds >= failureBreaker) {
                    breakerTripped = true;
                    log.error("Production auto dispatch aborted taskId={} reason=failure breaker roundsWithoutSuccess={} lastRoundSize={} lastRoundSkipped={}",
                            taskId, consecutiveFailedRounds, candidateIds.size(), roundSkipped);
                    break;
                }
            } else {
                consecutiveFailedRounds = 0;
            }

            if (!sleepQuietly(intervalMs)) {
                log.warn("Production auto dispatch interrupted during batch interval taskId={}", taskId);
                break;
            }
        }

        long remaining = safeRemainingCount();
        String status;
        if (breakerTripped) {
            status = RESULT_STATUS_FAILED;
        } else if (failed > 0 || roundLimitReached || remaining > 0) {
            status = RESULT_STATUS_PARTIAL;
        } else {
            status = RESULT_STATUS_SUCCESS;
        }
        String message = buildMessage(status, rounds, total, success, failed, skipped, remaining, breakerTripped);
        return new RunOutcome(status, message, total, success, failed, skipped, rounds, remaining, breakerTripped);
    }

    private long safeRemainingCount() {
        try {
            return allocationMapper.countAutoDispatchCandidates();
        } catch (RuntimeException error) {
            log.warn("Production auto dispatch count remaining candidates failed", error);
            return 0L;
        }
    }

    private String buildMessage(String status, int rounds, int total, int success, int failed,
                                int skipped, long remaining, boolean breakerTripped) {
        StringBuilder builder = new StringBuilder();
        builder.append("status=").append(status)
                .append(" rounds=").append(rounds)
                .append(" total=").append(total)
                .append(" success=").append(success)
                .append(" failed=").append(failed)
                .append(" skipped=").append(skipped)
                .append(" remaining=").append(remaining);
        if (breakerTripped) {
            builder.append(" abortedByFailureBreaker=true");
        }
        return builder.toString();
    }

    private boolean sleepQuietly(int intervalMs) {
        if (intervalMs <= 0) {
            return true;
        }
        try {
            Thread.sleep(intervalMs);
            return true;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private SystemTask createTask(String trigger) {
        ProductionItemAutoDispatchConfig config = configService.loadConfig();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("trigger", trigger);
        request.put("batchSize", config.getBatchSize());
        request.put("batchIntervalMs", config.getBatchIntervalMs());
        request.put("maxRounds", config.getMaxRounds());
        SystemTask task = new SystemTask();
        task.setTaskType(TASK_TYPE);
        task.setStatus(TASK_STATUS_PENDING);
        task.setProgress(0);
        task.setTotal(1);
        task.setRequestJson(request);
        task.setCreatedBy(TRIGGER_SCHEDULED.equals(trigger) ? "SYSTEM" : currentUsername());
        task.setCreatedAt(LocalDateTime.now());
        taskMapper.insert(task);
        log.info("Production auto dispatch submitted taskId={} trigger={} batchSize={}",
                task.getId(), trigger, config.getBatchSize());
        return task;
    }

    private void recordSubmitAudit(SystemTask task, String trigger) {
        if (auditService == null) {
            return;
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("taskId", task.getId());
        summary.put("taskType", task.getTaskType());
        summary.put("trigger", trigger);
        auditService.record(
                "PRODUCTION_ITEM_MES_PRODUCTION_AUTO_SUBMIT",
                "system_task",
                task.getId(),
                null,
                summary,
                "DELIVERY",
                "SUCCESS",
                "提交待产零件定时下发任务 taskId=" + task.getId() + " trigger=" + trigger,
                null
        );
    }

    private void markTaskRunning(Long taskId) {
        SystemTask update = new SystemTask();
        update.setId(taskId);
        update.setStatus(TASK_STATUS_RUNNING);
        update.setStartedAt(LocalDateTime.now());
        taskMapper.updateById(update);
    }

    private void markTaskSuccess(Long taskId, Object resultJson) {
        SystemTask update = new SystemTask();
        update.setId(taskId);
        update.setStatus(TASK_STATUS_SUCCESS);
        update.setProgress(1);
        update.setTotal(1);
        update.setResultJson(resultJson);
        update.setFinishedAt(LocalDateTime.now());
        taskMapper.updateById(update);
    }

    private void markTaskFailed(Long taskId, Throwable error) {
        SystemTask update = new SystemTask();
        update.setId(taskId);
        update.setStatus(TASK_STATUS_FAILED);
        update.setErrorMessage(error == null ? null : safeMessage(error));
        update.setFinishedAt(LocalDateTime.now());
        taskMapper.updateById(update);
    }

    private int intOf(Map<String, Object> result, String key) {
        if (result == null) {
            return 0;
        }
        Object value = result.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private int positive(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private long safeWarnDuration(ProductionItemAutoDispatchConfig config) {
        Long value = config == null ? null : config.getWarnDurationMs();
        return value == null || value <= 0
                ? ProductionItemAutoDispatchConfigService.DEFAULT_WARN_DURATION_MS
                : value;
    }

    private String currentUsername() {
        com.machining.quote.config.AuthContext.SessionUser current = com.machining.quote.config.AuthContext.get();
        return current == null ? null : current.username();
    }

    private String safeMessage(Throwable error) {
        if (error == null) {
            return null;
        }
        String message = error.getMessage();
        return message == null ? error.getClass().getSimpleName() : message;
    }

    private record RunOutcome(
            String status,
            String message,
            int total,
            int success,
            int failed,
            int skipped,
            int rounds,
            long remaining,
            boolean breakerTripped) {

        ProductionItemAutoDispatchResult toAutoDispatchResult(long durationMs) {
            return new ProductionItemAutoDispatchResult(total, success, failed, skipped, rounds, durationMs);
        }

        Map<String, Object> toResultJson(Long taskId, String trigger, long durationMs) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("taskType", TASK_TYPE);
            result.put("taskId", taskId);
            result.put("trigger", trigger);
            result.put("status", status);
            result.put("rounds", rounds);
            result.put("totalCount", total);
            result.put("successCount", success);
            result.put("failedCount", failed);
            result.put("skippedCount", skipped);
            result.put("remainingCount", remaining);
            result.put("abortedByFailureBreaker", breakerTripped);
            result.put("durationMs", durationMs);
            result.put("message", message);
            return result;
        }
    }
}
