package com.machining.quote.service.mes;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.machining.quote.dto.mes.MesProductionAssemblyRelationDTO;
import com.machining.quote.dto.mes.MesProductionDispatchReq;
import com.machining.quote.dto.mes.MesProductionDispatchResp;
import com.machining.quote.dto.mes.MesProductionProcessRouteDTO;
import com.machining.quote.entity.ItemAssemblyRelation;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderDeliveryTicketAllocation;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.entity.SalesOrderItemAssemblyRelation;
import com.machining.quote.mapper.ItemAssemblyRelationMapper;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderDeliveryTicketAllocationMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.SalesOrderItemAssemblyRelationMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.DataScopeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<Map<String, Object>>> PROCESS_ROUTE_LIST_TYPE = new TypeReference<>() {
    };
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
    private ItemAssemblyRelationMapper itemAssemblyRelationMapper;
    @Autowired(required = false)
    private ItemQuoteVersionMapper itemQuoteVersionMapper;
    @Autowired(required = false)
    private SalesOrderItemAssemblyRelationMapper salesOrderItemAssemblyRelationMapper;
    @Autowired(required = false)
    private SalesOrderItemMapper salesOrderItemMapper;
    @Autowired(required = false)
    private AuditService auditService;
    @Autowired(required = false)
    private DataScopeService dataScopeService;

    public List<Integer> normalizeAllocationIds(List<Integer> allocationIds) {
        if (allocationIds == null) {
            throw new RuntimeException("Please select at least one production item");
        }
        LinkedHashSet<Integer> normalized = new LinkedHashSet<>();
        for (Integer allocationId : allocationIds) {
            if (allocationId != null && allocationId > 0) {
                normalized.add(allocationId);
            }
        }
        if (normalized.isEmpty()) {
            throw new RuntimeException("Please select at least one production item");
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
            return row(allocationId, STATUS_SKIPPED, "Production item allocation does not exist", null);
        }
        String skipReason = skipReason(allocation);
        if (skipReason != null) {
            log.info("MES production dispatch skipped allocationId={} reason={}", allocationId, skipReason);
            return row(allocationId, STATUS_SKIPPED, skipReason, null);
        }
        String validationError = validationError(allocation);
        if (validationError != null) {
            markFailed(allocation, null, validationError);
            recordAudit("PRODUCTION_ITEM_MES_PRODUCTION_FAILED", allocation, taskId, null, validationError);
            return row(allocationId, STATUS_FAILED, validationError, null);
        }

        String requestId = requestId(allocation.getId());
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
            MesProductionDispatchReq request = buildRequest(allocation, requestId);
            MesProductionDispatchResp response = mesProductionDispatchClient.dispatch(request);
            if (response == null || !Boolean.TRUE.equals(response.getSuccess())) {
                throw new RuntimeException(response == null || isBlank(response.getMessage())
                        ? "MES production dispatch failed"
                        : response.getMessage());
            }
            markSuccess(allocation, requestId, response);
            Map<String, Object> result = resultSummary(allocation, requestId, response);
            recordAudit("PRODUCTION_ITEM_MES_PRODUCTION_SUCCESS", allocation, taskId, result, null);
            log.info("MES production dispatch completed allocationId={} requestId={} externalId={} durationMs={}",
                    allocation.getId(), requestId, response.getExternalId(), System.currentTimeMillis() - startedAt);
            return row(allocationId, STATUS_SUCCESS, messageOrDefault(response.getMessage(), "MES production dispatch succeeded"), result);
        } catch (RuntimeException error) {
            String message = safeMessage(error);
            markFailed(allocation, requestId, message);
            Map<String, Object> result = resultSummary(allocation, requestId, null);
            result.put("errorMessage", abbreviate(message));
            recordAudit("PRODUCTION_ITEM_MES_PRODUCTION_FAILED", allocation, taskId, result, message);
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
        req.setEngineeringMaterialCode(allocation.getEngineeringMaterialCode());
        req.setEngineeringMaterialName(allocation.getEngineeringMaterialName());
        req.setEngineeringMaterialTypeCode(allocation.getEngineeringMaterialTypeCode());
        req.setEngineeringMaterialTypeName(allocation.getEngineeringMaterialTypeName());
        req.setEngineeringMaterialCategory(allocation.getEngineeringMaterialCategory());
        req.setMaterialShape(allocation.getMaterialShape());
        req.setSurfaceTreatment(allocation.getSurfaceTreatment());
        req.setDescription(allocation.getDescription());
        req.setRemark(allocation.getRemark());
        req.setDeliveryDate(allocation.getDeliveryDate());
        req.setProcessRoute(buildProcessRoute(allocation.getProcessRoute()));
        req.setMaterialGrossWeight(allocation.getMaterialGrossWeight());
        req.setPartNetWeight(allocation.getPartNetWeight());
        req.setAssemblyRelations(buildAssemblyRelations(allocation));
        return req;
    }

    private List<MesProductionProcessRouteDTO> buildProcessRoute(Object processRoute) {
        List<MesProductionProcessRouteDTO> result = new ArrayList<>();
        for (Map<String, Object> row : normalizeProcessRouteRows(processRoute)) {
            if (isFeeRow(row)) {
                continue;
            }
            String processCode = text(row.get("processCode"));
            String categoryName = text(row.get("categoryName"));
            String detailName = firstNonBlank(
                    text(row.get("detailName")),
                    text(row.get("processName")),
                    text(row.get("name")));
            Double unitWorkHours = firstNumber(row.get("unitWorkHours"), row.get("inputVal"));
            Integer sortNo = integer(row.get("sortNo"));
            if (isBlank(processCode) && isBlank(categoryName) && isBlank(detailName)
                    && unitWorkHours == null && sortNo == null) {
                continue;
            }
            MesProductionProcessRouteDTO dto = new MesProductionProcessRouteDTO();
            dto.setProcessCode(processCode);
            dto.setCategoryName(categoryName);
            dto.setDetailName(detailName);
            dto.setUnitWorkHours(unitWorkHours);
            dto.setSortNo(sortNo);
            result.add(dto);
        }
        return result;
    }

    private List<Map<String, Object>> normalizeProcessRouteRows(Object processRoute) {
        if (processRoute == null) {
            return List.of();
        }
        if (processRoute instanceof String text) {
            if (text.isBlank()) {
                return List.of();
            }
            try {
                return OBJECT_MAPPER.readValue(text, PROCESS_ROUTE_LIST_TYPE);
            } catch (Exception ignored) {
                return List.of();
            }
        }
        if (processRoute instanceof Iterable<?> items) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Object item : items) {
                Map<String, Object> row = normalizeProcessRouteRow(item);
                if (!row.isEmpty()) {
                    rows.add(row);
                }
            }
            return rows;
        }
        Map<String, Object> row = normalizeProcessRouteRow(processRoute);
        return row.isEmpty() ? List.of() : List.of(row);
    }

    private Map<String, Object> normalizeProcessRouteRow(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, mapValue) -> result.put(String.valueOf(key), mapValue));
        return result;
    }

    private boolean isFeeRow(Map<String, Object> row) {
        String routeType = text(row.get("routeType"));
        String sourceType = text(row.get("sourceType"));
        return "FEE".equalsIgnoreCase(routeType) || "extra".equalsIgnoreCase(sourceType);
    }

    private List<MesProductionAssemblyRelationDTO> buildAssemblyRelations(OrderDeliveryTicketAllocation allocation) {
        if (allocation == null || allocation.getSourceItemId() == null) {
            return List.of();
        }
        if ("SALES_ORDER".equalsIgnoreCase(blankToEmpty(allocation.getSourceType()))) {
            return buildSalesOrderAssemblyRelations(allocation);
        }
        return buildRfqAssemblyRelations(allocation);
    }

    private List<MesProductionAssemblyRelationDTO> buildRfqAssemblyRelations(OrderDeliveryTicketAllocation allocation) {
        Integer parentItemId = toInteger(allocation.getSourceItemId());
        if (parentItemId == null || itemAssemblyRelationMapper == null || itemQuoteVersionMapper == null) {
            return List.of();
        }
        QueryWrapper<ItemAssemblyRelation> query = new QueryWrapper<>();
        query.eq("parent_item_id", parentItemId).orderByAsc("sort_no").orderByAsc("id");
        List<ItemAssemblyRelation> relations = itemAssemblyRelationMapper.selectList(query);
        if (relations == null || relations.isEmpty()) {
            return List.of();
        }
        ItemQuoteVersion parent = itemQuoteVersionMapper.selectById(parentItemId);
        List<MesProductionAssemblyRelationDTO> result = new ArrayList<>();
        for (ItemAssemblyRelation relation : relations) {
            if (relation == null) {
                continue;
            }
            ItemQuoteVersion child = relation.getChildItemId() == null
                    ? null
                    : itemQuoteVersionMapper.selectById(relation.getChildItemId());
            MesProductionAssemblyRelationDTO dto = new MesProductionAssemblyRelationDTO();
            dto.setParentItemId(toLong(relation.getParentItemId(), parentItemId.longValue()));
            dto.setChildItemId(toLong(relation.getChildItemId(), null));
            dto.setParentPartNo(firstNonBlank(parent == null ? null : parent.getPartNo(), allocation.getPartNo()));
            dto.setParentVersion(firstNonBlank(parent == null ? null : parent.getVersion(), allocation.getVersion()));
            dto.setChildPartNo(child == null ? null : child.getPartNo());
            dto.setChildVersion(child == null ? null : child.getVersion());
            dto.setQtyPerParent(relation.getChildQtyPerParent() == null
                    ? null
                    : BigDecimal.valueOf(relation.getChildQtyPerParent()));
            dto.setSortNo(relation.getSortNo());
            result.add(dto);
        }
        return result;
    }

    private List<MesProductionAssemblyRelationDTO> buildSalesOrderAssemblyRelations(OrderDeliveryTicketAllocation allocation) {
        Long parentItemId = allocation.getSourceItemId();
        if (parentItemId == null || salesOrderItemAssemblyRelationMapper == null || salesOrderItemMapper == null) {
            return List.of();
        }
        QueryWrapper<SalesOrderItemAssemblyRelation> query = new QueryWrapper<>();
        query.eq("parent_item_id", parentItemId).orderByAsc("sort_no").orderByAsc("id");
        List<SalesOrderItemAssemblyRelation> relations = salesOrderItemAssemblyRelationMapper.selectList(query);
        if (relations == null || relations.isEmpty()) {
            return List.of();
        }
        SalesOrderItem parent = salesOrderItemMapper.selectById(parentItemId);
        List<MesProductionAssemblyRelationDTO> result = new ArrayList<>();
        for (SalesOrderItemAssemblyRelation relation : relations) {
            if (relation == null) {
                continue;
            }
            SalesOrderItem child = relation.getChildItemId() == null
                    ? null
                    : salesOrderItemMapper.selectById(relation.getChildItemId());
            MesProductionAssemblyRelationDTO dto = new MesProductionAssemblyRelationDTO();
            dto.setParentItemId(relation.getParentItemId());
            dto.setChildItemId(relation.getChildItemId());
            dto.setParentPartNo(firstNonBlank(parent == null ? null : parent.getPartNo(), allocation.getPartNo()));
            dto.setParentVersion(firstNonBlank(parent == null ? null : parent.getVersion(), allocation.getVersion()));
            dto.setChildPartNo(child == null ? null : child.getPartNo());
            dto.setChildVersion(child == null ? null : child.getVersion());
            dto.setQtyPerParent(relation.getQtyPerParent());
            dto.setSortNo(relation.getSortNo());
            dto.setSourceParentItemId(relation.getSourceParentItemId());
            dto.setSourceChildItemId(relation.getSourceChildItemId());
            result.add(dto);
        }
        return result;
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
            return "Only PRODUCTION_REQUIRED production items can be dispatched to MES production";
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

    private Integer toInteger(Long value) {
        if (value == null || value > Integer.MAX_VALUE || value < Integer.MIN_VALUE) {
            return null;
        }
        return value.intValue();
    }

    private Long toLong(Integer value, Long fallback) {
        return value == null ? fallback : value.longValue();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private Double firstNumber(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            Double number = number(value);
            if (number != null) {
                return number;
            }
        }
        return null;
    }

    private Double number(Object value) {
        if (value instanceof Number numeric) {
            return numeric.doubleValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Integer integer(Object value) {
        if (value instanceof Number numeric) {
            return numeric.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
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
