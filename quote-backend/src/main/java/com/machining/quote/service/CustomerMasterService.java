package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.dto.CustomerMasterDTO;
import com.machining.quote.dto.CustomerOptionDTO;
import com.machining.quote.dto.PageResult;
import com.machining.quote.entity.CustomerMaster;
import com.machining.quote.mapper.CustomerMasterMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CustomerMasterService {
    @Autowired
    private CustomerMasterMapper customerMapper;
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

    public List<CustomerOptionDTO> listCustomerOptions(String field, String keyword) {
        if (dataScopeService != null) {
            return dataScopeService.listCustomerOptions(field, keyword);
        }
        return listOptions(field, keyword);
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

    private void applyKeyword(QueryWrapper<CustomerMaster> query, String field, String keyword) {
        if (!hasText(keyword)) {
            return;
        }
        String value = keyword.trim();
        switch (normalizeField(field)) {
            case "code" -> query.like("customer_code", value);
            case "shortname" -> query.like("customer_short_name", value);
            case "name" -> query.like("customer_name", value);
            default -> query.and(wrapper -> wrapper
                    .like("customer_code", value)
                    .or()
                    .like("customer_short_name", value)
                    .or()
                    .like("customer_name", value));
        }
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
}
