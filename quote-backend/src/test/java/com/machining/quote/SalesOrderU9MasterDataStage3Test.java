package com.machining.quote;

import com.machining.quote.config.DataCenterProperties;
import com.machining.quote.config.RequirePermission;
import com.machining.quote.controller.SalesOrderU9SyncController;
import com.machining.quote.dto.SalesOrderU9SyncOptionsResp;
import com.machining.quote.dto.u9.U9CommonEntityQueryReq;
import com.machining.quote.dto.u9.U9CommonEntityResp;
import com.machining.quote.dto.u9.U9CommonEntityRowDTO;
import com.machining.quote.entity.SystemDataCenterConfig;
import com.machining.quote.entity.SystemDataCenterEndpointConfig;
import com.machining.quote.mapper.SystemDataCenterEndpointConfigMapper;
import com.machining.quote.service.DataCenterConfigService;
import com.machining.quote.service.DataCenterEndpointRegistry;
import com.machining.quote.service.DataCenterIntegrationClient;
import com.machining.quote.service.DataCenterTokenService;
import com.machining.quote.service.u9.U9CommonEntityClient;
import com.machining.quote.service.u9.U9MasterDataService;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

class SalesOrderU9MasterDataStage3Test {
    private static final String COMMON_ENTITY = "U9_COMMON_ENTITY_QUERY";
    private static final String ORG_101 = "101";

    @Test
    void registryExposesU9CommonEntityEndpointFallbackAndMinimumTimeout() {
        DataCenterEndpointRegistry registry = new DataCenterEndpointRegistry();
        DataCenterProperties properties = new DataCenterProperties();
        properties.setU9CommonEntityQueryPath("/dc/u9/common-entity");

        SystemDataCenterEndpointConfig commonEndpoint = registry.newEndpoint(COMMON_ENTITY);
        SystemDataCenterEndpointConfig createEndpoint = registry.newEndpoint(DataCenterEndpointRegistry.U9_SALES_ORDER_SYNC);

        assertThat(registry.definitions())
                .extracting(DataCenterEndpointRegistry.EndpointDefinition::endpointCode)
                .contains(COMMON_ENTITY);
        assertThat(registry.requireDefinition(COMMON_ENTITY))
                .satisfies(definition -> {
                    assertThat(definition.displayName()).isEqualTo("U9通用实体查询");
                    assertThat(definition.targetSystemCode()).isEqualTo("U9");
                    assertThat(definition.requestAdapter()).isEqualTo("U9_COMMON_ENTITY_QUERY_V1");
                    assertThat(definition.responseAdapter()).isEqualTo("U9_COMMON_ENTITY_RESPONSE_V1");
                    assertThat(definition.minimumTimeoutMs()).isEqualTo(60000);
                });
        assertThat(commonEndpoint.getTimeoutMs()).isEqualTo(60000);
        assertThat(createEndpoint.getTimeoutMs()).isEqualTo(60000);
        assertThat(registry.fallbackPath(COMMON_ENTITY, properties)).isEqualTo("/dc/u9/common-entity");
    }

    @Test
    void optionsEndpointUsesMarketU9ViewPermission() throws Exception {
        Method method = SalesOrderU9SyncController.class.getMethod("options");

        assertThat(method.getAnnotation(org.springframework.web.bind.annotation.GetMapping.class).value())
                .containsExactly("/options");
        assertThat(method.getAnnotation(RequirePermission.class).value())
                .containsExactly("market:u9-sync:view");
    }

