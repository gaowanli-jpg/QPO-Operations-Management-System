package com.machining.quote.service.mes;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.machining.quote.dto.mes.MesProductionAssemblyRelationDTO;
import com.machining.quote.dto.mes.MesProductionDispatchReq;
import com.machining.quote.dto.mes.MesProductionDispatchResp;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderDeliveryTicketAllocation;
import com.machining.quote.mapper.OrderDeliveryTicketAllocationMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.DataScopeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class MesProductionDispatchService {
    private static final Logger log = LoggerFactory.getLogger(MesProductionDispatchService.class);
    private static final String TASK_TYPE = "MES_PRODUCTION_DISPATCH";
    private static final String TYPE_PRODUCTION_REQUIRED = "PRODUCTION_REQUIRED";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_SKIPPED = "SKIPPED";
    private static final DateTimeFormatter REQUEST_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Autowired
    private OrderDeliveryTicketAllocationMapper allocationMapper;
    @Autowired(required = false)
    private OrderDeliveryTicketMapper ticketMapper;
    @Autowired
    private MesProductionDispatchClient mesProductionDispatchClient;
    @Autowired(required = false)
    private AuditService auditService;
    @Autowired(required = false)
    private DataScopeService dataScopeService;

    public List<Integer> normalizeAllocationIds(List<Integer> allocationIds) {
        if (allocationIds == null) {
            throw new RuntimeException("Please select at least one locked item");
        }
        LinkedHashSet<Integer> normalized = new LinkedHashSet<>();
        for (Integer allocationId : allocationIds) {
            if (allocationId != null && allocationId > 0) {
                normalized.add(allocationId);
            }
        }
        if (normalized.isEmpty()) {
            throw new RuntimeException("Please select at least one locked item");
        }
        return new ArrayList<>(normalized);
    }

    public void assertCanAccessAllocations(List<Integer> allocationIds) {
        for (Integer allocationId : normalizeAllocationIds(allocationIds)) {
            OrderDeliveryTicketAllocation allocation = allocationMapper.selectById(allocationId);
            if (allocation == null || dataScopeService == null) {
                continue;
            }
            if (ticketMapper == null) {
                throw new RuntimeException("Delivery ticket data permission service is unavailable");
            }
            OrderDeliveryTicket ticket = ticketMapper.selectById(allocation.getTicketId());
            if (ticket != null) {
                dataScopeService.assertCanAccessDeliveryTicket(ticket);
            }
        }
    }

    public Map<String, Object> dispatchBatch(List<Integer> allocationIds, Long taskId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Integer allocationId : normalizeAllocationIds(allocationIds)) {
            rows.add(dispatchOne(allocationId, taskId));
        }
        return summary(taskId, rows);
    }

    private Map<String, Object> dispatchOne(Integer allocationId, Long taskId) {
        OrderDeliveryTicketAllocation allocation = allocationMapper.selectById(allocationId);
        if (allocation == null) {
            return row(allocationId, STATUS_SKIPPED, "Locked item allocation does not exist", null);
        }
        String skipReason = skipReason(allocation);
        if (skipReason != null) {
            log.info("MES production dispatch skipped allocationId={} reason={}", allocationId, skipReason);
            return row(allocationId, STATUS_SKIPPED, skipReason, null);
        }
        String validationError = validationError(allocation);
        if (validationError != null) {
            markFailed(allocation, null, validationError);
            recordAudit("LOCKED_ITEM_MES_PRODUCTION_FAILED", allocation, taskId, null, validationError);
            return row(allocationId, STATUS_FAILED, validationError, null);
        }

        String requestId = requestId(allocation.getId());
        MesProductionDispatchReq request = buildRequest(allocation, requestId);
        long startedAt = System.currentTimeMillis();
        if (!markProcessing(allocation, requestId)) {
            String reason = "MES production status changed before dispatch";
            log.info("MES production dispatch skipped allocationId={} reason={}", allocationId, reason);
            return row(allocationId, STATUS_SKIPPED, reason, null);
        }
        log.info("MES production dispatch started allocationId={} requestId={} sourceType={} sourceOrderId={} sourceItemId={} productionQty={} ticketQty={}",
                allocation.getId(), requestId, allocation.getSourceType(), allocation.getSourceOrderId(),
                allocation.getSourceItemId(), allocation.getQty(), allocation.getTicketQty());
        try {
            MesProductionDispatchResp response = mesProductionDispatchClient.dispatch(request);
            if (response == null || !Boolean.TRUE.equals(response.getSuccess())) {
                throw new RuntimeException(response == null || isBlank(response.getMessage())
                        ? "MES production dispatch failed"
                        : response.getMessage());
            }
            markSuccess(allocation, requestId, response);
            Map<String, Object> result = resultSummary(allocation, requestId, response);
            recordAudit("LOCKED_ITEM_MES_PRODUCTION_SUCCESS", allocation, taskId, result, null);
            log.info("MES production dispatch completed allocationId={} requestId={} externalId={} durationMs={}",
                    allocation.getId(), requestId, response.getExternalId(), System.currentTimeMillis() - startedAt);
            return row(allocationId, STATUS_SUCCESS, messageOrDefault(response.getMessage(), "MES production dispatch succeeded"), result);
        } catch (RuntimeException error) {
            String message = safeMessage(error);
            markFailed(allocation, requestId, message);
            Map<String, Object> result = resultSummary(allocation, requestId, null);
            result.put("errorMessage", abbreviate(message));
            recordAudit("LOCKED_ITEM_MES_PRODUCTION_FAILED", allocation, taskId, result, message);
            log.error("MES production dispatch failed allocationId={} requestId={} durationMs={}",
                    allocation.getId(), requestId, System.currentTimeMillis() - startedAt, error);
            return row(allocationId, STATUS_FAILED, message, result);
        }
    }

    private MesProductionDispatchReq buildRequest(OrderDeliveryTicketAllocation allocation, String requestId) {
        MesProductionDispatchReq req = new MesProductionDispatchReq();
        req.setRequestId(requestId);
        req.setAllocationId(allocation.getId());
        req.setTicketId(allocation.getTicketId());
        req.setSourceType(allocation.getSourceType());
        req.setSourceOrderId(allocation.getSourceOrderId());
        req.setSourceItemId(allocation.getSourceItemId());
        req.setSourceOrderNo(allocation.getRfqNo());
        req.setCustomerName(allocation.getCustomerName());
        req.setPartNo(allocation.getPartNo());
        req.setVersion(allocation.getVersion());
        req.setMaterialNo(allocation.getMaterialNo());
        req.setItemType(allocation.getItemType());
        req.setAllocationType(allocation.getAllocationType());
        req.setProductionQty(allocation.getQty());
        req.setTicketQty(allocation.getTicketQty());
        req.setOrderType(allocation.getOrderType());
        req.setInternalProjectNo(allocation.getInternalProjectNo());
        req.setCustomerOrderNo(allocation.getCustomerOrderNo());
        req.setCustomerProjectNo(allocation.getCustomerProjectNo());
        req.setMaterialName(allocation.getMaterialName());
        req.setEngineeringMaterialName(allocation.getEngineeringMaterialName());
        req.setSurfaceTreatment(allocation.getSurfaceTreatment());
        req.setDescription(allocation.getDescription());
        req.setRemark(allocation.getRemark());
        req.setDeliveryDate(allocation.getDeliveryDate());
        req.setProcessRoute(allocation.getProcessRoute());
        req.setMaterialData(allocation.getMaterialData());
        req.setMaterialGrossWeight(allocation.getMaterialGrossWeight());
        req.setPartNetWeight(allocation.getPartNetWeight());
        req.setAssemblyRelations(List.<MesProductionAssemblyRelationDTO>of());
        return req;
    }

    private boolean markProcessing(OrderDeliveryTicketAllocation allocation, String requestId) {
        OrderDeliveryTicketAllocation update = new OrderDeliveryTicketAllocation();
        update.setId(allocation.getId());
        update.setMesProductionStatus(STATUS_PROCESSING);
        update.setMesProductionRequestId(requestId);
        update.setMesProductionMessage("MES production dispatch processing");
        update.setUpdatedAt(LocalDateTime.now());
        UpdateWrapper<OrderDeliveryTicketAllocation> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", allocation.getId())
                .and(status -> status
                        .isNull("mes_production_status")
                        .or()
                        .eq("mes_production_status", "")
                        .or()
                        .notIn("mes_production_status", STATUS_PROCESSING, STATUS_SUCCESS));
        return allocationMapper.update(update, wrapper) > 0;
    }

    private void markSuccess(
            OrderDeliveryTicketAllocation allocation,
            String requestId,
            MesProductionDispatchResp response) {
        OrderDeliveryTicketAllocation update = new OrderDeliveryTicketAllocation();
        update.setId(allocation.getId());
        update.setMesProductionStatus(STATUS_SUCCESS);
        update.setMesProductionRequestId(requestId);
        update.setMesProductionExternalId(response == null ? null : response.getExternalId());
        update.setMesProductionSyncedAt(LocalDateTime.now());
        update.setMesProductionMessage(abbreviate(messageOrDefault(
                response == null ? null : response.getMessage(),
                "MES production dispatch succeeded")));
        update.setUpdatedAt(LocalDateTime.now());
        allocationMapper.updateById(update);
    }

    private void markFailed(OrderDeliveryTicketAllocation allocation, String requestId, String message) {
        OrderDeliveryTicketAllocation update = new OrderDeliveryTicketAllocation();
        update.setId(allocation.getId());
        update.setMesProductionStatus(STATUS_FAILED);
        if (!isBlank(requestId)) {
            update.setMesProductionRequestId(requestId);
        }
        update.setMesProductionSyncedAt(LocalDateTime.now());
        update.setMesProductionMessage(abbreviate(messageOrDefault(message, "MES production dispatch failed")));
        update.setUpdatedAt(LocalDateTime.now());
        allocationMapper.updateById(update);
    }

    private Map<String, Object> summary(Long taskId, List<Map<String, Object>> rows) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("taskType", TASK_TYPE);
        summary.put("taskId", taskId);
        summary.put("totalCount", rows.size());
        summary.put("successCount", count(rows, STATUS_SUCCESS));
        summary.put("failedCount", count(rows, STATUS_FAILED));
        summary.put("skippedCount", count(rows, STATUS_SKIPPED));
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
            String requestId,
            MesProductionDispatchResp response) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("allocationId", allocation.getId());
        result.put("ticketId", allocation.getTicketId());
        result.put("requestId", requestId);
        result.put("sourceType", allocation.getSourceType());
        result.put("sourceOrderId", allocation.getSourceOrderId());
        result.put("sourceItemId", allocation.getSourceItemId());
        result.put("productionQty", allocation.getQty());
        result.put("ticketQty", allocation.getTicketQty());
        if (response != null && !isBlank(response.getExternalId())) {
            result.put("externalId", response.getExternalId());
        }
        return result;
    }

    private String skipReason(OrderDeliveryTicketAllocation allocation) {
        if (!TYPE_PRODUCTION_REQUIRED.equalsIgnoreCase(blankToEmpty(allocation.getAllocationType()))) {
            return "Only PRODUCTION_REQUIRED locked items can be dispatched to MES production";
        }
        String status = blankToDefault(allocation.getMesProductionStatus(), STATUS_PENDING);
        if (STATUS_SUCCESS.equalsIgnoreCase(status)) {
            return "MES production dispatch already succeeded";
        }
        if (STATUS_PROCESSING.equalsIgnoreCase(status)) {
            return "MES production dispatch is already processing";
        }
        return null;
    }

    private String validationError(OrderDeliveryTicketAllocation allocation) {
        if (allocation.getQty() == null || allocation.getQty() <= 0) {
            return "Production quantity must be greater than 0";
        }
        if (allocation.getTicketQty() == null || allocation.getTicketQty() <= 0) {
            return "Ticket quantity must be greater than 0";
        }
        if (isBlank(allocation.getMaterialNo())) {
            return "Material number is required before MES production dispatch";
        }
        return null;
    }

    private int count(List<Map<String, Object>> rows, String status) {
        return Math.toIntExact(rows.stream().filter(row -> status.equals(row.get("status"))).count());
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

    private String requestId(Integer allocationId) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return "QPO-MP-" + LocalDateTime.now().format(REQUEST_TIME_FORMAT) + "-" + allocationId + "-" + suffix;
    }

    private String safeMessage(RuntimeException error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    private String messageOrDefault(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private String blankToDefault(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
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
}
