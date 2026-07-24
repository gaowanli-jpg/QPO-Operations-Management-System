package com.machining.quote.service;

import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class DeliveryIntegrationBatchService {
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
                dataScopeService.assertCanAccessOrder(ticket.getRfqId(), null);
            }
        }
    }

    public Map<String, Object> syncMesMaterialBatch(List<Integer> ticketIds, Long taskId) {
        if (mesMaterialSyncService == null) {
            throw new RuntimeException("MES 物料号同步服务不可用");
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Integer ticketId : normalizeTicketIds(ticketIds)) {
            OrderDeliveryTicket ticket = ticketMapper.selectById(ticketId);
            String skipReason = mesSkipReason(ticket);
            if (skipReason != null) {
                rows.add(row(ticketId, "SKIPPED", skipReason, null));
                continue;
            }
            try {
                Object result = mesMaterialSyncService.syncMaterialNo(ticketId, taskId);
                rows.add(row(ticketId, "SUCCESS", "MES 物料号同步成功", result));
            } catch (RuntimeException error) {
                rows.add(row(ticketId, "FAILED", safeMessage(error), null));
            }
        }
        return summary("MES_MATERIAL_BATCH_SYNC", taskId, rows);
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
                rows.add(row(ticketId, "SKIPPED", skipReason, null));
                continue;
            }
            try {
                Object result = wmsAllocationService.allocate(ticketId, taskId);
                rows.add(row(ticketId, "SUCCESS", "WMS 库存数据获取成功", result));
            } catch (RuntimeException error) {
                rows.add(row(ticketId, "FAILED", safeMessage(error), null));
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
        }
        return row;
    }

    private Map<String, Object> summary(String taskType, Long taskId, List<Map<String, Object>> rows) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("taskType", taskType);
        summary.put("taskId", taskId);
        summary.put("totalCount", rows.size());
        summary.put("successCount", count(rows, "SUCCESS"));
        summary.put("failedCount", count(rows, "FAILED"));
        summary.put("skippedCount", count(rows, "SKIPPED"));
        summary.put("results", rows);
        return summary;
    }

    private int count(List<Map<String, Object>> rows, String status) {
        return Math.toIntExact(rows.stream().filter(row -> status.equals(row.get("status"))).count());
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
