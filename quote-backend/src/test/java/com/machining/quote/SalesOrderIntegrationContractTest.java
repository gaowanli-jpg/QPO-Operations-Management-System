package com.machining.quote;

import com.fasterxml.jackson.annotation.JsonInclude;
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

class SalesOrderIntegrationContractTest {
    private static final List<String> FORBIDDEN_RESPONSE_FIELDS = List.of(
            "documentNo",
            "customerName",
            "customerShortName",
            "salesOrderDate",
            "customerOrderTitle",
            "updatedAt",
            "saleOrderNo\":\"SO-20260814-001\",\"saleOrderItemNo",
            "lineNo",
            "partNo",
            "version",
            "qty",
            "materialNoStatus",
            "salesMaterialNo",
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
    private final ObjectMapper nonNullObjectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    @Test
    void responseDtosExposeOnlyMesDemandContractFields() {
        assertThat(declaredFieldNames(SalesOrderSyncOrderDTO.class))
                .containsExactlyInAnyOrder(
                        "saleOrderNo",
                        "createdAt",
                        "changedAt",
                        "status",
                        "orderType",
                        "customerCode",
                        "supplierCode",
                        "items");
        assertThat(declaredFieldNames(SalesOrderSyncItemDTO.class))
                .containsExactlyInAnyOrder(
                        "saleOrderItemNo",
                        "materialNo",
                        "releasedQty",
                        "materialName",
                        "description",
                        "surfaceTreatment",
                        "remark",
                        "internalProjectNo",
                        "customerOrderNo",
                        "customerProjectNo");
    }

    @Test
    void queryBuildsMesDemandSlimResponseContractWithReleasedQtyAndRemark() throws Exception {
        SalesOrderIntegrationMapper mapper = mock(SalesOrderIntegrationMapper.class);
        SalesOrderIntegrationSyncService service = service(mapper);
        SalesOrderSyncQueryReq req = req(100);
        LocalDateTime changedAt = LocalDateTime.of(2026, 8, 14, 10, 30);
        when(mapper.selectOrderPage(eq(req), eq(null), eq(null), eq(101)))
                .thenReturn(List.of(order(1L, "SO-20260814-001", "FORMAL", changedAt)));
        SalesOrderSyncItemRow inheritedTypeItem = item(1L, "SO-20260814-001", 1, "P-001", "A", "MAT-001", null);
        setMesDemandItemFields(inheritedTypeItem);
        when(mapper.selectItemsByOrderIds(eq(List.of(1L)), eq(req)))
                .thenReturn(List.of(
                        inheritedTypeItem,
                        item(1L, "SO-20260814-001", 2, "P-002", "B", "MAT-002", "STOCK")
                ));

        SalesOrderSyncResp resp = service.query(req);

        assertThat(resp.isSuccess()).isTrue();
        assertThat(resp.getData().getRecords()).hasSize(1);
        SalesOrderSyncOrderDTO order = resp.getData().getRecords().get(0);
        assertThat(order.getSaleOrderNo()).isEqualTo("SO-20260814-001");
        assertThat(order.getOrderType()).isEqualTo("FORMAL");
        assertThat(order.getCustomerCode()).isEqualTo("C02058");
        assertThat(order.getSupplierCode()).isEqualTo("SUP-C02058");
        assertThat(order.getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 8, 14, 9, 0));
        assertThat(order.getChangedAt()).isEqualTo(changedAt);

        assertThat(order.getItems()).hasSize(2);
        SalesOrderSyncItemDTO firstItem = order.getItems().get(0);
        assertThat(firstItem.getSaleOrderItemNo()).isEqualTo("1");
        assertThat(firstItem.getMaterialNo()).isEqualTo("MAT-001");
        assertThat(firstItem.getReleasedQty()).isEqualTo(7);
        assertThat(firstItem.getMaterialName()).isEqualTo("AL6061");
        assertThat(firstItem.getDescription()).isEqualTo("Part description");
        assertThat(firstItem.getSurfaceTreatment()).isEqualTo("Anodized");
        assertThat(firstItem.getInternalProjectNo()).isEqualTo("IP-LINE-001");
        assertThat(firstItem.getCustomerOrderNo()).isEqualTo("PO-001");
        assertThat(firstItem.getCustomerProjectNo()).isEqualTo("CP-001");
        assertThat(objectMapper.writeValueAsString(firstItem))
                .contains("\"surfaceTreatment\":\"Anodized\"")
                .contains("\"releasedQty\":7")
                .contains("\"materialName\":\"AL6061\"")
                .contains("\"remark\":\"MES demand remark\"")
                .contains("\"internalProjectNo\":\"IP-LINE-001\"")
                .contains("\"customerOrderNo\":\"PO-001\"")
                .contains("\"customerProjectNo\":\"CP-001\"");

        SalesOrderSyncItemDTO secondItem = order.getItems().get(1);
        assertThat(secondItem.getSaleOrderItemNo()).isEqualTo("2");

        String json = objectMapper.writeValueAsString(resp);
        assertThat(json).doesNotContain(FORBIDDEN_RESPONSE_FIELDS.toArray(String[]::new));
        assertThat(fieldNames(objectMapper.valueToTree(order)))
                .containsExactlyInAnyOrder(
                        "saleOrderNo",
                        "createdAt",
                        "changedAt",
                        "status",
                        "orderType",
                        "customerCode",
                        "supplierCode",
                        "items");
        assertThat(fieldNames(objectMapper.valueToTree(firstItem)))
                .containsExactlyInAnyOrder(
                        "saleOrderItemNo",
                        "materialNo",
                        "releasedQty",
                        "materialName",
                        "description",
                        "surfaceTreatment",
                        "remark",
                        "internalProjectNo",
                        "customerOrderNo",
                        "customerProjectNo");
    }

