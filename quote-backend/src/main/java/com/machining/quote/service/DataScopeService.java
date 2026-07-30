package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.config.AuthContext;
import com.machining.quote.dto.CustomerOptionDTO;
import com.machining.quote.entity.AuditLog;
import com.machining.quote.entity.CustomerMaster;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.entity.SystemRole;
import com.machining.quote.entity.SystemUser;
import com.machining.quote.mapper.CustomerMasterMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.mapper.SystemRoleDataScopeCustomerMapper;
import com.machining.quote.mapper.SystemRoleMapper;
import com.machining.quote.mapper.SystemUserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
public class DataScopeService {
    @Autowired(required = false)
    private SystemUserMapper systemUserMapper;
    @Autowired(required = false)
    private SystemRoleMapper roleMapper;
    @Autowired(required = false)
    private SystemRoleDataScopeCustomerMapper dataScopeCustomerMapper;
    @Autowired(required = false)
    private OrderRfqMapper orderRfqMapper;
    @Autowired(required = false)
    private CustomerMasterMapper customerMasterMapper;

    public void applyOrderScope(QueryWrapper<OrderRfq> query) {
        applyScopeSql(query, currentOrderScopeSql(""));
    }

    public void applyRfqScope(QueryWrapper<?> query, String rfqColumn) {
        String safeColumn = isBlank(rfqColumn) ? "rfq_id" : rfqColumn;
        ScopeSql scopeSql = currentOrderScopeSql("");
        if (scopeSql.allAccess()) {
            return;
        }
        if (scopeSql.noAccess()) {
            query.apply("1 = 0");
            return;
        }
        query.apply(safeColumn + " IN (SELECT id FROM order_rfq WHERE " + scopeSql.sql() + ")", scopeSql.params().toArray());
    }

    public void applyAuditScope(QueryWrapper<AuditLog> query) {
        DataScopeSnapshot snapshot = currentSnapshot();
        if (snapshot.noContext() || snapshot.superAdmin()) {
            return;
        }
        if (snapshot.noAccess()) {
            query.apply("1 = 0");
            return;
        }
        List<Object> params = new ArrayList<>();
        List<String> clauses = new ArrayList<>();
        clauses.add("(role IS NULL OR UPPER(role) NOT IN (" + placeholders(params, List.of("ADMIN", "SUPER_ADMIN")) + "))");
        if (snapshot.selfOnly()) {
            clauses.add("username = {" + params.size() + "}");
            params.add(snapshot.username());
        }
        query.apply("(" + String.join(" AND ", clauses) + ")", params.toArray());
    }

    public void assertCanAccessOrder(OrderRfq order) {
        if (order == null) {
            return;
        }
        DataScopeSnapshot snapshot = currentSnapshot();
        if (snapshot.noContext() || snapshot.allAccess()) {
            return;
        }
        if (matchesOrder(snapshot, order)) {
            return;
        }
        throw new RuntimeException("No data permission for order " + order.getId());
    }

    public void assertCanAccessOrder(Integer rfqId, OrderRfqMapper mapper) {
        OrderRfqMapper effectiveMapper = mapper == null ? orderRfqMapper : mapper;
        if (rfqId == null || effectiveMapper == null) {
            throw new RuntimeException("Order ID is required");
        }
        OrderRfq order = effectiveMapper.selectById(rfqId);
        if (order == null) {
            throw new RuntimeException("Order does not exist");
        }
        assertCanAccessOrder(order);
    }

    public void assignCurrentOwnership(OrderRfq order) {
        if (order == null) {
            return;
        }
        AuthContext.SessionUser current = AuthContext.get();
        if (current == null) {
            return;
        }
        SystemUser systemUser = loadSystemUser(current.id());
        if (order.getCreatedBy() == null) {
            order.setCreatedBy(current.id());
        }
        if (order.getOwnerUserId() == null) {
            order.setOwnerUserId(current.id());
        }
        if (order.getOwnerDeptId() == null && systemUser != null) {
            order.setOwnerDeptId(systemUser.getDeptId());
        }
    }

