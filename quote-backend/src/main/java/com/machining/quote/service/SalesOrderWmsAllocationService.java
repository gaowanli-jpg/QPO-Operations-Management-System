package com.machining.quote.service;

import com.machining.quote.entity.OrderDeliveryTicketAllocation;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.mapper.OrderDeliveryTicketAllocationMapper;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class SalesOrderWmsAllocationService {
    private static final String STATUS_SUCCESS = "SUCCESS";

    @Autowired
    private SalesOrderHeaderMapper salesOrderHeaderMapper;
    @Autowired
    private OrderDeliveryTicketAllocationMapper allocationMapper;
    @Autowired(required = false)
    private DataScopeService dataScopeService;

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
        return allocationMapper.selectSalesOrderProductionAllocations(salesOrderId).stream()
                .filter(this::isEligible)
                .map(OrderDeliveryTicketAllocation::getId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    public List<String> resolveSkippedReasons(Long salesOrderId) {
        requireAccessibleSalesOrder(salesOrderId);
        return allocationMapper.selectSalesOrderProductionAllocations(salesOrderId).stream()
                .map(allocation -> {
                    String reason = skipReason(allocation);
                    return reason == null ? null : "Allocation " + allocation.getId() + ": " + reason;
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private boolean isEligible(OrderDeliveryTicketAllocation allocation) {
        return skipReason(allocation) == null;
    }

    private String skipReason(OrderDeliveryTicketAllocation allocation) {
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
        if (allocation.getQty() == null || allocation.getQty() <= 0) {
            return "Allocation quantity is not positive";
        }
        if (trim(allocation.getMaterialNo()) == null) {
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
}