    @Test
    void commonEntityClientCallsDataCenterEndpointAndDropsFailureDataHeaders() {
        DataCenterIntegrationClient dataCenterClient = mock(DataCenterIntegrationClient.class);
        U9CommonEntityClient client = new U9CommonEntityClient();
        ReflectionTestUtils.setField(client, "dataCenterIntegrationClient", dataCenterClient);
        U9CommonEntityQueryReq req = U9CommonEntityQueryReq.builder()
                .entityFullName("UFIDA.U9.CBO.HR.Operator.Operators")
                .returnFields(List.of("Code", "Name", "Org.Code"))
                .filter("Org.Code", "eq", ORG_101)
                .page(1, 200)
                .build();
        U9CommonEntityResp ok = new U9CommonEntityResp();
        ok.setResCode(0);
        ok.setSuccess(true);
        ok.setData(List.of(row(Map.of(
                "Code", "C00150",
                "Name", "周洪",
                "Org.Code", ORG_101,
                "Authorization", "Bearer must-not-surface"))));
        when(dataCenterClient.call(eq(COMMON_ENTITY), any(), eq(U9CommonEntityResp.class))).thenReturn(ok);

        List<U9CommonEntityRowDTO> rows = client.query(req);

        assertThat(rows).singleElement().satisfies(result -> {
            assertThat(result.text("Code")).isEqualTo("C00150");
            assertThat(result.text("Name")).isEqualTo("周洪");
            assertThat(result.text("Org.Code")).isEqualTo(ORG_101);
            assertThat(result.text("Authorization")).isNull();
        });

        U9CommonEntityResp failure = new U9CommonEntityResp();
        failure.setResCode(500);
        failure.setSuccess(false);
        failure.setResMsg("metadata error token=hidden-token Authorization: Bearer hidden-auth client_secret=hidden-secret");
        failure.setData(List.of(row(Map.of("Headers", Map.of("token", "hidden-token")))));
        when(dataCenterClient.call(eq(COMMON_ENTITY), any(), eq(U9CommonEntityResp.class))).thenReturn(failure);

        assertThatThrownBy(() -> client.query(req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("U9 common entity query failed")
                .hasMessageNotContaining("hidden-token")
                .hasMessageNotContaining("hidden-auth")
                .hasMessageNotContaining("hidden-secret")
                .hasMessageNotContaining("Headers");
    }

    @Test
    void masterDataOptionsAreServedFromPersistentCacheAfterStageFive() throws Exception {
        assertThat(U9MasterDataService.class.getDeclaredField("sellerOptionMapper")).isNotNull();
        assertThat(U9MasterDataService.class.getDeclaredField("departmentOptionMapper")).isNotNull();
        assertThatThrownBy(() -> U9MasterDataService.class.getDeclaredField("commonEntityClient"))
                .isInstanceOf(NoSuchFieldException.class);
    }

    @Test
    void dataCenterRuntimeClampsU9CreateAndQueryReadTimeoutsToSixtySeconds() throws Exception {
        DataCenterIntegrationClient client = new DataCenterIntegrationClient();
        SystemDataCenterConfig config = new SystemDataCenterConfig();
        config.setReadTimeoutMs(1000);

        SystemDataCenterEndpointConfig common = endpoint(COMMON_ENTITY, 1000);
        SystemDataCenterEndpointConfig create = endpoint(DataCenterEndpointRegistry.U9_SALES_ORDER_SYNC, 1000);
        SystemDataCenterEndpointConfig mes = endpoint(DataCenterEndpointRegistry.MES_MATERIAL_RESOLVE, 1000);

        assertThat((Integer) ReflectionTestUtils.invokeMethod(client, "resolveReadTimeout", config, common)).isEqualTo(60000);
        assertThat((Integer) ReflectionTestUtils.invokeMethod(client, "resolveReadTimeout", config, create)).isEqualTo(60000);
        assertThat((Integer) ReflectionTestUtils.invokeMethod(client, "resolveReadTimeout", config, mes)).isEqualTo(60000);
    }

    private U9MasterDataService masterDataService(U9CommonEntityClient client, Clock clock) {
        U9MasterDataService service = new U9MasterDataService();
        ReflectionTestUtils.setField(service, "commonEntityClient", client);
        ReflectionTestUtils.setField(service, "orgCode", ORG_101);
        ReflectionTestUtils.setField(service, "clock", clock);
        return service;
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

    private List<U9CommonEntityRowDTO> sellerPage(int pageIndex) {
        if (pageIndex > 1) {
            return List.of();
        }
        return List.of(
                row(Map.of("Code", "C00150", "Name", "周洪", "Org.Code", ORG_101, "Dept.Code", "200201", "Dept.Name", "订单运维部", "Effective.IsEffective", true, "OperatorLine.OperatorType", List.of("销售业务员"))),
                row(Map.of("Code", "C00150", "Name", "重复周洪", "Org.Code", ORG_101, "Dept.Code", "200201", "Dept.Name", "订单运维部", "Effective.IsEffective", true, "OperatorLine.OperatorType", List.of("销售业务员"))),
                row(Map.of("Code", "C00200", "Name", "采购员", "Org.Code", ORG_101, "Dept.Code", "200201", "Dept.Name", "订单运维部", "Effective.IsEffective", true, "OperatorLine.OperatorType", List.of("采购员"))),
                row(Map.of("Code", "C00300", "Name", "失效销售", "Org.Code", ORG_101, "Dept.Code", "200201", "Dept.Name", "订单运维部", "Effective.IsEffective", false, "OperatorLine.OperatorType", List.of("销售业务员"))),
                row(Map.of("Code", "C00400", "Name", "其他组织销售", "Org.Code", "102", "Dept.Code", "200201", "Dept.Name", "订单运维部", "Effective.IsEffective", true, "OperatorLine.OperatorType", List.of("销售业务员"))),
                row(Map.of("Code", "C00999", "Name", "树外销售", "Org.Code", ORG_101, "Dept.Code", "900001", "Dept.Name", "特殊销售部", "Effective.IsEffective", true, "OperatorLine.OperatorType", List.of("销售业务员")))
        );
    }

    private List<U9CommonEntityRowDTO> departmentPage(int pageIndex) {
        if (pageIndex > 1) {
            return List.of();
        }
        return List.of(
                row(Map.of("Code", "2002", "Name", "销售部", "Org.Code", ORG_101, "Effective.IsEffective", true, "TreeDisplayName", "市场营销中心/销售部")),
                row(Map.of("Code", "200201", "Name", "订单运维部", "Org.Code", ORG_101, "ParentNode.Code", "2002", "Effective.IsEffective", true, "TreeDisplayName", "市场营销中心/销售部/订单运维部")),
                row(Map.of("Code", "200202", "Name", "失效部门", "Org.Code", ORG_101, "ParentNode.Code", "2002", "Effective.IsEffective", false, "TreeDisplayName", "市场营销中心/销售部/失效部门")),
                row(Map.of("Code", "3001", "Name", "非销售部门", "Org.Code", ORG_101, "Effective.IsEffective", true, "TreeDisplayName", "制造中心/生产部")),
                row(Map.of("Code", "900001", "Name", "特殊销售部", "Org.Code", ORG_101, "Effective.IsEffective", true, "TreeDisplayName", "特殊销售部")),
                row(Map.of("Code", "200203", "Name", "其他组织部门", "Org.Code", "102", "ParentNode.Code", "2002", "Effective.IsEffective", true, "TreeDisplayName", "市场营销中心/销售部/其他组织"))
        );
    }

    private static U9CommonEntityRowDTO row(Map<String, Object> values) {
        U9CommonEntityRowDTO row = new U9CommonEntityRowDTO();
        row.setValues(new LinkedHashMap<>(values));
        return row;
    }

    private static SystemDataCenterEndpointConfig endpoint(String endpointCode, Integer timeoutMs) {
        SystemDataCenterEndpointConfig endpoint = new SystemDataCenterEndpointConfig();
        endpoint.setEndpointCode(endpointCode);
        endpoint.setTimeoutMs(timeoutMs);
        return endpoint;
    }

    private static Instant instant(String value) {
        return Instant.parse(value);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void setInstant(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("Asia/Shanghai");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