    @Test
    void serializedResponseOmitsPricesCostsDrawingUrlsRoutesAndInternalGovernanceFields() throws Exception {
        SalesOrderIntegrationMapper mapper = mock(SalesOrderIntegrationMapper.class);
        SalesOrderIntegrationSyncService service = service(mapper);
        SalesOrderSyncQueryReq req = req(100);
        when(mapper.selectOrderPage(eq(req), eq(null), eq(null), eq(101)))
                .thenReturn(List.of(order(1L, "SO-20260814-001", "FORECAST", LocalDateTime.of(2026, 8, 14, 10, 30))));
        when(mapper.selectItemsByOrderIds(eq(List.of(1L)), eq(req)))
                .thenReturn(List.of(item(1L, "SO-20260814-001", 1, "P-001", "A", "MAT-001", "FORECAST")));

        String json = objectMapper.writeValueAsString(service.query(req));

        assertThat(json)
                .contains("saleOrderNo")
                .contains("saleOrderItemNo")
                .contains("orderType")
                .contains("customerCode")
                .contains("supplierCode")
                .contains("materialName")
                .contains("releasedQty")
                .contains("remark")
                .contains("internalProjectNo")
                .contains("customerOrderNo")
                .contains("customerProjectNo");
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
    void queryRejectsNonPositiveLineNoInsteadOfInventingSaleOrderItemNo() {
        SalesOrderIntegrationMapper mapper = mock(SalesOrderIntegrationMapper.class);
        SalesOrderIntegrationSyncService service = service(mapper);
        SalesOrderSyncQueryReq req = req(100);
        when(mapper.selectOrderPage(eq(req), eq(null), eq(null), eq(101)))
                .thenReturn(List.of(order(1L, "SO-20260814-001", "FORMAL", LocalDateTime.of(2026, 8, 14, 10, 30))));
        SalesOrderSyncItemRow item = item(1L, "SO-20260814-001", 0, "P-001", "A", "MAT-001", "FORMAL");
        when(mapper.selectItemsByOrderIds(eq(List.of(1L)), eq(req)))
                .thenReturn(List.of(item));

        assertThatThrownBy(() -> service.query(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sales_order_item.line_no");
    }

    @Test
    void queryAllowsItemRowsWithoutMaterialNoBecauseMaterialMayBeAssignedLater() {
        SalesOrderIntegrationMapper mapper = mock(SalesOrderIntegrationMapper.class);
        SalesOrderIntegrationSyncService service = service(mapper);
        SalesOrderSyncQueryReq req = req(100);
        when(mapper.selectOrderPage(eq(req), eq(null), eq(null), eq(101)))
                .thenReturn(List.of(order(1L, "SO-20260814-001", "FORMAL", LocalDateTime.of(2026, 8, 14, 10, 30))));
        SalesOrderSyncItemRow item = item(1L, "SO-20260814-001", 1, "P-001", "A", null, "FORMAL");
        when(mapper.selectItemsByOrderIds(eq(List.of(1L)), eq(req)))
                .thenReturn(List.of(item));

        SalesOrderSyncResp resp = service.query(req);

        assertThat(resp.isSuccess()).isTrue();
        assertThat(resp.getData().getRecords()).hasSize(1);
        SalesOrderSyncItemDTO returnedItem = resp.getData().getRecords().get(0).getItems().get(0);
        assertThat(returnedItem.getSaleOrderItemNo()).isEqualTo("1");
        assertThat(returnedItem.getMaterialNo()).isNull();
    }

    @Test
    void serializedResponseIncludesNullFieldsForStableMesContract() throws Exception {
        SalesOrderIntegrationMapper mapper = mock(SalesOrderIntegrationMapper.class);
        SalesOrderIntegrationSyncService service = service(mapper);
        SalesOrderSyncQueryReq req = req(100);
        SalesOrderSyncOrderRow order = order(1L, "SO-20260814-001", null, LocalDateTime.of(2026, 8, 14, 10, 30));
        order.setStatus(null);
        order.setSupplierCode(null);
        when(mapper.selectOrderPage(eq(req), eq(null), eq(null), eq(101)))
                .thenReturn(List.of(order));
        SalesOrderSyncItemRow item = item(1L, "SO-20260814-001", 1, "P-001", "A", null, null);
        item.setDescription(null);
        item.setSurfaceTreatment(null);
        item.setRemark(null);
        ReflectionTestUtils.setField(item, "materialName", null);
        ReflectionTestUtils.setField(item, "internalProjectNo", null);
        ReflectionTestUtils.setField(item, "customerOrderNo", null);
        ReflectionTestUtils.setField(item, "customerProjectNo", null);
        when(mapper.selectItemsByOrderIds(eq(List.of(1L)), eq(req)))
                .thenReturn(List.of(item));

        String json = nonNullObjectMapper.writeValueAsString(service.query(req));

        assertThat(json)
                .contains("\"status\":null")
                .contains("\"orderType\":null")
                .contains("\"customerCode\":\"C02058\"")
                .contains("\"supplierCode\":null")
                .contains("\"materialNo\":null")
                .contains("\"materialName\":null")
                .contains("\"description\":null")
                .contains("\"surfaceTreatment\":null")
                .contains("\"remark\":null")
                .contains("\"internalProjectNo\":null")
                .contains("\"customerOrderNo\":null")
                .contains("\"customerProjectNo\":null")
                .contains("\"nextCursor\":null");
        com.fasterxml.jackson.databind.JsonNode orderNode = objectMapper.readTree(json).path("data").path("records").get(0);
        assertThat(orderNode.has("internalProjectNo")).isFalse();
        assertThat(orderNode.path("items").get(0).has("materialName")).isTrue();
        assertThat(orderNode.path("items").get(0).has("internalProjectNo")).isTrue();
        assertThat(orderNode.path("items").get(0).has("customerOrderNo")).isTrue();
        assertThat(orderNode.path("items").get(0).has("customerProjectNo")).isTrue();
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
        row.setCustomerCode("C02058");
        row.setSupplierCode("SUP-C02058");
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
        row.setQty(7);
        ReflectionTestUtils.setField(row, "materialName", "AL6061");
        ReflectionTestUtils.setField(row, "remark", "MES demand remark");
        ReflectionTestUtils.setField(row, "internalProjectNo", "IP-LINE-001");
        ReflectionTestUtils.setField(row, "customerOrderNo", "PO-001");
        ReflectionTestUtils.setField(row, "customerProjectNo", "CP-001");
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
