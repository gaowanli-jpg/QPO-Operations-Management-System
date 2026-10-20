package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.config.RequirePermission;
import com.machining.quote.controller.SalesOrderU9SyncController;
import com.machining.quote.dto.SalesOrderU9SyncOptionsResp;
import com.machining.quote.dto.u9.U9CommonEntityQueryReq;
import com.machining.quote.dto.u9.U9CommonEntityRowDTO;
import com.machining.quote.entity.U9MasterDataSyncRun;
import com.machining.quote.entity.U9SyncDepartmentOptionCache;
import com.machining.quote.entity.U9SyncSellerOptionCache;
import com.machining.quote.mapper.U9MasterDataSyncRunMapper;
import com.machining.quote.mapper.U9SyncDepartmentOptionCacheMapper;
import com.machining.quote.mapper.U9SyncSellerOptionCacheMapper;
import com.machining.quote.service.u9.U9CommonEntityClient;
import com.machining.quote.service.u9.U9MasterDataService;
import com.machining.quote.service.u9.U9MasterDataSyncService;
import com.machining.quote.service.u9.U9OrgCodeResolver;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class U9SyncOptionsCacheServiceTest {
    private static final String ORG_101 = "101";

    @Test
    void optionsEndpointReadsCacheAndRefreshEndpointUsesSubmitPermission() throws Exception {
        Method options = SalesOrderU9SyncController.class.getMethod("options");
        Method refresh = SalesOrderU9SyncController.class.getMethod("refreshOptions");

        assertThat(options.getAnnotation(org.springframework.web.bind.annotation.GetMapping.class).value())
                .containsExactly("/options");
        assertThat(options.getAnnotation(RequirePermission.class).value())
                .containsExactly("market:u9-sync:view");
        assertThat(refresh.getAnnotation(org.springframework.web.bind.annotation.PostMapping.class).value())
                .containsExactly("/options/refresh");
        assertThat(refresh.getAnnotation(RequirePermission.class).value())
                .containsExactly("market:u9-sync:submit");
    }

    @Test
    void optionsReadsEnabledSellerAndDepartmentRowsFromPersistentCache() {
        U9SyncSellerOptionCacheMapper sellerMapper = mock(U9SyncSellerOptionCacheMapper.class);
        U9SyncDepartmentOptionCacheMapper departmentMapper = mock(U9SyncDepartmentOptionCacheMapper.class);
        U9MasterDataService service = masterDataService(sellerMapper, departmentMapper,
                Clock.fixed(instant("2026-08-11T05:30:00Z"), ZoneId.of("Asia/Shanghai")));

        when(sellerMapper.selectList(any())).thenReturn(List.of(
                seller("C00150", "周洪", "200201", "订单运维部", true, "2026-08-11T05:00:00Z"),
                seller("C00200", "停用销售", "200201", "订单运维部", false, "2026-08-11T05:00:00Z")
        ));
        when(departmentMapper.selectList(any())).thenReturn(List.of(
                department("200201", "订单运维部", "销售部/订单运维部", true, "2026-08-11T05:00:00Z"),
                department("300001", "停用部门", "停用部门", false, "2026-08-11T05:00:00Z")
        ));

        SalesOrderU9SyncOptionsResp options = service.options();

        assertThat(options.getOrgCode()).isEqualTo(ORG_101);
        assertThat(options.getStale()).isFalse();
        assertThat(options.getRefreshedAt()).isEqualTo(LocalDateTime.of(2026, 8, 11, 13, 0));
        assertThat(options.getSellers())
                .extracting("code", "name", "departmentCode", "departmentName")
                .containsExactly(org.assertj.core.groups.Tuple.tuple("C00150", "周洪", "200201", "订单运维部"));
        assertThat(options.getDepartments())
                .extracting("code", "name", "treeDisplayName")
                .containsExactly(org.assertj.core.groups.Tuple.tuple("200201", "订单运维部", "销售部/订单运维部"));
    }

    @Test
    void emptyOrExpiredPersistentOptionsReturnWarningInsteadOfCallingU9Inline() {
        U9SyncSellerOptionCacheMapper sellerMapper = mock(U9SyncSellerOptionCacheMapper.class);
        U9SyncDepartmentOptionCacheMapper departmentMapper = mock(U9SyncDepartmentOptionCacheMapper.class);
        U9MasterDataService service = masterDataService(sellerMapper, departmentMapper,
                Clock.fixed(instant("2026-08-11T05:30:00Z"), ZoneId.of("Asia/Shanghai")));

        when(sellerMapper.selectList(any())).thenReturn(List.of(
                seller("C00150", "周洪", "200201", "订单运维部", true, "2026-08-11T04:00:00Z")
        ));
        when(departmentMapper.selectList(any())).thenReturn(List.of());

        SalesOrderU9SyncOptionsResp expired = service.options();

        assertThat(expired.getStale()).isTrue();
        assertThat(expired.getWarning()).contains("选项缓存已超过");
        assertThat(expired.getSellers()).extracting("code").containsExactly("C00150");

        when(sellerMapper.selectList(any())).thenReturn(List.of());
        SalesOrderU9SyncOptionsResp empty = service.options();

        assertThat(empty.getStale()).isTrue();
        assertThat(empty.getWarning()).contains("选项缓存为空");
        assertThat(empty.getSellers()).isEmpty();
        assertThat(empty.getDepartments()).isEmpty();
    }

    @Test
    void optionsReadsPersistentCacheForConfiguredU9OrgCode() {
        U9SyncSellerOptionCacheMapper sellerMapper = mock(U9SyncSellerOptionCacheMapper.class);
        U9SyncDepartmentOptionCacheMapper departmentMapper = mock(U9SyncDepartmentOptionCacheMapper.class);
        U9MasterDataService service = masterDataService(sellerMapper, departmentMapper,
                Clock.fixed(instant("2026-08-11T05:30:00Z"), ZoneId.of("Asia/Shanghai")));
        U9OrgCodeResolver resolver = orgResolver("106");
        ReflectionTestUtils.setField(service, "u9OrgCodeResolver", resolver);
        ArgumentCaptor<QueryWrapper<U9SyncSellerOptionCache>> sellerQueryCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        ArgumentCaptor<QueryWrapper<U9SyncDepartmentOptionCache>> departmentQueryCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        when(sellerMapper.selectList(sellerQueryCaptor.capture())).thenReturn(List.of());
        when(departmentMapper.selectList(departmentQueryCaptor.capture())).thenReturn(List.of());

        SalesOrderU9SyncOptionsResp options = service.options();

        assertThat(options.getOrgCode()).isEqualTo("106");
        verify(resolver).effectiveOrgCode();
        assertThat(sellerQueryCaptor.getValue().getSqlSegment()).contains("u9_org_code");
        assertThat(departmentQueryCaptor.getValue().getSqlSegment()).contains("u9_org_code");
    }

    @Test
    void refreshSyncsSellerAndDepartmentOptionsIntoPersistentCacheAndDisablesMissingRows() {
        U9CommonEntityClient client = mock(U9CommonEntityClient.class);
        U9SyncSellerOptionCacheMapper sellerMapper = mock(U9SyncSellerOptionCacheMapper.class);
        U9SyncDepartmentOptionCacheMapper departmentMapper = mock(U9SyncDepartmentOptionCacheMapper.class);
        U9MasterDataSyncRunMapper syncRunMapper = mock(U9MasterDataSyncRunMapper.class);
        U9MasterDataSyncService service = syncService(client, sellerMapper, departmentMapper, syncRunMapper,
                Clock.fixed(instant("2026-08-11T04:00:00Z"), ZoneId.of("Asia/Shanghai")));
        when(client.query(any())).thenAnswer(invocation -> pageFor(invocation.getArgument(0)));
        when(sellerMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(
                existingSeller(11L, "C00150", true),
                existingSeller(12L, "OLD-SELLER", true)
        )));
        when(departmentMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(
                existingDepartment(21L, "200201", true),
                existingDepartment(22L, "OLD-DEPT", true)
        )));
        when(syncRunMapper.insert(any())).thenAnswer(invocation -> {
            U9MasterDataSyncRun run = invocation.getArgument(0);
            run.setId(8801L);
            return 1;
        });

        U9MasterDataSyncService.SyncResult result = service.syncOptionsManual(99L);

        assertThat(result.success()).isTrue();
        assertThat(result.fetchedCount()).isEqualTo(12);
        assertThat(result.upsertedCount()).isGreaterThanOrEqualTo(4);
        assertThat(result.disabledCount()).isEqualTo(2);
        verify(sellerMapper).insert(argThat(row -> "C00999".equals(row.getSellerCode()) && Boolean.TRUE.equals(row.getEnabled())));
        verify(sellerMapper, times(2)).updateById(any(U9SyncSellerOptionCache.class));
        verify(departmentMapper).insert(argThat(row -> "900001".equals(row.getDepartmentCode()) && Boolean.TRUE.equals(row.getEnabled())));
        verify(departmentMapper, times(2)).updateById(any(U9SyncDepartmentOptionCache.class));
        verify(syncRunMapper).updateById(argThat(run -> "U9_SYNC_OPTIONS".equals(run.getSyncType()) && "SUCCESS".equals(run.getStatus())));
    }

    @Test
    void refreshPaginatesU9SellerAndDepartmentQueriesForOrganization101() {
        U9CommonEntityClient client = mock(U9CommonEntityClient.class);
        U9SyncSellerOptionCacheMapper sellerMapper = mock(U9SyncSellerOptionCacheMapper.class);
        U9SyncDepartmentOptionCacheMapper departmentMapper = mock(U9SyncDepartmentOptionCacheMapper.class);
        U9MasterDataSyncRunMapper syncRunMapper = mock(U9MasterDataSyncRunMapper.class);
        U9MasterDataSyncService service = syncService(client, sellerMapper, departmentMapper, syncRunMapper,
                Clock.fixed(instant("2026-08-11T04:00:00Z"), ZoneId.of("Asia/Shanghai")));
        ArgumentCaptor<U9CommonEntityQueryReq> requestCaptor = ArgumentCaptor.forClass(U9CommonEntityQueryReq.class);
        when(client.query(requestCaptor.capture())).thenAnswer(invocation -> pageFor(invocation.getArgument(0)));
        when(sellerMapper.selectList(any())).thenReturn(List.of());
        when(departmentMapper.selectList(any())).thenReturn(List.of());

        service.syncOptionsManual(99L);

        assertThat(requestCaptor.getAllValues())
                .extracting(U9CommonEntityQueryReq::getEntityFullName)
                .contains(U9MasterDataService.OPERATOR_ENTITY, U9MasterDataService.DEPARTMENT_ENTITY);
        assertThat(requestCaptor.getAllValues())
                .allSatisfy(req -> {
                    assertThat(req.getPageSize()).isEqualTo(200);
                    assertThat(req.getFilters()).anySatisfy(filter -> {
                        assertThat(filter.getField()).isEqualTo("Org.Code");
                        assertThat(filter.getOperator()).isEqualTo("eq");
                        assertThat(filter.getValue()).isEqualTo(ORG_101);
                    });
                });
    }

    @Test
    void refreshUsesConfiguredU9OrgCodeForSellerAndDepartmentQueries() {
        U9CommonEntityClient client = mock(U9CommonEntityClient.class);
        U9SyncSellerOptionCacheMapper sellerMapper = mock(U9SyncSellerOptionCacheMapper.class);
        U9SyncDepartmentOptionCacheMapper departmentMapper = mock(U9SyncDepartmentOptionCacheMapper.class);
        U9MasterDataSyncRunMapper syncRunMapper = mock(U9MasterDataSyncRunMapper.class);
        U9MasterDataSyncService service = syncService(client, sellerMapper, departmentMapper, syncRunMapper,
                Clock.fixed(instant("2026-08-11T04:00:00Z"), ZoneId.of("Asia/Shanghai")));
        ReflectionTestUtils.setField(service, "u9OrgCodeResolver", orgResolver("106"));
        ArgumentCaptor<U9CommonEntityQueryReq> requestCaptor = ArgumentCaptor.forClass(U9CommonEntityQueryReq.class);
        when(client.query(requestCaptor.capture())).thenAnswer(invocation -> pageForOrg106(invocation.getArgument(0)));
        when(sellerMapper.selectList(any())).thenReturn(List.of());
        when(departmentMapper.selectList(any())).thenReturn(List.of());
        when(syncRunMapper.insert(any())).thenAnswer(invocation -> {
            U9MasterDataSyncRun run = invocation.getArgument(0);
            run.setId(8806L);
            return 1;
        });

        U9MasterDataSyncService.SyncResult result = service.syncOptionsManual(106L);

        assertThat(result.success()).isTrue();
        assertThat(requestCaptor.getAllValues()).allSatisfy(req -> assertThat(req.getFilters()).anySatisfy(filter -> {
            assertThat(filter.getField()).isEqualTo("Org.Code");
            assertThat(filter.getValue()).isEqualTo("106");
        }));
    }

    private U9MasterDataService masterDataService(
            U9SyncSellerOptionCacheMapper sellerMapper,
            U9SyncDepartmentOptionCacheMapper departmentMapper,
            Clock clock) {
        U9MasterDataService service = new U9MasterDataService();
        ReflectionTestUtils.setField(service, "sellerOptionMapper", sellerMapper);
        ReflectionTestUtils.setField(service, "departmentOptionMapper", departmentMapper);
        ReflectionTestUtils.setField(service, "u9OrgCodeResolver", orgResolver(ORG_101));
        ReflectionTestUtils.setField(service, "clock", clock);
        return service;
    }

    private U9MasterDataSyncService syncService(
            U9CommonEntityClient client,
            U9SyncSellerOptionCacheMapper sellerMapper,
            U9SyncDepartmentOptionCacheMapper departmentMapper,
            U9MasterDataSyncRunMapper syncRunMapper,
            Clock clock) {
        U9MasterDataSyncService service = new U9MasterDataSyncService();
        ReflectionTestUtils.setField(service, "commonEntityClient", client);
        ReflectionTestUtils.setField(service, "sellerOptionMapper", sellerMapper);
        ReflectionTestUtils.setField(service, "departmentOptionMapper", departmentMapper);
        ReflectionTestUtils.setField(service, "syncRunMapper", syncRunMapper);
        ReflectionTestUtils.setField(service, "u9OrgCodeResolver", orgResolver(ORG_101));
        ReflectionTestUtils.setField(service, "clock", clock);
        return service;
    }

    private U9OrgCodeResolver orgResolver(String orgCode) {
        U9OrgCodeResolver resolver = mock(U9OrgCodeResolver.class);
        when(resolver.effectiveOrgCode()).thenReturn(orgCode);
        return resolver;
    }

    private List<U9CommonEntityRowDTO> pageFor(U9CommonEntityQueryReq req) {
        if (U9MasterDataService.OPERATOR_ENTITY.equals(req.getEntityFullName())) {
            return sellerPage(req.getPageIndex());
        }
        if (U9MasterDataService.DEPARTMENT_ENTITY.equals(req.getEntityFullName())) {
            return departmentPage(req.getPageIndex());
        }
        return List.of();
    }

    private List<U9CommonEntityRowDTO> pageForOrg106(U9CommonEntityQueryReq req) {
        if (U9MasterDataService.OPERATOR_ENTITY.equals(req.getEntityFullName())) {
            return List.of(row(Map.of(
                    "ID", "S106",
                    "Code", "S106",
                    "Name", "Seller 106",
                    "Org.Code", "106",
                    "Dept.Code", "D106",
                    "Dept.Name", "Dept 106",
                    "Effective.IsEffective", true,
                    "OperatorLine.OperatorType", List.of(U9MasterDataService.SALES_OPERATOR_TYPE)
            )));
        }
        return List.of();
    }

    private List<U9CommonEntityRowDTO> sellerPage(int pageIndex) {
        if (pageIndex > 1) return List.of();
        return List.of(
                row(Map.of("ID", "S1", "Code", "C00150", "Name", "周洪", "Org.Code", ORG_101, "Dept.Code", "200201", "Dept.Name", "订单运维部", "Effective.IsEffective", true, "OperatorLine.OperatorType", List.of("销售业务员"))),
                row(Map.of("ID", "S1-DUP", "Code", "C00150", "Name", "重复周洪", "Org.Code", ORG_101, "Dept.Code", "200201", "Dept.Name", "订单运维部", "Effective.IsEffective", true, "OperatorLine.OperatorType", List.of("销售业务员"))),
                row(Map.of("ID", "S2", "Code", "C00999", "Name", "树外销售", "Org.Code", ORG_101, "Dept.Code", "900001", "Dept.Name", "特殊销售部", "Effective.IsEffective", true, "OperatorLine.OperatorType", List.of("销售业务员"))),
                row(Map.of("ID", "S3", "Code", "C00300", "Name", "采购员", "Org.Code", ORG_101, "Dept.Code", "200201", "Dept.Name", "订单运维部", "Effective.IsEffective", true, "OperatorLine.OperatorType", List.of("采购员"))),
                row(Map.of("ID", "S4", "Code", "C00400", "Name", "失效销售", "Org.Code", ORG_101, "Dept.Code", "200201", "Dept.Name", "订单运维部", "Effective.IsEffective", false, "OperatorLine.OperatorType", List.of("销售业务员"))),
                row(Map.of("ID", "S5", "Code", "C00500", "Name", "其他组织", "Org.Code", "102", "Dept.Code", "200201", "Dept.Name", "订单运维部", "Effective.IsEffective", true, "OperatorLine.OperatorType", List.of("销售业务员")))
        );
    }

    private List<U9CommonEntityRowDTO> departmentPage(int pageIndex) {
        if (pageIndex > 1) return List.of();
        return List.of(
                row(Map.of("ID", "D1", "Code", "2002", "Name", "销售部", "Org.Code", ORG_101, "Effective.IsEffective", true, "TreeDisplayName", "销售部")),
                row(Map.of("ID", "D2", "Code", "200201", "Name", "订单运维部", "Org.Code", ORG_101, "ParentNode.Code", "2002", "Effective.IsEffective", true, "TreeDisplayName", "销售部/订单运维部")),
                row(Map.of("ID", "D3", "Code", "900001", "Name", "特殊销售部", "Org.Code", ORG_101, "Effective.IsEffective", true, "TreeDisplayName", "特殊销售部")),
                row(Map.of("ID", "D4", "Code", "200202", "Name", "失效部门", "Org.Code", ORG_101, "ParentNode.Code", "2002", "Effective.IsEffective", false, "TreeDisplayName", "销售部/失效部门")),
                row(Map.of("ID", "D5", "Code", "300001", "Name", "非销售部门", "Org.Code", ORG_101, "Effective.IsEffective", true, "TreeDisplayName", "非销售部门")),
                row(Map.of("ID", "D6", "Code", "200203", "Name", "其他组织部门", "Org.Code", "102", "ParentNode.Code", "2002", "Effective.IsEffective", true, "TreeDisplayName", "销售部/其他组织部门"))
        );
    }

    private U9SyncSellerOptionCache seller(String code, String name, String departmentCode, String departmentName, boolean enabled, String syncedAt) {
        U9SyncSellerOptionCache row = existingSeller(null, code, enabled);
        row.setSellerName(name);
        row.setDepartmentCode(departmentCode);
        row.setDepartmentName(departmentName);
        row.setLastSyncedAt(LocalDateTime.ofInstant(instant(syncedAt), ZoneId.of("Asia/Shanghai")));
        return row;
    }

    private U9SyncSellerOptionCache existingSeller(Long id, String code, boolean enabled) {
        U9SyncSellerOptionCache row = new U9SyncSellerOptionCache();
        row.setId(id);
        row.setU9OrgCode(ORG_101);
        row.setSellerCode(code);
        row.setSellerName(code + " name");
        row.setDepartmentCode("200201");
        row.setDepartmentName("订单运维部");
        row.setEnabled(enabled);
        return row;
    }

    private U9SyncDepartmentOptionCache department(String code, String name, String treeDisplayName, boolean enabled, String syncedAt) {
        U9SyncDepartmentOptionCache row = existingDepartment(null, code, enabled);
        row.setDepartmentName(name);
        row.setTreeDisplayName(treeDisplayName);
        row.setLastSyncedAt(LocalDateTime.ofInstant(instant(syncedAt), ZoneId.of("Asia/Shanghai")));
        return row;
    }

    private U9SyncDepartmentOptionCache existingDepartment(Long id, String code, boolean enabled) {
        U9SyncDepartmentOptionCache row = new U9SyncDepartmentOptionCache();
        row.setId(id);
        row.setU9OrgCode(ORG_101);
        row.setDepartmentCode(code);
        row.setDepartmentName(code + " name");
        row.setEnabled(enabled);
        return row;
    }

    private static U9CommonEntityRowDTO row(Map<String, Object> values) {
        U9CommonEntityRowDTO row = new U9CommonEntityRowDTO();
        row.setValues(new LinkedHashMap<>(values));
        return row;
    }

    private static Instant instant(String value) {
        return Instant.parse(value);
    }
}
