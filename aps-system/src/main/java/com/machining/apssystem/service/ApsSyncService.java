package com.machining.apssystem.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.machining.apssystem.dto.ApsSyncResultDTO;
import com.machining.apssystem.dto.ErpDeliveryItemBatchDTO;
import com.machining.apssystem.dto.ErpDeliveryItemDTO;
import com.machining.apssystem.dto.WorkItemSummaryDTO;
import com.machining.apssystem.entity.ApsMaterialInventory;
import com.machining.apssystem.entity.ApsMaterialLock;
import com.machining.apssystem.entity.ApsMaterialShortage;
import com.machining.apssystem.entity.ApsPartInventory;
import com.machining.apssystem.entity.ApsPartInventoryLock;
import com.machining.apssystem.entity.ApsPartInventoryLockDetail;
import com.machining.apssystem.entity.ApsPartInventoryPendingStock;
import com.machining.apssystem.entity.ApsProductionRequirement;
import com.machining.apssystem.entity.ApsWorkItem;
import com.machining.apssystem.entity.ApsWorkItemProcess;
import com.machining.apssystem.mapper.ApsErpSyncRunMapper;
import com.machining.apssystem.mapper.ApsMaterialInventoryMapper;
import com.machining.apssystem.mapper.ApsMaterialLockMapper;
import com.machining.apssystem.mapper.ApsMaterialShortageMapper;
import com.machining.apssystem.mapper.ApsPartInventoryLockDetailMapper;
import com.machining.apssystem.mapper.ApsPartInventoryLockMapper;
import com.machining.apssystem.mapper.ApsPartInventoryMapper;
import com.machining.apssystem.mapper.ApsPartInventoryPendingStockMapper;
import com.machining.apssystem.mapper.ApsProductionRequirementMapper;
import com.machining.apssystem.mapper.ApsWorkItemMapper;
import com.machining.apssystem.mapper.ApsWorkItemProcessMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

@Service
public class ApsSyncService {
    private static final Logger log = LoggerFactory.getLogger(ApsSyncService.class);
    private static final int DEFAULT_PAGE_SIZE = 200;
    private static final int MAX_PAGE_SIZE = 500;

    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApsErpSyncRunMapper syncRunMapper;
    @Autowired
    private ApsWorkItemMapper workItemMapper;
    @Autowired
    private ApsWorkItemProcessMapper workItemProcessMapper;
    @Autowired
    private ApsPartInventoryMapper partInventoryMapper;
    @Autowired
    private ApsPartInventoryPendingStockMapper partInventoryPendingStockMapper;
    @Autowired
    private ApsPartInventoryLockMapper partInventoryLockMapper;
    @Autowired
    private ApsPartInventoryLockDetailMapper partInventoryLockDetailMapper;
    @Autowired
    private ApsProductionRequirementMapper productionRequirementMapper;
    @Autowired
    private ApsMaterialInventoryMapper materialInventoryMapper;
    @Autowired
    private ApsMaterialLockMapper materialLockMapper;
    @Autowired
    private ApsMaterialShortageMapper materialShortageMapper;

    @Value("${aps.erp.base-url:http://localhost:8080}")
    private String erpBaseUrl;
    @Value("${aps.erp.auth-token:}")
    private String erpAuthToken;
    @Value("${aps.erp.page-size:200}")
    private Integer configuredPageSize;

    private final RestTemplate restTemplate = new RestTemplate();

