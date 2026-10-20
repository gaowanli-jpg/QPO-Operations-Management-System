package com.machining.quote.service;

import com.machining.quote.entity.OrderDeliveryTicketAllocation;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.mapper.OrderDeliveryTicketAllocationMapper;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.service.SalesOrderAllocationContextLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class SalesOrderWmsAllocationService {
    private static final String STATUS_SUCCESS = "SUCCESS";

    @Autowired
    private SalesOrderHeaderMapper salesOrderHeaderMapper;
    @Autowired
    private OrderDeliveryTicketAllocationMapper allocationMapper;
    @Autowired(required = false)
    private DataScopeService dataScopeService;
    @Autowired(required = false)
    private SalesOrderAllocationContextLoader contextLoader;

    public SalesOrderHeader requireAccessibleSalesOrder(Long salesOrderId) {
        if (salesOrderId == null) {
            throw new RuntimeException("销售订单ID不能为空");
        }
        SalesOrderHeader order = salesOrderHeaderMapper.selectById(salesOrderId);
        if (order == null) {
            throw new RuntimeException("销售订单不存在或无访问权限");
        }
        if (dataScopeService != null) {
            dataScopeService.assertCanAccessSalesOrder(order);
        }
        return order;
    }

    public void assertCanAccessSalesOrder(Long salesOrderId) {
        requireAccessibleSalesOrder(salesOrderId);
    }

    public List<Integer> resolveAllocationIds(Long salesOrderId) {
        requireAccessibleSalesOrder(salesOrderId);
        return allocationMapper.selectSalesOrderProductionAllocations(salesOrderId).stream()
                .map(OrderDeliveryTicketAllocation::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    public List<Integer> resolveEligibleAllocationIds(Long salesOrderId) {
        requireAccessibleSalesOrder(salesOrderId);
        List<OrderDeliveryTicketAllocation> allocations = allocationMapper.selectSalesOrderProductionAllocations(salesOrderId);
        Map<Integer, SalesOrderAllocationContextLoader.Context> contexts = loadContexts(allocations);
        return allocations.stream()
                .filter(allocation -> isEligible(allocation, contexts.get(allocation.getId())))
                .map(OrderDeliveryTicketAllocation::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    public List<Integer> resolveSelectedAllocationIds(Long salesOrderId, List<Integer> requestedAllocationIds) {
        requireAccessibleSalesOrder(salesOrderId);
        Set<Integer> requested = normalizeIds(requestedAllocationIds);
        if (requested.isEmpty()) {
            throw new RuntimeException("至少选择一个待产零件");
        }
        Set<Integer> orderAllocationIds = allocationMapper.selectSalesOrderProductionAllocations(salesOrderId).stream()
                .map(OrderDeliveryTicketAllocation::getId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<Integer> outsideOrder = requested.stream()
                .filter(id -> !orderAllocationIds.contains(id))
                .toList();
        if (!outsideOrder.isEmpty()) {
            throw new RuntimeException("所选 Allocation 不属于当前销售订单：" + outsideOrder);
        }
        return requested.stream().toList();
    }

    public List<Integer> resolveEligibleAllocationIds(Long salesOrderId, List<Integer> requestedAllocationIds) {
        Set<Integer> requested = new LinkedHashSet<>(resolveSelectedAllocationIds(salesOrderId, requestedAllocationIds));
        List<OrderDeliveryTicketAllocation> allocations = allocationMapper.selectSalesOrderProductionAllocations(salesOrderId);
        Map<Integer, SalesOrderAllocationContextLoader.Context> contexts = loadContexts(allocations);
        return allocations.stream()
                .filter(allocation -> allocation.getId() != null && requested.contains(allocation.getId()))
                .filter(allocation -> isEligible(allocation, contexts.get(allocation.getId())))
                .map(OrderDeliveryTicketAllocation::getId)
                .distinct()
                .toList();
    }

    public List<String> resolveSkippedReasons(Long salesOrderId) {
        requireAccessibleSalesOrder(salesOrderId);
        List<OrderDeliveryTicketAllocation> allocations = allocationMapper.selectSalesOrderProductionAllocations(salesOrderId);
        Map<Integer, SalesOrderAllocationContextLoader.Context> contexts = loadContexts(allocations);
        return allocations.stream()
                .map(allocation -> {
                    String reason = skipReason(allocation, contexts.get(allocation.getId()));
                    return reason == null ? null : "Allocation " + allocation.getId() + ": " + reason;
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private boolean isEligible(OrderDeliveryTicketAllocation allocation,
                               SalesOrderAllocationContextLoader.Context context) {
        return skipReason(allocation, context) == null;
    }

    private String skipReason(OrderDeliveryTicketAllocation allocation,
                              SalesOrderAllocationContextLoader.Context context) {
        if (allocation == null || allocation.getId() == null) {
            return "Production allocation does not exist";
        }
        if (!"PRODUCTION_REQUIRED".equalsIgnoreCase(trim(allocation.getAllocationType()))
                || !"ACTIVE".equalsIgnoreCase(defaultText(allocation.getStatus(), "ACTIVE"))) {
            return "Allocation is not an active production row";
        }
        if (Boolean.TRUE.equals(allocation.getDiscarded())) {
            return "Allocation is discarded";
        }
        if (context != null && context.error() != null) {
            return context.error();
        }
        Integer qty = context != null && context.item() != null
                ? context.item().getQty()
                : allocation.getQty();
        String materialNo = context != null && context.item() != null
                ? context.item().getMaterialNo()
                : allocation.getMaterialNo();
        if (qty == null || qty <= 0) {
            return "Allocation quantity is not positive";
        }
        if (trim(materialNo) == null) {
            return "Material number is not confirmed";
        }
        String mesStatus = trim(allocation.getMesProductionStatus());
        if ("PROCESSING".equalsIgnoreCase(mesStatus)
                || "SUBMITTING".equalsIgnoreCase(mesStatus)
                || "RESULT_UNKNOWN".equalsIgnoreCase(mesStatus)
                || STATUS_SUCCESS.equalsIgnoreCase(mesStatus)) {
            return "MES production has started or completed";
        }
        if (allocation.getWmsReservationTicketId() != null) {
            return "WMS reservation has already been applied";
        }
        return null;
    }

    private Map<Integer, SalesOrderAllocationContextLoader.Context> loadContexts(
            List<OrderDeliveryTicketAllocation> allocations) {
        if (contextLoader == null || allocations == null || allocations.isEmpty()) {
            return Map.of();
        }
        return contextLoader.load(allocations);
    }

    private String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String defaultText(String value, String fallback) {
        String trimmed = trim(value);
        return trimmed == null ? fallback : trimmed;
    }

    private Set<Integer> normalizeIds(List<Integer> ids) {
        if (ids == null) {
            return Set.of();
        }
        return ids.stream()
                .filter(id -> id != null && id > 0)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }
}
