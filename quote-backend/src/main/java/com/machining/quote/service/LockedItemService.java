package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.dto.CustomerOptionDTO;
import com.machining.quote.dto.LockedItemDTO;
import com.machining.quote.dto.LockedItemPageDTO;
import com.machining.quote.dto.LockedItemQueryReq;
import com.machining.quote.mapper.OrderDeliveryTicketAllocationMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class LockedItemService {
    private static final int TICKET_ALLOCATION_LIMIT = 1000;

    @Autowired
    private OrderDeliveryTicketAllocationMapper allocationMapper;
    @Autowired
    private DataScopeService dataScopeService;
    @Autowired(required = false)
    private CustomerMasterService customerMasterService;

    public LockedItemPageDTO listAllocations(LockedItemQueryReq req) {
        LockedItemQueryReq effectiveReq = req == null ? new LockedItemQueryReq() : req;
        int safePage = normalizePage(effectiveReq.getPage());
        int safePageSize = normalizePageSize(effectiveReq.getPageSize());
        QueryWrapper<LockedItemDTO> query = buildQuery(effectiveReq);

        long total = allocationMapper.countLockedItems(query);
        if (total == 0) {
            return LockedItemPageDTO.of(List.of(), 0, safePage, safePageSize);
        }
        List<LockedItemDTO> records = allocationMapper.selectLockedItems(
                query, safePageSize, (safePage - 1L) * safePageSize);
        return LockedItemPageDTO.of(records, total, safePage, safePageSize);
    }

    public List<LockedItemDTO> listTicketAllocations(Integer ticketId) {
        if (ticketId == null) {
            throw new RuntimeException("Ticket ID is required");
        }
        LockedItemQueryReq req = new LockedItemQueryReq();
        QueryWrapper<LockedItemDTO> query = buildQuery(req);
        query.eq("alloc.ticket_id", ticketId);
        return allocationMapper.selectLockedItems(query, TICKET_ALLOCATION_LIMIT, 0);
    }

    public List<String> listCustomers() {
        if (customerMasterService != null) {
            return customerMasterService.listCustomerNamesForCurrentScope();
        }
        QueryWrapper<LockedItemDTO> query = new QueryWrapper<>();
        query.isNotNull("COALESCE(alloc.customer_name, ticket.customer_name, rfq.customer_name)");
        query.ne("COALESCE(alloc.customer_name, ticket.customer_name, rfq.customer_name)", "");
        dataScopeService.applyRfqScope(query, "alloc.rfq_id");
        return allocationMapper.selectLockedItemCustomers(query).stream()
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

    private QueryWrapper<LockedItemDTO> buildQuery(LockedItemQueryReq req) {
        QueryWrapper<LockedItemDTO> query = new QueryWrapper<>();
        applyEquals(query, "alloc.customer_name", req.getCustomerName());
        applyLike(query, "alloc.rfq_no", req.getRfqNo());
        applyLike(query, "alloc.part_no", req.getPartNo());
        applyEquals(query, "alloc.version", req.getVersion());
        applyLike(query, "alloc.material_no", req.getMaterialNo());
        applyLike(query, "alloc.internal_project_no", req.getInternalProjectNo());
        applyLike(query, "alloc.customer_order_no", req.getCustomerOrderNo());
        applyLike(query, "alloc.customer_project_no", req.getCustomerProjectNo());
        applyEquals(query, "alloc.item_type", req.getItemType());
        applyEquals(query, "alloc.allocation_type", normalizeUpper(req.getAllocationType()));
        applyLike(query, "alloc.wms_production_order_no", req.getWmsProductionOrderNo());
        if (req.getDateFrom() != null) {
            query.ge("alloc.delivery_date", req.getDateFrom());
        }
        if (req.getDateTo() != null) {
            query.le("alloc.delivery_date", req.getDateTo());
        }
        dataScopeService.applyRfqScope(query, "alloc.rfq_id");
        return query;
    }

    private void applyLike(QueryWrapper<LockedItemDTO> query, String column, String value) {
        if (hasText(value)) {
            query.like(column, value.trim());
        }
    }

    private void applyEquals(QueryWrapper<LockedItemDTO> query, String column, String value) {
        if (hasText(value)) {
            query.eq(column, value.trim());
        }
    }

    private String normalizeUpper(String value) {
        return hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : value;
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
        String normalizedField = field == null ? "all" : field.replace("_", "").replace("-", "").trim().toLowerCase(Locale.ROOT);
        if (!"all".equals(normalizedField) && !"name".equals(normalizedField)) {
            return false;
        }
        return customerName != null && customerName.toLowerCase(Locale.ROOT).contains(keyword.trim().toLowerCase(Locale.ROOT));
    }

    private CustomerOptionDTO toCustomerOption(String customerName) {
        CustomerOptionDTO option = new CustomerOptionDTO();
        option.setCustomerCode(customerName);
        option.setCustomerShortName(customerName);
        option.setCustomerName(customerName);
        return option;
    }
}
