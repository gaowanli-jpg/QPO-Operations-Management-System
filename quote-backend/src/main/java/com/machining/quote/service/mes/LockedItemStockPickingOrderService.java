package com.machining.quote.service.mes;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.machining.quote.dto.mes.MesProductionOrderCreateReq;
import com.machining.quote.dto.mes.MesProductionOrderCreateResp;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderDeliveryTicketAllocation;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.OrderDeliveryTicketAllocationMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.DataCenterCallException;
import com.machining.quote.service.DataCenterEndpointRegistry;
import com.machining.quote.service.DataScopeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class LockedItemStockPickingOrderService {
    private static final Logger log = LoggerFactory.getLogger(LockedItemStockPickingOrderService.class);
    private static final String TASK_TYPE = "LOCKED_ITEM_STOCK_PICKING_ORDER";
    private static final String ORDER_TYPE_STOCK_PICKING = "STOCK_PICKING";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_SKIPPED = "SKIPPED";
    private static final String STATUS_RESULT_UNKNOWN = "RESULT_UNKNOWN";
    private static final String SOURCE_SALES_ORDER = "SALES_ORDER";
    private static final Set<String> LOCKED_STOCK_TYPES = Set.of("IN_STOCK_LOCK", "IN_PRODUCTION_LOCK");
    private static final DateTimeFormatter BIZ_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Autowired
    private OrderDeliveryTicketAllocationMapper allocationMapper;
    @Autowired(required = false)
    private OrderDeliveryTicketMapper ticketMapper;
    @Autowired
    private MesProductionOrderCreateClient mesProductionOrderCreateClient;
    @Autowired(required = false)
    private SalesOrderItemMapper salesOrderItemMapper;
    @Autowired(required = false)
    private SalesOrderHeaderMapper salesOrderHeaderMapper;
    @Autowired(required = false)
    private AuditService auditService;
    @Autowired(required = false)
    private DataScopeService dataScopeService;

    private Clock clock = Clock.system(ZoneId.of("Asia/Shanghai"));

    public List<Integer> normalizeAllocationIds(List<Integer> allocationIds) {
        if (allocationIds == null) {
            throw new RuntimeException("请选择至少一条锁库零件");
        }
        LinkedHashSet<Integer> normalized = new LinkedHashSet<>();
        for (Integer allocationId : allocationIds) {
            if (allocationId != null && allocationId > 0) {
                normalized.add(allocationId);
            }
        }
        if (normalized.isEmpty()) {
            throw new RuntimeException("请选择至少一条锁库零件");
        }
        return new ArrayList<>(normalized);
    }

    public void assertCanAccessAllocations(List<Integer> allocationIds) {
        if (dataScopeService == null) {
            return;
        }
        if (ticketMapper == null) {
            throw new RuntimeException("Delivery ticket data permission service is unavailable");
        }
        for (Integer allocationId : normalizeAllocationIds(allocationIds)) {
            OrderDeliveryTicketAllocation allocation = allocationMapper.selectById(allocationId);
            if (allocation == null) {
                continue;
            }
            OrderDeliveryTicket ticket = allocation.getTicketId() == null ? null : ticketMapper.selectById(allocation.getTicketId());
            if (ticket != null) {
                dataScopeService.assertCanAccessDeliveryTicket(ticket);
            }
        }
    }

    public Map<String, Object> dispatchBatch(List<Integer> allocationIds, Long taskId) {
        List<Integer> normalizedIds = normalizeAllocationIds(allocationIds);
        LinkedHashMap<Integer, Map<String, Object>> rowsByAllocationId = new LinkedHashMap<>();
        List<StockPickingPlan> plans = new ArrayList<>();

        for (Integer allocationId : normalizedIds) {
            OrderDeliveryTicketAllocation allocation = allocationMapper.selectById(allocationId);
            if (allocation == null) {
                rowsByAllocationId.put(allocationId, row(allocationId, STATUS_SKIPPED, "锁库零件不存在", null));
                continue;
            }
            String skipReason = skipReason(allocation);
            if (skipReason != null) {
                rowsByAllocationId.put(allocationId, row(allocationId, STATUS_SKIPPED, skipReason, resultSummary(allocation, null, null)));
                continue;
            }
            try {
                plans.add(buildPlan(allocation));
            } catch (RuntimeException error) {
                String message = safeMessage(error);
                markFailed(allocation, allocation.getStockPickingBizId(), message);
                Map<String, Object> result = resultSummary(allocation, allocation.getStockPickingBizId(), null);
                result.put("errorMessage", abbreviate(message));
                rowsByAllocationId.put(allocationId, row(allocationId, STATUS_FAILED, message, result));
                recordAudit("LOCKED_ITEM_STOCK_PICKING_FAILED", allocation, taskId, result, message);
            }
        }

        for (List<StockPickingPlan> group : groupPlans(plans)) {
            dispatchPlanGroup(group, taskId, rowsByAllocationId);
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Integer allocationId : normalizedIds) {
            Map<String, Object> row = rowsByAllocationId.get(allocationId);
            if (row != null) {
                rows.add(row);
            }
        }
        return summary(taskId, rows);
    }

    private StockPickingPlan buildPlan(OrderDeliveryTicketAllocation allocation) {
        String validationError = validationError(allocation);
        if (validationError != null) {
            throw new RuntimeException(validationError);
        }
        SalesOrderIdentity salesOrder = resolveSalesOrder(allocation);
        MesProductionOrderCreateReq.Item item = new MesProductionOrderCreateReq.Item();
        item.setOrderType(ORDER_TYPE_STOCK_PICKING);
        item.setProductCode(trimToNull(allocation.getMaterialNo()));
        item.setPlanCount(BigDecimal.valueOf(allocation.getQty()));
        item.setPlanStartTime(LocalDateTime.now(clock));
        item.setPlanEndTime(allocation.getDeliveryDate().atStartOfDay());
        item.setRemark(trimToNull(allocation.getRemark()));
        item.setSaleOrderNo(salesOrder.saleOrderNo());
        item.setSaleOrderItemNo(salesOrder.saleOrderItemNo());
        item.setSaleRemark(MesCustomerRequirementsAssembler.assemble(
                allocation.getDescription(),
                allocation.getSurfaceTreatment()));
        item.setCustomerPoNo("");
        item.setCustomerProjectNo(trimToNull(allocation.getCustomerProjectNo()));
        item.setCustomerOrderNo(trimToNull(allocation.getCustomerOrderNo()));
        item.setInternalProjectNo(trimToNull(allocation.getInternalProjectNo()));

        String existingBizId = trimToNull(allocation.getStockPickingBizId());
        String groupKey = existingBizId == null ? "NEW" : existingBizId;
        return new StockPickingPlan(allocation, item, existingBizId, groupKey);
    }

    private SalesOrderIdentity resolveSalesOrder(OrderDeliveryTicketAllocation allocation) {
        if (salesOrderItemMapper == null || salesOrderHeaderMapper == null) {
            throw new RuntimeException("Official sales order line service is unavailable before MES stock picking dispatch");
        }
        SalesOrderItem itemByTicket = allocation.getTicketId() == null
                ? null
                : salesOrderItemMapper.selectBySourceTicketIdForUpdate(allocation.getTicketId());
        SalesOrderItem itemByDirectSource = null;
        if (SOURCE_SALES_ORDER.equalsIgnoreCase(blankToEmpty(allocation.getSourceType()))
                && allocation.getSourceItemId() != null) {
            itemByDirectSource = salesOrderItemMapper.selectByIdForUpdate(allocation.getSourceItemId());
        }
        if (itemByTicket != null && itemByDirectSource != null
                && !sameSalesOrderItem(itemByTicket, itemByDirectSource)) {
            throw new RuntimeException("Official sales order line resolution is ambiguous for MES stock picking dispatch");
        }
        SalesOrderItem item = itemByTicket != null ? itemByTicket : itemByDirectSource;
        if (item == null) {
            throw new RuntimeException("Official sales order line is required before MES stock picking dispatch");
        }
        if (itemByTicket == null
                && SOURCE_SALES_ORDER.equalsIgnoreCase(blankToEmpty(allocation.getSourceType()))
                && allocation.getSourceOrderId() != null
                && item.getSalesOrderId() != null
                && !Objects.equals(allocation.getSourceOrderId(), item.getSalesOrderId())) {
            throw new RuntimeException("Official sales order source does not match stock picking allocation");
        }
        if (item.getLineNo() == null || item.getLineNo() <= 0) {
            throw new RuntimeException("Official sales order line number is required before MES stock picking dispatch");
        }
        if (item.getSalesOrderId() == null) {
            throw new RuntimeException("Official sales order header is required before MES stock picking dispatch");
        }
        SalesOrderHeader header = salesOrderHeaderMapper.selectById(item.getSalesOrderId());
        if (header == null) {
            throw new RuntimeException("Official sales order header is required before MES stock picking dispatch");
        }
        String saleOrderNo = firstNonBlank(header.getDocumentNo(), header.getSalesOrderNo(), allocation.getRfqNo());
        if (isBlank(saleOrderNo)) {
            throw new RuntimeException("Official sales order number is required before MES stock picking dispatch");
        }
        return new SalesOrderIdentity(saleOrderNo, String.valueOf(item.getLineNo()));
    }

    private boolean sameSalesOrderItem(SalesOrderItem left, SalesOrderItem right) {
        return Objects.equals(left.getId(), right.getId())
                && Objects.equals(left.getSalesOrderId(), right.getSalesOrderId());
    }

    private List<List<StockPickingPlan>> groupPlans(List<StockPickingPlan> plans) {
        LinkedHashMap<String, List<StockPickingPlan>> grouped = new LinkedHashMap<>();
        for (StockPickingPlan plan : plans) {
            grouped.computeIfAbsent(plan.groupKey(), ignored -> new ArrayList<>()).add(plan);
        }
        return new ArrayList<>(grouped.values());
    }

    private void dispatchPlanGroup(
            List<StockPickingPlan> plans,
            Long taskId,
            Map<Integer, Map<String, Object>> rowsByAllocationId) {
        if (plans == null || plans.isEmpty()) {
            return;
        }
        String bizId = firstNonBlank(plans.get(0).existingBizId(), newStockPickingBizId());
        List<StockPickingPlan> reservedPlans = new ArrayList<>();
        for (StockPickingPlan plan : plans) {
            if (markProcessing(plan.allocation(), bizId)) {
                reservedPlans.add(plan);
            } else {
                rowsByAllocationId.put(plan.allocation().getId(), row(
                        plan.allocation().getId(),
                        STATUS_SKIPPED,
                        "提库存工单已成功或正在下发，请刷新后查看",
                        withBizId(resultSummary(plan.allocation(), bizId, null), bizId)));
            }
        }
        if (reservedPlans.isEmpty()) {
            return;
        }
        MesProductionOrderCreateReq request = new MesProductionOrderCreateReq();
        request.setBizId(bizId);
        request.setItems(reservedPlans.stream().map(StockPickingPlan::item).toList());

        long startedAt = System.currentTimeMillis();
        log.info("Locked item stock picking order dispatch started bizId={} taskId={} allocationCount={}",
                bizId, taskId, reservedPlans.size());
        try {
            MesProductionOrderCreateResp response = mesProductionOrderCreateClient.create(request);
            if (response == null || !Boolean.TRUE.equals(response.getSuccess())) {
                throw new RuntimeException(response == null || isBlank(response.getMessage())
                        ? "MES 提库存工单创建失败"
                        : response.getMessage());
            }
            if (!isBlank(response.getBizId()) && !bizId.equals(response.getBizId())) {
                throw new RuntimeException("MES 提库存工单响应 bizId 与请求不一致");
            }
            List<MesProductionOrderCreateResp.Item> responseItems = response.getData() == null
                    || response.getData().getItems() == null
                    ? List.of()
                    : response.getData().getItems();
            if (responseItems.size() != reservedPlans.size()) {
                throw new RuntimeException("MES 提库存工单响应明细数量与请求不一致");
            }
            for (int i = 0; i < reservedPlans.size(); i++) {
                StockPickingPlan plan = reservedPlans.get(i);
                MesProductionOrderCreateResp.Item responseItem = responseItems.get(i);
                markSuccess(plan.allocation(), bizId, responseItem, response.getMessage());
                Map<String, Object> result = withBizId(resultSummary(plan.allocation(), bizId, responseItem), bizId);
                rowsByAllocationId.put(plan.allocation().getId(), row(plan.allocation().getId(), STATUS_SUCCESS,
                        "提库存工单下发成功", result));
                recordAudit("LOCKED_ITEM_STOCK_PICKING_SUCCESS", plan.allocation(), taskId, result, null);
            }
            log.info("Locked item stock picking order dispatch completed bizId={} taskId={} allocationCount={} durationMs={}",
                    bizId, taskId, reservedPlans.size(), System.currentTimeMillis() - startedAt);
        } catch (DataCenterCallException error) {
            if (error.getCertainty() == DataCenterCallException.FailureCertainty.RESULT_UNKNOWN) {
                markResultUnknown(reservedPlans, bizId, taskId, rowsByAllocationId, safeMessage(error));
                return;
            }
            markGroupFailed(reservedPlans, bizId, taskId, rowsByAllocationId, safeMessage(error), error);
        } catch (RuntimeException error) {
            markGroupFailed(reservedPlans, bizId, taskId, rowsByAllocationId, safeMessage(error), error);
        }
    }

    private boolean markProcessing(OrderDeliveryTicketAllocation allocation, String bizId) {
        OrderDeliveryTicketAllocation update = new OrderDeliveryTicketAllocation();
        update.setId(allocation.getId());
        update.setStockPickingStatus(STATUS_PROCESSING);
        update.setStockPickingBizId(bizId);
        update.setStockPickingMessage("提库存工单正在下发");
        update.setUpdatedAt(LocalDateTime.now(clock));
        UpdateWrapper<OrderDeliveryTicketAllocation> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", allocation.getId())
                .and(status -> status
                        .isNull("stock_picking_status")
                        .or()
                        .eq("stock_picking_status", "")
                        .or()
                        .notIn("stock_picking_status", STATUS_PROCESSING, STATUS_SUCCESS));
        return allocationMapper.update(update, wrapper) > 0;
    }

    private void markSuccess(
            OrderDeliveryTicketAllocation allocation,
            String bizId,
            MesProductionOrderCreateResp.Item responseItem,
            String responseMessage) {
        OrderDeliveryTicketAllocation update = new OrderDeliveryTicketAllocation();
        update.setId(allocation.getId());
        update.setStockPickingStatus(STATUS_SUCCESS);
        update.setStockPickingBizId(bizId);
        update.setStockPickingExternalId(externalId(responseItem));
        update.setStockPickingSyncedAt(LocalDateTime.now(clock));
        update.setStockPickingMessage(abbreviate(firstNonBlank(responseMessage, "提库存工单下发成功")));
        update.setUpdatedAt(LocalDateTime.now(clock));
        allocationMapper.updateById(update);
    }

    private void markResultUnknown(
            List<StockPickingPlan> plans,
            String bizId,
            Long taskId,
            Map<Integer, Map<String, Object>> rowsByAllocationId,
            String message) {
        for (StockPickingPlan plan : plans) {
            OrderDeliveryTicketAllocation allocation = plan.allocation();
            OrderDeliveryTicketAllocation update = new OrderDeliveryTicketAllocation();
            update.setId(allocation.getId());
            update.setStockPickingStatus(STATUS_RESULT_UNKNOWN);
            update.setStockPickingBizId(bizId);
            update.setStockPickingSyncedAt(LocalDateTime.now(clock));
            update.setStockPickingMessage(abbreviate(firstNonBlank(message, "提库存工单结果待核对")));
            update.setUpdatedAt(LocalDateTime.now(clock));
            allocationMapper.updateById(update);
            Map<String, Object> result = withBizId(resultSummary(allocation, bizId, null), bizId);
            result.put("errorMessage", abbreviate(message));
            rowsByAllocationId.put(allocation.getId(), row(allocation.getId(), STATUS_RESULT_UNKNOWN, message, result));
            recordAudit("LOCKED_ITEM_STOCK_PICKING_RESULT_UNKNOWN", allocation, taskId, result, null);
        }
        log.warn("Locked item stock picking order dispatch result unknown bizId={} taskId={} allocationCount={} message={}",
                bizId, taskId, plans.size(), abbreviate(message));
    }

    private void markGroupFailed(
            List<StockPickingPlan> plans,
            String bizId,
            Long taskId,
            Map<Integer, Map<String, Object>> rowsByAllocationId,
            String message,
            RuntimeException error) {
        for (StockPickingPlan plan : plans) {
            OrderDeliveryTicketAllocation allocation = plan.allocation();
            markFailed(allocation, bizId, message);
            Map<String, Object> result = withBizId(resultSummary(allocation, bizId, null), bizId);
            result.put("errorMessage", abbreviate(message));
            rowsByAllocationId.put(allocation.getId(), row(allocation.getId(), STATUS_FAILED, message, result));
            recordAudit("LOCKED_ITEM_STOCK_PICKING_FAILED", allocation, taskId, result, message);
        }
        log.warn("Locked item stock picking order dispatch failed bizId={} taskId={} allocationCount={} message={}",
                bizId, taskId, plans.size(), abbreviate(message), error);
    }

    private void markFailed(OrderDeliveryTicketAllocation allocation, String bizId, String message) {
        OrderDeliveryTicketAllocation update = new OrderDeliveryTicketAllocation();
        update.setId(allocation.getId());
        update.setStockPickingStatus(STATUS_FAILED);
        if (!isBlank(bizId)) {
            update.setStockPickingBizId(bizId);
        }
        update.setStockPickingSyncedAt(LocalDateTime.now(clock));
        update.setStockPickingMessage(abbreviate(firstNonBlank(message, "提库存工单下发失败")));
        update.setUpdatedAt(LocalDateTime.now(clock));
        allocationMapper.updateById(update);
    }

    private String validationError(OrderDeliveryTicketAllocation allocation) {
        if (allocation.getQty() == null || allocation.getQty() <= 0) {
            return "锁库数量必须大于 0";
        }
        if (isBlank(allocation.getMaterialNo())) {
            return "下发提库存工单前必须先有物料号";
        }
        if (allocation.getDeliveryDate() == null) {
            return "内部目标交期不能为空";
        }
        return null;
    }

    private String skipReason(OrderDeliveryTicketAllocation allocation) {
        if (Boolean.TRUE.equals(allocation.getDiscarded())) {
            return "锁库零件已废弃";
        }
        if (!LOCKED_STOCK_TYPES.contains(blankToEmpty(allocation.getAllocationType()).toUpperCase())) {
            return "只有锁库零件可以下发提库存工单";
        }
        String status = blankToDefault(allocation.getStockPickingStatus(), STATUS_PENDING);
        if (STATUS_SUCCESS.equalsIgnoreCase(status)) {
            return "提库存工单已下发成功";
        }
        if (STATUS_PROCESSING.equalsIgnoreCase(status)) {
            return "提库存工单正在下发";
        }
        return null;
    }

    private Map<String, Object> summary(Long taskId, List<Map<String, Object>> rows) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("taskType", TASK_TYPE);
        summary.put("taskId", taskId);
        summary.put("totalCount", rows.size());
        summary.put("successCount", count(rows, STATUS_SUCCESS));
        summary.put("failedCount", count(rows, STATUS_FAILED));
        summary.put("unknownCount", count(rows, STATUS_RESULT_UNKNOWN));
        summary.put("skippedCount", count(rows, STATUS_SKIPPED));
        summary.put("batchCount", countDistinctBizIds(rows));
        summary.put("rows", rows);
        return summary;
    }

    private Map<String, Object> row(Integer allocationId, String status, String message, Object result) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("allocationId", allocationId);
        row.put("status", status);
        row.put("message", abbreviate(message));
        if (result != null) {
            row.put("result", result);
        }
        return row;
    }

    private Map<String, Object> resultSummary(
            OrderDeliveryTicketAllocation allocation,
            String bizId,
            MesProductionOrderCreateResp.Item responseItem) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("allocationId", allocation.getId());
        result.put("ticketId", allocation.getTicketId());
        result.put("bizId", bizId);
        result.put("sourceType", allocation.getSourceType());
        result.put("sourceOrderId", allocation.getSourceOrderId());
        result.put("sourceItemId", allocation.getSourceItemId());
        result.put("materialNo", allocation.getMaterialNo());
        result.put("planCount", allocation.getQty());
        if (responseItem != null && !isBlank(externalId(responseItem))) {
            result.put("externalId", externalId(responseItem));
        }
        return result;
    }

    private Map<String, Object> withBizId(Map<String, Object> result, String bizId) {
        if (result != null && !isBlank(bizId)) {
            result.put("bizId", bizId);
        }
        return result;
    }

    private int count(List<Map<String, Object>> rows, String status) {
        return Math.toIntExact(rows.stream().filter(row -> status.equals(row.get("status"))).count());
    }

    private int countDistinctBizIds(List<Map<String, Object>> rows) {
        return Math.toIntExact(rows.stream()
                .map(this::bizIdOf)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .count());
    }

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

    private void recordAudit(
            String action,
            OrderDeliveryTicketAllocation allocation,
            Long taskId,
            Map<String, Object> summary,
            String errorMessage) {
        if (auditService == null) {
            return;
        }
        Map<String, Object> auditSummary = new LinkedHashMap<>();
        if (summary != null) {
            auditSummary.putAll(summary);
        }
        auditSummary.put("taskId", taskId);
        auditSummary.put("status", action.endsWith("_FAILED") ? STATUS_FAILED : STATUS_SUCCESS);
        auditService.record(
                action,
                "order_delivery_ticket_allocation",
                allocation.getId(),
                null,
                auditSummary,
                "DELIVERY",
                action.endsWith("_FAILED") ? STATUS_FAILED : STATUS_SUCCESS,
                AuditService.labelForAction(action) + " allocationId=" + allocation.getId() + " taskId=" + taskId,
                errorMessage
        );
    }

    private String externalId(MesProductionOrderCreateResp.Item responseItem) {
        if (responseItem == null) {
            return null;
        }
        String productOrder = trimToNull(responseItem.getProductOrder());
        if (productOrder != null) {
            return productOrder;
        }
        return responseItem.getId() == null ? null : String.valueOf(responseItem.getId());
    }

    private String newStockPickingBizId() {
        return "QPO-STOCK-" + LocalDateTime.now(clock).format(BIZ_TIME_FORMAT) + "-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    private String safeMessage(RuntimeException error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    private String blankToDefault(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String abbreviate(String value) {
        if (value == null) {
            return "";
        }
        String text = value.replaceAll("\\s+", " ").trim();
        return text.length() <= 500 ? text : text.substring(0, 500);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private record StockPickingPlan(
            OrderDeliveryTicketAllocation allocation,
            MesProductionOrderCreateReq.Item item,
            String existingBizId,
            String groupKey) {
    }

    private record SalesOrderIdentity(String saleOrderNo, String saleOrderItemNo) {
    }
}
