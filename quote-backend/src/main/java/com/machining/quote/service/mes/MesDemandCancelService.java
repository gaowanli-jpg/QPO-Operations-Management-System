package com.machining.quote.service.mes;

import com.machining.quote.dto.mes.MesDemandCancelTarget;
import com.machining.quote.dto.mes.MesDemandDeleteResp;
import com.machining.quote.mapper.MesProductionBatchItemMapper;
import com.machining.quote.mapper.OrderDeliveryTicketAllocationMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.DataCenterCallException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 销售订单废弃时同步撤销该订单已下发到 MES 的需求任务。
 *
 * <p>只做两件事：把删除请求发给 MES、把结果记下来。MES 需求任务后续怎么处理
 * （是否已派工、是否要人工介入）不属于 QPO 职责，本服务不判断、不兜底、不重试补偿。
 *
 * <p>调用挂在事务提交之后（{@link #scheduleAfterCommit}），因此 MES 不可用或删除失败
 * 都不会回滚本地废弃。取消方法本身不加事务，逐行自动提交，进程中断不会把已删除的
 * MES 任务回滚成"未删除"。
 */
@Service
public class MesDemandCancelService {
    private static final Logger log = LoggerFactory.getLogger(MesDemandCancelService.class);

    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String STATUS_CANCEL_FAILED = "CANCEL_FAILED";

    private static final String AUDIT_ACTION = "SALES_ORDER_MES_DEMAND_CANCEL";
    private static final DateTimeFormatter BIZ_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int MAX_ATTEMPTS = 2;
    private static final int CONSECUTIVE_TRANSPORT_FAILURE_LIMIT = 5;
    /** mes_production_batch_item.message 是 VARCHAR(500)，超长会写库失败，留痕就丢了。 */
    private static final int MAX_MESSAGE_LENGTH = 500;

    private final OrderDeliveryTicketAllocationMapper allocationMapper;
    private final MesProductionBatchItemMapper batchItemMapper;
    private final MesDemandDeleteClient client;
    private final AuditService auditService;

    @Autowired
    public MesDemandCancelService(OrderDeliveryTicketAllocationMapper allocationMapper,
                                  MesProductionBatchItemMapper batchItemMapper,
                                  MesDemandDeleteClient client,
                                  @Autowired(required = false) AuditService auditService) {
        this.allocationMapper = allocationMapper;
        this.batchItemMapper = batchItemMapper;
        this.client = client;
        this.auditService = auditService;
    }

    /**
     * 在当前事务提交后执行撤销。没有活动事务时直接执行。
     *
     * <p>afterCommit 里抛出的异常会传播给发起事务的调用方，导致"废弃已成功但接口报错"，
     * 所以这里必须吞掉。
     */
    public void scheduleAfterCommit(Long salesOrderId, String reason) {
        if (salesOrderId == null) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            cancelQuietly(salesOrderId, reason);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                cancelQuietly(salesOrderId, reason);
            }
        });
    }

    private void cancelQuietly(Long salesOrderId, String reason) {
        try {
            cancel(salesOrderId, reason);
        } catch (RuntimeException e) {
            log.error("MES demand cancel failed salesOrderId={} reason={}", salesOrderId, reason, e);
        }
    }

    /**
     * 订单恢复时把撤销结果改回待下发，由业务决定是否重新下发。
     *
     * @return 更新的待产行数
     */
    public int reopenCancelled(Long salesOrderId) {
        if (salesOrderId == null) {
            return 0;
        }
        return allocationMapper.reopenMesDemandCancelled(salesOrderId);
    }

    /**
     * 撤销该销售订单全部已成功下发的需求任务。
     *
     * @return 汇总信息，供调用方与测试断言
     */
    public Map<String, Object> cancel(Long salesOrderId, String reason) {
        List<MesDemandCancelTarget> targets = allocationMapper.selectMesDemandCancelTargets(salesOrderId);
        if (targets == null || targets.isEmpty()) {
            log.debug("MES demand cancel skipped salesOrderId={} reason=no successful demand task", salesOrderId);
            return summary(salesOrderId, 0, 0, 0, 0, reason);
        }

        int cancelled = 0;
        int failed = 0;
        int consecutiveTransportFailures = 0;
        boolean mesUnavailable = false;
        Map<Integer, List<String>> statusesByAllocation = new LinkedHashMap<>();
        Map<Integer, String> messageByAllocation = new LinkedHashMap<>();

        for (MesDemandCancelTarget target : targets) {
            DeleteOutcome outcome;
            if (mesUnavailable) {
                outcome = DeleteOutcome.failed("MES 不可用，未执行");
            } else {
                outcome = deleteOne(target);
                consecutiveTransportFailures = outcome.transportFailure()
                        ? consecutiveTransportFailures + 1
                        : 0;
                if (consecutiveTransportFailures >= CONSECUTIVE_TRANSPORT_FAILURE_LIMIT) {
                    mesUnavailable = true;
                    log.warn("MES demand cancel stops remaining rows salesOrderId={} consecutiveTransportFailures={}",
                            salesOrderId, consecutiveTransportFailures);
                }
            }
            writeBatchItemStatus(target, outcome);
            if (STATUS_CANCELLED.equals(outcome.status())) {
                cancelled++;
            } else {
                failed++;
            }
            statusesByAllocation
                    .computeIfAbsent(target.getAllocationId(), key -> new ArrayList<>())
                    .add(outcome.status());
            messageByAllocation.merge(target.getAllocationId(), outcome.message(), this::mergeMessage);
        }

        int updatedAllocations = 0;
        for (Map.Entry<Integer, List<String>> entry : statusesByAllocation.entrySet()) {
            String status = entry.getValue().stream().anyMatch(STATUS_CANCEL_FAILED::equals)
                    ? STATUS_CANCEL_FAILED
                    : STATUS_CANCELLED;
            updatedAllocations += markAllocationStatus(
                    entry.getKey(), status, messageByAllocation.get(entry.getKey()));
        }

        recordAudit(salesOrderId, targets.size(), cancelled, failed, updatedAllocations, reason);
        log.info("MES demand cancel completed salesOrderId={} demandTasks={} cancelled={} failed={} allocations={}",
                salesOrderId, targets.size(), cancelled, failed, updatedAllocations);
        return summary(salesOrderId, targets.size(), cancelled, failed, updatedAllocations, reason);
    }

    /**
     * 删一条需求任务。重试只用同一个 bizId：MES 靠它做幂等，
     * 换 bizId 会返回 1030700000（任务不存在），把"上次其实成功了"误判成"本来就没有"。
     */
    private DeleteOutcome deleteOne(MesDemandCancelTarget target) {
        Long demandId = parseDemandId(target.getMesRecordId());
        if (demandId == null) {
            return DeleteOutcome.failed("MES 需求任务主键不可用，无法删除：" + target.getMesRecordId());
        }
        String bizId = newDeleteBizId();
        String lastMessage = null;
        boolean transportFailure = false;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                MesDemandDeleteResp response = client.delete(bizId, demandId);
                if (response == null) {
                    lastMessage = "MES 返回空响应";
                    transportFailure = true;
                    continue;
                }
                if (response.isDeleted()) {
                    return DeleteOutcome.cancelled("MES 需求任务已删除");
                }
                if (response.isStateRejected()) {
                    return DeleteOutcome.failed("MES 需求任务状态不允许删除(code="
                            + response.codeText() + ")，需去 MES 处理");
                }
                if (response.isRetryable()) {
                    lastMessage = "MES 返回可重试状态 code=" + response.codeText();
                    transportFailure = true;
                    continue;
                }
                return DeleteOutcome.failed("MES 拒绝删除 code=" + response.codeText()
                        + blankToEmptySuffix(response.messageText()));
            } catch (DataCenterCallException e) {
                if (e.getCertainty() == DataCenterCallException.FailureCertainty.DEFINITE_FAILURE
                        && e.getHttpStatus() != null) {
                    return DeleteOutcome.failed("MES 拒绝删除 httpStatus=" + e.getHttpStatus());
                }
                lastMessage = e.getMessage();
                transportFailure = true;
            } catch (RuntimeException e) {
                lastMessage = e.getMessage();
                transportFailure = true;
            }
        }
        return new DeleteOutcome(STATUS_CANCEL_FAILED, "结果未知：" + blankToDefault(lastMessage, "MES 未返回明确结果"), transportFailure);
    }

    private void writeBatchItemStatus(MesDemandCancelTarget target, DeleteOutcome outcome) {
        try {
            batchItemMapper.markCancelledById(
                    target.getBatchItemId(), outcome.status(), truncateMessage(outcome.message()));
        } catch (RuntimeException e) {
            log.warn("MES demand cancel batch item status write failed batchItemId={} allocationId={}",
                    target.getBatchItemId(), target.getAllocationId(), e);
        }
    }

    private int markAllocationStatus(Integer allocationId, String status, String message) {
        try {
            return allocationMapper.markMesDemandCancelStatus(allocationId, status, truncateMessage(message));
        } catch (RuntimeException e) {
            log.warn("MES demand cancel allocation status write failed allocationId={} status={}",
                    allocationId, status, e);
            return 0;
        }
    }

    private String truncateMessage(String value) {
        if (value == null) {
            return null;
        }
        String text = value.replaceAll("\\s+", " ").trim();
        if (text.length() <= MAX_MESSAGE_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_MESSAGE_LENGTH - 3) + "...";
    }

    private void recordAudit(Long salesOrderId,
                             int demandTaskCount,
                             int cancelledCount,
                             int failedCount,
                             int updatedAllocationCount,
                             String reason) {
        if (auditService == null) {
            return;
        }
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("salesOrderId", salesOrderId);
        after.put("demandTaskCount", demandTaskCount);
        after.put("cancelledCount", cancelledCount);
        after.put("failedCount", failedCount);
        after.put("updatedAllocationCount", updatedAllocationCount);
        after.put("reason", reason);
        boolean allCancelled = failedCount == 0;
        try {
            auditService.record(
                    AUDIT_ACTION,
                    "sales_order_header",
                    salesOrderId,
                    null,
                    after,
                    "MARKET",
                    allCancelled ? "SUCCESS" : "FAILED",
                    allCancelled
                            ? "已同步撤销 " + cancelledCount + " 条 MES 需求任务"
                            : "撤销 MES 需求任务：" + cancelledCount + " 条成功，" + failedCount + " 条失败",
                    allCancelled ? null : "部分 MES 需求任务未删除，请在待产零件页筛选「撤销失败」处理"
            );
        } catch (RuntimeException e) {
            log.warn("MES demand cancel audit failed salesOrderId={}", salesOrderId, e);
        }
    }

    private Map<String, Object> summary(Long salesOrderId,
                                        int demandTaskCount,
                                        int cancelledCount,
                                        int failedCount,
                                        int updatedAllocationCount,
                                        String reason) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("salesOrderId", salesOrderId);
        summary.put("demandTaskCount", demandTaskCount);
        summary.put("cancelledCount", cancelledCount);
        summary.put("failedCount", failedCount);
        summary.put("updatedAllocationCount", updatedAllocationCount);
        summary.put("reason", reason);
        return summary;
    }

    private String newDeleteBizId() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        return "QPO-DEMDEL-" + LocalDate.now().format(BIZ_DATE_FORMAT) + "-" + suffix;
    }

    private Long parseDemandId(String value) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String mergeMessage(String existing, String next) {
        if (existing == null || existing.isEmpty()) {
            return next;
        }
        if (next == null || next.isEmpty() || existing.contains(next)) {
            return existing;
        }
        String merged = existing + "；" + next;
        return merged.length() <= 500 ? merged : merged.substring(0, 500);
    }

    private String blankToEmptySuffix(String value) {
        return value == null || value.trim().isEmpty() ? "" : " msg=" + value.trim();
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private record DeleteOutcome(String status, String message, boolean transportFailure) {
        static DeleteOutcome cancelled(String message) {
            return new DeleteOutcome(STATUS_CANCELLED, message, false);
        }

        static DeleteOutcome failed(String message) {
            return new DeleteOutcome(STATUS_CANCEL_FAILED, message, false);
        }
    }
}
