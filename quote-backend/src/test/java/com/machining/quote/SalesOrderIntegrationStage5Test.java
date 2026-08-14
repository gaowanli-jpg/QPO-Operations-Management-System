package com.machining.quote;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.machining.quote.dto.SalesOrderSyncItemRow;
import com.machining.quote.dto.SalesOrderSyncOrderRow;
import com.machining.quote.dto.SalesOrderSyncQueryReq;
import com.machining.quote.dto.SalesOrderSyncResp;
import com.machining.quote.mapper.SalesOrderIntegrationMapper;
import com.machining.quote.service.SalesOrderIntegrationSyncService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesOrderIntegrationStage5Test {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void queryReturnsAllItemsWhenNoLineFilterAndBuildsNextCursorFromLastReturnedOrder() throws Exception {
        SalesOrderIntegrationMapper mapper = mock(SalesOrderIntegrationMapper.class);
        SalesOrderIntegrationSyncService service = service(mapper);
        SalesOrderSyncQueryReq req = req(2);
        LocalDateTime changed1 = LocalDateTime.of(2026, 8, 14, 10, 0);
        LocalDateTime changed2 = LocalDateTime.of(2026, 8, 14, 10, 5);
        LocalDateTime changed3 = LocalDateTime.of(2026, 8, 14, 10, 10);
        when(mapper.selectOrderPage(eq(req), eq(null), eq(null), eq(3)))
                .thenReturn(List.of(order(1L, "SO-1", changed1), order(2L, "SO-2", changed2), order(3L, "SO-3", changed3)));
        when(mapper.selectItemsByOrderIds(eq(List.of(1L, 2L)), eq(req)))
                .thenReturn(List.of(
                        item(1L, "SO-1", 10, "P-1", "A", "MAT-1"),
                        item(1L, "SO-1", 20, "P-2", "A", "MAT-2"),
                        item(2L, "SO-2", 10, "P-3", "B", "MAT-3")
                ));

        SalesOrderSyncResp resp = service.query(req);

        assertThat(resp.isSuccess()).isTrue();
        assertThat(resp.getData().getPageSize()).isEqualTo(2);
        assertThat(resp.getData().isHasMore()).isTrue();
        assertThat(resp.getData().getRecords()).hasSize(2);
        Map<String, Object> first = record(resp, 0);
        Map<String, Object> second = record(resp, 1);
        assertThat(first).containsEntry("saleOrderNo", "SO-1");
        assertThat((List<?>) first.get("items")).hasSize(2);
        assertThat(second).containsEntry("saleOrderNo", "SO-2");
        assertThat((List<?>) second.get("items")).hasSize(1);
        JsonNode cursor = decodeCursor(resp.getData().getNextCursor());
        assertThat(cursor.get("changedAt").asText()).isEqualTo("2026-08-14T10:05:00");
        assertThat(cursor.get("salesOrderId").asLong()).isEqualTo(2L);
    }

    @Test
    void queryReusesLineFilterWhenLoadingItemsSoOnlyMatchingRowsAreReturned() {
        SalesOrderIntegrationMapper mapper = mock(SalesOrderIntegrationMapper.class);
        SalesOrderIntegrationSyncService service = service(mapper);
        SalesOrderSyncQueryReq req = req(100);
        req.setMaterialNo("MAT-ONLY");
        when(mapper.selectOrderPage(eq(req), eq(null), eq(null), eq(101)))
                .thenReturn(List.of(order(1L, "SO-1", LocalDateTime.of(2026, 8, 14, 10, 0))));
        when(mapper.selectItemsByOrderIds(eq(List.of(1L)), eq(req)))
                .thenReturn(List.of(item(1L, "SO-1", 10, "P-1", "A", "MAT-ONLY")));

        SalesOrderSyncResp resp = service.query(req);

        Map<String, Object> first = record(resp, 0);
        List<?> items = (List<?>) first.get("items");
        assertThat(items).hasSize(1);
        assertThat(itemRecord(items, 0)).containsEntry("materialNo", "MAT-ONLY");
        verify(mapper).selectItemsByOrderIds(List.of(1L), req);
    }

    @Test
    void queryDecodesCursorAndPassesStableChangedAtAndSalesOrderIdBoundaryToMapper() throws Exception {
        SalesOrderIntegrationMapper mapper = mock(SalesOrderIntegrationMapper.class);
        SalesOrderIntegrationSyncService service = service(mapper);
        SalesOrderSyncQueryReq req = req(50);
        req.setCursor(encodeCursor("2026-08-14T10:05:00", 22L));
        when(mapper.selectOrderPage(any(), any(), any(), eq(51))).thenReturn(List.of());

        service.query(req);

        ArgumentCaptor<LocalDateTime> changedAtCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<Long> idCaptor = ArgumentCaptor.forClass(Long.class);
        verify(mapper).selectOrderPage(eq(req), changedAtCaptor.capture(), idCaptor.capture(), eq(51));
        assertThat(changedAtCaptor.getValue()).isEqualTo(LocalDateTime.of(2026, 8, 14, 10, 5));
        assertThat(idCaptor.getValue()).isEqualTo(22L);
    }

    @Test
    void mapperSqlDefinesChangedAtFiltersLineFiltersAndCursorOrdering() throws Exception {
        String source = Files.readString(Path.of(
                "src", "main", "java", "com", "machining", "quote", "mapper", "SalesOrderIntegrationSqlProvider.java"
        ), StandardCharsets.UTF_8);

        assertThat(source)
                .contains("GREATEST(so.updated_at, COALESCE(MAX(soi.updated_at), so.updated_at)) AS changed_at")
                .contains("so.created_at >= #{req.createdFrom}")
                .contains("so.created_at &lt;= #{req.createdTo}")
                .contains("oc.changed_at >= #{req.updatedFrom}")
                .contains("oc.changed_at &lt;= #{req.updatedTo}")
                .contains("so.sales_order_no = #{req.saleOrderNo}")
                .contains("so.status = #{req.status}")
                .contains("EXISTS")
                .contains("f.material_no = #{req.materialNo}")
                .contains("f.part_no = #{req.partNo}")
                .contains("f.version = #{req.version}")
                .contains("oc.changed_at > #{cursorChangedAt}")
                .contains("oc.changed_at = #{cursorChangedAt} AND so.id > #{cursorSalesOrderId}")
                .contains("ORDER BY oc.changed_at ASC, so.id ASC")
                .contains("ORDER BY soi.sales_order_id ASC, soi.line_no ASC, soi.id ASC");
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

    private SalesOrderSyncOrderRow order(Long id, String saleOrderNo, LocalDateTime changedAt) {
        SalesOrderSyncOrderRow row = new SalesOrderSyncOrderRow();
        row.setSalesOrderId(id);
        row.setSaleOrderNo(saleOrderNo);
        row.setDocumentNo(saleOrderNo);
        row.setOrderType("正式订单");
        row.setStatus("已报价");
        row.setChangedAt(changedAt);
        return row;
    }

    private SalesOrderSyncItemRow item(Long orderId, String saleOrderNo, Integer lineNo, String partNo, String version, String materialNo) {
        SalesOrderSyncItemRow row = new SalesOrderSyncItemRow();
        row.setSalesOrderId(orderId);
        row.setSaleOrderNo(saleOrderNo);
        row.setSaleOrderItemNo(String.valueOf(lineNo));
        row.setLineNo(lineNo);
        row.setPartNo(partNo);
        row.setVersion(version);
        row.setQty(1);
        row.setMaterialNo(materialNo);
        row.setOrderType("正式订单");
        row.setStatus("已报价");
        return row;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> record(SalesOrderSyncResp resp, int index) {
        return (Map<String, Object>) resp.getData().getRecords().get(index);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> itemRecord(List<?> items, int index) {
        return (Map<String, Object>) items.get(index);
    }

    private String encodeCursor(String changedAt, Long salesOrderId) throws Exception {
        String json = objectMapper.writeValueAsString(Map.of("changedAt", changedAt, "salesOrderId", salesOrderId));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private JsonNode decodeCursor(String cursor) throws Exception {
        byte[] decoded = Base64.getUrlDecoder().decode(cursor);
        return objectMapper.readTree(decoded);
    }
}
