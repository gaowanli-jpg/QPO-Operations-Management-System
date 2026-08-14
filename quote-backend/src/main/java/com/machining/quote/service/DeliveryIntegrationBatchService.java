package com.machining.quote.service;

import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.service.mes.MesMaterialSyncService;
import com.machining.quote.service.wms.WmsAllocationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class DeliveryIntegrationBatchService {
    private static final String TASK_MES_MATERIAL_BATCH_SYNC = "MES_MATERIAL_BATCH_SYNC";
    private static final String STATUS_CALLBACK_PENDING = "CALLBACK_PENDING";
    private static final String STATUS_DEDUP_FOLLOWING = "DEDUP_FOLLOWING";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_RESULT_UNKNOWN = "RESULT_UNKNOWN";
    private static final String STATUS_SKIPPED = "SKIPPED";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String DEDUP_FOLLOW_EXISTING_ACTIVE = "FOLLOW_EXISTING_ACTIVE";

    @Autowired
    private OrderDeliveryTicketMapper ticketMapper;
    @Autowired(required = false)
    private MesMaterialSyncService mesMaterialSyncService;
    @Autowired(required = false)
    private WmsAllocationService wmsAllocationService;
    @Autowired(required = false)
    private DataScopeService dataScopeService;

    public List<Integer> normalizeTicketIds(List<Integer> ticketIds) {
        if (ticketIds == null) {
            throw new RuntimeException("请选择至少一个已提零件");
        }
        LinkedHashSet<Integer> normalized = new LinkedHashSet<>();
        for (Integer ticketId : ticketIds) {
            if (ticketId != null && ticketId > 0) {
                normalized.add(ticketId);
            }
        }
        if (normalized.isEmpty()) {
            throw new RuntimeException("请选择至少一个已提零件");
        }
        return new ArrayList<>(normalized);
    }

    public void assertCanAccessTickets(List<Integer> ticketIds) {
        for (Integer ticketId : normalizeTicketIds(ticketIds)) {
            OrderDeliveryTicket ticket = ticketMapper.selectById(ticketId);
            if (ticket == null) {
                continue;
            }
            if (dataScopeService != null) {
                dataScopeService.assertCanAccessDeliveryTicket(ticket);
            }
        }
    }

    public Map<String, Object> syncMesMaterialBatch(List<Integer> ticketIds, Long taskId) {
        if (mesMaterialSyncService == null) {
            throw new RuntimeException("MES 物料号同步服务不可用");
        }
        List<Integer> normalizedTicketIds = normalizeTicketIds(ticketIds);
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<Integer, Map<String, Object>> rowByTicketId = new LinkedHashMap<>();
        List<OrderDeliveryTicket> pendingTickets = new ArrayList<>();
        for (Integer ticketId : normalizedTicketIds) {
            OrderDeliveryTicket ticket = ticketMapper.selectById(ticketId);
            if (isMesCallbackPending(ticket)) {
                rowByTicketId.put(ticketId, row(ticketId, STATUS_CALLBACK_PENDING, "MES 物料号等待回调", null));
                continue;
            }
            String skipReason = mesSkipReason(ticket);
            if (skipReason != null) {
                rowByTicketId.put(ticketId, row(ticketId, STATUS_SKIPPED, skipReason, null));
                continue;
            }
            pendingTickets.add(ticket);
        }

        List<Map<String, Object>> batchResults = null;
        boolean batchAttemptFailed = false;
        if (!pendingTickets.isEmpty()) {
            try {
                batchResults = mesMaterialSyncService.syncMaterialBatch(pendingTickets, taskId);
            } catch (RuntimeException error) {
                batchAttemptFailed = true;
                for (OrderDeliveryTicket ticket : pendingTickets) {
                    Map<String, Object> failed = row(ticket.getId(), STATUS_FAILED, safeMessage(error), null);
                    failed.put("submittedToMes", true);
                    rowByTicketId.put(ticket.getId(), failed);
                }
            }
        }

        if (!batchAttemptFailed && (batchResults == null || batchResults.isEmpty()) && !pendingTickets.isEmpty()) {
            for (OrderDeliveryTicket ticket : pendingTickets) {
                Map<String, Object> failed = row(ticket.getId(), STATUS_FAILED, "MES 批量结果未生成", null);
                failed.put("submittedToMes", true);
                rowByTicketId.put(ticket.getId(), failed);
            }
        } else if (batchResults != null) {
            for (Map<String, Object> result : batchResults) {
                Integer resultTicketId = ticketIdFromResult(result);
                if (resultTicketId != null) {
                    rowByTicketId.put(resultTicketId, mesResultRow(resultTicketId, result));
                }
            }
        }
        for (Integer ticketId : normalizedTicketIds) {
            rows.add(rowByTicketId.getOrDefault(ticketId,
                    row(ticketId, STATUS_FAILED, "MES 批量结果未生成", null)));
        }
        return summary(TASK_MES_MATERIAL_BATCH_SYNC, taskId, rows);
    }

    public Map<String, Object> allocateWmsBatch(List<Integer> ticketIds, Long taskId) {
        if (wmsAllocationService == null) {
            throw new RuntimeException("WMS 锁库服务不可用");
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Integer ticketId : normalizeTicketIds(ticketIds)) {
            OrderDeliveryTicket ticket = ticketMapper.selectById(ticketId);
            String skipReason = wmsSkipReason(ticket);
            if (skipReason != null) {
                rows.add(row(ticketId, STATUS_SKIPPED, skipReason, null));
                continue;
            }
            try {
                Object result = wmsAllocationService.allocate(ticketId, taskId);
                rows.add(row(ticketId, STATUS_SUCCESS, "WMS 库存数据获取成功", result));
            } catch (RuntimeException error) {
                rows.add(row(ticketId, STATUS_FAILED, safeMessage(error), null));
            }
        }
        return summary("WMS_ALLOCATION_BATCH", taskId, rows);
    }

    private String mesSkipReason(OrderDeliveryTicket ticket) {
        if (ticket == null) {
            return "提单行不存在";
        }
        if ("SUCCESS".equalsIgnoreCase(ticket.getMesSyncStatus()) && !isBlank(ticket.getMaterialNo())) {
            return "MES 物料号已获取";
        }
        if ("PROCESSING".equalsIgnoreCase(ticket.getMesSyncStatus())) {
            return "MES 物料号任务处理中";
        }
        return null;
    }

    private String wmsSkipReason(OrderDeliveryTicket ticket) {
        if (ticket == null) {
            return "提单行不存在";
        }
        if (isBlank(ticket.getMaterialNo())) {
            return "缺少 MES 物料号";
        }
        if ("SUCCESS".equalsIgnoreCase(ticket.getWmsAllocateStatus())) {
            return "WMS 锁库已完成";
        }
        if ("PROCESSING".equalsIgnoreCase(ticket.getWmsAllocateStatus())) {
            return "WMS 锁库任务处理中";
        }
        if (ticket.getQty() == null || ticket.getQty() <= 0) {
            return "提单数量必须大于 0";
        }
        return null;
    }

    private Map<String, Object> row(Integer ticketId, String status, String message, Object result) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("ticketId", ticketId);
        row.put("status", status);
        row.put("message", abbreviate(message));
        if (result != null) {
            row.put("result", result);
            if (result instanceof Map<?, ?> raw) {
                Object dedupResult = raw.get("dedupResult");
                Object dedupKey = raw.get("dedupKey");
                if (dedupResult != null) {
                    row.put("dedupResult", dedupResult);
                }
                if (dedupKey != null) {
                    row.put("dedupKey", dedupKey);
                }
            }
        }
        return row;
    }

    private Map<String, Object> mesResultRow(Integer ticketId, Object result) {
        String dedupResult = dedupResult(result);
        if (DEDUP_FOLLOW_EXISTING_ACTIVE.equals(dedupResult)) {
            Map<String, Object> row = row(ticketId, STATUS_DEDUP_FOLLOWING,
                    "已关联同图号版本 MES 主请求，等待回调", result);
            row.put("submittedToMes", false);
            return row;
        }
        String status = resultStatus(result);
        if (STATUS_FAILED.equals(status)) {
            Map<String, Object> row = row(ticketId, STATUS_FAILED, resultMessage(result), result);
            row.put("submittedToMes", submittedToMes(result));
            return row;
        }
        if (STATUS_RESULT_UNKNOWN.equals(status)) {
            Map<String, Object> row = row(ticketId, STATUS_RESULT_UNKNOWN, resultMessage(result), result);
            row.put("submittedToMes", submittedToMes(result));
            return row;
        }
        if (isCallbackPendingResult(result)) {
            Map<String, Object> row = row(ticketId, STATUS_CALLBACK_PENDING,
                    "MES 已受理，等待回调", result);
            row.put("submittedToMes", submittedToMes(result));
            return row;
        }
        Map<String, Object> row = row(ticketId, STATUS_SUCCESS, "MES 物料号同步成功", result);
        row.put("submittedToMes", submittedToMes(result));
        return row;
    }

    private Map<String, Object> summary(String taskType, Long taskId, List<Map<String, Object>> rows) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("taskType", taskType);
        summary.put("taskId", taskId);
        summary.put("totalCount", rows.size());
        if (TASK_MES_MATERIAL_BATCH_SYNC.equals(taskType)) {
            summary.put("successCount", count(rows, STATUS_SUCCESS));
            summary.put("callbackPendingCount", countAny(rows, STATUS_CALLBACK_PENDING, STATUS_DEDUP_FOLLOWING));
            summary.put("failedCount", count(rows, STATUS_FAILED));
            summary.put("unknownCount", count(rows, STATUS_RESULT_UNKNOWN));
            summary.put("skippedCount", count(rows, STATUS_SKIPPED));
            summary.put("dedupFollowerCount", count(rows, STATUS_DEDUP_FOLLOWING));
            summary.put("submittedToMesCount", countSubmittedToMes(rows));
            summary.put("batchCount", countDistinctBizIds(rows));
        } else {
            summary.put("successCount", count(rows, STATUS_SUCCESS));
            summary.put("callbackPendingCount", count(rows, STATUS_CALLBACK_PENDING));
            summary.put("failedCount", count(rows, STATUS_FAILED));
            summary.put("skippedCount", count(rows, STATUS_SKIPPED));
        }
        summary.put("results", rows);
        return summary;
    }

    private int count(List<Map<String, Object>> rows, String status) {
        return Math.toIntExact(rows.stream().filter(row -> status.equals(row.get("status"))).count());
    }

    private int countAny(List<Map<String, Object>> rows, String... statuses) {
        return Math.toIntExact(rows.stream().filter(row -> {
            Object rowStatus = row.get("status");
            for (String status : statuses) {
                if (status.equals(rowStatus)) {
                    return true;
                }
            }
            return false;
        }).count());
    }

    private int countSubmittedToMes(List<Map<String, Object>> rows) {
        return Math.toIntExact(rows.stream()
                .filter(row -> Boolean.TRUE.equals(row.get("submittedToMes")))
                .count());
    }

    private int countDistinctBizIds(List<Map<String, Object>> rows) {
        return Math.toIntExact(rows.stream()
                .map(this::bizIdOf)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .count());
    }

    @SuppressWarnings("unchecked")
    private String bizIdOf(Map<String, Object> row) {
        Object direct = row == null ? null : row.get("bizId");
        if (direct != null && !String.valueOf(direct).isBlank()) {
            return String.valueOf(direct).trim();
        }
        Object nested = row == null ? null : row.get("result");
        if (nested instanceof Map<?, ?> nestedMap) {
            Object value = nestedMap.get("bizId");
            return value == null ? null : String.valueOf(value).trim();
        }
        return null;
    }

    private Integer ticketIdFromResult(Map<String, Object> result) {
        Object ticketId = result == null ? null : result.get("ticketId");
        return ticketId instanceof Number number ? number.intValue() : null;
    }

    private String dedupResult(Object result) {
        if (!(result instanceof Map<?, ?> raw)) {
            return null;
        }
        Object dedupResult = raw.get("dedupResult");
        return dedupResult == null ? null : String.valueOf(dedupResult);
    }

    private boolean isCallbackPendingResult(Object result) {
        String status = resultStatus(result);
        return status != null && STATUS_CALLBACK_PENDING.equalsIgnoreCase(status);
    }

    private String resultStatus(Object result) {
        if (!(result instanceof Map<?, ?> raw)) {
            return null;
        }
        Object status = raw.get("status");
        return status == null ? null : String.valueOf(status).trim().toUpperCase();
    }

    private String resultMessage(Object result) {
        if (!(result instanceof Map<?, ?> raw)) {
            return null;
        }
        Object message = raw.get("message");
        return message == null ? null : String.valueOf(message);
    }

    private boolean submittedToMes(Object result) {
        if (!(result instanceof Map<?, ?> raw)) {
            return true;
        }
        Object submitted = raw.get("submittedToMes");
        return submitted == null || Boolean.TRUE.equals(submitted);
    }

    private boolean isMesCallbackPending(OrderDeliveryTicket ticket) {
        return ticket != null && "CALLBACK_PENDING".equalsIgnoreCase(ticket.getMesSyncStatus());
    }

    private String safeMessage(RuntimeException error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    private String abbreviate(String value) {
        if (value == null) {
            return "";
        }
        String text = value.trim();
        return text.length() <= 500 ? text : text.substring(0, 500);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