    public ApsSyncResultDTO syncErpDeliveryItems(Integer requestedLimit) {
        int pageSize = normalizeLimit(requestedLimit == null ? configuredPageSize : requestedLimit);
        ApsSyncResultDTO result = new ApsSyncResultDTO();
        Long syncRunId = null;

        try {
            Integer startAfterTicketId = maxSyncedTicketId();
            syncRunId = createSyncRun(startAfterTicketId, pageSize);
            Long currentSyncRunId = syncRunId;
            result.setSyncRunId(currentSyncRunId);
            result.setStartAfterTicketId(startAfterTicketId);
            result.setNextAfterTicketId(startAfterTicketId);

            Integer afterTicketId = startAfterTicketId;
            boolean hasMore;
            do {
                ErpDeliveryItemBatchDTO batch = fetchErpBatch(afterTicketId, pageSize);
                List<ErpDeliveryItemDTO> records = batch.getRecords() == null ? List.of() : batch.getRecords();
                result.setReceivedCount(result.getReceivedCount() + records.size());

                for (ErpDeliveryItemDTO item : records) {
                    ImportOutcome outcome = transactionTemplate.execute(status -> importOneItem(currentSyncRunId, item));
                    if (outcome == null || outcome.skipped()) {
                        result.setSkippedCount(result.getSkippedCount() + 1);
                        continue;
                    }
                    result.setImportedCount(result.getImportedCount() + 1);
                    result.setProductionRequirementCount(result.getProductionRequirementCount() + outcome.productionRequirementCount());
                    result.setMaterialShortageCount(result.getMaterialShortageCount() + outcome.materialShortageCount());
                    result.getImportedItems().add(outcome.summary());
                }

                afterTicketId = batch.getNextAfterTicketId();
                result.setNextAfterTicketId(afterTicketId);
                hasMore = batch.isHasMore() && afterTicketId != null;
            } while (hasMore);

            finishSyncRun(currentSyncRunId, "SUCCESS", result, null);
            result.setMessage("ERP delivery items synced.");
            return result;
        } catch (Exception ex) {
            log.warn("APS ERP delivery item sync failed.", ex);
            result.setSuccess(false);
            result.setMessage(errorMessage(ex));
            safeFinishSyncRun(syncRunId, result);
            return result;
        }
    }

    public List<WorkItemSummaryDTO> listRecentTasks(Integer requestedLimit) {
        int limit = normalizeLimit(requestedLimit == null ? 50 : requestedLimit);
        return workItemMapper.selectRecentSummaryDtos(limit);
    }

    private ImportOutcome importOneItem(Long syncRunId, ErpDeliveryItemDTO item) {
        if (item == null || item.getTicketId() == null) {
            return ImportOutcome.skip();
        }
        Long workItemId = insertWorkItem(syncRunId, item);
        if (workItemId == null) {
            return ImportOutcome.skip();
        }
        insertProcessRows(workItemId, item.getProcessRows());
        PartLockResult partLockResult = lockPartInventory(workItemId, item);
        MaterialLockResult materialLockResult = lockMaterialInventory(workItemId, item);

        workItemMapper.updateStockStatuses(workItemId, partLockResult.status(), materialLockResult.status());

        WorkItemSummaryDTO summary = findTaskById(workItemId);
        return new ImportOutcome(false, partLockResult.productionRequirementCount(),
                materialLockResult.shortageCount(), summary);
    }

    private Long insertWorkItem(Long syncRunId, ErpDeliveryItemDTO item) {
        ApsWorkItem workItem = new ApsWorkItem();
        workItem.setErpSyncRunId(syncRunId);
        workItem.setErpTicketId(item.getTicketId());
        workItem.setErpRfqId(item.getRfqId());
        workItem.setErpItemId(item.getItemId());
        workItem.setProcessSnapshotSource(text(item.getProcessSnapshotSource(), "ORDER_DELIVERY_TICKET"));
        workItem.setCustomerName(text(item.getCustomerName()));
        workItem.setOrderStatus(text(item.getOrderStatus()));
        workItem.setInternalProjectNo(text(item.getInternalProjectNo()));
        workItem.setCustomerOrderNo(text(item.getCustomerOrderNo()));
        workItem.setCustomerProjectNo(text(item.getCustomerProjectNo()));
        workItem.setProjectNo(text(item.getProjectNo()));
        workItem.setOrderType(text(item.getOrderType()));
        workItem.setItemType(text(item.getItemType()));
        workItem.setPartNo(requireText(item.getPartNo(), "partNo"));
        workItem.setVersion(text(item.getVersion()));
        workItem.setDescription(text(item.getDescription()));
        workItem.setQty(safeQty(item.getQty()));
        workItem.setSourceItemQty(item.getSourceItemQty());
        workItem.setUnit(text(item.getUnit()));
        workItem.setMaterialName(text(item.getMaterialName()));
        workItem.setMaterialType(text(item.getMaterialType()));
        workItem.setMaterialGrossWeightKg(decimal(item.getMaterialGrossWeight()));
        workItem.setMaterialDemandWeightKg(materialDemandWeight(item));
        workItem.setPartNetWeightKg(decimal(item.getPartNetWeight()));
        workItem.setSurfaceTreatment(text(item.getSurfaceTreatment()));
        workItem.setLeadTime(text(item.getLeadTime()));
        workItem.setQuoteLeadTime(text(item.getQuoteLeadTime()));
        workItem.setInternalTargetLeadTime(text(item.getInternalTargetLeadTime()));
        workItem.setStartDate(item.getStartDate());
        workItem.setDeliveryDate(item.getDeliveryDate());
        workItem.setRemark(text(item.getRemark()));
        workItem.setArrangeType(text(item.getArrangeType()));
        workItem.setDrawingUrl(text(item.getDrawingUrl()));
        workItem.setDrawingViewUrl(text(item.getDrawingViewUrl()));
        workItem.setMaterialData(json(item.getMaterialData()));
        workItem.setSourcePayload(json(item));
        workItem.setTotalWorkHours(decimal(item.getTotalWorkHours()));
        ProcessRoute processRoute = processRoute(item.getProcessRows());
        workItem.setProcessRouteText(processRoute.text());
        workItem.setProcessRouteSignature(processRoute.signature());

        Long id = workItemMapper.insertFromErp(workItem);
        return id;
    }

