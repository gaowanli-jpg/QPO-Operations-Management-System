package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.config.AuthContext;
import com.machining.quote.entity.AuditLog;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.entity.SystemRole;
import com.machining.quote.entity.SystemUser;
import com.machining.quote.mapper.SystemRoleDataScopeCustomerMapper;
import com.machining.quote.mapper.SystemRoleDataScopeDeptMapper;
import com.machining.quote.mapper.SystemRoleMapper;
import com.machining.quote.mapper.SystemUserMapper;
import com.machining.quote.service.DataScopeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PermissionSystemStage6Test {
    private static final Path MAIN_JAVA = Path.of("src", "main", "java");
    private static final Path MIGRATION_DIR = Path.of("src", "main", "resources", "db", "migration");

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void migrationAddsOrderOwnershipColumnsForDataScope() throws Exception {
        Path migration = MIGRATION_DIR.resolve("V27__data_scope_order_ownership.sql");

        assertThat(migration).exists();
        String sql = Files.readString(migration, StandardCharsets.UTF_8).toLowerCase();
        assertThat(sql)
                .contains("alter table order_rfq")
                .contains("created_by")
                .contains("owner_user_id")
                .contains("owner_dept_id")
                .contains("idx_order_rfq_owner_user")
                .contains("idx_order_rfq_owner_dept")
                .contains("idx_order_rfq_customer");
    }

    @Test
    void selfDataScopeFiltersOrdersByCreatorOrOwner() {
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
        when(customerMapper.selectCustomerNamesByRoleId(2L)).thenReturn(List.of("ACME", "Globex"));
        ReflectionTestUtils.setField(service, "dataScopeCustomerMapper", customerMapper);
        AuthContext.set(new AuthContext.SessionUser(8, "sales", "Sales", "MARKET"));
        QueryWrapper<OrderRfq> query = new QueryWrapper<>();

        service.applyOrderScope(query);

        assertThat(query.getCustomSqlSegment().toLowerCase()).contains("customer_name");
        assertThat(query.getParamNameValuePairs()).containsValue("ACME").containsValue("Globex");
    }

    @Test
    void multipleRoleDataScopesAreUnionedForOrderQueries() {
        DataScopeService service = serviceWithRoles(
                role(5L, "SELF_MARKET", "SELF", false),
                role(6L, "KEY_ACCOUNT", "CUSTOMER", false));
        SystemRoleDataScopeCustomerMapper customerMapper = mock(SystemRoleDataScopeCustomerMapper.class);
        when(customerMapper.selectCustomerNamesByRoleId(6L)).thenReturn(List.of("ACME"));
        ReflectionTestUtils.setField(service, "dataScopeCustomerMapper", customerMapper);
        AuthContext.set(new AuthContext.SessionUser(7, "market", "Market", "MARKET"));
        QueryWrapper<OrderRfq> query = new QueryWrapper<>();

        service.applyOrderScope(query);

        String sql = query.getCustomSqlSegment().toLowerCase();
        assertThat(sql)
                .contains("created_by")
                .contains("owner_user_id")
                .contains("customer_name")
                .contains(" or ");
        assertThat(query.getParamNameValuePairs()).containsValue(7).containsValue("ACME");
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
        assertSourceContains("service/AuditLogQueryService.java", "dataScopeService.applyAuditScope");
        assertSourceContains("service/AuditLogQueryService.java", "applyAuditDataScope(qw);");
        assertSourceContains("service/AuditLogQueryService.java", "applyAuditDataScope(actionQuery);");
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

        SystemRoleDataScopeDeptMapper deptMapper = mock(SystemRoleDataScopeDeptMapper.class);
        for (SystemRole role : roles) {
            when(deptMapper.selectDeptIdsByRoleId(role.getId())).thenReturn(List.of(20L, 21L));
        }

        SystemRoleDataScopeCustomerMapper customerMapper = mock(SystemRoleDataScopeCustomerMapper.class);

        DataScopeService service = new DataScopeService();
        ReflectionTestUtils.setField(service, "systemUserMapper", userMapper);
        ReflectionTestUtils.setField(service, "roleMapper", roleMapper);
        ReflectionTestUtils.setField(service, "dataScopeDeptMapper", deptMapper);
        ReflectionTestUtils.setField(service, "dataScopeCustomerMapper", customerMapper);
        return service;
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
