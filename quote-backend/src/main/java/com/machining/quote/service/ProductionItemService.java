package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.dto.CustomerOptionDTO;
import com.machining.quote.dto.LockedItemDTO;
import com.machining.quote.dto.LockedItemPageDTO;
import com.machining.quote.dto.ProductionItemQueryReq;
import com.machining.quote.mapper.OrderDeliveryTicketAllocationMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class ProductionItemService {
    private static final String PRODUCTION_REQUIRED_TYPE = "PRODUCTION_REQUIRED";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_FAILED = "FAILED";

    @Autowired
    private OrderDeliveryTicketAllocationMapper allocationMapper;
    @Autowired
    private DataScopeService dataScopeService;
    @Autowired(required = false)
    private CustomerMasterService customerMasterService;

    public LockedItemPageDTO listProductionItems(ProductionItemQueryReq req) {
        ProductionItemQueryReq effectiveReq = req == null ? new ProductionItemQueryReq() : req;
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

    public List<String> listCustomers() {
        if (customerMasterService != null) {
            return customerMasterService.listCustomerNamesForCurrentScope();
        }
        QueryWrapper<LockedItemDTO> query = new QueryWrapper<>();
        query.eq("alloc.allocation_type", PRODUCTION_REQUIRED_TYPE);
        applyProductionActiveScope(query);
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

    private QueryWrapper<LockedItemDTO> buildQuery(ProductionItemQueryReq req) {
        QueryWrapper<LockedItemDTO> query = new QueryWrapper<>();
        query.eq("alloc.allocation_type", PRODUCTION_REQUIRED_TYPE);
        applyCustomerFilter(query, req.getCustomerCode(), req.getCustomerName());
        applyLike(query, "alloc.rfq_no", req.getRfqNo());
        applyLike(query, "alloc.part_no", req.getPartNo());
        applyEquals(query, "alloc.version", req.getVersion());
        applyLike(query, "alloc.material_no", req.getMaterialNo());
        applyLike(query, "alloc.internal_project_no", req.getInternalProjectNo());
        applyLike(query, "alloc.customer_order_no", req.getCustomerOrderNo());
        applyLike(query, "alloc.customer_project_no", req.getCustomerProjectNo());
        applyEquals(query, "alloc.item_type", req.getItemType());
        applyProductionStatusScope(query, req.getMesProductionStatus());
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

    private void applyProductionStatusScope(QueryWrapper<LockedItemDTO> query, String status) {
        String normalized = normalizeUpper(status);
        if (!hasText(normalized)) {
            applyProductionActiveScope(query);
            return;
        }
        switch (normalized) {
            case STATUS_PENDING -> query.apply("COALESCE(NULLIF(TRIM(alloc.mes_production_status), ''), 'PENDING') = 'PENDING'");
            case STATUS_PROCESSING -> query.eq("alloc.mes_production_status", STATUS_PROCESSING);
            case STATUS_FAILED -> query.eq("alloc.mes_production_status", STATUS_FAILED);
            default -> {
                applyProductionActiveScope(query);
                query.eq("alloc.mes_production_status", normalized);
            }
        }
    }

    private void applyProductionActiveScope(QueryWrapper<LockedItemDTO> query) {
        query.apply("COALESCE(NULLIF(TRIM(alloc.mes_production_status), ''), 'PENDING') <> 'SUCCESS'");
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
