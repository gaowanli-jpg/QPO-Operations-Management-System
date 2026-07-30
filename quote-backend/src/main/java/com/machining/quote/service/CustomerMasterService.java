package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.dto.CustomerMasterDTO;
import com.machining.quote.dto.CustomerMasterSaveReq;
import com.machining.quote.dto.CustomerOptionDTO;
import com.machining.quote.dto.PageResult;
import com.machining.quote.entity.CustomerMaster;
import com.machining.quote.mapper.CustomerMasterMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CustomerMasterService {
    private static final Logger log = LoggerFactory.getLogger(CustomerMasterService.class);

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
        query.orderByAsc("customer_name").orderByAsc("id");
        query.last("LIMIT " + safePageSize + " OFFSET " + ((safePage - 1L) * safePageSize));
        return PageResult.of(customerMapper.selectList(query).stream().map(this::toDto).toList(), total, safePage, safePageSize);
    }

    public List<CustomerOptionDTO> listOptions(String field, String keyword) {
        QueryWrapper<CustomerMaster> query = new QueryWrapper<>();
        query.eq("enabled", true);
        applyKeyword(query, field, keyword);
        query.orderByAsc("customer_name").orderByAsc("id");
        query.last("LIMIT 200");
        return customerMapper.selectList(query).stream().map(this::toOption).toList();
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
        if (dataScopeService != null) {
            return dataScopeService.listCustomerOptions();
        }
        return listCustomerNames();
    }

    @Transactional
    public CustomerMasterDTO saveCustomer(CustomerMasterSaveReq req) {
        String customerCode = requiredText(req == null ? null : req.getCustomerCode(), "客户编码");
        String customerShortName = requiredText(req == null ? null : req.getCustomerShortName(), "客户简称");
        String customerName = requiredText(req == null ? null : req.getCustomerName(), "客户名称");
        Long id = req == null ? null : req.getId();

        assertUnique("customer_code", customerCode, id, "客户编码已存在");
        assertUnique("customer_short_name", customerShortName, id, "客户简称已存在");
        assertUnique("customer_name", customerName, id, "客户名称已存在");

        boolean creating = id == null;
        CustomerMaster customer = creating ? new CustomerMaster() : requireCustomer(id);
        Map<String, Object> before = creating ? null : snapshot(customer);
        LocalDateTime now = LocalDateTime.now();
        customer.setCustomerCode(customerCode);
        customer.setCustomerShortName(customerShortName);
        customer.setCustomerName(customerName);
        customer.setEnabled(req == null || req.getEnabled() == null || req.getEnabled());
        customer.setUpdatedAt(now);

        if (creating) {
            customer.setCreatedAt(now);
            customerMapper.insert(customer);
            auditService.record("CUSTOMER_MASTER_CREATE", "customer_master", customer.getId(), null, snapshot(customer));
            log.info("Customer master created customerId={} customerCode={}", customer.getId(), customer.getCustomerCode());
        } else {
            customerMapper.updateById(customer);
            auditService.record("CUSTOMER_MASTER_UPDATE", "customer_master", customer.getId(), before, snapshot(customer));
            log.info("Customer master updated customerId={} customerCode={}", customer.getId(), customer.getCustomerCode());
        }
        return toDto(customer);
    }

    @Transactional
    public void deleteCustomer(Long id) {
        CustomerMaster customer = requireCustomer(id);
        if (customerMapper.countOrderReferences(customer.getCustomerName()) > 0) {
            log.warn("Customer master delete rejected because orders reference it customerId={} customerName={}",
                    id, customer.getCustomerName());
            throw new RuntimeException("客户已被订单引用，不能删除");
        }
        if (customerMapper.countDataScopeReferences(customer.getCustomerName()) > 0) {
            log.warn("Customer master delete rejected because data scopes reference it customerId={} customerName={}",
                    id, customer.getCustomerName());
            throw new RuntimeException("客户已被数据权限引用，不能删除");
        }
        Map<String, Object> before = snapshot(customer);
        customerMapper.deleteById(id);
        auditService.record("CUSTOMER_MASTER_DELETE", "customer_master", id, before, null);
        log.info("Customer master deleted customerId={} customerCode={}", id, customer.getCustomerCode());
    }

    private CustomerMaster requireCustomer(Long id) {
        if (id == null) {
            throw new RuntimeException("客户 ID 不能为空");
        }
        CustomerMaster customer = customerMapper.selectById(id);
        if (customer == null) {
            throw new RuntimeException("客户资料不存在");
        }
        return customer;
    }

    private void assertUnique(String column, String value, Long currentId, String message) {
        QueryWrapper<CustomerMaster> query = new QueryWrapper<>();
        query.eq(column, value);
        if (currentId != null) {
            query.ne("id", currentId);
        }
        Long count = customerMapper.selectCount(query);
        if (count != null && count > 0) {
            throw new RuntimeException(message);
        }
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
        dto.setCustomerCode(customer.getCustomerCode());
        dto.setCustomerShortName(customer.getCustomerShortName());
        dto.setCustomerName(customer.getCustomerName());
        dto.setEnabled(customer.getEnabled());
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

    private Map<String, Object> snapshot(CustomerMaster customer) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("customerId", customer.getId());
        row.put("customerCode", customer.getCustomerCode());
        row.put("customerShortName", customer.getCustomerShortName());
        row.put("customerName", customer.getCustomerName());
        row.put("enabled", customer.getEnabled());
        return row;
    }

    private String requiredText(String value, String label) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw new RuntimeException(label + "不能为空");
        }
        return trimmed;
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
