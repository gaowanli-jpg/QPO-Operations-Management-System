package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.dto.ReleaseSourceType;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.entity.WmsReservationTicket;
import com.machining.quote.mapper.OrderDeliveryTicketAllocationMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.mapper.WmsReservationTicketMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class SalesOrderWmsAllocationService {
    private static final String STATUS_SUCCESS = "SUCCESS";

    @Autowired
    private SalesOrderItemMapper salesOrderItemMapper;
    @Autowired
    private OrderDeliveryTicketMapper ticketMapper;
    @Autowired
    private SalesOrderHeaderMapper salesOrderHeaderMapper;
    @Autowired
    private OrderDeliveryTicketAllocationMapper allocationMapper;
    @Autowired
    private WmsReservationTicketMapper wmsReservationTicketMapper;
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

    public List<Integer> resolveEligibleTicketIds(Long salesOrderId) {
        requireAccessibleSalesOrder(salesOrderId);
        List<Integer> linkedSourceTicketIds = linkedSourceTicketIds(salesOrderId);
        List<OrderDeliveryTicket> tickets = ticketMapper.selectList(ticketQuery(salesOrderId, linkedSourceTicketIds));
        List<Integer> eligibleTicketIds = new ArrayList<>();
        for (OrderDeliveryTicket ticket : tickets) {
            if (isEligible(ticket)) {
                eligibleTicketIds.add(ticket.getId());
            }
        }
        return eligibleTicketIds;
    }

    public List<Integer> resolveTicketIds(Long salesOrderId) {
        requireAccessibleSalesOrder(salesOrderId);
        return ticketMapper.selectList(ticketQuery(salesOrderId, linkedSourceTicketIds(salesOrderId))).stream()
                .map(OrderDeliveryTicket::getId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
    }

    public List<String> resolveSkippedReasons(Long salesOrderId) {
        requireAccessibleSalesOrder(salesOrderId);
        List<Integer> linkedSourceTicketIds = linkedSourceTicketIds(salesOrderId);
        List<OrderDeliveryTicket> tickets = ticketMapper.selectList(ticketQuery(salesOrderId, linkedSourceTicketIds));
        List<String> reasons = new ArrayList<>();
        for (OrderDeliveryTicket ticket : tickets) {
            String reason = skipReason(ticket);
            if (reason != null) {
                reasons.add("提单行" + ticket.getId() + "：" + reason);
            }
        }
        return reasons;
    }

    private boolean isEligible(OrderDeliveryTicket ticket) {
        return skipReason(ticket) == null;
    }

    private String skipReason(OrderDeliveryTicket ticket) {
        if (ticket == null || ticket.getId() == null) {
            return "提单行不存在";
        }
        if (Boolean.TRUE.equals(ticket.getDiscarded())) {
            return "已废弃，不允许获取库存信息";
        }
        if (!STATUS_SUCCESS.equalsIgnoreCase(trim(ticket.getMesSyncStatus()))
                || trim(ticket.getMaterialNo()) == null) {
            return "物料号尚未获取成功";
        }
        String wmsStatus = trim(ticket.getWmsAllocateStatus());
        if (STATUS_SUCCESS.equalsIgnoreCase(wmsStatus)) {
            return "已获取过库存信息";
        }
        if (wmsStatus != null && !"PENDING".equalsIgnoreCase(wmsStatus)
                && !"FAILED".equalsIgnoreCase(wmsStatus)) {
            WmsReservationTicket active = wmsReservationTicketMapper.selectActiveByTicketId(ticket.getId());
            return active == null
                    ? "库存操作状态需要核对后再重试"
                    : "库存信息正在处理中或等待核对";
        }
        List<com.machining.quote.entity.OrderDeliveryTicketAllocation> allocations = allocationMapper.selectByTicketId(ticket.getId());
        if (allocations != null && allocations.stream().anyMatch(this::hasStartedMesProduction)) {
            return "MES生产已开始或完成，不能再获取库存信息";
        }
        return null;
    }

    private boolean hasStartedMesProduction(com.machining.quote.entity.OrderDeliveryTicketAllocation allocation) {
        if (allocation == null
                || !"PRODUCTION_REQUIRED".equalsIgnoreCase(trim(allocation.getAllocationType()))
                || !"ACTIVE".equalsIgnoreCase(trim(allocation.getStatus()))) {
            return false;
        }
        String status = trim(allocation.getMesProductionStatus());
        return "PROCESSING".equalsIgnoreCase(status)
                || "SUBMITTING".equalsIgnoreCase(status)
                || "RESULT_UNKNOWN".equalsIgnoreCase(status)
                || "SUCCESS".equalsIgnoreCase(status);
    }

    private QueryWrapper<OrderDeliveryTicket> ticketQuery(Long salesOrderId, List<Integer> linkedSourceTicketIds) {
        QueryWrapper<OrderDeliveryTicket> query = new QueryWrapper<>();
        if (linkedSourceTicketIds == null || linkedSourceTicketIds.isEmpty()) {
            return query.eq("source_type", ReleaseSourceType.SALES_ORDER.name())
                    .eq("source_order_id", salesOrderId);
        }
        return query.and(wrapper -> wrapper
                .eq("source_type", ReleaseSourceType.SALES_ORDER.name())
                .eq("source_order_id", salesOrderId)
                .or()
                .in("id", linkedSourceTicketIds));
    }

    private List<Integer> linkedSourceTicketIds(Long salesOrderId) {
        QueryWrapper<SalesOrderItem> query = new QueryWrapper<>();
        query.select("source_ticket_id")
                .eq("sales_order_id", salesOrderId)
                .eq("source_type", ReleaseSourceType.RFQ.name())
                .isNotNull("source_ticket_id");
        List<SalesOrderItem> items = salesOrderItemMapper.selectList(query);
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream()
                .map(SalesOrderItem::getSourceTicketId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
    }

    private String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
