package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.config.AuthContext;
import com.machining.quote.dto.CustomerMasterDTO;
import com.machining.quote.dto.CustomerOptionDTO;
import com.machining.quote.dto.CustomerSupplierCodeUpdateReq;
import com.machining.quote.dto.PageResult;
import com.machining.quote.entity.CustomerMaster;
import com.machining.quote.mapper.CustomerMasterMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class CustomerMasterService {
    @Autowired
    private CustomerMasterMapper customerMapper;
    @Autowired
    private AuditService auditService;
    @Autowired(required = false)
    private DataScopeService dataScopeService;

    public PageResult<CustomerMasterDTO> pageCustomers(String field, String keyword, Integer page, Integer pageSize) {
        int safePage = normalizePage(page);
        int safePageSize = normalizePageSize(pageSize);
        QueryWrapper<CustomerMaster> query = new QueryWrapper<>();
        applyKeyword(query, field, keyword);
        Long total = customerMapper.selectCount(query);
        if (total == null || total == 0) {
            return PageResult.of(List.of(), 0, safePage, safePageSize);
        }
        query.orderByAsc("customer_code").orderByAsc("id");
        query.last("LIMIT " + safePageSize + " OFFSET " + ((safePage - 1L) * safePageSize));
        return PageResult.of(customerMapper.selectList(query).stream().map(this::toDto).toList(), total, safePage, safePageSize);
    }

    public List<CustomerOptionDTO> listOptions(String field, String keyword) {
        QueryWrapper<CustomerMaster> query = new QueryWrapper<>();
        query.eq("enabled", true);
        applyKeyword(query, field, keyword);
        query.orderByAsc("customer_code").orderByAsc("id");
        query.last("LIMIT 200");
        return customerMapper.selectList(query).stream().map(this::toOption).toList();
    }

    public PageResult<CustomerOptionDTO> pageOptions(String field, String keyword, Integer page, Integer pageSize) {
        int safePage = normalizePage(page);
        int safePageSize = normalizePageSize(pageSize);
        QueryWrapper<CustomerMaster> query = new QueryWrapper<>();
        query.eq("enabled", true);
        applyKeyword(query, field, keyword);
        Long total = customerMapper.selectCount(query);
        if (total == null || total == 0) {
            return PageResult.of(List.of(), 0, safePage, safePageSize);
        }
        query.orderByAsc("customer_code").orderByAsc("id");
        query.last("LIMIT " + safePageSize + " OFFSET " + ((safePage - 1L) * safePageSize));
        return PageResult.of(customerMapper.selectList(query).stream().map(this::toOption).toList(), total, safePage, safePageSize);
    }

    public List<CustomerOptionDTO> listCustomerOptions(String field, String keyword) {
        if (dataScopeService != null) {
            return dataScopeService.listCustomerOptions(field, keyword);
        }
        return listOptions(field, keyword);
    }

    public PageResult<CustomerOptionDTO> pageCustomerOptions(String field, String keyword, Integer page, Integer pageSize) {
        if (dataScopeService != null) {
            return dataScopeService.pageCustomerOptions(field, keyword, page, pageSize);
        }
        return pageOptions(field, keyword, page, pageSize);
    }

    public List<String> listCustomerNames() {
        QueryWrapper<CustomerMaster> query = new QueryWrapper<>();
        query.select("customer_name");
        query.eq("enabled", true);
        query.isNotNull("customer_name");
        query.ne("customer_name", "");
        query.orderByAsc("customer_name").orderByAsc("id");
        return customerMapper.selectList(query).stream()
                .map(CustomerMaster::getCustomerName)
                .filter(this::hasText)
                .distinct()
                .toList();
    }

    public List<String> listCustomerNamesForCurrentScope() {
        return listCustomerOptions("all", null).stream()
                .map(CustomerOptionDTO::getCustomerName)
                .filter(this::hasText)
                .distinct()
                .toList();
    }

    @Transactional
    public CustomerMasterDTO updateSupplierCode(Long id, CustomerSupplierCodeUpdateReq req) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("客户ID不能为空");
        }
        String supplierCode = normalizeSupplierCode(req == null ? null : req.getSupplierCode());
        CustomerMaster customer = customerMapper.selectById(id);
        if (customer == null) {
            throw new IllegalArgumentException("客户不存在");
        }
        String beforeSupplierCode = customer.getSupplierCode();
        if (Objects.equals(beforeSupplierCode, supplierCode)) {
            return toDto(customer);
        }

        customer.setSupplierCode(supplierCode);
        customer.setUpdatedBy(currentUserId());
        customer.setUpdatedAt(LocalDateTime.now());
        customerMapper.updateById(customer);

        auditService.record(
                "CUSTOMER_SUPPLIER_CODE_UPDATE",
                "customer_master",
                customer.getId(),
                supplierAuditSummary(customer, beforeSupplierCode),
                supplierAuditSummary(customer, supplierCode),
                "SYSTEM",
                "SUCCESS",
                "维护客户供应商代码",
                null
        );
        return toDto(customer);
    }

    private void applyKeyword(QueryWrapper<CustomerMaster> query, String field, String keyword) {
        if (!hasText(keyword)) {
            return;
        }
        String value = keyword.trim();
        switch (normalizeField(field)) {
            case "code" -> query.like("customer_code", value);
            case "shortname" -> query.like("customer_short_name", value);
            case "name" -> query.like("customer_name", value);
            case "suppliercode" -> query.like("supplier_code", value);
            default -> query.and(wrapper -> wrapper
                    .like("customer_code", value)
                    .or()
                    .like("customer_short_name", value)
                    .or()
                    .like("customer_name", value)
                    .or()
                    .like("supplier_code", value));
        }
    }

    private String normalizeSupplierCode(String value) {
        String trimmed = trimToNull(value);
        if (trimmed != null && trimmed.length() > 64) {
            throw new IllegalArgumentException("供应商代码不能超过64个字符");
        }
        return trimmed;
    }

    private Map<String, Object> supplierAuditSummary(CustomerMaster customer, String supplierCode) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("customerId", customer.getId());
        summary.put("customerCode", customer.getCustomerCode());
        summary.put("customerName", customer.getCustomerName());
        summary.put("supplierCode", supplierCode);
        return summary;
    }

    private Long currentUserId() {
        AuthContext.SessionUser user = AuthContext.get();
        return user == null || user.id() == null ? null : user.id().longValue();
    }

    private String normalizeField(String field) {
        return field == null ? "all" : field.replace("_", "").replace("-", "").trim().toLowerCase();
    }

    private CustomerMasterDTO toDto(CustomerMaster customer) {
        CustomerMasterDTO dto = new CustomerMasterDTO();
        dto.setId(customer.getId());
        dto.setU9OrgCode(customer.getU9OrgCode());
        dto.setU9CustomerId(customer.getU9CustomerId());
        dto.setCustomerCode(customer.getCustomerCode());
        dto.setCustomerShortName(customer.getCustomerShortName());
        dto.setCustomerName(customer.getCustomerName());
        dto.setSupplierCode(customer.getSupplierCode());
        dto.setEnabled(customer.getEnabled());
        dto.setLastSyncedAt(customer.getLastSyncedAt());
        dto.setCreatedAt(customer.getCreatedAt());
        dto.setUpdatedAt(customer.getUpdatedAt());
        return dto;
    }

    private CustomerOptionDTO toOption(CustomerMaster customer) {
        CustomerOptionDTO dto = new CustomerOptionDTO();
        dto.setId(customer.getId());
        dto.setCustomerCode(customer.getCustomerCode());
        dto.setCustomerShortName(customer.getCustomerShortName());
        dto.setCustomerName(customer.getCustomerName());
        return dto;
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

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
