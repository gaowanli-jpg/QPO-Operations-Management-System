package com.machining.quote.service.wms;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.machining.quote.dto.wms.WmsAllocationReq;
import com.machining.quote.dto.wms.WmsAllocationResp;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderDeliveryTicketAllocation;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.OrderDeliveryTicketAllocationMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.service.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class WmsAllocationService {
    private static final Logger log = LoggerFactory.getLogger(WmsAllocationService.class);
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String ALLOCATION_ACTIVE = "ACTIVE";
    private static final String TYPE_IN_STOCK = "IN_STOCK_LOCK";
    private static final String TYPE_IN_PRODUCTION = "IN_PRODUCTION_LOCK";
    private static final String TYPE_PRODUCTION_REQUIRED = "PRODUCTION_REQUIRED";

    @Autowired
    private OrderDeliveryTicketMapper ticketMapper;
    @Autowired
    private OrderDeliveryTicketAllocationMapper allocationMapper;
    @Autowired(required = false)
    private OrderRfqMapper orderRfqMapper;
    @Autowired
    private WmsAllocationClient wmsAllocationClient;
    @Autowired(required = false)
    private AuditService auditService;
    @Autowired(required = false)
    private TransactionTemplate transactionTemplate;

    public Map<String, Object> allocate(Integer ticketId, Long taskId) {
        long startedAt = System.currentTimeMillis();
        OrderDeliveryTicket ticket = loadTicketForUpdate(ticketId);
        assertCanStart(ticket);
        OrderRfq order = loadOrder(ticket);
        log.info("WMS allocation started ticketId={} taskId={} rfqId={} materialNo={} demandQty={}",
                ticket.getId(), taskId, ticket.getRfqId(), ticket.getMaterialNo(), ticket.getQty());
        markProcessing(ticket);
        try {
            WmsAllocationReq req = buildRequest(ticket, order);
            WmsAllocationResp resp = normalizeResponse(ticket, wmsAllocationClient.allocate(req));
            AllocationPlan plan = buildAllocationPlan(ticket, order, resp);
            writeSuccessRows(ticket, plan, resp.getMessage());
            Map<String, Object> summary = summary(ticket, taskId, plan, resp);
            recordAudit("DELIVERY_WMS_ALLOCATION_SUCCESS", ticket, taskId, summary, "SUCCESS", null);
            log.info("WMS allocation completed ticketId={} taskId={} allocationCount={} inStockQty={} inProductionQty={} productionRequiredQty={} durationMs={}",
                    ticket.getId(), taskId, plan.rows().size(), plan.inStockQty(), plan.inProductionQty(),
                    plan.productionRequiredQty(), System.currentTimeMillis() - startedAt);
            return summary;
        } catch (RuntimeException error) {
            markFailed(ticket, error.getMessage());
            Map<String, Object> summary = failureSummary(ticket, taskId, error.getMessage());
            recordAudit("DELIVERY_WMS_ALLOCATION_FAILED", ticket, taskId, summary, "FAILED", error.getMessage());
            log.error("WMS allocation failed ticketId={} taskId={} rfqId={} materialNo={} durationMs={}",
                    ticket.getId(), taskId, ticket.getRfqId(), ticket.getMaterialNo(),
                    System.currentTimeMillis() - startedAt, error);
            throw error;
        }
    }

    private OrderDeliveryTicket loadTicketForUpdate(Integer ticketId) {
        if (ticketId == null) {
            throw new RuntimeException("提单行 ID 不能为空");
        }
        QueryWrapper<OrderDeliveryTicket> query = new QueryWrapper<>();
        query.eq("id", ticketId).last("FOR UPDATE");
        OrderDeliveryTicket ticket = ticketMapper.selectOne(query);
        if (ticket == null) {
            throw new RuntimeException("提单行不存在");
        }
        return ticket;
    }

    private void assertCanStart(OrderDeliveryTicket ticket) {
        String status = ticket.getWmsAllocateStatus();
        if (STATUS_SUCCESS.equalsIgnoreCase(status)) {
            throw new RuntimeException("WMS allocation already completed");
        }
        if (STATUS_PROCESSING.equalsIgnoreCase(status)) {
            throw new RuntimeException("WMS allocation is already processing");
        }
    }

    private OrderRfq loadOrder(OrderDeliveryTicket ticket) {
        if (orderRfqMapper == null || ticket.getRfqId() == null) {
            return null;
        }
        return orderRfqMapper.selectById(ticket.getRfqId());
    }

    private WmsAllocationReq buildRequest(OrderDeliveryTicket ticket, OrderRfq order) {
        WmsAllocationReq req = new WmsAllocationReq();
        req.setTicketId(ticket.getId());
        req.setRfqId(ticket.getRfqId());
        req.setRfqNo(resolveRfqNo(ticket, order));
        req.setCustomerName(resolveCustomerName(ticket, order));
        req.setMaterialNo(required(ticket.getMaterialNo(), "物料号不能为空"));
        req.setPartNo(required(ticket.getPartNo(), "图号不能为空"));
        req.setVersion(ticket.getVersion() == null ? "" : ticket.getVersion());
        req.setInternalProjectNo(required(ticket.getInternalProjectNo(), "内部项目号不能为空"));
        Integer demandQty = ticket.getQty();
        if (demandQty == null || demandQty <= 0) {
            throw new RuntimeException("提单数量必须大于 0");
        }
        req.setDemandQty(demandQty);
        return req;
    }

    private WmsAllocationResp normalizeResponse(OrderDeliveryTicket ticket, WmsAllocationResp resp) {
        if (resp == null || !Boolean.TRUE.equals(resp.getSuccess())) {
            throw new RuntimeException(resp == null || isBlank(resp.getMessage()) ? "WMS allocation failed" : resp.getMessage());
        }
        String returnedMaterialNo = required(resp.getMaterialNo(), "WMS 未返回物料号");
        if (!returnedMaterialNo.equals(ticket.getMaterialNo())) {
            throw new RuntimeException("WMS material number mismatch");
        }
        if (resp.getInProductionLocks() == null) {
            resp.setInProductionLocks(List.of());
        }
        return resp;
    }

    private AllocationPlan buildAllocationPlan(OrderDeliveryTicket ticket, OrderRfq order, WmsAllocationResp resp) {
        int demandQty = ticket.getQty();
        int inStockQty = Math.max(0, valueOrZero(resp.getInStockLockedQty()));
        int inProductionQty = 0;
        List<OrderDeliveryTicketAllocation> rows = new ArrayList<>();
        if (inStockQty > 0) {
            rows.add(row(ticket, order, TYPE_IN_STOCK, inStockQty, null, resp));
        }
        for (WmsAllocationResp.InProductionLock lock : resp.getInProductionLocks()) {
            if (lock == null) {
                continue;
            }
            String orderNo = required(lock.getProductionOrderNo(), "WMS production order number is required");
            Integer lockedQty = lock.getLockedQty();
            if (lockedQty == null || lockedQty <= 0) {
                throw new RuntimeException("WMS production locked quantity must be greater than 0");
            }
            inProductionQty += lockedQty;
            rows.add(row(ticket, order, TYPE_IN_PRODUCTION, lockedQty, orderNo, resp));
        }
        int coveredQty = inStockQty + inProductionQty;
        if (coveredQty > demandQty) {
            throw new RuntimeException("WMS locked quantity exceeds demand");
        }
        int productionRequiredQty = demandQty - coveredQty;
        if (productionRequiredQty > 0) {
            rows.add(row(ticket, order, TYPE_PRODUCTION_REQUIRED, productionRequiredQty, null, resp));
        }
        int total = rows.stream().mapToInt(OrderDeliveryTicketAllocation::getQty).sum();
        if (total != demandQty) {
            throw new RuntimeException("WMS allocation quantity does not match demand");
        }
        return new AllocationPlan(rows, inStockQty, inProductionQty, productionRequiredQty);
    }

    private void writeSuccessRows(OrderDeliveryTicket ticket, AllocationPlan plan, String message) {
        executeWithoutResult(() -> {
            OrderDeliveryTicket lockedTicket = loadTicketForUpdate(ticket.getId());
            if (STATUS_SUCCESS.equalsIgnoreCase(lockedTicket.getWmsAllocateStatus())) {
                throw new RuntimeException("WMS allocation already completed");
            }
            if (!required(ticket.getMaterialNo(), "物料号不能为空")
                    .equals(required(lockedTicket.getMaterialNo(), "物料号不能为空"))) {
                throw new RuntimeException("WMS material number changed before allocation write");
            }
            for (OrderDeliveryTicketAllocation row : plan.rows()) {
                allocationMapper.insert(row);
            }
            markSuccess(lockedTicket, message);
        });
    }

    private void executeWithoutResult(Runnable action) {
        if (transactionTemplate == null) {
            action.run();
            return;
        }
        transactionTemplate.executeWithoutResult(status -> action.run());
    }

    private OrderDeliveryTicketAllocation row(
            OrderDeliveryTicket ticket,
            OrderRfq order,
            String type,
            Integer qty,
            String productionOrderNo,
            WmsAllocationResp resp) {
        OrderDeliveryTicketAllocation row = new OrderDeliveryTicketAllocation();
        row.setTicketId(ticket.getId());
        row.setRfqId(ticket.getRfqId());
        row.setItemId(ticket.getItemId());
        row.setSourceType(ticket.getSourceType());
        row.setSourceOrderId(ticket.getSourceOrderId());
        row.setSourceItemId(ticket.getSourceItemId());
        row.setAllocationType(type);
        row.setQty(qty);
        row.setTicketQty(ticket.getQty());
        row.setMaterialNo(ticket.getMaterialNo());
        row.setOrderType(ticket.getOrderType());
        row.setItemType(ticket.getItemType());
        row.setPartNo(ticket.getPartNo());
        row.setVersion(ticket.getVersion());
        row.setRfqNo(resolveRfqNo(ticket, order));
        row.setCustomerName(resolveCustomerName(ticket, order));
        row.setInternalProjectNo(ticket.getInternalProjectNo());
        row.setCustomerOrderNo(ticket.getCustomerOrderNo());
        row.setCustomerProjectNo(ticket.getCustomerProjectNo());
        row.setMaterialName(ticket.getMaterialName());
        row.setEngineeringMaterialName(ticket.getEngineeringMaterialName());
        row.setMaterialShape(ticket.getMaterialShape());
        row.setSurfaceTreatment(ticket.getSurfaceTreatment());
        row.setRemark(ticket.getRemark());
        row.setCustomerRequirement(ticket.getCustomerRequirement());
        row.setDescription(ticket.getDescription());
        row.setDeliveryDate(ticket.getDeliveryDate());
        row.setProcessRoute(ticket.getProcessRoute());
        row.setMaterialData(ticket.getMaterialData());
        row.setMaterialGrossWeight(ticket.getMaterialGrossWeight());
        row.setPartNetWeight(ticket.getPartNetWeight());
        row.setWmsProductionOrderNo(productionOrderNo);
        row.setSourcePayload(rowPayload(type, qty, productionOrderNo, resp));
        row.setMesProductionStatus("PENDING");
        row.setStatus(ALLOCATION_ACTIVE);
        LocalDateTime now = LocalDateTime.now();
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        return row;
    }

    private Map<String, Object> rowPayload(String type, Integer qty, String productionOrderNo, WmsAllocationResp resp) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("allocationType", type);
        payload.put("qty", qty);
        if (!isBlank(productionOrderNo)) {
            payload.put("productionOrderNo", productionOrderNo);
        }
        payload.put("externalTraceId", resp.getExternalTraceId());
        payload.put("message", resp.getMessage());
        return payload;
    }

    private void markProcessing(OrderDeliveryTicket ticket) {
        OrderDeliveryTicket update = new OrderDeliveryTicket();
        update.setId(ticket.getId());
        update.setWmsAllocateStatus(STATUS_PROCESSING);
        update.setWmsAllocateMessage("WMS allocation processing");
        UpdateWrapper<OrderDeliveryTicket> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", ticket.getId())
                .and(status -> status
                        .isNull("wms_allocate_status")
                        .or()
                        .notIn("wms_allocate_status", STATUS_PROCESSING, STATUS_SUCCESS));
        int updated = ticketMapper.update(update, wrapper);
        if (updated <= 0) {
            OrderDeliveryTicket current = loadTicketForUpdate(ticket.getId());
            assertCanStart(current);
            throw new RuntimeException("WMS allocation status changed before processing");
        }
    }

    private void markSuccess(OrderDeliveryTicket ticket, String message) {
        OrderDeliveryTicket update = new OrderDeliveryTicket();
        update.setId(ticket.getId());
        update.setWmsAllocateStatus(STATUS_SUCCESS);
        update.setWmsAllocateMessage(isBlank(message) ? "WMS allocation completed" : message.trim());
        update.setWmsAllocatedAt(LocalDateTime.now());
        ticketMapper.updateById(update);
    }

    private void markFailed(OrderDeliveryTicket ticket, String message) {
        OrderDeliveryTicket update = new OrderDeliveryTicket();
        update.setId(ticket.getId());
        update.setWmsAllocateStatus(STATUS_FAILED);
        update.setWmsAllocateMessage(abbreviate(isBlank(message) ? "WMS allocation failed" : message));
        ticketMapper.updateById(update);
    }

    private void recordAudit(
            String action,
            OrderDeliveryTicket ticket,
            Long taskId,
            Map<String, Object> summary,
            String result,
            String errorMessage) {
        if (auditService == null) {
            return;
        }
        auditService.record(
                action,
                "order_delivery_ticket",
                ticket.getId(),
                null,
                summary,
                "DELIVERY",
                result,
                AuditService.labelForAction(action) + " ticketId=" + ticket.getId() + " taskId=" + taskId,
                errorMessage
        );
    }

    private Map<String, Object> summary(OrderDeliveryTicket ticket, Long taskId, AllocationPlan plan, WmsAllocationResp resp) {
        Map<String, Object> summary = baseSummary(ticket, taskId);
        summary.put("demandQty", ticket.getQty());
        summary.put("allocationCount", plan.rows().size());
        summary.put("inStockQty", plan.inStockQty());
        summary.put("inProductionQty", plan.inProductionQty());
        summary.put("productionRequiredQty", plan.productionRequiredQty());
        if (!isBlank(resp.getExternalTraceId())) {
            summary.put("externalTraceId", resp.getExternalTraceId());
        }
        return summary;
    }

    private Map<String, Object> failureSummary(OrderDeliveryTicket ticket, Long taskId, String errorMessage) {
        Map<String, Object> summary = baseSummary(ticket, taskId);
        summary.put("errorMessage", abbreviate(errorMessage));
        return summary;
    }

    private Map<String, Object> baseSummary(OrderDeliveryTicket ticket, Long taskId) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("ticketId", ticket.getId());
        summary.put("taskId", taskId);
        summary.put("rfqId", ticket.getRfqId());
        summary.put("itemId", ticket.getItemId());
        summary.put("sourceType", ticket.getSourceType());
        summary.put("sourceOrderId", ticket.getSourceOrderId());
        summary.put("sourceItemId", ticket.getSourceItemId());
        summary.put("partNo", ticket.getPartNo());
        summary.put("version", ticket.getVersion());
        summary.put("materialNo", ticket.getMaterialNo());
        summary.put("internalProjectNo", ticket.getInternalProjectNo());
        return summary;
    }

    private String required(String value, String message) {
        if (isBlank(value)) {
            throw new RuntimeException(message);
        }
        return value.trim();
    }

    private String resolveRfqNo(OrderDeliveryTicket ticket, OrderRfq order) {
        if (!isBlank(ticket.getRfqNo())) {
            return ticket.getRfqNo().trim();
        }
        return order == null ? null : order.getRfqNo();
    }

    private String resolveCustomerName(OrderDeliveryTicket ticket, OrderRfq order) {
        if (!isBlank(ticket.getCustomerName())) {
            return ticket.getCustomerName().trim();
        }
        return order == null ? null : order.getCustomerName();
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private String abbreviate(String value) {
        String text = value == null ? "" : value.trim();
        return text.length() <= 500 ? text : text.substring(0, 500);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private record AllocationPlan(
            List<OrderDeliveryTicketAllocation> rows,
            int inStockQty,
            int inProductionQty,
            int productionRequiredQty) {
    }
}
