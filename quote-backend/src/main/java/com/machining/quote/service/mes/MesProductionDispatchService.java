package com.machining.quote.service.mes;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.machining.quote.dto.mes.MesProductionAssemblyRelationDTO;
import com.machining.quote.dto.mes.MesProductionBomDTO;
import com.machining.quote.dto.mes.MesProductionDispatchReq;
import com.machining.quote.dto.mes.MesProductionDispatchResp;
import com.machining.quote.dto.mes.MesProductionProcessRouteDTO;
import com.machining.quote.entity.ItemAssemblyRelation;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.MesIntegrationBatch;
import com.machining.quote.entity.MesProductionBatchItem;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderDeliveryTicketAllocation;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.entity.SalesOrderItemAssemblyRelation;
import com.machining.quote.mapper.ItemAssemblyRelationMapper;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.MesIntegrationBatchMapper;
import com.machining.quote.mapper.MesProductionBatchItemMapper;
import com.machining.quote.mapper.OrderDeliveryTicketAllocationMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemAssemblyRelationMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.DataScopeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private static final String STATUS_RESULT_UNKNOWN = "RESULT_UNKNOWN";
    private static final String STATUS_SUBMITTING = "SUBMITTING";
    private static final String SOURCE_SALES_ORDER = "SALES_ORDER";
    private static final DateTimeFormatter REQUEST_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter BIZ_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

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
    private SalesOrderHeaderMapper salesOrderHeaderMapper;
    @Autowired(required = false)
    private MesBatchStateService mesBatchStateService;
    @Autowired(required = false)
    private MesBatchFingerprintService mesBatchFingerprintService;
    @Autowired(required = false)
    private MesProductionBatchItemMapper mesProductionBatchItemMapper;
    @Autowired(required = false)
    private MesIntegrationBatchMapper mesIntegrationBatchMapper;
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
        List<Integer> normalizedIds = normalizeAllocationIds(allocationIds);
        if (!batchOrchestrationReady()) {
            return dispatchBatchLegacy(normalizedIds, taskId);
        }

        LinkedHashMap<Integer, Map<String, Object>> rowsByAllocationId = new LinkedHashMap<>();
        List<ProductionDispatchPlan> plans = new ArrayList<>();
        for (Integer allocationId : normalizedIds) {
            OrderDeliveryTicketAllocation allocation = allocationMapper.selectById(allocationId);
            if (allocation == null) {
                rowsByAllocationId.put(allocationId, row(allocationId, STATUS_SKIPPED, "Production item allocation does not exist", null));
                continue;
            }
            String skipReason = skipReason(allocation);
            if (skipReason != null) {
                log.info("MES production dispatch skipped allocationId={} reason={}", allocationId, skipReason);
                rowsByAllocationId.put(allocationId, row(allocationId, STATUS_SKIPPED, skipReason, null));
                continue;
            }
            try {
                plans.add(buildProductionPlan(allocation));
            } catch (RuntimeException error) {
                String message = safeMessage(error);
                markFailed(allocation, null, message);
                recordAudit("PRODUCTION_ITEM_MES_PRODUCTION_FAILED", allocation, taskId, null, message);
                rowsByAllocationId.put(allocationId, row(allocationId, STATUS_FAILED, message, resultSummary(allocation, null, null)));
            }
        }

        for (List<ProductionDispatchPlan> group : splitAmbiguousResponseIdentityGroups(groupPlans(plans))) {
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

    private Map<String, Object> dispatchBatchLegacy(List<Integer> allocationIds, Long taskId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Integer allocationId : allocationIds) {
            rows.add(dispatchOne(allocationId, taskId));
        }
        return summary(taskId, rows);
    }

    private boolean batchOrchestrationReady() {
        return mesBatchStateService != null
                && salesOrderItemMapper != null
                && salesOrderHeaderMapper != null
                && mesProductionBatchItemMapper != null
                && mesIntegrationBatchMapper != null;
    }

    private ProductionDispatchPlan buildProductionPlan(OrderDeliveryTicketAllocation allocation) {
        String validationError = productionValidationError(allocation);
        if (validationError != null) {
            throw new RuntimeException(validationError);
        }
        SalesOrderResolution resolution = resolveSalesOrder(allocation);
        List<MesProductionAssemblyRelationDTO> assemblyRelations = buildAssemblyRelations(allocation);
        List<MesProductionBomDTO> bom = buildBom(allocation);

        MesProductionDispatchReq.Item item = new MesProductionDispatchReq.Item();
        item.setSaleOrderNo(resolution.saleOrderNo());
        item.setSaleOrderItemNo(resolution.saleOrderItemNo());
        item.setProductCode(trimToNull(allocation.getMaterialNo()));
        item.setDemandQty(allocation.getQty());
        item.setMakeQty(allocation.getQty());
        item.setBuyQty(0);
        item.setStockQty(0);
        item.setRequiredDate(allocation.getDeliveryDate().atTime(23, 59, 59));
        item.setRemark(trimToNull(allocation.getRemark()));
        item.setCustomerRequirements(MesCustomerRequirementsAssembler.assemble(
                allocation.getDescription(),
                allocation.getSurfaceTreatment()));
        item.setBom(bom);
        item.setProcessRoute(buildProcessRoute(allocation.getProcessRoute()));

        String customerCode = trimToNull(resolution.header().getCustomerCode());
        String customerName = firstNonBlank(resolution.header().getCustomerName(), allocation.getCustomerName());
        String internalProjectNo = firstNonBlank(resolution.header().getInternalProjectNo(), allocation.getInternalProjectNo());
        String groupKey = String.join("|",
                blankToEmpty(customerCode),
                blankToEmpty(customerName),
                blankToEmpty(internalProjectNo),
                resolution.saleOrderNo());
        String itemKey = String.join("|",
                resolution.saleOrderNo(),
                resolution.saleOrderItemNo(),
                trimToNull(allocation.getMaterialNo()),
                String.valueOf(allocation.getId()));
        return new ProductionDispatchPlan(
                allocation,
                resolution.header(),
                item,
                groupKey,
                itemKey,
                customerCode,
                customerName,
                internalProjectNo);
    }

    private String productionValidationError(OrderDeliveryTicketAllocation allocation) {
        if (allocation.getQty() == null || allocation.getQty() <= 0) {
            return "Production quantity must be greater than 0";
        }
        if (isBlank(allocation.getMaterialNo())) {
            return "Material number is required before MES production dispatch";
        }
        if (allocation.getDeliveryDate() == null) {
            return "Delivery date is required before MES production dispatch";
        }
        return null;
    }

    private SalesOrderResolution resolveSalesOrder(OrderDeliveryTicketAllocation allocation) {
        SalesOrderItem itemByTicket = allocation.getTicketId() == null
                ? null
                : salesOrderItemMapper.selectBySourceTicketIdForUpdate(allocation.getTicketId());
        SalesOrderItem itemByDirectSource = null;
        if (SOURCE_SALES_ORDER.equalsIgnoreCase(blankToEmpty(allocation.getSourceType()))
                && allocation.getSourceItemId() != null) {
            itemByDirectSource = salesOrderItemMapper.selectByIdForUpdate(allocation.getSourceItemId());
        }
        if (itemByTicket != null && itemByDirectSource != null
                && !Objects.equals(itemByTicket.getId(), itemByDirectSource.getId())) {
            throw new RuntimeException("Official sales order line resolution is ambiguous for MES production dispatch");
        }
        SalesOrderItem item = itemByTicket != null ? itemByTicket : itemByDirectSource;
        if (item == null) {
            throw new RuntimeException("Official sales order line is required before MES production dispatch");
        }
        if (itemByTicket == null && allocation.getSourceOrderId() != null && item.getSalesOrderId() != null
                && !Objects.equals(allocation.getSourceOrderId(), item.getSalesOrderId())) {
            throw new RuntimeException("Official sales order source does not match production allocation");
        }
        if (item.getLineNo() == null) {
            throw new RuntimeException("Official sales order line number is required before MES production dispatch");
        }
        if (item.getSalesOrderId() == null) {
            throw new RuntimeException("Official sales order header is required before MES production dispatch");
        }
        SalesOrderHeader header = salesOrderHeaderMapper.selectById(item.getSalesOrderId());
        if (header == null) {
            throw new RuntimeException("Official sales order header is required before MES production dispatch");
        }
        String saleOrderNo = firstNonBlank(header.getDocumentNo(), header.getSalesOrderNo());
        if (isBlank(saleOrderNo)) {
            throw new RuntimeException("Official sales order number is required before MES production dispatch");
        }
        return new SalesOrderResolution(header, saleOrderNo, String.valueOf(item.getLineNo()));
    }

    private List<List<ProductionDispatchPlan>> groupPlans(List<ProductionDispatchPlan> plans) {
        LinkedHashMap<String, List<ProductionDispatchPlan>> grouped = new LinkedHashMap<>();
        for (ProductionDispatchPlan plan : plans) {
            grouped.computeIfAbsent(plan.groupKey(), ignored -> new ArrayList<>()).add(plan);
        }
        return new ArrayList<>(grouped.values());
    }

    private List<List<ProductionDispatchPlan>> splitAmbiguousResponseIdentityGroups(List<List<ProductionDispatchPlan>> groupedPlans) {
        List<List<ProductionDispatchPlan>> result = new ArrayList<>();
        for (List<ProductionDispatchPlan> group : groupedPlans) {
            Map<String, Integer> salesOrderLineCounts = new LinkedHashMap<>();
            for (ProductionDispatchPlan plan : group) {
                salesOrderLineCounts.merge(salesOrderLineKey(plan.item()), 1, Integer::sum);
            }
            boolean hasDuplicateSalesOrderLine = salesOrderLineCounts.values().stream().anyMatch(count -> count > 1);
            if (!hasDuplicateSalesOrderLine) {
                result.add(group);
                continue;
            }
            for (ProductionDispatchPlan plan : group) {
                result.add(List.of(plan));
            }
        }
        return result;
    }

    private void dispatchPlanGroup(
            List<ProductionDispatchPlan> plans,
            Long taskId,
            Map<Integer, Map<String, Object>> rowsByAllocationId) {
        if (plans == null || plans.isEmpty()) {
            return;
        }
        String proposedBizId = newProductionBizId();
        MesProductionDispatchReq request = buildBatchRequest(proposedBizId, plans);
        MesBatchStateService.ProductionBatchReservation reservation = new MesBatchStateService.ProductionBatchReservation(
                taskId,
                null,
                plans.get(0).groupKey(),
                fingerprintService().fingerprint(request),
                plans.stream()
                        .map(plan -> new MesBatchStateService.ProductionItemReservation(
                                plan.allocation().getId(),
                                plan.item().getSaleOrderNo(),
                                plan.item().getSaleOrderItemNo(),
                                plan.item().getProductCode(),
                                plan.itemKey(),
                                BigDecimal.valueOf(plan.item().getDemandQty())))
                        .toList());
        MesBatchStateService.ProductionBatchReservationResult reservationResult =
                mesBatchStateService.reserveProductionBatch(proposedBizId, reservation);
        MesIntegrationBatch batch = reservationResult.batch();
        request.setBizId(batch.getBizId());
        Map<Integer, MesProductionBatchItem> batchItemByAllocationId = indexProductionBatchItems(reservationResult.items());
        markBatchSubmitting(batch);
        reservationResult.items().forEach(item -> updateProductionBatchItem(item, STATUS_SUBMITTING, null, "MES production dispatch submitting"));

        long startedAt = System.currentTimeMillis();
        log.info("MES production batch dispatch started bizId={} taskId={} itemCount={} allocationIds={}",
                batch.getBizId(), taskId, plans.size(), allocationIds(plans));
        try {
            MesProductionDispatchResp response = mesProductionDispatchClient.dispatch(request);
            if (response == null || !Boolean.TRUE.equals(response.getSuccess())) {
                throw new RuntimeException(response == null || isBlank(response.getMessage())
                        ? "MES production dispatch failed"
                        : response.getMessage());
            }
            String responseBizId = response.getRequestId();
            if (!isBlank(responseBizId) && !batch.getBizId().equals(responseBizId)) {
                throw new RuntimeException("MES production dispatch response bizId does not match request bizId");
            }
            handleBatchResponse(batch, plans, batchItemByAllocationId, response, taskId, rowsByAllocationId);
            log.info("MES production batch dispatch responded bizId={} taskId={} itemCount={} durationMs={}",
                    batch.getBizId(), taskId, plans.size(), System.currentTimeMillis() - startedAt);
        } catch (RuntimeException error) {
            String message = safeMessage(error);
            for (ProductionDispatchPlan plan : plans) {
                MesProductionBatchItem batchItem = batchItemByAllocationId.get(plan.allocation().getId());
                String requestId = batchItem == null ? null : batchItem.getLineRequestId();
                markFailed(plan.allocation(), requestId, message);
                updateProductionBatchItem(batchItem, STATUS_FAILED, null, message);
                Map<String, Object> result = withBizId(resultSummary(plan.allocation(), requestId, null), batch.getBizId());
                result.put("errorMessage", abbreviate(message));
                rowsByAllocationId.put(plan.allocation().getId(), row(plan.allocation().getId(), STATUS_FAILED, message, result));
                recordAudit("PRODUCTION_ITEM_MES_PRODUCTION_FAILED", plan.allocation(), taskId, result, message);
            }
            updateBatch(batch, STATUS_FAILED, null, message, true);
            log.error("MES production batch dispatch failed bizId={} taskId={} itemCount={} durationMs={}",
                    batch.getBizId(), taskId, plans.size(), System.currentTimeMillis() - startedAt, error);
        }
    }

    private MesProductionDispatchReq buildBatchRequest(String bizId, List<ProductionDispatchPlan> plans) {
        ProductionDispatchPlan first = plans.get(0);
        MesProductionDispatchReq req = new MesProductionDispatchReq();
        req.setBizId(bizId);
        req.setCustomerCode(first.customerCode());
        req.setCustomerName(first.customerName());
        req.setInternalProjectNo(first.internalProjectNo());
        req.setItems(plans.stream().map(ProductionDispatchPlan::item).toList());
        return req;
    }

    private void handleBatchResponse(
            MesIntegrationBatch batch,
            List<ProductionDispatchPlan> plans,
            Map<Integer, MesProductionBatchItem> batchItemByAllocationId,
            MesProductionDispatchResp response,
            Long taskId,
            Map<Integer, Map<String, Object>> rowsByAllocationId) {
        Map<String, List<MesProductionDispatchResp.Item>> responseItemsBySalesOrderLine = responseItemsBySalesOrderLine(response);
        int successCount = 0;
        int unknownCount = 0;
        int failedCount = 0;
        for (ProductionDispatchPlan plan : plans) {
            MesProductionBatchItem batchItem = batchItemByAllocationId.get(plan.allocation().getId());
            String requestId = batchItem == null ? null : batchItem.getLineRequestId();
            String matchLabel = "saleOrderNo/saleOrderItemNo " + plan.item().getSaleOrderNo() + "/" + plan.item().getSaleOrderItemNo();
            List<MesProductionDispatchResp.Item> matches = responseItemsBySalesOrderLine.get(salesOrderLineKey(plan.item()));
            if (matches == null || matches.isEmpty()) {
                String message = "MES production response missing row for " + matchLabel;
                markResultUnknown(plan.allocation(), requestId, message);
                updateProductionBatchItem(batchItem, STATUS_RESULT_UNKNOWN, null, message);
                rowsByAllocationId.put(plan.allocation().getId(), row(plan.allocation().getId(), STATUS_RESULT_UNKNOWN, message,
                        withBizId(resultSummary(plan.allocation(), requestId, null), batch.getBizId())));
                unknownCount++;
                continue;
            }
            if (matches.size() > 1) {
                String message = "MES production response has ambiguous duplicate " + matchLabel;
                markResultUnknown(plan.allocation(), requestId, message);
                updateProductionBatchItem(batchItem, STATUS_RESULT_UNKNOWN, null, message);
                rowsByAllocationId.put(plan.allocation().getId(), row(plan.allocation().getId(), STATUS_RESULT_UNKNOWN, message,
                        withBizId(resultSummary(plan.allocation(), requestId, null), batch.getBizId())));
                unknownCount++;
                continue;
            }
            MesProductionDispatchResp.Item responseItem = matches.get(0);
            if (!isSuccessStatus(responseItem.getStatus())) {
                String message = "MES production response row failed for " + matchLabel;
                markFailed(plan.allocation(), requestId, message);
                updateProductionBatchItem(batchItem, STATUS_FAILED, responseItem, message);
                Map<String, Object> result = withBizId(resultSummary(plan.allocation(), requestId, null), batch.getBizId());
                result.put("errorMessage", abbreviate(message));
                rowsByAllocationId.put(plan.allocation().getId(), row(plan.allocation().getId(), STATUS_FAILED, message, result));
                recordAudit("PRODUCTION_ITEM_MES_PRODUCTION_FAILED", plan.allocation(), taskId, result, message);
                failedCount++;
                continue;
            }
            String externalId = responseExternalId(responseItem);
            if (isBlank(externalId)) {
                String message = "MES production response missing planNo for " + matchLabel;
                markResultUnknown(plan.allocation(), requestId, message);
                updateProductionBatchItem(batchItem, STATUS_RESULT_UNKNOWN, responseItem, message);
                rowsByAllocationId.put(plan.allocation().getId(), row(plan.allocation().getId(), STATUS_RESULT_UNKNOWN, message,
                        withBizId(resultSummary(plan.allocation(), requestId, null), batch.getBizId())));
                unknownCount++;
                continue;
            }
            String message = messageOrDefault(response.getMessage(), "MES production dispatch succeeded");
            markSuccess(plan.allocation(), requestId, externalId, message);
            updateProductionBatchItem(batchItem, STATUS_SUCCESS, responseItem, message);
            Map<String, Object> result = withBizId(resultSummary(plan.allocation(), requestId, null), batch.getBizId());
            result.put("externalId", externalId);
            rowsByAllocationId.put(plan.allocation().getId(), row(plan.allocation().getId(), STATUS_SUCCESS, message, result));
            recordAudit("PRODUCTION_ITEM_MES_PRODUCTION_SUCCESS", plan.allocation(), taskId, result, null);
            successCount++;
        }
        String batchStatus = failedCount > 0
                ? STATUS_FAILED
                : unknownCount > 0 ? STATUS_RESULT_UNKNOWN : STATUS_SUCCESS;
        updateBatch(batch, batchStatus, responseCode(response), response.getMessage(), true);
    }

    private Map<String, List<MesProductionDispatchResp.Item>> responseItemsBySalesOrderLine(MesProductionDispatchResp response) {
        Map<String, List<MesProductionDispatchResp.Item>> result = new LinkedHashMap<>();
        if (response == null || response.getData() == null || response.getData().getItems() == null) {
            return result;
        }
        for (MesProductionDispatchResp.Item item : response.getData().getItems()) {
            if (item == null || isBlank(item.getSaleOrderNo()) || isBlank(item.getSaleOrderItemNo())) {
                continue;
            }
            result.computeIfAbsent(salesOrderLineKey(item), ignored -> new ArrayList<>()).add(item);
        }
        return result;
    }

    private String salesOrderLineKey(MesProductionDispatchReq.Item item) {
        return String.join("|",
                blankToEmpty(item == null ? null : item.getSaleOrderNo()),
                blankToEmpty(item == null ? null : item.getSaleOrderItemNo()));
    }

    private String salesOrderLineKey(MesProductionDispatchResp.Item item) {
        return String.join("|",
                blankToEmpty(item == null ? null : item.getSaleOrderNo()),
                blankToEmpty(item == null ? null : item.getSaleOrderItemNo()));
    }

    private Map<Integer, MesProductionBatchItem> indexProductionBatchItems(List<MesProductionBatchItem> items) {
        Map<Integer, MesProductionBatchItem> result = new LinkedHashMap<>();
        if (items == null) {
            return result;
        }
        for (MesProductionBatchItem item : items) {
            if (item != null && item.getAllocationId() != null) {
                result.put(item.getAllocationId(), item);
            }
        }
        return result;
    }

    private void markBatchSubmitting(MesIntegrationBatch batch) {
        updateBatch(batch, STATUS_SUBMITTING, null, "MES production dispatch submitting", false);
    }

    private void updateBatch(MesIntegrationBatch source, String status, Object responseCode, String message, boolean completed) {
        if (source == null || source.getId() == null || mesIntegrationBatchMapper == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        MesIntegrationBatch update = new MesIntegrationBatch();
        update.setId(source.getId());
        update.setStatus(status);
        if (STATUS_SUBMITTING.equals(status)) {
            update.setSubmittedAt(now);
        } else {
            update.setRespondedAt(now);
        }
        if (completed) {
            update.setCompletedAt(now);
        }
        update.setResponseCode(responseCode == null ? null : abbreviate(String.valueOf(responseCode)));
        update.setResponseMessage(abbreviate(message));
        update.setUpdatedAt(now);
        mesIntegrationBatchMapper.updateById(update);
    }

    private void updateProductionBatchItem(
            MesProductionBatchItem source,
            String status,
            MesProductionDispatchResp.Item responseItem,
            String message) {
        if (source == null || source.getId() == null || mesProductionBatchItemMapper == null) {
            return;
        }
        MesProductionBatchItem update = new MesProductionBatchItem();
        update.setId(source.getId());
        update.setStatus(status);
        if (responseItem != null) {
            update.setMesPlanNo(trimToNull(responseItem.getPlanNo()));
            update.setMesRecordId(responseItem.getId() == null ? null : String.valueOf(responseItem.getId()));
            update.setResponseStatus(responseItem.getStatus() == null ? null : abbreviate(String.valueOf(responseItem.getStatus())));
        }
        update.setMessage(abbreviate(message));
        if (!STATUS_SUBMITTING.equals(status)) {
            update.setCompletedAt(LocalDateTime.now());
        }
        update.setUpdatedAt(LocalDateTime.now());
        mesProductionBatchItemMapper.updateById(update);
    }

    private MesBatchFingerprintService fingerprintService() {
        return mesBatchFingerprintService == null ? new MesBatchFingerprintService() : mesBatchFingerprintService;
    }

    private boolean isSuccessStatus(Object status) {
        if (status == null) {
            return true;
        }
        if (status instanceof Number number) {
            return number.intValue() == 0;
        }
        return "0".equals(String.valueOf(status).trim()) || "SUCCESS".equalsIgnoreCase(String.valueOf(status).trim());
    }

    private String responseExternalId(MesProductionDispatchResp.Item item) {
        if (item == null) {
            return null;
        }
        return firstNonBlank(item.getPlanNo(), item.getId() == null ? null : String.valueOf(item.getId()));
    }

    private Object responseCode(MesProductionDispatchResp response) {
        return response == null ? null : response.getCode();
    }

    private List<Integer> allocationIds(List<ProductionDispatchPlan> plans) {
        List<Integer> result = new ArrayList<>();
        for (ProductionDispatchPlan plan : plans) {
            result.add(plan.allocation().getId());
        }
        return result;
    }

    private String newProductionBizId() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        return "QPO-DEM-" + LocalDate.now().format(BIZ_DATE_FORMAT) + "-" + suffix;
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
        req.setBizId(requestId);
        req.setAllocationId(allocation.getId());
        req.setTicketId(allocation.getTicketId());
        req.setSourceType(allocation.getSourceType());
        req.setSourceOrderId(allocation.getSourceOrderId());
        req.setSourceItemId(allocation.getSourceItemId());
        req.setCustomerName(allocation.getCustomerName());
        req.setItemType(allocation.getItemType());
        req.setAllocationType(allocation.getAllocationType());
        req.setTicketQty(allocation.getTicketQty());
        req.setOrderType(allocation.getOrderType());
        req.setInternalProjectNo(allocation.getInternalProjectNo());
        req.setCustomerOrderNo(allocation.getCustomerOrderNo());
        req.setCustomerProjectNo(allocation.getCustomerProjectNo());
        req.setPartNo(allocation.getPartNo());
        req.setVersion(allocation.getVersion());
        req.setMaterialName(allocation.getMaterialName());
        req.setEngineeringMaterialCode(allocation.getEngineeringMaterialCode());
        req.setEngineeringMaterialName(allocation.getEngineeringMaterialName());
        req.setEngineeringMaterialTypeCode(allocation.getEngineeringMaterialTypeCode());
        req.setEngineeringMaterialTypeName(allocation.getEngineeringMaterialTypeName());
        req.setEngineeringMaterialCategory(allocation.getEngineeringMaterialCategory());
        req.setMaterialShape(allocation.getMaterialShape());
        req.setSurfaceTreatment(allocation.getSurfaceTreatment());
        req.setDescription(allocation.getDescription());
        req.setMaterialGrossWeight(allocation.getMaterialGrossWeight());
        req.setPartNetWeight(allocation.getPartNetWeight());
        List<MesProductionAssemblyRelationDTO> assemblyRelations = buildAssemblyRelations(allocation);
        req.setAssemblyRelations(assemblyRelations);

        MesProductionDispatchReq.Item item = new MesProductionDispatchReq.Item();
        item.setSaleOrderNo(trimToNull(allocation.getRfqNo()));
        item.setSaleOrderItemNo(allocation.getSourceItemId() == null ? null : String.valueOf(allocation.getSourceItemId()));
        item.setProductCode(trimToNull(allocation.getMaterialNo()));
        item.setDemandQty(allocation.getQty());
        item.setMakeQty(allocation.getQty());
        item.setBuyQty(0);
        item.setStockQty(0);
        if (allocation.getDeliveryDate() != null) {
            item.setRequiredDate(allocation.getDeliveryDate().atTime(23, 59, 59));
        }
        item.setRemark(trimToNull(allocation.getRemark()));
        item.setCustomerRequirements(MesCustomerRequirementsAssembler.assemble(
                allocation.getDescription(),
                allocation.getSurfaceTreatment()));
        item.setBom(buildBom(allocation));
        item.setProcessRoute(buildProcessRoute(allocation.getProcessRoute()));
        req.setItems(List.of(item));
        return req;
    }

    private List<MesProductionProcessRouteDTO> buildProcessRoute(Object processRoute) {
        List<MesProductionProcessRouteDTO> result = new ArrayList<>();
        for (Map<String, Object> row : normalizeProcessRouteRows(processRoute)) {
            boolean feeRow = isFeeRow(row);
            String processCode = feeRow
                    ? firstNonBlank(text(row.get("processCode")), text(row.get("feeCode")))
                    : text(row.get("processCode"));
            String categoryName = feeRow ? null : text(row.get("categoryName"));
            String detailName = feeRow
                    ? firstNonBlank(
                    text(row.get("detailName")),
                    text(row.get("processName")),
                    text(row.get("feeName")),
                    text(row.get("name")))
                    : firstNonBlank(
                    text(row.get("detailName")),
                    text(row.get("processName")),
                    text(row.get("name")),
                    text(row.get("categoryName")));
            Double unitWorkHours = feeRow ? null : firstNumber(row.get("unitWorkHours"), row.get("inputVal"));
            Integer sortNo = integer(row.get("sortNo"));
            String processDescription = firstNonBlank(text(row.get("description")), text(row.get("remark")));
            if (isBlank(processCode) && isBlank(categoryName) && isBlank(detailName)
                    && unitWorkHours == null && sortNo == null && isBlank(processDescription)) {
                continue;
            }
            MesProductionProcessRouteDTO dto = new MesProductionProcessRouteDTO();
            dto.setProcessCode(processCode);
            dto.setCategoryName(categoryName);
            dto.setDetailName(detailName);
            dto.setUnitWorkHours(unitWorkHours);
            dto.setSortNo(sortNo);
            dto.setProcessDescription(processDescription);
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

    private List<MesProductionBomDTO> buildBom(OrderDeliveryTicketAllocation allocation) {
        if (allocation == null) {
            return List.of();
        }
        String materialCode = trimToNull(allocation.getEngineeringMaterialCode());
        if (materialCode == null) {
            return List.of();
        }
        Double grossWeightKg = allocation.getMaterialGrossWeight();
        if (grossWeightKg == null || grossWeightKg <= 0) {
            return List.of();
        }
        return List.of(new MesProductionBomDTO(materialCode, BigDecimal.valueOf(grossWeightKg)));
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
            dto.setChildMaterialNo(child == null ? null : child.getMaterialNo());
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
            dto.setChildMaterialNo(child == null ? null : child.getMaterialNo());
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
        markSuccess(
                allocation,
                requestId,
                response == null ? null : response.getExternalId(),
                response == null ? null : response.getMessage());
    }

    private void markSuccess(
            OrderDeliveryTicketAllocation allocation,
            String requestId,
            String externalId,
            String message) {
        OrderDeliveryTicketAllocation update = new OrderDeliveryTicketAllocation();
        update.setId(allocation.getId());
        update.setMesProductionStatus(STATUS_SUCCESS);
        update.setMesProductionRequestId(requestId);
        update.setMesProductionExternalId(externalId);
        update.setMesProductionSyncedAt(LocalDateTime.now());
        update.setMesProductionMessage(abbreviate(messageOrDefault(message, "MES production dispatch succeeded")));
        update.setUpdatedAt(LocalDateTime.now());
        allocationMapper.updateById(update);
    }

    private void markResultUnknown(OrderDeliveryTicketAllocation allocation, String requestId, String message) {
        OrderDeliveryTicketAllocation update = new OrderDeliveryTicketAllocation();
        update.setId(allocation.getId());
        update.setMesProductionStatus(STATUS_RESULT_UNKNOWN);
        if (!isBlank(requestId)) {
            update.setMesProductionRequestId(requestId);
        }
        update.setMesProductionSyncedAt(LocalDateTime.now());
        update.setMesProductionMessage(abbreviate(messageOrDefault(message, "MES production dispatch result is unknown")));
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
        summary.put("unknownCount", count(rows, STATUS_RESULT_UNKNOWN));
        summary.put("skippedCount", count(rows, STATUS_SKIPPED));
        summary.put("batchCount", countDistinctBizIds(rows));
        summary.put("rows", rows);
        return summary;
    }

    private Map<String, Object> withBizId(Map<String, Object> result, String bizId) {
        if (result != null && !isBlank(bizId)) {
            result.put("bizId", bizId);
        }
        return result;
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
        if (Boolean.TRUE.equals(allocation.getDiscarded())) {
            return "Production item is discarded";
        }
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
        if (STATUS_RESULT_UNKNOWN.equalsIgnoreCase(status)) {
            return "MES production dispatch result is waiting for reconciliation";
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

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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

    private record SalesOrderResolution(
            SalesOrderHeader header,
            String saleOrderNo,
            String saleOrderItemNo) {
    }

    private record ProductionDispatchPlan(
            OrderDeliveryTicketAllocation allocation,
            SalesOrderHeader header,
            MesProductionDispatchReq.Item item,
            String groupKey,
            String itemKey,
            String customerCode,
            String customerName,
            String internalProjectNo) {
    }
}