    private void insertProcessRows(Long workItemId, List<ErpDeliveryItemDTO.ProcessRow> processRows) {
        if (processRows == null || processRows.isEmpty()) {
            return;
        }
        for (ErpDeliveryItemDTO.ProcessRow row : processRows) {
            if (row == null || isBlank(row.getProcessName())) {
                continue;
            }
            ApsWorkItemProcess process = new ApsWorkItemProcess();
            process.setWorkItemId(workItemId);
            process.setSortNo(row.getSortNo());
            process.setProcessName(row.getProcessName());
            process.setCalcType(text(row.getCalcType()));
            process.setSourceType(text(row.getSourceType()));
            process.setInputVal(decimal(row.getInputVal()));
            process.setWorkHours(decimal(row.getWorkHours()));
            process.setUnitRate(row.getUnitRate() == null ? null : decimal(row.getUnitRate()));
            process.setLineCost(decimal(row.getLineCost()));
            process.setRawRow(json(row));
            workItemProcessMapper.insertFromErp(process);
        }
    }

    private PartLockResult lockPartInventory(Long workItemId, ErpDeliveryItemDTO item) {
        int demandQty = safeQty(item.getQty());
        ApsPartInventory inventory = partInventoryMapper.selectByCustomerPartVersionForUpdate(
                text(item.getCustomerName()), requireText(item.getPartNo(), "partNo"), text(item.getVersion()));

        Long inventoryId = null;
        int lockedInStock = 0;
        int lockedInProduction = 0;
        int productionRequired = demandQty;
        List<PendingLockAllocation> productionAllocations = List.of();
        if (inventory != null) {
            inventoryId = inventory.getId();
            int availableInStock = Math.max(0, numberInt(inventory.getAvailableInStockQty()));
            lockedInStock = Math.min(productionRequired, availableInStock);
            productionRequired -= lockedInStock;

            int availableInProduction = Math.max(0, numberInt(inventory.getAvailableInProductionQty()));
            lockedInProduction = Math.min(productionRequired, availableInProduction);
            if (lockedInProduction > 0) {
                productionAllocations = lockPendingPartInventory(inventoryId, lockedInProduction);
            }
            productionRequired -= lockedInProduction;

            if (lockedInStock > 0 || lockedInProduction > 0) {
                partInventoryMapper.addLockedQuantities(inventoryId, lockedInStock, lockedInProduction);
            }
        }

        String status = partLockStatus(demandQty, lockedInStock + lockedInProduction, productionRequired);
        ApsPartInventoryLock lock = new ApsPartInventoryLock();
        lock.setWorkItemId(workItemId);
        lock.setPartInventoryId(inventoryId);
        lock.setCustomerName(text(item.getCustomerName()));
        lock.setPartNo(requireText(item.getPartNo(), "partNo"));
        lock.setVersion(text(item.getVersion()));
        lock.setDemandQty(demandQty);
        lock.setLockedInStockQty(lockedInStock);
        lock.setLockedInProductionQty(lockedInProduction);
        lock.setProductionRequiredQty(productionRequired);
        lock.setStatus(status);
        partInventoryLockMapper.insert(lock);
        insertPartLockDetails(lock, workItemId, inventoryId, lockedInStock, productionAllocations);

        int productionRequirementCount = 0;
        if (productionRequired > 0) {
            productionRequirementCount = 1;
            ApsProductionRequirement requirement = new ApsProductionRequirement();
            requirement.setWorkItemId(workItemId);
            requirement.setCustomerName(text(item.getCustomerName()));
            requirement.setPartNo(requireText(item.getPartNo(), "partNo"));
            requirement.setVersion(text(item.getVersion()));
            requirement.setRequiredQty(productionRequired);
            requirement.setStatus("PENDING_PRODUCTION");
            productionRequirementMapper.insert(requirement);
        }
        return new PartLockResult(status, productionRequirementCount);
    }

