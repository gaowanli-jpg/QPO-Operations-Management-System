package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.config.AuthContext;
import com.machining.quote.entity.AuditLog;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.entity.SystemDept;
import com.machining.quote.entity.SystemRole;
import com.machining.quote.entity.SystemUser;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.mapper.SystemDeptMapper;
import com.machining.quote.mapper.SystemRoleDataScopeCustomerMapper;
import com.machining.quote.mapper.SystemRoleDataScopeDeptMapper;
import com.machining.quote.mapper.SystemRoleMapper;
import com.machining.quote.mapper.SystemUserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class DataScopeService {
    @Autowired(required = false)
    private SystemUserMapper systemUserMapper;
    @Autowired(required = false)
    private SystemRoleMapper roleMapper;
    @Autowired(required = false)
    private SystemRoleDataScopeDeptMapper dataScopeDeptMapper;
    @Autowired(required = false)
    private SystemRoleDataScopeCustomerMapper dataScopeCustomerMapper;
    @Autowired(required = false)
    private SystemDeptMapper systemDeptMapper;
    @Autowired(required = false)
    private OrderRfqMapper orderRfqMapper;

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

    public List<Map<String, Object>> listDepartmentTree() {
        if (systemDeptMapper == null) {
            return List.of();
        }
        QueryWrapper<SystemDept> query = new QueryWrapper<>();
        query.eq("deleted", false).eq("enabled", true).orderByAsc("sort_order").orderByAsc("id");
        return buildDeptTree(systemDeptMapper.selectList(query));
    }

    public List<String> listCustomerOptions() {
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
        if (!snapshot.deptIds().isEmpty()) {
            clauses.add(column(alias, "owner_dept_id") + " IN (" + placeholders(params, snapshot.deptIds()) + ")");
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
        SystemUser systemUser = loadSystemUser(current.id());
        List<SystemRole> roles = loadRoles(current.id());
        if (roles.isEmpty()) {
            return DataScopeSnapshot.none(current.id(), current.username());
        }
        boolean superAdmin = false;
        boolean allAccess = false;
        boolean selfOnly = false;
        Set<Long> deptIds = new LinkedHashSet<>();
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
                case "DEPT" -> addIfPresent(deptIds, systemUser == null ? null : systemUser.getDeptId());
                case "DEPT_AND_CHILD" -> deptIds.addAll(loadDeptAndChildren(systemUser == null ? null : systemUser.getDeptId()));
                case "CUSTOM" -> deptIds.addAll(loadConfiguredDeptIds(role.getId()));
                case "CUSTOMER" -> customerNames.addAll(loadConfiguredCustomerNames(role.getId()));
                default -> {
                }
            }
        }
        if (allAccess) {
            return DataScopeSnapshot.all(current.id(), current.username(), superAdmin);
        }
        boolean noAccess = !selfOnly && deptIds.isEmpty() && customerNames.isEmpty();
        return new DataScopeSnapshot(false, current.id(), current.username(), superAdmin, false, noAccess,
                selfOnly, List.copyOf(deptIds), List.copyOf(customerNames));
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
        if (order.getOwnerDeptId() != null && snapshot.deptIds().contains(order.getOwnerDeptId())) {
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

    private List<Long> loadConfiguredDeptIds(Long roleId) {
        if (roleId == null || dataScopeDeptMapper == null) {
            return List.of();
        }
        List<Long> ids = dataScopeDeptMapper.selectDeptIdsByRoleId(roleId);
        return ids == null ? List.of() : ids.stream().filter(Objects::nonNull).toList();
    }

    private List<String> loadConfiguredCustomerNames(Long roleId) {
        if (roleId == null || dataScopeCustomerMapper == null) {
            return List.of();
        }
        List<String> names = dataScopeCustomerMapper.selectCustomerNamesByRoleId(roleId);
        return names == null ? List.of() : names.stream().filter(value -> !isBlank(value)).toList();
    }

    private List<Long> loadDeptAndChildren(Long deptId) {
        if (deptId == null) {
            return List.of();
        }
        if (systemDeptMapper == null) {
            return List.of(deptId);
        }
        QueryWrapper<SystemDept> query = new QueryWrapper<>();
        query.eq("deleted", false).eq("enabled", true);
        List<SystemDept> all = systemDeptMapper.selectList(query);
        Map<Long, List<Long>> childrenByParent = new LinkedHashMap<>();
        for (SystemDept dept : all) {
            if (dept.getId() == null || dept.getParentId() == null) {
                continue;
            }
            childrenByParent.computeIfAbsent(dept.getParentId(), ignored -> new ArrayList<>()).add(dept.getId());
        }
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        queue.add(deptId);
        while (!queue.isEmpty()) {
            Long current = queue.removeFirst();
            if (!result.add(current)) {
                continue;
            }
            queue.addAll(childrenByParent.getOrDefault(current, List.of()));
        }
        return List.copyOf(result);
    }

    private List<Map<String, Object>> buildDeptTree(List<SystemDept> departments) {
        Map<Long, Map<String, Object>> byId = new LinkedHashMap<>();
        Map<Long, List<Map<String, Object>>> childrenByParent = new LinkedHashMap<>();
        for (SystemDept dept : departments == null ? List.<SystemDept>of() : departments) {
            if (dept.getId() == null) {
                continue;
            }
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", dept.getId());
            node.put("label", dept.getDeptName());
            node.put("deptCode", dept.getDeptCode());
            node.put("children", new ArrayList<Map<String, Object>>());
            byId.put(dept.getId(), node);
            childrenByParent.computeIfAbsent(dept.getParentId() == null ? 0L : dept.getParentId(), ignored -> new ArrayList<>()).add(node);
        }
        for (Map.Entry<Long, List<Map<String, Object>>> entry : childrenByParent.entrySet()) {
            Map<String, Object> parent = byId.get(entry.getKey());
            if (parent != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> children = (List<Map<String, Object>>) parent.get("children");
                children.addAll(entry.getValue());
            }
        }
        return childrenByParent.getOrDefault(0L, List.of());
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

    private void addIfPresent(Set<Long> values, Long value) {
        if (value != null) {
            values.add(value);
        }
    }

    private String normalize(String value) {
        return value == null ? "NONE" : value.trim().toUpperCase(Locale.ROOT);
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
            List<Long> deptIds,
            List<String> customerNames) {
        private static DataScopeSnapshot withoutContext() {
            return new DataScopeSnapshot(true, null, null, false, true, false, false, List.of(), List.of());
        }

        private static DataScopeSnapshot all(Integer legacyUserId, String username, boolean superAdmin) {
            return new DataScopeSnapshot(false, legacyUserId, username, superAdmin, true, false, false, List.of(), List.of());
        }

        private static DataScopeSnapshot none(Integer legacyUserId, String username) {
            return new DataScopeSnapshot(false, legacyUserId, username, false, false, true, false, List.of(), List.of());
        }
    }
}
