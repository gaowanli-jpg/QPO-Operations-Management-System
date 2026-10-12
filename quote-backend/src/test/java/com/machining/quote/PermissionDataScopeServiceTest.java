package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.config.AuthContext;
import com.machining.quote.dto.RoleDataScopeAssignReq;
import com.machining.quote.entity.AuditLog;
import com.machining.quote.entity.CustomerMaster;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SystemRole;
import com.machining.quote.entity.SystemUser;
import com.machining.quote.mapper.CustomerMasterMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SystemRoleDataScopeCustomerMapper;
import com.machining.quote.mapper.SystemRoleDataScopeDeptMapper;
import com.machining.quote.mapper.SystemRoleMapper;
import com.machining.quote.mapper.SystemUserMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.DataScopeService;
import com.machining.quote.service.SystemRoleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PermissionDataScopeServiceTest {
    private static final Path MAIN_JAVA = Path.of("src", "main", "java");
    private static final Path MIGRATION_DIR = Path.of("src", "main", "resources", "db", "migration");

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void migrationAddsOrderOwnershipColumnsForDataScope() throws Exception {
        Path migration = MIGRATION_DIR.resolve("V1__qpo_baseline_schema.sql");

        assertThat(migration).exists();
        String sql = Files.readString(migration, StandardCharsets.UTF_8).toLowerCase();
        assertThat(sql)
                .contains("create table order_rfq")
                .contains("created_by")
                .contains("owner_user_id")
                .contains("owner_dept_id")
                .contains("idx_order_rfq_owner_user")
                .contains("idx_order_rfq_owner_dept")
                .contains("idx_order_rfq_customer");
    }

    @Test
    void selfDataScopeKeepsCreatorOrOwnerCompatibilityForSchemeA() {
        DataScopeService service = serviceWithRoles(role(1L, "MARKET_USER", "SELF", false));
        AuthContext.set(new AuthContext.SessionUser(7, "market", "Market", "MARKET"));
        QueryWrapper<OrderRfq> query = new QueryWrapper<>();

        service.applyOrderScope(query);

        String sql = query.getCustomSqlSegment().toLowerCase();
        assertThat(sql).contains("created_by").contains("owner_user_id");
        assertThat(query.getParamNameValuePairs()).containsValue(7).containsValue(7);
    }

    @Test
    void customerDataScopeFiltersOrdersByConfiguredCustomerNames() {
        DataScopeService service = serviceWithRoles(role(2L, "KEY_ACCOUNT", "CUSTOMER", false));
        SystemRoleDataScopeCustomerMapper customerMapper = mock(SystemRoleDataScopeCustomerMapper.class);
        when(customerMapper.selectCustomerCodesByRoleId(2L)).thenReturn(List.of("C001", "C002"));
        ReflectionTestUtils.setField(service, "dataScopeCustomerMapper", customerMapper);
        AuthContext.set(new AuthContext.SessionUser(8, "sales", "Sales", "MARKET"));
        QueryWrapper<OrderRfq> query = new QueryWrapper<>();

        service.applyOrderScope(query);

        assertThat(query.getCustomSqlSegment().toLowerCase()).contains("customer_code");
        assertThat(query.getParamNameValuePairs()).containsValue("C001").containsValue("C002");
    }

    @Test
    void customerDataScopeDoesNotMatchSameNameDifferentCodeOrders() {
        DataScopeService service = serviceWithRoles(role(23L, "KEY_ACCOUNT", "CUSTOMER", false));
        SystemRoleDataScopeCustomerMapper customerMapper = mock(SystemRoleDataScopeCustomerMapper.class);
        when(customerMapper.selectCustomerCodesByRoleId(23L)).thenReturn(List.of("C001"));
        ReflectionTestUtils.setField(service, "dataScopeCustomerMapper", customerMapper);
        AuthContext.set(new AuthContext.SessionUser(8, "sales", "Sales", "MARKET"));

        OrderRfq allowed = new OrderRfq();
        allowed.setId(1001);
        allowed.setCustomerCode("C001");
        allowed.setCustomerName("同名客户");
        OrderRfq denied = new OrderRfq();
        denied.setId(1002);
        denied.setCustomerCode("C002");
        denied.setCustomerName("同名客户");
        SalesOrderHeader deniedSalesOrder = new SalesOrderHeader();
        deniedSalesOrder.setId(2002L);
        deniedSalesOrder.setCustomerCode("C002");
        deniedSalesOrder.setCustomerName("同名客户");

        service.assertCanAccessOrder(allowed);
        assertThatThrownBy(() -> service.assertCanAccessOrder(denied))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("No data permission");
        assertThatThrownBy(() -> service.assertCanAccessSalesOrder(deniedSalesOrder))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("No data permission");
    }

    @Test
    void deliveryTicketBatchScopeLoadsDistinctSalesOrdersAndRfqsOnce() {
        DataScopeService service = serviceWithRoles(role(24L, "KEY_ACCOUNT", "CUSTOMER", false));
        SystemRoleDataScopeCustomerMapper customerMapper = mock(SystemRoleDataScopeCustomerMapper.class);
        OrderRfqMapper rfqMapper = mock(OrderRfqMapper.class);
        SalesOrderHeaderMapper salesOrderMapper = mock(SalesOrderHeaderMapper.class);
        when(customerMapper.selectCustomerCodesByRoleId(24L)).thenReturn(List.of("C001"));
        OrderRfq rfq = new OrderRfq();
        rfq.setId(501);
        rfq.setCustomerCode("C001");
        SalesOrderHeader salesOrder = new SalesOrderHeader();
        salesOrder.setId(701L);
        salesOrder.setCustomerCode("C001");
        when(rfqMapper.selectBatchIds(List.of(501))).thenReturn(List.of(rfq));
        when(salesOrderMapper.selectBatchIds(List.of(701L))).thenReturn(List.of(salesOrder));
        ReflectionTestUtils.setField(service, "dataScopeCustomerMapper", customerMapper);
        ReflectionTestUtils.setField(service, "orderRfqMapper", rfqMapper);
        ReflectionTestUtils.setField(service, "salesOrderHeaderMapper", salesOrderMapper);
        AuthContext.set(new AuthContext.SessionUser(8, "sales", "Sales", "MARKET"));

        OrderDeliveryTicket firstRfqTicket = new OrderDeliveryTicket();
        firstRfqTicket.setId(1);
        firstRfqTicket.setRfqId(501);
        OrderDeliveryTicket secondRfqTicket = new OrderDeliveryTicket();
        secondRfqTicket.setId(2);
        secondRfqTicket.setRfqId(501);
        OrderDeliveryTicket salesOrderTicket = new OrderDeliveryTicket();
        salesOrderTicket.setId(3);
        salesOrderTicket.setSourceType("SALES_ORDER");
        salesOrderTicket.setSourceOrderId(701L);

        service.assertCanAccessDeliveryTickets(List.of(firstRfqTicket, secondRfqTicket, salesOrderTicket));

        verify(rfqMapper).selectBatchIds(List.of(501));
        verify(salesOrderMapper).selectBatchIds(List.of(701L));
        verify(rfqMapper, never()).selectById(org.mockito.ArgumentMatchers.any());
        verify(salesOrderMapper, never()).selectById(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deliveryTicketBatchScopeRejectsAnOutOfScopeOrder() {
        DataScopeService service = serviceWithRoles(role(25L, "KEY_ACCOUNT", "CUSTOMER", false));
        SystemRoleDataScopeCustomerMapper customerMapper = mock(SystemRoleDataScopeCustomerMapper.class);
        OrderRfqMapper rfqMapper = mock(OrderRfqMapper.class);
        when(customerMapper.selectCustomerCodesByRoleId(25L)).thenReturn(List.of("C001"));
        OrderRfq allowed = new OrderRfq();
        allowed.setId(501);
        allowed.setCustomerCode("C001");
        OrderRfq denied = new OrderRfq();
        denied.setId(502);
        denied.setCustomerCode("C002");
        when(rfqMapper.selectBatchIds(List.of(501, 502))).thenReturn(List.of(allowed, denied));
        ReflectionTestUtils.setField(service, "dataScopeCustomerMapper", customerMapper);
        ReflectionTestUtils.setField(service, "orderRfqMapper", rfqMapper);
        AuthContext.set(new AuthContext.SessionUser(8, "sales", "Sales", "MARKET"));
        OrderDeliveryTicket allowedTicket = new OrderDeliveryTicket();
        allowedTicket.setId(1);
        allowedTicket.setRfqId(501);
        OrderDeliveryTicket deniedTicket = new OrderDeliveryTicket();
        deniedTicket.setId(2);
        deniedTicket.setRfqId(502);

        assertThatThrownBy(() -> service.assertCanAccessDeliveryTickets(List.of(allowedTicket, deniedTicket)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("No data permission for order 502");
        verify(rfqMapper).selectBatchIds(List.of(501, 502));
    }

    @Test
    void multipleRoleDataScopesAreUnionedForOrderQueries() {
        DataScopeService service = serviceWithRoles(
                role(5L, "SELF_MARKET", "SELF", false),
                role(6L, "KEY_ACCOUNT", "CUSTOMER", false));
        SystemRoleDataScopeCustomerMapper customerMapper = mock(SystemRoleDataScopeCustomerMapper.class);
        when(customerMapper.selectCustomerCodesByRoleId(6L)).thenReturn(List.of("C001"));
        ReflectionTestUtils.setField(service, "dataScopeCustomerMapper", customerMapper);
        AuthContext.set(new AuthContext.SessionUser(7, "market", "Market", "MARKET"));
        QueryWrapper<OrderRfq> query = new QueryWrapper<>();

        service.applyOrderScope(query);

        String sql = query.getCustomSqlSegment().toLowerCase();
        assertThat(sql)
                .contains("created_by")
                .contains("owner_user_id")
                .contains("customer_code")
                .contains(" or ");
        assertThat(query.getParamNameValuePairs()).containsValue(7).containsValue("C001");
    }

    @Test
    void noneDataScopeReturnsEmptyOrderListInsteadOfThrowing() {
        DataScopeService service = serviceWithRoles(role(3L, "READONLY", "NONE", false));
        AuthContext.set(new AuthContext.SessionUser(9, "readonly", "Read Only", "MARKET"));
        QueryWrapper<OrderRfq> query = new QueryWrapper<>();

        service.applyOrderScope(query);

        assertThat(query.getCustomSqlSegment()).contains("1 = 0");
    }

    @Test
    void auditScopeHidesSuperAdminLogsForNonSuperAdminUsers() {
        DataScopeService service = serviceWithRoles(role(4L, "AUDITOR", "ALL", false));
        AuthContext.set(new AuthContext.SessionUser(10, "auditor", "Auditor", "ADMIN"));
        QueryWrapper<AuditLog> query = new QueryWrapper<>();

        service.applyAuditScope(query);

        String sql = query.getCustomSqlSegment().toUpperCase();
        assertThat(sql).contains("ROLE").contains("NOT IN");
        assertThat(query.getParamNameValuePairs()).containsValue("ADMIN").containsValue("SUPER_ADMIN");
    }

    @Test
    void orderRelatedServicesApplyDataScopeAtQueryBoundaries() throws Exception {
        assertSourceContains("service/OrderManagementService.java", "dataScopeService.applyOrderScope");
        assertSourceContains("service/OrderManagementService.java", "dataScopeService.assertCanAccessOrder");
        assertSourceContains("service/EngineerService.java", "dataScopeService.assertCanAccessOrder");
        assertSourceContains("service/QuoteProcessService.java", "dataScopeService.assertCanAccessOrder");
        assertSourceContains("service/DeliveryService.java", "dataScopeService.assertCanAccessOrder");
        assertSourceContains("service/InternalTargetLeadTimeService.java", "dataScopeService.assertCanAccessOrder");
        assertSourceContains("service/DeliveredItemService.java", "dataScopeService.applyDeliveryTicketScope");
        assertSourceContains("service/DeliveredItemService.java", "dataScopeService.assertCanAccessDeliveryTicket");
        assertSourceContains("service/AuditLogQueryService.java", "dataScopeService.applyAuditScope");
        assertSourceContains("service/AuditLogQueryService.java", "applyAuditDataScope(qw);");
        assertSourceContains("service/AuditLogQueryService.java", "applyAuditDataScope(actionQuery);");
    }

    @Test
    void roleDataScopeAssignmentOnlyPersistsCustomerCodesForCustomerScope() {
        SystemRoleDataScopeDeptMapper deptMapper = mock(SystemRoleDataScopeDeptMapper.class);
        SystemRoleDataScopeCustomerMapper customerMapper = mock(SystemRoleDataScopeCustomerMapper.class);
        CustomerMasterMapper customerMasterMapper = mock(CustomerMasterMapper.class);
        when(customerMasterMapper.selectEnabledByCode("C001")).thenReturn(customer("C001", "同名客户"));
        when(customerMasterMapper.selectEnabledByCode("C002")).thenReturn(customer("C002", "同名客户"));
        SystemRoleService service = roleServiceForAssignment(role(20L, "KEY_ACCOUNT", "ALL", false), deptMapper, customerMapper, customerMasterMapper);
        RoleDataScopeAssignReq req = dataScopeReq("CUSTOMER", List.of(10L, 11L), List.of("C001", " C001 ", "C002"));

        service.assignDataScope(20L, req);

        verify(deptMapper).deleteByRoleId(20L);
        verify(customerMapper).deleteByRoleId(20L);
        verify(deptMapper, never()).insertIgnore(eq(20L), anyLong());
        verify(customerMapper).insertIgnore(20L, "C001", "同名客户");
        verify(customerMapper).insertIgnore(20L, "C002", "同名客户");
    }

    @Test
    void roleDataScopeAssignmentRejectsRetiredDepartmentScopes() {
        SystemRoleDataScopeDeptMapper deptMapper = mock(SystemRoleDataScopeDeptMapper.class);
        SystemRoleDataScopeCustomerMapper customerMapper = mock(SystemRoleDataScopeCustomerMapper.class);
        SystemRoleService service = roleServiceForAssignment(role(21L, "DEPT_OWNER", "ALL", false), deptMapper, customerMapper);
        RoleDataScopeAssignReq req = dataScopeReq("CUSTOM", List.of(10L, 10L, 11L), List.of("ACME", "Globex"));

        assertThatThrownBy(() -> service.assignDataScope(21L, req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Invalid data scope");
        verify(deptMapper, never()).deleteByRoleId(21L);
        verify(customerMapper, never()).deleteByRoleId(21L);
        verify(deptMapper, never()).insertIgnore(eq(21L), anyLong());
        verify(customerMapper, never()).insertIgnore(eq(21L), anyString(), anyString());
    }

    @Test
    void roleDataScopeAssignmentClearsConfiguredFieldsForSelfScope() {
        SystemRoleDataScopeDeptMapper deptMapper = mock(SystemRoleDataScopeDeptMapper.class);
        SystemRoleDataScopeCustomerMapper customerMapper = mock(SystemRoleDataScopeCustomerMapper.class);
        SystemRoleService service = roleServiceForAssignment(role(22L, "SELF_USER", "CUSTOMER", false), deptMapper, customerMapper);
        RoleDataScopeAssignReq req = dataScopeReq("SELF", List.of(10L), List.of("ACME"));

        service.assignDataScope(22L, req);

        verify(deptMapper).deleteByRoleId(22L);
        verify(customerMapper).deleteByRoleId(22L);
        verify(deptMapper, never()).insertIgnore(eq(22L), anyLong());
        verify(customerMapper, never()).insertIgnore(eq(22L), anyString(), anyString());
    }

    private DataScopeService serviceWithRoles(SystemRole... roles) {
        SystemUser systemUser = new SystemUser();
        systemUser.setId(100L);
        systemUser.setLegacyUserId(7);
        systemUser.setDeptId(20L);

        SystemUserMapper userMapper = mock(SystemUserMapper.class);
        when(userMapper.selectByLegacyUserId(org.mockito.ArgumentMatchers.any())).thenReturn(systemUser);

        SystemRoleMapper roleMapper = mock(SystemRoleMapper.class);
        when(roleMapper.selectActiveRolesByLegacyUserId(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(roles));


        SystemRoleDataScopeCustomerMapper customerMapper = mock(SystemRoleDataScopeCustomerMapper.class);

        DataScopeService service = new DataScopeService();
        ReflectionTestUtils.setField(service, "systemUserMapper", userMapper);
        ReflectionTestUtils.setField(service, "roleMapper", roleMapper);
        ReflectionTestUtils.setField(service, "dataScopeCustomerMapper", customerMapper);
        return service;
    }

    private SystemRoleService roleServiceForAssignment(
            SystemRole role,
            SystemRoleDataScopeDeptMapper deptMapper,
            SystemRoleDataScopeCustomerMapper customerMapper) {
        SystemRoleMapper roleMapper = mock(SystemRoleMapper.class);
        when(roleMapper.selectById(role.getId())).thenReturn(role);
        when(deptMapper.selectDeptIdsByRoleId(role.getId())).thenReturn(List.of());
        when(customerMapper.selectCustomerOptionsByRoleId(role.getId())).thenReturn(List.of());

        SystemRoleService service = new SystemRoleService();
        ReflectionTestUtils.setField(service, "roleMapper", roleMapper);
        ReflectionTestUtils.setField(service, "dataScopeDeptMapper", deptMapper);
        ReflectionTestUtils.setField(service, "dataScopeCustomerMapper", customerMapper);
        ReflectionTestUtils.setField(service, "auditService", mock(AuditService.class));
        return service;
    }

    private SystemRoleService roleServiceForAssignment(
            SystemRole role,
            SystemRoleDataScopeDeptMapper deptMapper,
            SystemRoleDataScopeCustomerMapper customerMapper,
            CustomerMasterMapper customerMasterMapper) {
        SystemRoleService service = roleServiceForAssignment(role, deptMapper, customerMapper);
        ReflectionTestUtils.setField(service, "customerMasterMapper", customerMasterMapper);
        return service;
    }

    private RoleDataScopeAssignReq dataScopeReq(String dataScope, List<Long> deptIds, List<String> customerCodes) {
        RoleDataScopeAssignReq req = new RoleDataScopeAssignReq();
        req.setDataScope(dataScope);
        req.setDeptIds(deptIds);
        req.setCustomerCodes(customerCodes);
        return req;
    }

    private CustomerMaster customer(String code, String name) {
        CustomerMaster customer = new CustomerMaster();
        customer.setCustomerCode(code);
        customer.setCustomerShortName(code);
        customer.setCustomerName(name);
        return customer;
    }

    private SystemRole role(Long id, String code, String dataScope, boolean superAdmin) {
        SystemRole role = new SystemRole();
        role.setId(id);
        role.setRoleCode(code);
        role.setDataScope(dataScope);
        role.setSuperAdmin(superAdmin);
        return role;
    }

    private void assertSourceContains(String relativePath, String expected) throws Exception {
        Path path = MAIN_JAVA.resolve(Path.of("com", "machining", "quote")).resolve(relativePath.replace("/", java.io.File.separator));
        assertThat(path).exists();
        assertThat(Files.readString(path, StandardCharsets.UTF_8)).contains(expected);
    }
}