    private List<PendingLockAllocation> lockPendingPartInventory(Long inventoryId, int lockQty) {
        int remaining = lockQty;
        List<PendingLockAllocation> allocations = new ArrayList<>();
        List<ApsPartInventoryPendingStock> pendingRows =
                partInventoryPendingStockMapper.selectAvailableRowsForUpdate(inventoryId);
        for (ApsPartInventoryPendingStock row : pendingRows) {
            if (remaining <= 0) {
                break;
            }
            int used = Math.min(remaining, Math.max(0, numberInt(row.getAvailableQty())));
            if (used <= 0) {
                continue;
            }
            partInventoryPendingStockMapper.addLockedQty(row.getId(), used);
            allocations.add(new PendingLockAllocation(row.getId(), text(row.getPendingRefNo()), used));
            remaining -= used;
        }
        if (remaining > 0) {
            throw new IllegalStateException("Pending stock detail is lower than APS part inventory summary.");
        }
        return allocations;
    }

    private void insertPartLockDetails(
            ApsPartInventoryLock lock,
            Long workItemId,
            Long inventoryId,
            int lockedInStock,
            List<PendingLockAllocation> productionAllocations) {
        Long lockId = lock.getId();
        if (lockId == null && workItemId != null) {
            ApsPartInventoryLock persisted = partInventoryLockMapper.selectByWorkItemIdForUpdate(workItemId);
            lockId = persisted == null ? null : persisted.getId();
        }
        if (lockId == null) {
            throw new IllegalStateException("Failed to create APS part inventory lock detail.");
        }
        if (lockedInStock > 0) {
            insertPartLockDetail(lockId, workItemId, inventoryId, "IN_STOCK", null, null, lockedInStock);
        }
        for (PendingLockAllocation allocation : productionAllocations) {
            insertPartLockDetail(lockId, workItemId, inventoryId, "IN_PRODUCTION",
                    allocation.pendingStockId(), allocation.pendingRefNo(), allocation.lockedQty());
        }
    }

    private void insertPartLockDetail(
            Long lockId,
            Long workItemId,
            Long inventoryId,
            String lockType,
            Long pendingStockId,
            String pendingRefNo,
            int lockedQty) {
        if (lockedQty <= 0) {
            return;
        }
        ApsPartInventoryLockDetail detail = new ApsPartInventoryLockDetail();
        detail.setPartInventoryLockId(lockId);
        detail.setWorkItemId(workItemId);
        detail.setPartInventoryId(inventoryId);
        detail.setLockType(lockType);
        detail.setPendingStockId(pendingStockId);
        detail.setPendingRefNo(text(pendingRefNo));
        detail.setLockedQty(lockedQty);
        detail.setStatus("PENDING");
        partInventoryLockDetailMapper.insert(detail);
    }