    public List<String> listCustomerOptions() {
        if (customerMasterMapper != null) {
            QueryWrapper<CustomerMaster> query = new QueryWrapper<>();
            query.select("customer_name");
            query.eq("enabled", true);
            query.isNotNull("customer_name");
            query.ne("customer_name", "");
            // Customer options now come from customer_master while preserving order data-scope filtering.
            applyCustomerMasterScope(query);
            query.orderByAsc("customer_name").orderByAsc("id");
            return customerMasterMapper.selectList(query).stream()
                    .map(CustomerMaster::getCustomerName)
                    .filter(value -> !isBlank(value))
                    .distinct()
                    .toList();
        }
        if (orderRfqMapper == null) {
            return List.of();
        }
        QueryWrapper<OrderRfq> query = new QueryWrapper<>();
        query.select("DISTINCT customer_name");
        query.isNotNull("customer_name");
        query.ne("customer_name", "");
        applyOrderScope(query);
        query.orderByAsc("customer_name");
        return orderRfqMapper.selectList(query).stream()
                .map(OrderRfq::getCustomerName)
                .filter(value -> !isBlank(value))
                .distinct()
                .toList();
    }

    public List<CustomerOptionDTO> listCustomerOptions(String field, String keyword) {
        if (customerMasterMapper != null) {
            QueryWrapper<CustomerMaster> query = new QueryWrapper<>();
            query.eq("enabled", true);
            query.isNotNull("customer_name");
            query.ne("customer_name", "");
            applyCustomerKeyword(query, field, keyword);
            applyCustomerMasterScope(query);
            query.orderByAsc("customer_name").orderByAsc("id");
            query.last("LIMIT 200");
            return customerMasterMapper.selectList(query).stream()
                    .map(this::toCustomerOption)
                    .toList();
        }
        return listCustomerOptions().stream()
                .filter(name -> matchesCustomerKeyword(name, field, keyword))
                .map(this::toCustomerOption)
                .toList();
    }

    private void applyCustomerMasterScope(QueryWrapper<CustomerMaster> query) {
        ScopeSql scopeSql = currentOrderScopeSql("");
        if (scopeSql.allAccess()) {
            return;
        }
        if (scopeSql.noAccess()) {
            query.apply("1 = 0");
            return;
        }
        query.apply("customer_name IN (SELECT DISTINCT customer_name FROM order_rfq WHERE " + scopeSql.sql() + ")",
                scopeSql.params().toArray());
    }

