package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.dto.CustomerOptionDTO;
import com.machining.quote.dto.DeliveredItemDTO;
import com.machining.quote.dto.DeliveredItemQueryReq;
import com.machining.quote.dto.PageResult;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DeliveredItemService {
    private static final String FLOW_SCOPE_ACTIVE = "ACTIVE";
    private static final String FLOW_SCOPE_COMPLETED = "COMPLETED";
    private static final String FLOW_SCOPE_ALL = "ALL";

    @Autowired
    private OrderDeliveryTicketMapper ticketMapper;
    @Autowired
    private OrderRfqMapper orderRfqMapper;
    @Autowired
    private DataScopeService dataScopeService;
    @Autowired(required = false)
    private CustomerMasterService customerMasterService;

    public PageResult<DeliveredItemDTO> listTickets(DeliveredItemQueryReq req) {
        DeliveredItemQueryReq effectiveReq = req == null ? new DeliveredItemQueryReq() : req;
        int safePage = normalizePage(effectiveReq.getPage());
        int safePageSize = normalizePageSize(effectiveReq.getPageSize());
        QueryWrapper<DeliveredItemDTO> query = buildQuery(effectiveReq);

        long total = ticketMapper.countDeliveredItems(query);
        if (total == 0) {
            return PageResult.of(List.of(), 0, safePage, safePageSize);
        }
        List<DeliveredItemDTO> records = ticketMapper.selectDeliveredItems(
                query, safePageSize, (safePage - 1L) * safePageSize);
        return PageResult.of(records, total, safePage, safePageSize);
    }

    public DeliveredItemDTO getTicket(Integer ticketId) {
        if (ticketId == null) {
            throw new RuntimeException("Delivered item ID is required");
        }
        QueryWrapper<DeliveredItemDTO> query = new QueryWrapper<>();
        query.eq("ticket.id", ticketId);
        DeliveredItemDTO item = ticketMapper.selectDeliveredItem(query);
        if (item == null) {
            throw new RuntimeException("Delivered item does not exist");
        }
        dataScopeService.assertCanAccessDeliveryTicket(ticketId);
        return item;
    }

    public List<String> listCustomers() {
        if (customerMasterService != null) {
            return customerMasterService.listCustomerNamesForCurrentScope();
        }
        QueryWrapper<DeliveredItemDTO> query = new QueryWrapper<>();
        query.isNotNull("COALESCE(ticket.customer_name, rfq.customer_name)");
        query.ne("COALESCE(ticket.customer_name, rfq.customer_name)", "");
        dataScopeService.applyDeliveryTicketScope(query, "ticket");
        return ticketMapper.selectDeliveredItemCustomers(query).stream()
                .filter(this::hasText)
                .toList();
    }

    public List<CustomerOptionDTO> listCustomerOptions(String field, String keyword) {
        if (customerMasterService != null) {
            return customerMasterService.listCustomerOptions(field, keyword);
        }
        return listCustomers().stream()
                .filter(customerName -> matchesCustomerKeyword(customerName, field, keyword))
                .map(this::toCustomerOption)
                .toList();
    }

    private QueryWrapper<DeliveredItemDTO> buildQuery(DeliveredItemQueryReq req) {
        QueryWrapper<DeliveredItemDTO> query = new QueryWrapper<>();
        applyEquals(query, "ticket.customer_name", req.getCustomerName());
        applyLike(query, "ticket.rfq_no", req.getRfqNo());
        applyLike(query, "ticket.part_no", req.getPartNo());
        applyEquals(query, "ticket.version", req.getVersion());
        applyLike(query, "ticket.material_no", req.getMaterialNo());
        applyLike(query, "ticket.internal_project_no", req.getInternalProjectNo());
        applyLike(query, "ticket.customer_order_no", req.getCustomerOrderNo());
        applyLike(query, "ticket.customer_project_no", req.getCustomerProjectNo());
        applyEquals(query, "ticket.mes_sync_status", req.getMesSyncStatus());
        applyEquals(query, "ticket.wms_allocate_status", req.getWmsAllocateStatus());
        if (req.getDateFrom() != null) {
            query.ge("ticket.delivery_date", req.getDateFrom());
        }
        if (req.getDateTo() != null) {
            query.le("ticket.delivery_date", req.getDateTo());
        }
        applyFlowScope(query, req.getFlowScope());
        dataScopeService.applyDeliveryTicketScope(query, "ticket");
        return query;
    }

    private void applyFlowScope(QueryWrapper<DeliveredItemDTO> query, String flowScope) {
        String scope = normalizeFlowScope(flowScope);
        if (FLOW_SCOPE_ALL.equals(scope)) {
            return;
        }
        if (FLOW_SCOPE_COMPLETED.equals(scope)) {
            query.apply("NULLIF(BTRIM(ticket.material_no), '') IS NOT NULL");
            query.eq("ticket.mes_sync_status", "SUCCESS");
            query.eq("ticket.wms_allocate_status", "SUCCESS");
            query.apply("EXISTS (SELECT 1 FROM order_delivery_ticket_allocation alloc_done WHERE alloc_done.ticket_id = ticket.id)");
            return;
        }
        query.and(active -> active
                .apply("NULLIF(BTRIM(ticket.material_no), '') IS NULL")
                .or()
                .apply("COALESCE(ticket.mes_sync_status, 'PENDING') <> 'SUCCESS'")
                .or()
                .apply("COALESCE(ticket.wms_allocate_status, 'PENDING') <> 'SUCCESS'")
                .or()
                .apply("NOT EXISTS (SELECT 1 FROM order_delivery_ticket_allocation alloc_done WHERE alloc_done.ticket_id = ticket.id)"));
    }

    private String normalizeFlowScope(String flowScope) {
        if (!hasText(flowScope)) {
            return FLOW_SCOPE_ACTIVE;
        }
        String normalized = flowScope.trim().toUpperCase();
        if (FLOW_SCOPE_COMPLETED.equals(normalized) || FLOW_SCOPE_ALL.equals(normalized)) {
            return normalized;
        }
        return FLOW_SCOPE_ACTIVE;
    }

    private void applyLike(QueryWrapper<DeliveredItemDTO> query, String column, String value) {
        if (hasText(value)) {
            query.like(column, value.trim());
        }
    }

    private void applyEquals(QueryWrapper<DeliveredItemDTO> query, String column, String value) {
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

    private boolean matchesCustomerKeyword(String customerName, String field, String keyword) {
        if (!hasText(keyword)) {
            return true;
        }
        String normalizedField = field == null ? "all" : field.replace("_", "").replace("-", "").trim().toLowerCase();
        if (!"all".equals(normalizedField) && !"name".equals(normalizedField)) {
            return false;
        }
        return customerName != null && customerName.toLowerCase().contains(keyword.trim().toLowerCase());
    }

    private CustomerOptionDTO toCustomerOption(String customerName) {
        CustomerOptionDTO option = new CustomerOptionDTO();
        option.setCustomerCode(customerName);
        option.setCustomerShortName(customerName);
        option.setCustomerName(customerName);
        return option;
    }
}