    private MaterialLockResult lockMaterialInventory(Long workItemId, ErpDeliveryItemDTO item) {
        BigDecimal demandWeight = materialDemandWeight(item);
        String materialName = text(item.getMaterialName());
        String materialType = text(item.getMaterialType());
        if (materialName.isEmpty() || demandWeight.compareTo(BigDecimal.ZERO) <= 0) {
            return new MaterialLockResult("NOT_REQUIRED", 0);
        }

        ApsMaterialInventory inventory = materialInventoryMapper.selectByNameTypeForUpdate(materialName, materialType);

        Long inventoryId = null;
        BigDecimal lockedWeight = BigDecimal.ZERO;
        BigDecimal shortageWeight = demandWeight;
        if (inventory != null) {
            inventoryId = inventory.getId();
            BigDecimal availableWeight = maxZero(decimal(inventory.getAvailableWeightKg()));
            lockedWeight = demandWeight.min(availableWeight);
            shortageWeight = demandWeight.subtract(lockedWeight);
            if (lockedWeight.compareTo(BigDecimal.ZERO) > 0) {
                materialInventoryMapper.addLockedWeight(inventoryId, lockedWeight);
            }
        }

        String status = materialLockStatus(demandWeight, lockedWeight, shortageWeight);
        ApsMaterialLock lock = new ApsMaterialLock();
        lock.setWorkItemId(workItemId);
        lock.setMaterialInventoryId(inventoryId);
        lock.setInternalProjectNo(text(item.getInternalProjectNo()));
        lock.setCustomerName(text(item.getCustomerName()));
        lock.setPartNo(requireText(item.getPartNo(), "partNo"));
        lock.setVersion(text(item.getVersion()));
        lock.setMaterialName(materialName);
        lock.setMaterialType(materialType);
        lock.setDemandWeightKg(demandWeight);
        lock.setLockedWeightKg(lockedWeight);
        lock.setShortageWeightKg(shortageWeight);
        lock.setStatus(status);
        lock.setWarehouseStatus("PENDING");
        materialLockMapper.insert(lock);

        int shortageCount = 0;
        if (shortageWeight.compareTo(BigDecimal.ZERO) > 0) {
            shortageCount = 1;
            ApsMaterialShortage shortage = new ApsMaterialShortage();
            shortage.setWorkItemId(workItemId);
            shortage.setInternalProjectNo(text(item.getInternalProjectNo()));
            shortage.setCustomerName(text(item.getCustomerName()));
            shortage.setPartNo(requireText(item.getPartNo(), "partNo"));
            shortage.setVersion(text(item.getVersion()));
            shortage.setMaterialName(materialName);
            shortage.setMaterialType(materialType);
            shortage.setShortageWeightKg(shortageWeight);
            shortage.setSourceTask("APS_WORK_ITEM:" + workItemId);
            shortage.setStatus("PENDING_PURCHASE");
            materialShortageMapper.insert(shortage);
        }
        return new MaterialLockResult(status, shortageCount);
    }

