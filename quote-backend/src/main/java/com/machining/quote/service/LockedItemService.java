package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.dto.CustomerOptionDTO;
import com.machining.quote.dto.LockedItemDTO;
import com.machining.quote.dto.LockedItemPageDTO;
import com.machining.quote.dto.LockedItemQueryReq;
import com.machining.quote.dto.PageResult;
import com.machining.quote.mapper.OrderDeliveryTicketAllocationMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class LockedItemService {
    private static final int TICKET_ALLOCATION_LIMIT = 1000;
    private static final Set<String> LOCKED_STOCK_TYPES = Set.of("IN_STOCK_LOCK", "IN_PRODUCTION_LOCK");

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
        applyLockedStockScope(query);
        applyCustomerPresentScope(query);
        dataScopeService.applyDeliveryTicketScope(query, "ticket");
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

    public PageResult<CustomerOptionDTO> pageCustomerOptions(String field, String keyword, Integer page, Integer pageSize) {
        if (customerMasterService != null) {
            return customerMasterService.pageCustomerOptions(field, keyword, page, pageSize);
        }
        List<CustomerOptionDTO> rows = listCustomers().stream()
                .filter(customerName -> matchesCustomerKeyword(customerName, field, keyword))
                .map(this::toCustomerOption)
                .toList();
        return pageRows(rows, page, pageSize);
    }

    private QueryWrapper<LockedItemDTO> buildQuery(LockedItemQueryReq req) {
        QueryWrapper<LockedItemDTO> query = new QueryWrapper<>();
        applyLockedStockScope(query);
        applyCustomerFilter(query, req.getCustomerCode(), req.getCustomerName());
        applyLike(query, "alloc.rfq_no", req.getRfqNo());
        applyLike(query, "alloc.part_no", req.getPartNo());
        applyEquals(query, "alloc.version", req.getVersion());
        applyLike(query, "alloc.material_no", req.getMaterialNo());
        applyLike(query, "alloc.internal_project_no", req.getInternalProjectNo());
        applyLike(query, "alloc.customer_order_no", req.getCustomerOrderNo());
        applyLike(query, "alloc.customer_project_no", req.getCustomerProjectNo());
        applyEquals(query, "alloc.item_type", req.getItemType());
        applyLockedAllocationType(query, req.getAllocationType());
        applyLike(query, "alloc.wms_production_order_no", req.getWmsProductionOrderNo());
        if (req.getDateFrom() != null) {
            query.ge("alloc.delivery_date", req.getDateFrom());
        }
        if (req.getDateTo() != null) {
            query.le("alloc.delivery_date", req.getDateTo());
        }
        dataScopeService.applyDeliveryTicketScope(query, "ticket");
        return query;
    }

    private void applyCustomerFilter(QueryWrapper<LockedItemDTO> query, String customerCode, String customerName) {
        if (hasText(customerCode)) {
            String code = customerCode.trim();
            query.apply("(rfq.customer_code = {0} "
                    + "OR (alloc.source_type = 'SALES_ORDER' AND alloc.source_order_id IN "
                    + "(SELECT so.id FROM sales_order_header so WHERE so.customer_code = {1})) "
                    + "OR (ticket.source_type = 'SALES_ORDER' AND ticket.source_order_id IN "
                    + "(SELECT so.id FROM sales_order_header so WHERE so.customer_code = {2})))", code, code, code);
            return;
        }
        applyEquals(query, "alloc.customer_name", customerName);
    }

    private void applyLockedStockScope(QueryWrapper<LockedItemDTO> query) {
        query.in("alloc.allocation_type", LOCKED_STOCK_TYPES);
    }

    private void applyLockedAllocationType(QueryWrapper<LockedItemDTO> query, String allocationType) {
        String normalized = normalizeUpper(allocationType);
        if (!hasText(normalized)) {
            return;
        }
        if (LOCKED_STOCK_TYPES.contains(normalized)) {
            query.eq("alloc.allocation_type", normalized);
        } else {
            query.apply("1 = 0");
        }
    }

    private void applyCustomerPresentScope(QueryWrapper<LockedItemDTO> query) {
        query.isNotNull("COALESCE(alloc.customer_name, ticket.customer_name, rfq.customer_name)");
        query.ne("COALESCE(alloc.customer_name, ticket.customer_name, rfq.customer_name)", "");
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

    private <T> PageResult<T> pageRows(List<T> allRows, Integer page, Integer pageSize) {
        List<T> rows = allRows == null ? List.of() : allRows;
        int safePage = normalizePage(page);
        int safePageSize = normalizePageSize(pageSize);
        int fromIndex = (int) Math.min((long) (safePage - 1) * safePageSize, rows.size());
        int toIndex = Math.min(fromIndex + safePageSize, rows.size());
        return PageResult.of(rows.subList(fromIndex, toIndex), rows.size(), safePage, safePageSize);
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
