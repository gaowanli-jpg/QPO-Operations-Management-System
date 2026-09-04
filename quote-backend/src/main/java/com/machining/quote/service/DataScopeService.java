package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.config.AuthContext;
import com.machining.quote.dto.CustomerOptionDTO;
import com.machining.quote.dto.PageResult;
import com.machining.quote.entity.AuditLog;
import com.machining.quote.entity.CustomerMaster;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SystemRole;
import com.machining.quote.entity.SystemUser;
import com.machining.quote.mapper.CustomerMasterMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
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
    @Autowired(required = false)
    private SalesOrderHeaderMapper salesOrderHeaderMapper;
    @Autowired(required = false)
    private OrderDeliveryTicketMapper orderDeliveryTicketMapper;

    public void applyOrderScope(QueryWrapper<OrderRfq> query) {
        applyScopeSql(query, currentOrderScopeSql(""));
    }

    public void applyOrderScope(QueryWrapper<?> query, String alias) {
        applyScopeSql(query, currentOrderScopeSql(alias));
    }

    public void applySalesOrderScope(QueryWrapper<SalesOrderHeader> query) {
        applyScopeSql(query, currentOrderScopeSql(""));
    }

    public void applySalesOrderScope(QueryWrapper<?> query, String alias) {
        applyScopeSql(query, currentOrderScopeSql(alias));
    }

    public void applySalesOrderIdScope(QueryWrapper<?> query, String salesOrderIdColumn) {
        String safeColumn = isBlank(salesOrderIdColumn) ? "sales_order_id" : salesOrderIdColumn.trim();
        ScopeSql scopeSql = currentOrderScopeSql("so");
        if (scopeSql.allAccess()) {
            return;
        }
        if (scopeSql.noAccess()) {
            query.apply("1 = 0");
            return;
        }
        query.apply(safeColumn + " IN (SELECT so.id FROM sales_order_header so WHERE "
                + scopeSql.sql() + ")", scopeSql.params().toArray());
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

    public void applyDeliveryTicketScope(QueryWrapper<?> query, String ticketAlias) {
        ScopeSql baseScope = currentOrderScopeSql("");
        if (baseScope.allAccess()) {
            return;
        }
        if (baseScope.noAccess()) {
            query.apply("1 = 0");
            return;
        }
        List<Object> params = new ArrayList<>();
        String rfqScope = rebaseScopeSql(currentOrderScopeSql("rfq"), params);
        String salesOrderScope = rebaseScopeSql(currentOrderScopeSql("so"), params);
        String alias = isBlank(ticketAlias) ? "" : ticketAlias.trim() + ".";
        query.apply("("
                        + alias + "rfq_id IN (SELECT rfq.id FROM order_rfq rfq WHERE " + rfqScope + ")"
                        + " OR ("
                        + alias + "source_type = 'SALES_ORDER' AND "
                        + alias + "source_order_id IN (SELECT so.id FROM sales_order_header so WHERE " + salesOrderScope + ")"
                        + "))",
                params.toArray());
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

    public void assertCanAccessCustomer(CustomerMaster customer) {
        if (customer == null) {
            return;
        }
        DataScopeSnapshot snapshot = currentSnapshot();
        if (snapshot.noContext() || snapshot.allAccess() || snapshot.selfOnly()) {
            return;
        }
        if (!snapshot.noAccess()
                && !isBlank(customer.getCustomerCode())
                && snapshot.customerCodes().contains(customer.getCustomerCode())) {
            return;
        }
        throw new RuntimeException("No data permission for customer " + customer.getCustomerName());
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

    public void assertCanAccessSalesOrder(SalesOrderHeader header) {
        if (header == null) {
            return;
        }
        DataScopeSnapshot snapshot = currentSnapshot();
        if (snapshot.noContext() || snapshot.allAccess()) {
            return;
        }
        if (matchesSalesOrder(snapshot, header)) {
            return;
        }
        throw new RuntimeException("No data permission for sales order " + header.getId());
    }

    public void assertCanAccessDeliveryTicket(OrderDeliveryTicket ticket) {
        if (ticket == null) {
            return;
        }
        DataScopeSnapshot snapshot = currentSnapshot();
        if (snapshot.noContext() || snapshot.allAccess()) {
            return;
        }
        if ("SALES_ORDER".equalsIgnoreCase(ticket.getSourceType()) && ticket.getSourceOrderId() != null) {
            if (salesOrderHeaderMapper == null) {
                throw new RuntimeException("Sales order data permission service is unavailable");
            }
            SalesOrderHeader header = salesOrderHeaderMapper.selectById(ticket.getSourceOrderId());
            if (header == null) {
                throw new RuntimeException("Sales order does not exist");
            }
            assertCanAccessSalesOrder(header);
            return;
        }
        if (ticket.getRfqId() != null) {
            assertCanAccessOrder(ticket.getRfqId(), orderRfqMapper);
            return;
        }
        throw new RuntimeException("No data permission for delivery ticket " + ticket.getId());
    }

    public void assertCanAccessDeliveryTicket(Integer ticketId) {
        if (ticketId == null || orderDeliveryTicketMapper == null) {
            throw new RuntimeException("Delivery ticket ID is required");
        }
        OrderDeliveryTicket ticket = orderDeliveryTicketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new RuntimeException("Delivery ticket does not exist");
        }
        assertCanAccessDeliveryTicket(ticket);
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

    public void assignCurrentOwnership(SalesOrderHeader order) {
        if (order == null) {
            return;
        }
        AuthContext.SessionUser current = AuthContext.get();
        if (current == null) {
            return;
        }
        SystemUser systemUser = loadSystemUser(current.id());
        if (order.getCreatedBy() == null) {
            order.setCreatedBy(current.id().longValue());
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

    public PageResult<CustomerOptionDTO> pageCustomerOptions(String field, String keyword, Integer page, Integer pageSize) {
        int safePage = normalizePage(page);
        int safePageSize = normalizePageSize(pageSize);
        if (customerMasterMapper != null) {
            QueryWrapper<CustomerMaster> query = new QueryWrapper<>();
            query.eq("enabled", true);
            query.isNotNull("customer_name");
            query.ne("customer_name", "");
            applyCustomerKeyword(query, field, keyword);
            applyCustomerMasterScope(query);
            Long total = customerMasterMapper.selectCount(query);
            if (total == null || total == 0) {
                return PageResult.of(List.of(), 0, safePage, safePageSize);
            }
            query.orderByAsc("customer_code").orderByAsc("id");
            query.last("LIMIT " + safePageSize + " OFFSET " + ((safePage - 1L) * safePageSize));
            return PageResult.of(customerMasterMapper.selectList(query).stream()
                    .map(this::toCustomerOption)
                    .toList(), total, safePage, safePageSize);
        }
        List<CustomerOptionDTO> rows = listCustomerOptions().stream()
                .filter(name -> matchesCustomerKeyword(name, field, keyword))
                .map(this::toCustomerOption)
                .toList();
        return pageRows(rows, safePage, safePageSize);
    }

    private void applyCustomerMasterScope(QueryWrapper<CustomerMaster> query) {
        DataScopeSnapshot snapshot = currentSnapshot();
        if (snapshot.noContext() || snapshot.allAccess()) {
            return;
        }
        if (snapshot.noAccess()) {
            query.apply("1 = 0");
            return;
        }
        List<Object> params = new ArrayList<>();
        List<String> clauses = new ArrayList<>();
        if (snapshot.selfOnly()) {
            int userIndex = params.size();
            params.add(snapshot.legacyUserId());
            clauses.add("customer_code IN (SELECT DISTINCT customer_code FROM order_rfq WHERE (created_by = {"
                    + userIndex + "} OR owner_user_id = {" + userIndex + "}))");
            clauses.add("customer_code IN (SELECT DISTINCT customer_code FROM sales_order_header WHERE (created_by = {"
                    + userIndex + "} OR owner_user_id = {" + userIndex + "}))");
        }
        if (!snapshot.customerCodes().isEmpty()) {
            clauses.add("customer_code IN (" + placeholders(params, snapshot.customerCodes()) + ")");
        }
        if (clauses.isEmpty()) {
            query.apply("1 = 0");
            return;
        }
        query.apply("(" + String.join(" OR ", clauses) + ")", params.toArray());
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

    private int normalizePage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 20;
        }
        return Math.min(pageSize, 100);
    }

    private <T> PageResult<T> pageRows(List<T> rows, int safePage, int safePageSize) {
        List<T> safeRows = rows == null ? List.of() : rows;
        int fromIndex = (int) Math.min((long) (safePage - 1) * safePageSize, safeRows.size());
        int toIndex = Math.min(fromIndex + safePageSize, safeRows.size());
        return PageResult.of(safeRows.subList(fromIndex, toIndex), safeRows.size(), safePage, safePageSize);
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
        if (!snapshot.customerCodes().isEmpty()) {
            clauses.add(column(alias, "customer_code") + " IN (" + placeholders(params, snapshot.customerCodes()) + ")");
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
        Set<String> customerCodes = new LinkedHashSet<>();
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
                case "CUSTOMER" -> customerCodes.addAll(loadConfiguredCustomerCodes(role.getId()));
                default -> {
                }
            }
        }
        if (allAccess) {
            return DataScopeSnapshot.all(current.id(), current.username(), superAdmin);
        }
        boolean noAccess = !selfOnly && customerCodes.isEmpty();
        return new DataScopeSnapshot(false, current.id(), current.username(), superAdmin, false, noAccess,
                selfOnly, List.copyOf(customerCodes));
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
        return !isBlank(order.getCustomerCode()) && snapshot.customerCodes().contains(order.getCustomerCode());
    }

    private boolean matchesSalesOrder(DataScopeSnapshot snapshot, SalesOrderHeader header) {
        if (snapshot.noAccess()) {
            return false;
        }
        Long currentUserId = snapshot.legacyUserId() == null ? null : snapshot.legacyUserId().longValue();
        if (snapshot.selfOnly()
                && (Objects.equals(header.getCreatedBy(), currentUserId)
                || Objects.equals(header.getOwnerUserId(), snapshot.legacyUserId()))) {
            return true;
        }
        return !isBlank(header.getCustomerCode()) && snapshot.customerCodes().contains(header.getCustomerCode());
    }

    private String rebaseScopeSql(ScopeSql scopeSql, List<Object> targetParams) {
        String sql = scopeSql.sql();
        for (int i = 0; i < scopeSql.params().size(); i++) {
            sql = sql.replace("{" + i + "}", "{" + targetParams.size() + "}");
            targetParams.add(scopeSql.params().get(i));
        }
        return sql;
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

    private List<String> loadConfiguredCustomerCodes(Long roleId) {
        if (roleId == null || dataScopeCustomerMapper == null) {
            return List.of();
        }
        List<String> codes = dataScopeCustomerMapper.selectCustomerCodesByRoleId(roleId);
        return codes == null ? List.of() : codes.stream().filter(value -> !isBlank(value)).toList();
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
            List<String> customerCodes) {
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
