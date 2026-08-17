package com.machining.quote;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.machining.quote.dto.SalesOrderSyncItemDTO;
import com.machining.quote.dto.SalesOrderSyncItemRow;
import com.machining.quote.dto.SalesOrderSyncOrderDTO;
import com.machining.quote.dto.SalesOrderSyncOrderRow;
import com.machining.quote.dto.SalesOrderSyncQueryReq;
import com.machining.quote.dto.SalesOrderSyncResp;
import com.machining.quote.mapper.SalesOrderIntegrationMapper;
import com.machining.quote.service.SalesOrderIntegrationSyncService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SalesOrderIntegrationStage6Test {
    private static final List<String> FORBIDDEN_RESPONSE_FIELDS = List.of(
            "documentNo",
            "customerCode",
            "customerName",
            "customerShortName",
            "salesOrderDate",
            "customerOrderNo",
            "customerProjectNo",
            "customerOrderTitle",
            "updatedAt",
            "saleOrderNo\":\"SO-20260814-001\",\"saleOrderItemNo",
            "lineNo",
            "partNo",
            "version",
            "qty",
            "materialNoStatus",
            "salesMaterialNo",
            "materialName",
            "engineeringMaterialCode",
            "engineeringMaterialName",
            "engineeringMaterialTypeCode",
            "engineeringMaterialTypeName",
            "engineeringMaterialCategory",
            "materialShape",
            "customerDeliveryDate",
            "internalDeliveryDate",
            "shippedQty",
            "unshippedQty",
            "remainingQty",
            "taxIncludedUnitPrice",
            "untaxedUnitPrice",
            "taxIncludedTotalPrice",
            "untaxedTotalPrice",
            "assemblyFee",
            "baseCost",
            "materialData",
            "processRoute",
            "feeData",
            "drawingUrl",
            "createdBy",
            "ownerUserId",
            "ownerDeptId",
            "u9SyncRequestHash",
            "u9SyncAttemptId",
            "sourceReleaseRequestHash"
    );

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void responseDtosExposeOnlyMesDemandContractFields() {
        assertThat(declaredFieldNames(SalesOrderSyncOrderDTO.class))
                .containsExactlyInAnyOrder("saleOrderNo", "createdAt", "changedAt", "status", "orderType", "internalProjectNo", "items");
        assertThat(declaredFieldNames(SalesOrderSyncItemDTO.class))
                .containsExactlyInAnyOrder("saleOrderItemNo", "materialNo", "releasedQty", "description", "surfaceTreatment", "remark");
    }

    @Test
    void queryBuildsMesDemandSlimResponseContractWithReleasedQtyAndRemark() throws Exception {
        SalesOrderIntegrationMapper mapper = mock(SalesOrderIntegrationMapper.class);
        SalesOrderIntegrationSyncService service = service(mapper);
        SalesOrderSyncQueryReq req = req(100);
        LocalDateTime changedAt = LocalDateTime.of(2026, 8, 14, 10, 30);
        when(mapper.selectOrderPage(eq(req), eq(null), eq(null), eq(101)))
                .thenReturn(List.of(order(1L, "SO-20260814-001", "FORMAL", changedAt)));
        SalesOrderSyncItemRow inheritedTypeItem = item(1L, "SO-20260814-001", 10, "P-001", "A", "MAT-001", null);
        setMesDemandItemFields(inheritedTypeItem);
        when(mapper.selectItemsByOrderIds(eq(List.of(1L)), eq(req)))
                .thenReturn(List.of(
                        inheritedTypeItem,
                        item(1L, "SO-20260814-001", 20, "P-002", "B", "MAT-002", "STOCK")
                ));

        SalesOrderSyncResp resp = service.query(req);

        assertThat(resp.isSuccess()).isTrue();
        assertThat(resp.getData().getRecords()).hasSize(1);
        SalesOrderSyncOrderDTO order = resp.getData().getRecords().get(0);
        assertThat(order.getSaleOrderNo()).isEqualTo("SO-20260814-001");
        assertThat(order.getOrderType()).isEqualTo("FORMAL");
        assertThat(order.getInternalProjectNo()).isEqualTo("IP-001");
        assertThat(order.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 8, 14, 9, 0));
        assertThat(order.getChangedAt()).isEqualTo(changedAt);

        assertThat(order.getItems()).hasSize(2);
        SalesOrderSyncItemDTO firstItem = order.getItems().get(0);
        assertThat(firstItem.getSaleOrderItemNo()).isEqualTo("10");
        assertThat(firstItem.getMaterialNo()).isEqualTo("MAT-001");
        assertThat(firstItem.getReleasedQty()).isEqualTo(1);
        assertThat(firstItem.getDescription()).isEqualTo("Part description");
        assertThat(firstItem.getSurfaceTreatment()).isEqualTo("Anodized");
        assertThat(objectMapper.writeValueAsString(firstItem))
                .contains("\"surfaceTreatment\":\"Anodized\"")
                .contains("\"releasedQty\":1")
                .contains("\"remark\":\"MES demand remark\"");

        SalesOrderSyncItemDTO secondItem = order.getItems().get(1);
        assertThat(secondItem.getSaleOrderItemNo()).isEqualTo("20");

        String json = objectMapper.writeValueAsString(resp);
        assertThat(json).doesNotContain(FORBIDDEN_RESPONSE_FIELDS.toArray(String[]::new));
        assertThat(fieldNames(objectMapper.valueToTree(order)))
                .containsExactlyInAnyOrder("saleOrderNo", "createdAt", "changedAt", "status", "orderType", "internalProjectNo", "items");
        assertThat(fieldNames(objectMapper.valueToTree(firstItem)))
                .containsExactlyInAnyOrder("saleOrderItemNo", "materialNo", "releasedQty", "description", "surfaceTreatment", "remark");
    }

    @Test
    void serializedResponseOmitsPricesCostsDrawingUrlsRoutesAndInternalGovernanceFields() throws Exception {
        SalesOrderIntegrationMapper mapper = mock(SalesOrderIntegrationMapper.class);
        SalesOrderIntegrationSyncService service = service(mapper);
        SalesOrderSyncQueryReq req = req(100);
        when(mapper.selectOrderPage(eq(req), eq(null), eq(null), eq(101)))
                .thenReturn(List.of(order(1L, "SO-20260814-001", "FORECAST", LocalDateTime.of(2026, 8, 14, 10, 30))));
        when(mapper.selectItemsByOrderIds(eq(List.of(1L)), eq(req)))
                .thenReturn(List.of(item(1L, "SO-20260814-001", 10, "P-001", "A", "MAT-001", "FORECAST")));

        String json = objectMapper.writeValueAsString(service.query(req));

        assertThat(json)
                .contains("saleOrderNo")
                .contains("saleOrderItemNo")
                .contains("orderType")
                .contains("releasedQty")
                .contains("remark");
        assertThat(json).doesNotContain(FORBIDDEN_RESPONSE_FIELDS.toArray(String[]::new));
    }

    @Test
    void queryRejectsItemRowsWithoutLineNoInsteadOfInventingSaleOrderItemNo() {
        SalesOrderIntegrationMapper mapper = mock(SalesOrderIntegrationMapper.class);
        SalesOrderIntegrationSyncService service = service(mapper);
        SalesOrderSyncQueryReq req = req(100);
        when(mapper.selectOrderPage(eq(req), eq(null), eq(null), eq(101)))
                .thenReturn(List.of(order(1L, "SO-20260814-001", "FORMAL", LocalDateTime.of(2026, 8, 14, 10, 30))));
        SalesOrderSyncItemRow item = item(1L, "SO-20260814-001", null, "P-001", "A", "MAT-001", "FORMAL");
        when(mapper.selectItemsByOrderIds(eq(List.of(1L)), eq(req)))
                .thenReturn(List.of(item));

        assertThatThrownBy(() -> service.query(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sales_order_item.line_no");
    }

    @Test
    void queryRejectsItemRowsWithoutMaterialNoBecauseMesDemandRequiresIt() {
        SalesOrderIntegrationMapper mapper = mock(SalesOrderIntegrationMapper.class);
        SalesOrderIntegrationSyncService service = service(mapper);
        SalesOrderSyncQueryReq req = req(100);
        when(mapper.selectOrderPage(eq(req), eq(null), eq(null), eq(101)))
                .thenReturn(List.of(order(1L, "SO-20260814-001", "FORMAL", LocalDateTime.of(2026, 8, 14, 10, 30))));
        SalesOrderSyncItemRow item = item(1L, "SO-20260814-001", 10, "P-001", "A", null, "FORMAL");
        when(mapper.selectItemsByOrderIds(eq(List.of(1L)), eq(req)))
                .thenReturn(List.of(item));

        assertThatThrownBy(() -> service.query(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sales_order_item.material_no");
    }

    private SalesOrderIntegrationSyncService service(SalesOrderIntegrationMapper mapper) {
        SalesOrderIntegrationSyncService service = new SalesOrderIntegrationSyncService();
        ReflectionTestUtils.setField(service, "salesOrderIntegrationMapper", mapper);
        return service;
    }

    private SalesOrderSyncQueryReq req(int pageSize) {
        SalesOrderSyncQueryReq req = new SalesOrderSyncQueryReq();
        req.setPageSize(pageSize);
        return req;
    }

    private SalesOrderSyncOrderRow order(Long id, String saleOrderNo, String orderType, LocalDateTime changedAt) {
        SalesOrderSyncOrderRow row = new SalesOrderSyncOrderRow();
        row.setSalesOrderId(id);
        row.setSaleOrderNo(saleOrderNo);
        row.setInternalProjectNo("IP-001");
        row.setOrderType(orderType);
        row.setStatus("APPROVED");
        row.setCreatedAt(LocalDateTime.of(2026, 8, 14, 9, 0));
        row.setChangedAt(changedAt);
        return row;
    }

    private SalesOrderSyncItemRow item(
            Long orderId,
            String saleOrderNo,
            Integer lineNo,
            String partNo,
            String version,
            String materialNo,
            String orderType) {
        SalesOrderSyncItemRow row = new SalesOrderSyncItemRow();
        row.setSalesOrderId(orderId);
        row.setLineNo(lineNo);
        row.setDescription("Part description");
        row.setMaterialNo(materialNo);
        return row;
    }

    private void setMesDemandItemFields(SalesOrderSyncItemRow row) {
        ReflectionTestUtils.setField(row, "surfaceTreatment", "Anodized");
        ReflectionTestUtils.setField(row, "releasedQty", 1);
        ReflectionTestUtils.setField(row, "remark", "MES demand remark");
    }

    private List<String> fieldNames(Object node) {
        com.fasterxml.jackson.databind.JsonNode jsonNode = (com.fasterxml.jackson.databind.JsonNode) node;
        List<String> names = new ArrayList<>();
        jsonNode.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private List<String> declaredFieldNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .map(Field::getName)
                .toList();
    }
}
