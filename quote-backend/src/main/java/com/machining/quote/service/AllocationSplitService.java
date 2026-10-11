package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.machining.quote.entity.OrderDeliveryTicketAllocation;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.entity.WmsReservationTicket;
import com.machining.quote.mapper.OrderDeliveryTicketAllocationMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.mapper.WmsReservationTicketMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class AllocationSplitService {
    public static final String SOURCE_WMS = "WMS";
    public static final String SOURCE_MANUAL = "MANUAL";
    private static final String TYPE_PRODUCTION_REQUIRED = "PRODUCTION_REQUIRED";
    private static final String TYPE_IN_STOCK = "IN_STOCK_LOCK";
    private static final String TYPE_IN_PRODUCTION = "IN_PRODUCTION_LOCK";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    @Autowired
    private OrderDeliveryTicketAllocationMapper allocationMapper;
    @Autowired
    private SalesOrderItemMapper salesOrderItemMapper;
    @Autowired
    private WmsReservationTicketMapper wmsReservationTicketMapper;
    @Autowired
    private SalesOrderAllocationLinkService salesOrderAllocationLinkService;

    @Transactional
    public SplitResult applyManualSplit(Long salesOrderId, Integer allocationId, Integer inStockQty, Integer inProductionQty) {
        return applyInitialSplit(
                salesOrderId,
                allocationId,
                inStockQty,
                inProductionQty,
                SOURCE_MANUAL,
                null,
                null);
    }

    @Transactional
    public SplitResult applyWmsSplit(WmsReservationTicket reservationTicket) {
        if (reservationTicket == null || reservationTicket.getAllocationId() == null) {
            throw new IllegalArgumentException("WMS reservation must reference an allocation");
        }
        return applyInitialSplit(
                null,
                reservationTicket.getAllocationId(),
                exactInt(reservationTicket.getStockFrozenCount(), "WMS stock frozen quantity"),
                exactInt(reservationTicket.getInProductionCount(), "WMS in production quantity"),
                SOURCE_WMS,
                reservationTicket.getId(),
                wmsPayload(reservationTicket));
    }

    @Transactional
    public SplitResult applyInitialSplit(
            Long salesOrderId,
            Integer allocationId,
            Integer inStockQty,
            Integer inProductionQty,
            String source,
            Long wmsReservationTicketId,
            Map<String, Object> sourcePayload) {
        String normalizedSource = normalizeSource(source);
        int stockQty = requireNonNegativeInteger(inStockQty, "閿佸凡鍏ュ簱搴撳瓨鏁伴噺");
        int inProduction = requireNonNegativeInteger(inProductionQty, "in-production quantity");

        SalesOrderItem linkedItem = salesOrderAllocationLinkService.lockSalesOrderForAllocation(allocationId);
        if (salesOrderId != null && !Objects.equals(salesOrderId, linkedItem.getSalesOrderId())) {
            throw new IllegalArgumentException("allocation does not belong to the current sales order");
        }
        OrderDeliveryTicketAllocation allocation = allocationMapper.selectByIdForUpdate(allocationId);
        if (allocation == null) {
            throw new IllegalArgumentException("production allocation does not exist");
        }
        validateProductionAllocation(allocation);
        boolean sameWmsApplication = SOURCE_WMS.equals(normalizedSource)
                && SOURCE_WMS.equalsIgnoreCase(defaultText(allocation.getAllocationSplitSource()))
                && Objects.equals(allocation.getWmsReservationTicketId(), wmsReservationTicketId);
        int originalQty = sameWmsApplication
                ? exactInt(loadWmsReserveCount(wmsReservationTicketId), "WMS reserve quantity")
                : requirePositiveInteger(allocation.getQty(), "寰呬骇鏁伴噺");
        if (!sameWmsApplication) {
            validateMesNotStarted(allocation);
        }

        WmsReservationTicket activeReservation = wmsReservationTicketMapper.selectActiveByAllocationId(allocationId);
        if (SOURCE_MANUAL.equals(normalizedSource) && activeReservation != null) {
            throw new IllegalStateException("WMS inventory task is active or requires review");
        }
        if (SOURCE_MANUAL.equals(normalizedSource)
                && SOURCE_MANUAL.equalsIgnoreCase(defaultText(allocation.getAllocationSplitSource()))) {
            throw new IllegalStateException("production allocation has already been manually split");
        }
        if (SOURCE_MANUAL.equals(normalizedSource)
                && SOURCE_WMS.equalsIgnoreCase(defaultText(allocation.getAllocationSplitSource()))) {
            throw new IllegalStateException("production allocation has already been split by WMS");
        }
        if (SOURCE_WMS.equals(normalizedSource)
                && SOURCE_MANUAL.equalsIgnoreCase(defaultText(allocation.getAllocationSplitSource()))) {
            throw new IllegalStateException("production allocation has already been manually split");
        }
        if (SOURCE_WMS.equals(normalizedSource)
                && wmsReservationTicketId == null) {
            throw new IllegalArgumentException("WMS reservation ticket is required");
        }

        if (sameWmsApplication) {
            List<OrderDeliveryTicketAllocation> existing = allocationMapper.selectBySplitParentAllocationId(allocationId);
            List<OrderDeliveryTicketAllocation> linkedRows = new ArrayList<>(existing);
            linkedRows.add(allocation);
            salesOrderAllocationLinkService.syncAllocationSplitsForAllocation(allocationId, linkedRows);
            return result(linkedItem.getSalesOrderId(), allocation, originalQty, existing);
        }

        long splitTotal = (long) stockQty + inProduction;
        if (splitTotal > originalQty) {
            throw new IllegalArgumentException("閿佸凡鍏ュ簱鏁伴噺涓庨攣鍦ㄤ骇鏁伴噺鍚堣涓嶈兘瓒呰繃褰撳墠寰呬骇鏁伴噺");
        }
        int remainder = Math.toIntExact(originalQty - splitTotal);

        if (allocation.getAllocationSplitSource() != null && !sameWmsApplication) {
            throw new IllegalStateException("production allocation has already completed its initial split");
        }
        List<OrderDeliveryTicketAllocation> existingChildren = allocationMapper.selectBySplitParentAllocationId(allocationId);
        if (!sameWmsApplication && existingChildren != null && !existingChildren.isEmpty()) {
            throw new IllegalStateException("production allocation already has split results");
        }

        LocalDateTime now = LocalDateTime.now();
        List<OrderDeliveryTicketAllocation> created = new ArrayList<>();
        if (stockQty > 0) {
            OrderDeliveryTicketAllocation existingStock = existingChildren.stream()
                    .filter(row -> TYPE_IN_STOCK.equalsIgnoreCase(defaultText(row.getAllocationType())))
                    .findFirst().orElse(null);
            if (existingStock == null) {
                created.add(createChild(allocation, TYPE_IN_STOCK, stockQty, normalizedSource, wmsReservationTicketId, sourcePayload, now));
            } else {
                existingStock.setQty(stockQty);
                existingStock.setUpdatedAt(now);
                allocationMapper.updateById(existingStock);
            }
        }
        if (inProduction > 0) {
            OrderDeliveryTicketAllocation existingInProduction = existingChildren.stream()
                    .filter(row -> TYPE_IN_PRODUCTION.equalsIgnoreCase(defaultText(row.getAllocationType())))
                    .findFirst().orElse(null);
            if (existingInProduction == null) {
                created.add(createChild(allocation, TYPE_IN_PRODUCTION, inProduction, normalizedSource, wmsReservationTicketId, sourcePayload, now));
            } else {
                existingInProduction.setQty(inProduction);
                existingInProduction.setUpdatedAt(now);
                allocationMapper.updateById(existingInProduction);
            }
        }
        allocation.setQty(remainder);
        allocation.setAllocationSplitSource(normalizedSource);
        allocation.setSplitParentAllocationId(null);
        if (SOURCE_WMS.equals(normalizedSource)) {
            allocation.setWmsReservationTicketId(wmsReservationTicketId);
            allocation.setAllocationSource("WMS_RESERVATION");
        } else {
            allocation.setAllocationSource("MANUAL_SPLIT");
        }
        allocation.setMesProductionStatus("PENDING");
        allocation.setMesProductionRequestId(null);
        allocation.setMesProductionExternalId(null);
        allocation.setMesProductionSyncedAt(null);
        allocation.setMesProductionMessage(null);
        allocation.setUpdatedAt(now);
        allocationMapper.updateById(allocation);
        for (OrderDeliveryTicketAllocation child : created) {
            allocationMapper.insert(child);
        }

        List<OrderDeliveryTicketAllocation> linkedRows = new ArrayList<>(existingChildren);
        linkedRows.removeIf(row -> created.stream().anyMatch(newRow -> Objects.equals(row.getId(), newRow.getId())));
        linkedRows.addAll(created);
        linkedRows.add(allocation);
        salesOrderAllocationLinkService.syncAllocationSplitsForAllocation(allocationId, linkedRows);
        return result(linkedItem.getSalesOrderId(), allocation, originalQty, created);
    }

    private OrderDeliveryTicketAllocation createChild(
            OrderDeliveryTicketAllocation source,
            String allocationType,
            int qty,
            String splitSource,
            Long wmsReservationTicketId,
            Map<String, Object> sourcePayload,
            LocalDateTime now) {
        OrderDeliveryTicketAllocation child = new OrderDeliveryTicketAllocation();
        BeanUtils.copyProperties(source, child);
        child.setId(null);
        child.setAllocationType(allocationType);
        child.setQty(qty);
        child.setAllocationSource(SOURCE_WMS.equals(splitSource) ? "WMS_RESERVATION" : "MANUAL_SPLIT");
        child.setAllocationSplitSource(splitSource);
        child.setSplitParentAllocationId(source.getId());
        child.setWmsReservationTicketId(SOURCE_WMS.equals(splitSource) ? wmsReservationTicketId : null);
        child.setSourcePayload(copyPayload(sourcePayload));
        child.setMesProductionStatus("PENDING");
        child.setMesProductionRequestId(null);
        child.setMesProductionExternalId(null);
        child.setMesProductionSyncedAt(null);
        child.setMesProductionMessage(null);
        child.setStatus(STATUS_ACTIVE);
        child.setDiscarded(false);
        child.setCreatedAt(now);
        child.setUpdatedAt(now);
        return child;
    }

    private void validateProductionAllocation(OrderDeliveryTicketAllocation allocation) {
        if (!TYPE_PRODUCTION_REQUIRED.equalsIgnoreCase(defaultText(allocation.getAllocationType()))
                || !STATUS_ACTIVE.equalsIgnoreCase(defaultText(allocation.getStatus(), STATUS_ACTIVE))
                || Boolean.TRUE.equals(allocation.getDiscarded())) {
            throw new IllegalStateException("allocation is not an active production row");
        }
        if (isBlank(allocation.getMaterialNo())) {
            throw new IllegalStateException("鐗╂枡鍙峰皻鏈幏鍙栨垚鍔燂紝涓嶈兘鎷嗚");
        }
    }

    private void validateMesNotStarted(OrderDeliveryTicketAllocation allocation) {
        String status = defaultText(allocation.getMesProductionStatus());
        if ("PROCESSING".equalsIgnoreCase(status)
                || "SUBMITTING".equalsIgnoreCase(status)
                || "RESULT_UNKNOWN".equalsIgnoreCase(status)
                || "SUCCESS".equalsIgnoreCase(status)) {
            throw new IllegalStateException("MES鐢熶骇宸插紑濮嬫垨宸插畬鎴愶紝涓嶈兘鍒濇鎷嗚");
        }
    }

    private SplitResult result(Long salesOrderId, OrderDeliveryTicketAllocation source, int oldQty,
                               List<OrderDeliveryTicketAllocation> created) {
        SplitResult result = new SplitResult();
        result.salesOrderId = salesOrderId;
        result.sourceAllocationId = source.getId();
        result.sourceAllocationOldQty = oldQty;
        result.productionRequiredQty = source.getQty();
        result.createdAllocations = created == null ? List.of() : created.stream().toList();
        result.createdSalesOrderItemIds = result.createdAllocations.stream()
                .map(row -> salesOrderItemMapper.selectByAllocationId(row.getId().longValue()))
                .filter(Objects::nonNull)
                .map(SalesOrderItem::getId)
                .toList();
        return result;
    }

    private Map<String, Object> wmsPayload(WmsReservationTicket ticket) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("source", "WMS_SALE_LADING_RESERVE");
        payload.put("reservationTicketId", ticket.getId());
        payload.put("batchId", ticket.getBatchId());
        payload.put("productCode", ticket.getProductCode());
        payload.put("stockFrozenCount", ticket.getStockFrozenCount());
        payload.put("inProductionCount", ticket.getInProductionCount());
        payload.put("surplusCount", ticket.getSurplusCount());
        return payload;
    }

    private Map<String, Object> copyPayload(Map<String, Object> payload) {
        return payload == null ? null : OBJECT_MAPPER.convertValue(payload, Map.class);
    }

    private String normalizeSource(String source) {
        String normalized = defaultText(source);
        if (!SOURCE_MANUAL.equalsIgnoreCase(normalized) && !SOURCE_WMS.equalsIgnoreCase(normalized)) {
            throw new IllegalArgumentException("split source must be WMS or MANUAL");
        }
        return normalized.toUpperCase();
    }

    private int exactInt(BigDecimal value, String label) {
        if (value == null) {
            throw new IllegalArgumentException(label + " is required");
        }
        try {
            return value.intValueExact();
        } catch (ArithmeticException error) {
            throw new IllegalArgumentException(label + " must be a non-negative integer", error);
        }
    }

    private int requireNonNegativeInteger(Integer value, String label) {
        if (value == null || value < 0) {
            throw new IllegalArgumentException(label + " must be a non-negative integer");
        }
        return value;
    }

    private int requirePositiveInteger(Integer value, String label) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(label + "蹇呴』澶т簬 0");
        }
        return value;
    }

    private String defaultText(String value) {
        return defaultText(value, null);
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private BigDecimal loadWmsReserveCount(Long reservationTicketId) {
        WmsReservationTicket ticket = wmsReservationTicketMapper.selectById(reservationTicketId);
        if (ticket == null || ticket.getReserveCount() == null) {
            throw new IllegalStateException("WMS reservation quantity is missing");
        }
        return ticket.getReserveCount();
    }

    public static final class SplitResult {
        private Long salesOrderId;
        private Integer sourceAllocationId;
        private Integer sourceAllocationOldQty;
        private Integer productionRequiredQty;
        private List<OrderDeliveryTicketAllocation> createdAllocations = List.of();
        private List<Long> createdSalesOrderItemIds = List.of();

        public Long salesOrderId() { return salesOrderId; }
        public Integer sourceAllocationId() { return sourceAllocationId; }
        public Integer sourceAllocationOldQty() { return sourceAllocationOldQty; }
        public Integer productionRequiredQty() { return productionRequiredQty; }
        public List<OrderDeliveryTicketAllocation> createdAllocations() { return createdAllocations; }
        public List<Long> createdSalesOrderItemIds() { return createdSalesOrderItemIds; }
    }
}
