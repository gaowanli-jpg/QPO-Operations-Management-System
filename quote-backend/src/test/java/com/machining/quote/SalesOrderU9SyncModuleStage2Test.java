package com.machining.quote;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.SalesOrderU9SyncOrderRow;
import com.machining.quote.dto.SalesOrderU9SyncPreviewLine;
import com.machining.quote.dto.SalesOrderU9SyncPreviewResp;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.DataScopeService;
import com.machining.quote.service.SalesOrderU9SyncService;
import com.machining.quote.service.u9.U9SalesOrderClient;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesOrderU9SyncModuleStage2Test {
    @Test
    void controllerExposesReadOnlyListAndPreviewEndpointsWithU9SyncPermissions() throws Exception {
        String controller = Files.readString(Path.of(
                "src/main/java/com/machining/quote/controller/SalesOrderU9SyncController.java"), StandardCharsets.UTF_8);

        assertThat(controller)
                .contains(
                        "@RequestMapping(\"/api/market/u9-sync\")",
                        "@GetMapping(\"/orders\")",
                        "@GetMapping(\"/orders/{salesOrderId}/preview\")",
                        "@RequirePermission(\"market:u9-sync:view\")")
                .doesNotContain("OrderRfqMapper")
                .doesNotContain("order_rfq")
                .doesNotContain("OrderDeliveryTicketMapper")
                .doesNotContain("order_delivery_ticket");
    }

    @Test
    void listOrdersReturnsOnlySalesOrdersWithMaterialReadinessAndDataScope() {
        Fixture fixture = fixture();
        SalesOrderHeader released = header(9001L, "SOQPO-001", QuoteStatusWorkflow.RELEASED, "未同步");
        SalesOrderHeader quoted = header(9002L, "SOQPO-002", QuoteStatusWorkflow.QUOTED, "未同步");
        SalesOrderItem readyItem = item(5001L, released.getId(), "P-001", "MAT-001", "已确认");
        SalesOrderItem missingItem = item(5002L, released.getId(), "P-002", null, "未确认");
        SalesOrderItem quotedItem = item(5003L, quoted.getId(), "P-003", "MAT-003", "已确认");

        when(fixture.headerMapper.selectCount(any())).thenReturn(2L);
        when(fixture.headerMapper.selectList(any())).thenReturn(List.of(released, quoted));
        when(fixture.itemMapper.selectList(any())).thenReturn(List.of(readyItem, missingItem), List.of(quotedItem));

        PageResult<SalesOrderU9SyncOrderRow> result = fixture.service.listOrders(
                "客户A", "SOQPO", null, null, null, 1, 20);

        assertThat(result.getTotal()).isEqualTo(2);
        assertThat(result.getRecords()).hasSize(2);
        assertThat(result.getRecords().get(0))
                .satisfies(row -> {
                    assertThat(row.getSalesOrderId()).isEqualTo(released.getId());
                    assertThat(row.getSalesOrderNo()).isEqualTo("SOQPO-001");
                    assertThat(row.getCustomerCode()).isEqualTo("CUST-A");
                    assertThat(row.getCustomerShortName()).isEqualTo("客户简称A");
                    assertThat(row.getCustomerName()).isEqualTo("客户A");
                    assertThat(row.getCustomerOrderNo()).isEqualTo("PO-" + released.getId());
                    assertThat(row.getCustomerProjectNo()).isEqualTo("CP-" + released.getId());
                    assertThat(row.getCustomerOrderTitle()).isEqualTo("客户下单抬头-" + released.getId());
                    assertThat(row.getOrderType()).isEqualTo("正式订单");
                    assertThat(row.getInternalProjectNo()).isEqualTo("IP-" + released.getId());
                    assertThat(row.getSalesOrderDate()).isEqualTo(LocalDate.of(2026, 7, 31));
                    assertThat(row.getDocumentTypeCode()).isEqualTo("SO01");
                    assertThat(row.getItemCount()).isEqualTo(2);
                    assertThat(row.getMaterialConfirmedItemCount()).isEqualTo(1);
                    assertThat(row.getMissingMaterialItemCount()).isEqualTo(1);
                    assertThat(row.getSyncReady()).isFalse();
                    assertThat(row.getBlockedReason()).contains("物料号");
                });
        assertThat(result.getRecords().get(1).getBlockedReason()).contains("尚未生成 QPO 提单");
        verify(fixture.dataScopeService).applySalesOrderScope(any());
        verify(fixture.u9Client, never()).create(any());
    }

    @Test
    void previewUsesSalesOrderItemMaterialNoAndDoesNotCallU9OrDeliveryTicketsOrWriteDatabase() throws Exception {
        Fixture fixture = fixture();
        SalesOrderHeader header = header(9001L, "SOQPO-001", QuoteStatusWorkflow.RELEASED, "未同步");
        SalesOrderItem ready = item(5001L, header.getId(), "P-001", "MAT-READY-001", "已确认");
        ready.setMaterialNoSource("MES_TICKET");
        ready.setMaterialNoSourceTicketId(3001L);
        ready.setReleasedQty(5);
        ready.setRemainingQty(0);
        SalesOrderItem unconfirmed = item(5002L, header.getId(), "P-002", "MAT-TEMPLATE-002", "未确认");

        when(fixture.headerMapper.selectById(header.getId())).thenReturn(header);
        when(fixture.itemMapper.selectList(any())).thenReturn(List.of(ready, unconfirmed));

        SalesOrderU9SyncPreviewResp resp = fixture.service.previewSalesOrder(header.getId());

        assertThat(resp.getSalesOrderId()).isEqualTo(header.getId());
        assertThat(resp.getSalesOrderNo()).isEqualTo("SOQPO-001");
        assertThat(resp.getOrderBy()).isEqualTo("CUST-A");
        assertThat(resp.getCustomerCode()).isEqualTo("CUST-A");
        assertThat(resp.getCustomerShortName()).isEqualTo("客户简称A");
        assertThat(resp.getCustomerName()).isEqualTo("客户A");
        assertThat(resp.getCustomerOrderNo()).isEqualTo(header.getCustomerOrderNo());
        assertThat(resp.getCustomerProjectNo()).isEqualTo(header.getCustomerProjectNo());
        assertThat(resp.getCustomerOrderTitle()).isEqualTo(header.getCustomerOrderTitle());
        assertThat(resp.getOrderType()).isEqualTo(header.getOrderType());
        assertThat(resp.getInternalProjectNo()).isEqualTo(header.getInternalProjectNo());
        assertThat(resp.getSalesOrderDate()).isEqualTo(header.getSalesOrderDate());
        assertThat(resp.getDocumentTypeCode()).isEqualTo("SO01");
        assertThat(resp.getItemCount()).isEqualTo(2);
        assertThat(resp.getMaterialConfirmedItemCount()).isEqualTo(1);
        assertThat(resp.getMissingMaterialItemCount()).isEqualTo(1);
        assertThat(resp.getSyncReady()).isFalse();
        assertThat(resp.getBlockedReason()).contains("物料号");
        assertThat(resp.getLines()).hasSize(2);
        assertThat(resp.getLines().get(0))
                .satisfies(line -> {
                    assertThat(line.getSalesOrderItemId()).isEqualTo(ready.getId());
                    assertThat(line.getSalesOrderDate()).isEqualTo(header.getSalesOrderDate());
                    assertThat(line.getCustomerOrderTitle()).isEqualTo(header.getCustomerOrderTitle());
                    assertThat(line.getSalesMaterialNo()).isEqualTo(ready.getSalesMaterialNo());
                    assertThat(line.getCustomerDeliveryDate()).isEqualTo(ready.getCustomerDeliveryDate());
                    assertThat(line.getInternalDeliveryDate()).isEqualTo(ready.getInternalDeliveryDate());
                    assertThat(line.getShippedQty()).isEqualTo(ready.getShippedQty());
                    assertThat(line.getUnshippedQty()).isEqualTo(ready.getUnshippedQty());
                    assertThat(line.getDescription()).isEqualTo(ready.getDescription());
                    assertThat(line.getMaterialName()).isEqualTo(ready.getMaterialName());
                    assertThat(line.getSurfaceTreatment()).isEqualTo(ready.getSurfaceTreatment());
                    assertThat(line.getTaxIncludedUnitPrice()).isEqualByComparingTo(ready.getTaxIncludedUnitPrice());
                    assertThat(line.getUntaxedUnitPrice()).isEqualByComparingTo(ready.getUntaxedUnitPrice());
                    assertThat(line.getUntaxedTotalPrice()).isEqualByComparingTo(ready.getUntaxedTotalPrice());
                    assertThat(line.getRemark()).isEqualTo(ready.getRemark());
                    assertThat(line.getMaterialNo()).isEqualTo("MAT-READY-001");
                    assertThat(line.getMaterialNoStatus()).isEqualTo("已确认");
                    assertThat(line.getReady()).isTrue();
                    assertThat(line.getBlockedReason()).isBlank();
                });
        assertThat(resp.getLines().get(1))
                .extracting(SalesOrderU9SyncPreviewLine::getReady,
                        SalesOrderU9SyncPreviewLine::getBlockedReason)
                .containsExactly(false, "物料号未确认");
        assertThat(Arrays.stream(SalesOrderU9SyncPreviewLine.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName))
                .doesNotContain(
                        "itemCode",
                        "materialNoSource",
                        "materialNoSourceTicketId",
                        "customerOrderNo",
                        "customerProjectNo",
                        "orderType",
                        "internalProjectNo");
        verify(fixture.dataScopeService).assertCanAccessSalesOrder(header);
        verify(fixture.headerMapper, never()).updateById(any());
        verify(fixture.itemMapper, never()).updateById(any());
        verify(fixture.u9Client, never()).create(any());

        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(resp);
        assertThat(json)
                .doesNotContain("SOLineDTOList")
                .doesNotContain("SOShiplineDTOList")
                .doesNotContain("U9SalesOrderHeadDTO")
                .doesNotContain("requestJson");
    }

    @Test
    void previewBlocksRowsWithOnlyUntaxedPriceBeforeU9Sync() {
        Fixture fixture = fixture();
        SalesOrderHeader header = header(9001L, "SOQPO-001", QuoteStatusWorkflow.RELEASED, "未同步");
        SalesOrderItem missingPrice = item(5001L, header.getId(), "P-001", "MAT-READY-001", "已确认");
        missingPrice.setTaxIncludedUnitPrice(null);
        missingPrice.setTaxIncludedTotalPrice(null);
        missingPrice.setUntaxedUnitPrice(new BigDecimal("10.92"));
        missingPrice.setUntaxedTotalPrice(new BigDecimal("54.60"));

        when(fixture.headerMapper.selectById(header.getId())).thenReturn(header);
        when(fixture.itemMapper.selectList(any())).thenReturn(List.of(missingPrice));

        SalesOrderU9SyncPreviewResp resp = fixture.service.previewSalesOrder(header.getId());

        assertThat(resp.getSyncReady()).isFalse();
        assertThat(resp.getBlockedReason()).contains("含税单价为空");
        assertThat(resp.getLines()).singleElement()
                .satisfies(line -> {
                    assertThat(line.getMaterialNo()).isEqualTo("MAT-READY-001");
                    assertThat(line.getTaxIncludedUnitPrice()).isNull();
                    assertThat(line.getTaxIncludedTotalPrice()).isNull();
                    assertThat(line.getUntaxedUnitPrice()).isEqualByComparingTo("10.92");
                    assertThat(line.getReady()).isFalse();
                    assertThat(line.getBlockedReason()).isEqualTo("含税单价为空");
                });
        verify(fixture.headerMapper, never()).updateById(any());
        verify(fixture.itemMapper, never()).updateById(any());
        verify(fixture.u9Client, never()).create(any());
    }

    @Test
    void previewShowsStatusAndSyncBlockersWithoutTouchingDatabase() {
        Fixture fixture = fixture();
        SalesOrderHeader quoted = header(9001L, "SOQPO-001", QuoteStatusWorkflow.QUOTED, "未同步");
        SalesOrderHeader partial = header(9002L, "SOQPO-002", QuoteStatusWorkflow.PARTIALLY_RELEASED, "未同步");
        SalesOrderHeader synced = header(9003L, "SOQPO-003", QuoteStatusWorkflow.RELEASED, "已同步");
        synced.setU9OrderCode("U9-SO-003");

        when(fixture.headerMapper.selectById(9001L)).thenReturn(quoted);
        when(fixture.headerMapper.selectById(9002L)).thenReturn(partial);
        when(fixture.headerMapper.selectById(9003L)).thenReturn(synced);
        when(fixture.itemMapper.selectList(any())).thenReturn(
                List.of(item(5001L, quoted.getId(), "P-001", "MAT-001", "已确认")),
                List.of(item(5002L, partial.getId(), "P-002", "MAT-002", "已确认")),
                List.of(item(5003L, synced.getId(), "P-003", "MAT-003", "已确认"))
        );

        assertThat(fixture.service.previewSalesOrder(quoted.getId()).getBlockedReason())
                .contains("尚未生成 QPO 提单");
        assertThat(fixture.service.previewSalesOrder(partial.getId()).getBlockedReason())
                .contains("仍存在未提单数量");
        assertThat(fixture.service.previewSalesOrder(synced.getId()).getBlockedReason())
                .contains("已同步");
        verify(fixture.headerMapper, never()).updateById(any());
        verify(fixture.itemMapper, never()).updateById(any());
        verify(fixture.u9Client, never()).create(any());
    }

    private Fixture fixture() {
        SalesOrderU9SyncService service = new SalesOrderU9SyncService();
        Fixture fixture = new Fixture(service);
        ReflectionTestUtils.setField(service, "headerMapper", fixture.headerMapper);
        ReflectionTestUtils.setField(service, "itemMapper", fixture.itemMapper);
        ReflectionTestUtils.setField(service, "u9SalesOrderClient", fixture.u9Client);
        ReflectionTestUtils.setField(service, "auditService", fixture.auditService);
        ReflectionTestUtils.setField(service, "dataScopeService", fixture.dataScopeService);
        return fixture;
    }

    private SalesOrderHeader header(Long id, String salesOrderNo, String status, String u9SyncStatus) {
        SalesOrderHeader header = new SalesOrderHeader();
        header.setId(id);
        header.setSalesOrderNo(salesOrderNo);
        header.setDocumentNo("DOC-" + id);
        header.setDocumentTypeCode("SO01");
        header.setCustomerCode("CUST-A");
        header.setCustomerShortName("客户简称A");
        header.setCustomerName("客户A");
        header.setCustomerOrderNo("PO-" + id);
        header.setInternalProjectNo("IP-" + id);
        header.setCustomerProjectNo("CP-" + id);
        header.setCustomerOrderTitle("客户下单抬头-" + id);
        header.setOrderType("正式订单");
        header.setSalesOrderDate(LocalDate.of(2026, 7, 31));
        header.setStatus(status);
        header.setU9SyncStatus(u9SyncStatus);
        header.setU9SyncMessage("last message");
        return header;
    }

    private SalesOrderItem item(Long id, Long salesOrderId, String partNo, String materialNo, String materialNoStatus) {
        SalesOrderItem item = new SalesOrderItem();
        item.setId(id);
        item.setSalesOrderId(salesOrderId);
        item.setLineNo(id.intValue() % 100);
        item.setPartNo(partNo);
        item.setVersion("A");
        item.setDescription("零件 " + partNo);
        item.setSalesMaterialNo("SALES-" + partNo);
        item.setQty(5);
        item.setOrderType("正式订单");
        item.setCustomerOrderNo("PO-" + salesOrderId);
        item.setCustomerProjectNo("CP-" + salesOrderId);
        item.setInternalProjectNo("IP-" + salesOrderId);
        item.setReleasedQty(5);
        item.setRemainingQty(0);
        item.setCustomerDeliveryDate(LocalDate.of(2026, 8, 20));
        item.setInternalDeliveryDate(LocalDate.of(2026, 8, 18));
        item.setShippedQty(2);
        item.setUnshippedQty(3);
        item.setMaterialName("AL6061");
        item.setSurfaceTreatment("阳极氧化");
        item.setMaterialNo(materialNo);
        item.setMaterialNoStatus(materialNoStatus);
        item.setMaterialNoSource("MES_TICKET");
        item.setMaterialNoSyncedAt(LocalDateTime.of(2026, 7, 31, 10, 0));
        item.setTaxIncludedUnitPrice(new BigDecimal("12.34"));
        item.setTaxIncludedTotalPrice(new BigDecimal("61.70"));
        item.setUntaxedUnitPrice(new BigDecimal("10.92"));
        item.setUntaxedTotalPrice(new BigDecimal("54.60"));
        item.setRemark("销售订单备注");
        item.setStatus(QuoteStatusWorkflow.RELEASED);
        return item;
    }

    private static final class Fixture {
        private final SalesOrderU9SyncService service;
        private final SalesOrderHeaderMapper headerMapper = mock(SalesOrderHeaderMapper.class);
        private final SalesOrderItemMapper itemMapper = mock(SalesOrderItemMapper.class);
        private final U9SalesOrderClient u9Client = mock(U9SalesOrderClient.class);
        private final AuditService auditService = mock(AuditService.class);
        private final DataScopeService dataScopeService = mock(DataScopeService.class);

        private Fixture(SalesOrderU9SyncService service) {
            this.service = service;
        }
    }
}
