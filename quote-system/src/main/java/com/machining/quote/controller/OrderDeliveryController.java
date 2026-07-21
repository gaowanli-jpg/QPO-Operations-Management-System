package com.machining.quote.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.machining.quote.config.RequirePermission;
import com.machining.quote.dto.PageResult;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.service.DataScopeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/order-delivery")
public class OrderDeliveryController {
    @Autowired
    private OrderDeliveryTicketMapper ticketMapper;
    @Autowired
    private OrderRfqMapper orderRfqMapper;
    @Autowired
    private DataScopeService dataScopeService;

    @GetMapping("/tickets")
    @RequirePermission("market:delivered-item:view")
    public PageResult<OrderDeliveryTicket> listTickets(
            @RequestParam(value = "customerName", required = false) String customerName,
            @RequestParam(value = "rfqNo", required = false) String rfqNo,
            @RequestParam(value = "partNo", required = false) String partNo,
            @RequestParam(value = "version", required = false) String version,
            @RequestParam(value = "materialNo", required = false) String materialNo,
            @RequestParam(value = "internalProjectNo", required = false) String internalProjectNo,
            @RequestParam(value = "mesSyncStatus", required = false) String mesSyncStatus,
            @RequestParam(value = "wmsAllocateStatus", required = false) String wmsAllocateStatus,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        int safePage = normalizePage(page);
        int safePageSize = normalizePageSize(pageSize);
        QueryWrapper<OrderDeliveryTicket> query = new QueryWrapper<>();
        applyLike(query, "part_no", partNo);
        applyEquals(query, "version", version);
        applyLike(query, "material_no", materialNo);
        applyLike(query, "internal_project_no", internalProjectNo);
        applyEquals(query, "mes_sync_status", mesSyncStatus);
        applyEquals(query, "wms_allocate_status", wmsAllocateStatus);
        if (hasText(customerName)) {
            query.apply("rfq_id IN (SELECT id FROM order_rfq WHERE customer_name = {0})", customerName.trim());
        }
        if (hasText(rfqNo)) {
            query.apply("rfq_id IN (SELECT id FROM order_rfq WHERE rfq_no ILIKE {0})", "%" + rfqNo.trim() + "%");
        }
        dataScopeService.applyRfqScope(query, "rfq_id");
        query.orderByDesc("id");

        Page<OrderDeliveryTicket> result = ticketMapper.selectPage(Page.of(safePage, safePageSize), query);
        return PageResult.of(result.getRecords(), result.getTotal(), safePage, safePageSize);
    }

    @GetMapping("/tickets/{ticketId}")
    @RequirePermission("market:delivered-item:view")
    public OrderDeliveryTicket getTicket(@PathVariable Integer ticketId) {
        OrderDeliveryTicket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new RuntimeException("Delivered item does not exist");
        }
        dataScopeService.assertCanAccessOrder(ticket.getRfqId(), orderRfqMapper);
        return ticket;
    }

    private void applyLike(QueryWrapper<OrderDeliveryTicket> query, String column, String value) {
        if (hasText(value)) {
            query.like(column, value.trim());
        }
    }

    private void applyEquals(QueryWrapper<OrderDeliveryTicket> query, String column, String value) {
        if (hasText(value)) {
            query.eq(column, value.trim());
        }
    }

    private int normalizePage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 20;
        }
        return Math.min(pageSize, 100);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
