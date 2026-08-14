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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SalesOrderIntegrationStage6Test {
    private static final List<String> FORBIDDEN_RESPONSE_FIELDS = List.of(
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
    void queryBuildsTypedResponseDtosWithStableBusinessIdentityAndOrderTypeFallback() throws Exception {
        SalesOrderIntegrationMapper mapper = mock(SalesOrderIntegrationMapper.class);
        SalesOrderIntegrationSyncService service = service(mapper);
        SalesOrderSyncQueryReq req = req(100);
        LocalDateTime changedAt = LocalDateTime.of(2026, 8, 14, 10, 30);
        when(mapper.selectOrderPage(eq(req), eq(null), eq(null), eq(101)))
                .thenReturn(List.of(order(1L, "SO-20260814-001", "FORMAL", changedAt)));
        SalesOrderSyncItemRow inheritedTypeItem = item(1L, "SO-20260814-001", 10, "P-001", "A", "MAT-001", null);
        inheritedTypeItem.setSaleOrderItemNo("999");
        setExternalItemDetailFields(inheritedTypeItem);
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
        assertThat(order.getCustomerCode()).isEqualTo("CUST-001");
        assertThat(order.getSalesOrderDate()).isEqualTo(LocalDate.of(2026, 8, 13));
        assertThat(order.getInternalProjectNo()).isEqualTo("IP-001");
        assertThat(order.getChangedAt()).isEqualTo(changedAt);

        assertThat(order.getItems()).hasSize(2);
        SalesOrderSyncItemDTO firstItem = order.getItems().get(0);
        assertThat(firstItem.getSaleOrderNo()).isEqualTo("SO-20260814-001");
        assertThat(firstItem.getSaleOrderItemNo()).isEqualTo("10");
        assertThat(firstItem.getLineNo()).isEqualTo(10);
        assertThat(firstItem.getPartNo()).isEqualTo("P-001");
        assertThat(firstItem.getVersion()).isEqualTo("A");
        assertThat(firstItem.getQty()).isEqualTo(3);
        assertThat(firstItem.getMaterialNo()).isEqualTo("MAT-001");
        assertThat(firstItem.getCustomerDeliveryDate()).isEqualTo(LocalDate.of(2026, 8, 30));
        assertThat(firstItem.getStatus()).isEqualTo("READY");
        assertThat(firstItem.getOrderType()).isEqualTo("FORMAL");
        assertThat(objectMapper.writeValueAsString(firstItem))
                .contains("\"materialName\":\"Imported material\"")
                .contains("\"engineeringMaterialCode\":\"RAW-001\"")
                .contains("\"engineeringMaterialName\":\"6061-T6\"")
                .contains("\"engineeringMaterialTypeCode\":\"AL\"")
                .contains("\"engineeringMaterialTypeName\":\"Aluminum\"")
                .contains("\"engineeringMaterialCategory\":\"Plate\"")
                .contains("\"materialShape\":\"Plate\"")
                .contains("\"surfaceTreatment\":\"Anodized\"")
                .contains("\"shippedQty\":1")
                .contains("\"unshippedQty\":2")
                .contains("\"releasedQty\":1")
                .contains("\"remainingQty\":2");

        SalesOrderSyncItemDTO secondItem = order.getItems().get(1);
        assertThat(secondItem.getSaleOrderItemNo()).isEqualTo("20");
        assertThat(secondItem.getOrderType()).isEqualTo("STOCK");
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
                .contains("orderType");
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
        item.setSaleOrderItemNo("1001");
        when(mapper.selectItemsByOrderIds(eq(List.of(1L)), eq(req)))
                .thenReturn(List.of(item));

        assertThatThrownBy(() -> service.query(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sales_order_item.line_no");
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
        row.setDocumentNo("DOC-001");
        row.setCustomerCode("CUST-001");
        row.setCustomerName("Customer A");
        row.setCustomerShortName("CustA");
        row.setSalesOrderDate(LocalDate.of(2026, 8, 13));
        row.setCustomerOrderNo("PO-001");
        row.setCustomerProjectNo("CP-001");
        row.setCustomerOrderTitle("External Sync Order");
        row.setInternalProjectNo("IP-001");
        row.setOrderType(orderType);
        row.setStatus("APPROVED");
        row.setCreatedAt(LocalDateTime.of(2026, 8, 14, 9, 0));
        row.setUpdatedAt(LocalDateTime.of(2026, 8, 14, 10, 0));
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
        row.setSaleOrderNo(saleOrderNo);
        row.setSaleOrderItemNo(lineNo == null ? null : String.valueOf(lineNo));
        row.setLineNo(lineNo);
        row.setPartNo(partNo);
        row.setVersion(version);
        row.setDescription("Part description");
        row.setQty(3);
        row.setMaterialNo(materialNo);
        row.setMaterialNoStatus("CONFIRMED");
        row.setSalesMaterialNo("C-MAT-001");
        row.setCustomerDeliveryDate(LocalDate.of(2026, 8, 30));
        row.setInternalDeliveryDate(LocalDate.of(2026, 8, 28));
        row.setOrderType(orderType);
        row.setStatus("READY");
        row.setCreatedAt(LocalDateTime.of(2026, 8, 14, 9, 30));
        row.setUpdatedAt(LocalDateTime.of(2026, 8, 14, 10, 20));
        return row;
    }

    private void setExternalItemDetailFields(SalesOrderSyncItemRow row) {
        ReflectionTestUtils.setField(row, "materialName", "Imported material");
        ReflectionTestUtils.setField(row, "engineeringMaterialCode", "RAW-001");
        ReflectionTestUtils.setField(row, "engineeringMaterialName", "6061-T6");
        ReflectionTestUtils.setField(row, "engineeringMaterialTypeCode", "AL");
        ReflectionTestUtils.setField(row, "engineeringMaterialTypeName", "Aluminum");
        ReflectionTestUtils.setField(row, "engineeringMaterialCategory", "Plate");
        ReflectionTestUtils.setField(row, "materialShape", "Plate");
        ReflectionTestUtils.setField(row, "surfaceTreatment", "Anodized");
        ReflectionTestUtils.setField(row, "shippedQty", 1);
        ReflectionTestUtils.setField(row, "unshippedQty", 2);
        ReflectionTestUtils.setField(row, "releasedQty", 1);
        ReflectionTestUtils.setField(row, "remainingQty", 2);
    }
}