    private ErpDeliveryItemBatchDTO fetchErpBatch(Integer afterTicketId, int pageSize) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(trimTrailingSlash(erpBaseUrl) + "/api/aps/source/delivery-items")
                .queryParam("limit", pageSize);
        if (afterTicketId != null && afterTicketId > 0) {
            builder.queryParam("afterTicketId", afterTicketId);
        }
        URI uri = builder.build(true).toUri();
        HttpHeaders headers = new HttpHeaders();
        if (!isBlank(erpAuthToken)) {
            headers.setBearerAuth(erpAuthToken.trim());
        }
        ResponseEntity<ErpDeliveryItemBatchDTO> response;
        try {
            response = restTemplate.exchange(
                    uri, HttpMethod.GET, new HttpEntity<>(headers), ErpDeliveryItemBatchDTO.class);
        } catch (RestClientResponseException ex) {
            String hint = ex.getStatusCode().value() == 401
                    ? " Check APS_ERP_AUTH_TOKEN and ERP_APS_SOURCE_TOKEN."
                    : "";
            throw new IllegalStateException("ERP sync request failed: "
                    + ex.getStatusCode().value() + " " + ex.getStatusText() + "." + hint, ex);
        }
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException("ERP sync request failed: " + response.getStatusCode());
        }
        return response.getBody();
    }

    private WorkItemSummaryDTO findTaskById(Long workItemId) {
        return workItemMapper.selectSummaryDtoById(workItemId);
    }

    private Long createSyncRun(Integer afterTicketId, int pageSize) {
        Long id = syncRunMapper.createRun(afterTicketId, pageSize);
        if (id == null) {
            throw new IllegalStateException("Failed to create APS sync run.");
        }
        return id;
    }

    private void finishSyncRun(Long syncRunId, String status, ApsSyncResultDTO result, String errorMessage) {
        syncRunMapper.finishRun(syncRunId, result.getReceivedCount(), result.getImportedCount(),
                result.getSkippedCount(), status, errorMessage);
    }

    private void safeFinishSyncRun(Long syncRunId, ApsSyncResultDTO result) {
        if (syncRunId == null) {
            return;
        }
        try {
            finishSyncRun(syncRunId, "FAILED", result, result.getMessage());
        } catch (Exception ex) {
            log.warn("Failed to update APS sync run {} as FAILED.", syncRunId, ex);
            result.setMessage(result.getMessage() + " Failed to update APS sync run: " + errorMessage(ex));
        }
    }

    private String errorMessage(Exception ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message;
    }

    private Integer maxSyncedTicketId() {
        Integer maxTicketId = workItemMapper.maxSyncedTicketId();
        return maxTicketId == null || maxTicketId <= 0 ? null : maxTicketId;
    }

    private String partLockStatus(int demandQty, int lockedQty, int productionRequiredQty) {
        if (demandQty <= 0 || productionRequiredQty <= 0) {
            return "LOCKED";
        }
        return lockedQty > 0 ? "PARTIAL_LOCKED" : "NEED_PRODUCTION";
    }

    private String materialLockStatus(BigDecimal demandWeight, BigDecimal lockedWeight, BigDecimal shortageWeight) {
        if (demandWeight.compareTo(BigDecimal.ZERO) <= 0 || shortageWeight.compareTo(BigDecimal.ZERO) <= 0) {
            return "LOCKED";
        }
        return lockedWeight.compareTo(BigDecimal.ZERO) > 0 ? "PARTIAL_LOCKED" : "SHORTAGE";
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(limit, MAX_PAGE_SIZE);
    }

    private BigDecimal materialDemandWeight(ErpDeliveryItemDTO item) {
        BigDecimal explicitWeight = decimal(item.getMaterialDemandWeight());
        if (explicitWeight.compareTo(BigDecimal.ZERO) > 0) {
            return explicitWeight;
        }
        return decimal(item.getMaterialGrossWeight()).multiply(BigDecimal.valueOf(safeQty(item.getQty())));
    }

    private BigDecimal decimal(Double value) {
        if (value == null || value <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(value);
    }

    private BigDecimal decimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal maxZero(BigDecimal value) {
        return value.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : value;
    }

    private int safeQty(Integer value) {
        return value == null || value < 0 ? 0 : value;
    }

    private int numberInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String requireText(String value, String fieldName) {
        String text = text(value);
        if (text.isEmpty()) {
            throw new IllegalArgumentException("ERP delivery item missing " + fieldName + ".");
        }
        return text;
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }

    private String text(String value, String fallback) {
        String text = text(value);
        return text.isEmpty() ? fallback : text;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String trimTrailingSlash(String value) {
        String text = text(value, "http://localhost:8080");
        while (text.endsWith("/")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    private String json(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to serialize JSONB value.", ex);
        }
    }

    private ProcessRoute processRoute(List<ErpDeliveryItemDTO.ProcessRow> processRows) {
        if (processRows == null || processRows.isEmpty()) {
            return new ProcessRoute(null, "");
        }
        List<ProcessRouteRow> routeRows = new ArrayList<>();
        for (int i = 0; i < processRows.size(); i++) {
            ErpDeliveryItemDTO.ProcessRow row = processRows.get(i);
            if (row == null || isBlank(row.getProcessName())) {
                continue;
            }
            routeRows.add(new ProcessRouteRow(row.getSortNo(), i, text(row.getProcessName())));
        }
        if (routeRows.isEmpty()) {
            return new ProcessRoute(null, "");
        }
        routeRows.sort(Comparator
                .comparing(ProcessRouteRow::sortNo, Comparator.nullsLast(Integer::compareTo))
                .thenComparingInt(ProcessRouteRow::sourceIndex));
        String routeText = String.join(" > ", routeRows.stream()
                .map(ProcessRouteRow::processName)
                .toList());
        return new ProcessRoute(routeText, md5Hex(routeText));
    }

    private String md5Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("MD5 digest is not available.", ex);
        }
    }

    private record PartLockResult(String status, int productionRequirementCount) {
    }

    private record PendingLockAllocation(Long pendingStockId, String pendingRefNo, int lockedQty) {
    }

    private record MaterialLockResult(String status, int shortageCount) {
    }

    private record ProcessRoute(String text, String signature) {
    }

    private record ProcessRouteRow(Integer sortNo, int sourceIndex, String processName) {
    }

    private record ImportOutcome(boolean skipped, int productionRequirementCount, int materialShortageCount,
                                 WorkItemSummaryDTO summary) {
        static ImportOutcome skip() {
            return new ImportOutcome(true, 0, 0, null);
        }
    }
}