    private void applyCustomerKeyword(QueryWrapper<CustomerMaster> query, String field, String keyword) {
        if (isBlank(keyword)) {
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

    private boolean matchesCustomerKeyword(String name, String field, String keyword) {
        if (isBlank(keyword)) {
            return true;
        }
        String normalizedField = normalizeField(field);
        if (!"all".equals(normalizedField) && !"name".equals(normalizedField)) {
            return false;
        }
        return name != null && name.toLowerCase(Locale.ROOT).contains(keyword.trim().toLowerCase(Locale.ROOT));
    }

    private CustomerOptionDTO toCustomerOption(CustomerMaster customer) {
        CustomerOptionDTO dto = new CustomerOptionDTO();
        dto.setId(customer.getId());
        dto.setCustomerCode(customer.getCustomerCode());
        dto.setCustomerShortName(customer.getCustomerShortName());
        dto.setCustomerName(customer.getCustomerName());
        return dto;
    }

    private CustomerOptionDTO toCustomerOption(String customerName) {
        CustomerOptionDTO dto = new CustomerOptionDTO();
        dto.setCustomerCode(customerName);
        dto.setCustomerShortName(customerName);
        dto.setCustomerName(customerName);
        return dto;
    }

    private void applyScopeSql(QueryWrapper<?> query, ScopeSql scopeSql) {
        if (scopeSql.allAccess()) {
            return;
        }
        if (scopeSql.noAccess()) {
            query.apply("1 = 0");
            return;
        }
        query.apply(scopeSql.sql(), scopeSql.params().toArray());
    }

    private ScopeSql currentOrderScopeSql(String alias) {
        DataScopeSnapshot snapshot = currentSnapshot();
        if (snapshot.noContext() || snapshot.allAccess()) {
            return ScopeSql.all();
        }
        if (snapshot.noAccess()) {
            return ScopeSql.none();
        }
        List<Object> params = new ArrayList<>();
        List<String> clauses = new ArrayList<>();
        if (snapshot.selfOnly()) {
            int index = params.size();
            params.add(snapshot.legacyUserId());
            clauses.add("(" + column(alias, "created_by") + " = {" + index + "} OR " + column(alias, "owner_user_id") + " = {" + index + "})");
        }
        if (!snapshot.customerNames().isEmpty()) {
            clauses.add(column(alias, "customer_name") + " IN (" + placeholders(params, snapshot.customerNames()) + ")");
        }
        if (clauses.isEmpty()) {
            return ScopeSql.none();
        }
        return new ScopeSql(false, false, "(" + String.join(" OR ", clauses) + ")", params);
    }

    private DataScopeSnapshot currentSnapshot() {
        AuthContext.SessionUser current = AuthContext.get();
        if (current == null) {
            return DataScopeSnapshot.withoutContext();
        }
        List<SystemRole> roles = loadRoles(current.id());
        if (roles.isEmpty()) {
            return DataScopeSnapshot.none(current.id(), current.username());
        }
        boolean superAdmin = false;
        boolean allAccess = false;
        boolean selfOnly = false;
        Set<String> customerNames = new LinkedHashSet<>();
        for (SystemRole role : roles) {
            if (role == null) {
                continue;
            }
            if (Boolean.TRUE.equals(role.getSuperAdmin()) || "SUPER_ADMIN".equalsIgnoreCase(role.getRoleCode())) {
                superAdmin = true;
                allAccess = true;
                continue;
            }
            switch (normalize(role.getDataScope())) {
                case "ALL" -> allAccess = true;
                case "SELF" -> selfOnly = true;
                case "CUSTOMER" -> customerNames.addAll(loadConfiguredCustomerNames(role.getId()));
                default -> {
                }
            }
        }
        if (allAccess) {
            return DataScopeSnapshot.all(current.id(), current.username(), superAdmin);
        }
        boolean noAccess = !selfOnly && customerNames.isEmpty();
        return new DataScopeSnapshot(false, current.id(), current.username(), superAdmin, false, noAccess,
                selfOnly, List.copyOf(customerNames));
    }

    private boolean matchesOrder(DataScopeSnapshot snapshot, OrderRfq order) {
        if (snapshot.noAccess()) {
            return false;
        }
        if (snapshot.selfOnly()
                && (Objects.equals(order.getCreatedBy(), snapshot.legacyUserId())
                || Objects.equals(order.getOwnerUserId(), snapshot.legacyUserId()))) {
            return true;
        }
        return !isBlank(order.getCustomerName()) && snapshot.customerNames().contains(order.getCustomerName());
    }

    private SystemUser loadSystemUser(Integer legacyUserId) {
        if (legacyUserId == null || systemUserMapper == null) {
            return null;
        }
        try {
            return systemUserMapper.selectByLegacyUserId(legacyUserId);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private List<SystemRole> loadRoles(Integer legacyUserId) {
        if (legacyUserId == null || roleMapper == null) {
            return List.of();
        }
        try {
            List<SystemRole> roles = roleMapper.selectActiveRolesByLegacyUserId(legacyUserId);
            return roles == null ? List.of() : roles;
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private List<String> loadConfiguredCustomerNames(Long roleId) {
        if (roleId == null || dataScopeCustomerMapper == null) {
            return List.of();
        }
        List<String> names = dataScopeCustomerMapper.selectCustomerNamesByRoleId(roleId);
        return names == null ? List.of() : names.stream().filter(value -> !isBlank(value)).toList();
    }

    private String placeholders(List<Object> params, List<?> values) {
        List<String> result = new ArrayList<>();
        for (Object value : values) {
            result.add("{" + params.size() + "}");
            params.add(value);
        }
        return String.join(", ", result);
    }

    private String column(String alias, String column) {
        return isBlank(alias) ? column : alias + "." + column;
    }

    private String normalize(String value) {
        return value == null ? "NONE" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeField(String value) {
        return value == null ? "all" : value.replace("_", "").replace("-", "").trim().toLowerCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private record ScopeSql(boolean allAccess, boolean noAccess, String sql, List<Object> params) {
        private static ScopeSql all() {
            return new ScopeSql(true, false, "", List.of());
        }

        private static ScopeSql none() {
            return new ScopeSql(false, true, "1 = 0", List.of());
        }
    }

    private record DataScopeSnapshot(
            boolean noContext,
            Integer legacyUserId,
            String username,
            boolean superAdmin,
            boolean allAccess,
            boolean noAccess,
            boolean selfOnly,
            List<String> customerNames) {
        private static DataScopeSnapshot withoutContext() {
            return new DataScopeSnapshot(true, null, null, false, true, false, false, List.of());
        }

        private static DataScopeSnapshot all(Integer legacyUserId, String username, boolean superAdmin) {
            return new DataScopeSnapshot(false, legacyUserId, username, superAdmin, true, false, false, List.of());
        }

        private static DataScopeSnapshot none(Integer legacyUserId, String username) {
            return new DataScopeSnapshot(false, legacyUserId, username, false, false, true, false, List.of());
        }
    }
}
