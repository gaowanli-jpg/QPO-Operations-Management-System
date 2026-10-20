package com.machining.quote;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.machining.quote.dto.mes.master.MesMaterialPageResp;
import com.machining.quote.dto.mes.master.MesProcessTypeListResp;
import com.machining.quote.service.DataCenterEndpointRegistry;
import com.machining.quote.service.DataCenterIntegrationClient;
import com.machining.quote.service.mes.MesMasterDataClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MesMasterDataClientTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void listProcessTypesParsesStableRowsFromDataCenterResponse() throws Exception {
        DataCenterIntegrationClient dataCenterClient = mock(DataCenterIntegrationClient.class);
        MesMasterDataClient client = client(dataCenterClient);
        MesProcessTypeListResp resp = JSON.readValue("""
                {
                  "code": 0,
                  "msg": "ok",
                  "data": [
                    {
                      "id": "pt-100",
                      "code": "PROC-CNC",
                      "name": "CNC machining",
                      "hourPrice": 128.50,
                      "deptId": "D-01",
                      "deptName": "Machining Center",
                      "status": "ENABLE",
                      "remark": "from MES",
                      "createTime": "2026-08-01 10:00:00",
                      "updateTime": "2026-08-02 11:00:00"
                    }
                  ]
                }
                """, MesProcessTypeListResp.class);
        when(dataCenterClient.callGet(
                eq(DataCenterEndpointRegistry.MES_PROCESS_TYPE_LIST),
                eq(Map.of()),
                eq(MesProcessTypeListResp.class)
        )).thenReturn(resp);

        List<MesProcessTypeListResp.Row> rows = client.listProcessTypes();

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.getId()).isEqualTo("pt-100");
            assertThat(row.getCode()).isEqualTo("PROC-CNC");
            assertThat(row.getName()).isEqualTo("CNC machining");
            assertThat(row.getHourPrice()).isEqualByComparingTo(new BigDecimal("128.50"));
            assertThat(row.getDeptId()).isEqualTo("D-01");
            assertThat(row.getDeptName()).isEqualTo("Machining Center");
            assertThat(row.getStatus()).isEqualTo("ENABLE");
            assertThat(row.getRemark()).isEqualTo("from MES");
            assertThat(row.getCreateTime()).isEqualTo("2026-08-01 10:00:00");
            assertThat(row.getUpdateTime()).isEqualTo("2026-08-02 11:00:00");
        });
    }

    @Test
    void listProcessTypesIgnoresUnknownMesFields() throws Exception {
        MesProcessTypeListResp resp = JSON.readValue("""
                {
                  "code": 0,
                  "msg": "ok",
                  "data": [
                    {
                      "id": "pt-auto",
                      "code": "AUTO-CNC",
                      "name": "Auto dispatch CNC",
                      "hourPrice": 99.25,
                      "status": "ENABLE",
                      "autoDispatch": true
                    }
                  ]
                }
                """, MesProcessTypeListResp.class);

        assertThat(resp.rows()).singleElement().satisfies(row -> {
            assertThat(row.getId()).isEqualTo("pt-auto");
            assertThat(row.getCode()).isEqualTo("AUTO-CNC");
            assertThat(row.getName()).isEqualTo("Auto dispatch CNC");
            assertThat(row.getHourPrice()).isEqualByComparingTo(new BigDecimal("99.25"));
        });
    }

    @Test
    void pageMaterialsParsesTotalListAndSendsOnlyPagingQuery() throws Exception {
        DataCenterIntegrationClient dataCenterClient = mock(DataCenterIntegrationClient.class);
        MesMasterDataClient client = client(dataCenterClient);
        MesMaterialPageResp resp = JSON.readValue("""
                {
                  "code": "0",
                  "data": {
                    "total": 1,
                    "list": [
                      {
                        "id": "mat-200",
                        "code": "AL-6061",
                        "name": "Aluminum 6061",
                        "materialType": "MATERIAL",
                        "purchaseType": "BUY",
                        "productionType": "OUTSOURCE",
                        "standard": "T6",
                        "docNum": "DOC-9",
                        "weight": 12.34,
                        "density": 2700,
                        "status": "ENABLE"
                      }
                    ]
                  }
                }
                """, MesMaterialPageResp.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> queryCaptor = ArgumentCaptor.forClass(Map.class);
        when(dataCenterClient.callGet(
                eq(DataCenterEndpointRegistry.MES_MATERIAL_PAGE),
                queryCaptor.capture(),
                eq(MesMaterialPageResp.class)
        )).thenReturn(resp);

        MesMaterialPageResp.Data page = client.pageMaterials(1, 200);

        assertThat(queryCaptor.getValue()).containsExactlyInAnyOrderEntriesOf(Map.of("pageNo", 1, "pageSize", 200));
        assertThat(page.getTotal()).isEqualTo(1L);
        assertThat(page.getList()).singleElement().satisfies(row -> {
            assertThat(row.getId()).isEqualTo("mat-200");
            assertThat(row.getCode()).isEqualTo("AL-6061");
            assertThat(row.getName()).isEqualTo("Aluminum 6061");
            assertThat(row.getMaterialType()).isEqualTo("MATERIAL");
            assertThat(row.getPurchaseType()).isEqualTo("BUY");
            assertThat(row.getProductionType()).isEqualTo("OUTSOURCE");
            assertThat(row.getStandard()).isEqualTo("T6");
            assertThat(row.getDocNum()).isEqualTo("DOC-9");
            assertThat(row.getWeight()).isEqualByComparingTo(new BigDecimal("12.34"));
            assertThat(row.getDensity()).isEqualByComparingTo(new BigDecimal("2700"));
            assertThat(row.getStatus()).isEqualTo("ENABLE");
        });
    }

    @Test
    void pageMaterialsIgnoresUnknownMesFields() throws Exception {
        MesMaterialPageResp resp = JSON.readValue("""
                {
                  "code": "0",
                  "data": {
                    "total": 1,
                    "list": [
                      {
                        "id": "mat-extra",
                        "code": "SUS-304",
                        "name": "Stainless 304",
                        "materialType": "STEEL",
                        "density": 7930,
                        "status": "ENABLE",
                        "unitCode": "KG",
                        "unitName": "kilogram"
                      }
                    ]
                  }
                }
                """, MesMaterialPageResp.class);

        MesMaterialPageResp.Data page = resp.toData();

        assertThat(page.getList()).singleElement().satisfies(row -> {
            assertThat(row.getCode()).isEqualTo("SUS-304");
            assertThat(row.getName()).isEqualTo("Stainless 304");
            assertThat(row.getDensity()).isEqualByComparingTo(new BigDecimal("7930"));
        });
    }

    @Test
    void businessFailureThrowsSafeMessageWithoutTokensAuthorizationOrFullBody() throws Exception {
        DataCenterIntegrationClient dataCenterClient = mock(DataCenterIntegrationClient.class);
        MesMasterDataClient client = client(dataCenterClient);
        MesProcessTypeListResp resp = JSON.readValue("""
                {
                  "code": 500,
                  "msg": "MES denied token=hidden-token Authorization: Bearer hidden-auth; client_secret=hidden-secret",
                  "data": [{"code":"SHOULD_NOT_SURFACE","name":"full body row"}]
                }
                """, MesProcessTypeListResp.class);
        when(dataCenterClient.callGet(
                eq(DataCenterEndpointRegistry.MES_PROCESS_TYPE_LIST),
                eq(Map.of()),
                eq(MesProcessTypeListResp.class)
        )).thenReturn(resp);

        assertThatThrownBy(client::listProcessTypes)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MES process type list failed")
                .hasMessageContaining("MES denied")
                .hasMessageNotContaining("hidden-token")
                .hasMessageNotContaining("hidden-auth")
                .hasMessageNotContaining("hidden-secret")
                .hasMessageNotContaining("Authorization")
                .hasMessageNotContaining("SHOULD_NOT_SURFACE")
                .hasMessageNotContaining("full body row");
    }

    @Test
    void dataCenterExceptionIsSanitizedBeforeSurfacing() {
        DataCenterIntegrationClient dataCenterClient = mock(DataCenterIntegrationClient.class);
        MesMasterDataClient client = client(dataCenterClient);
        when(dataCenterClient.callGet(
                eq(DataCenterEndpointRegistry.MES_MATERIAL_PAGE),
                eq(Map.of("pageNo", 1, "pageSize", 200)),
                eq(MesMaterialPageResp.class)
        )).thenThrow(new IllegalStateException(
                "Data Center failed token=hidden-token Authorization: Bearer hidden-auth response={\"list\":[{\"code\":\"BODY\"}]}")
        );

        assertThatThrownBy(() -> client.pageMaterials(1, 200))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MES material page failed")
                .hasMessageNotContaining("hidden-token")
                .hasMessageNotContaining("hidden-auth")
                .hasMessageNotContaining("Authorization")
                .hasMessageNotContaining("BODY");

        verify(dataCenterClient).callGet(
                DataCenterEndpointRegistry.MES_MATERIAL_PAGE,
                Map.of("pageNo", 1, "pageSize", 200),
                MesMaterialPageResp.class);
    }

    private MesMasterDataClient client(DataCenterIntegrationClient dataCenterClient) {
        MesMasterDataClient client = new MesMasterDataClient();
        ReflectionTestUtils.setField(client, "dataCenterIntegrationClient", dataCenterClient);
        return client;
    }
}
