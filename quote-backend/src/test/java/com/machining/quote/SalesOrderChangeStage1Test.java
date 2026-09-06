package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.SalesOrderChangeOrderRow;
import com.machining.quote.dto.SalesOrderChangePreviewResp;
import com.machining.quote.dto.SalesOrderU9SyncPreviewLine;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.service.DataScopeService;
import com.machining.quote.service.PermissionService;
import com.machining.quote.service.SalesOrderChangeService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesOrderChangeStage1Test {
    private static final Path PERMISSION_SERVICE = Path.of("src", "main", "java", "com", "machining", "quote", "service", "PermissionService.java");
    private static final Path CONTROLLER = Path.of("src", "main", "java", "com", "machining", "quote", "controller", "SalesOrderChangeController.java");

    @Test
    void permissionCatalogAddsSalesOrderChangeModuleWithoutCommonPermissions() throws Exception {
        String source = read(PERMISSION_SERVICE);

        assertThat(source)
                .contains(
                        "permission(202L, \"MENU\", \"change:sales-order:view\"",
                        "\"销售变更\"",
                        "\"/sales-order-change\"",
                        "\"SalesOrderChangeManage\"",
                        "permission(203L, \"API\", \"change:sales-order:customer-select\"",
                        "permission(204L, \"API\", \"change:sales-order:order-select\"",
                        "permission(205L, \"BUTTON\", \"change:sales-order:update\"",
                        "Map.entry(\"change:sales-order:view\", Set.of(\"MARKET_USER\", \"MARKET_MANAGER\", \"READONLY\"))",
                        "Map.entry(\"change:sales-order:update\", Set.of(\"MARKET_USER\", \"MARKET_MANAGER\"))")
                .doesNotContain("common:sales-order-change");

        assertThat(new PermissionService().getPermissionInfo(null, "MARKET").getPermissions())
                .contains("change:sales-order:view", "change:sales-order:order-select", "change:sales-order:update");
        assertThat(new PermissionService().getPermissionInfo(null, "READONLY").getPermissions())
                .contains("change:sales-order:view", "change:sales-order:order-select")
                .doesNotContain("change:sales-order:update");
    }

    @Test
    void controllerExposesReadOnlySalesOrderChangeEndpointsWithChangePermissions() throws Exception {
        String source = read(CONTROLLER);

        assertThat(source)
                .contains(
                        "@RequestMapping(\"/api/change/sales-orders\")",
                        "@GetMapping",
                        "@GetMapping(\"/{salesOrderId}/preview\")",
                        "@RequirePermission(\"change:sales-order:view\")")
                .doesNotContain(
                        "U9SalesOrderClient",
                        "Mes",
                        "Wms",
                        "auditService.record");
    }

    @Test
    void listOrdersOnlyQueriesSyncedU9SalesOrdersAndAppliesDataScope() {
        Fixture fixture = fixture();
        SalesOrderHeader synced = header(9001L, "SO-9001", "已同步");
        synced.setU9OrderCode("U9-SO-9001");
        SalesOrderItem item = item(5001L, synced.getId(), "P-001", "MAT-001", "已确认");

        when(fixture.headerMapper.selectCount(any())).thenReturn(1L);
        when(fixture.headerMapper.selectList(any())).thenReturn(List.of(synced));
        when(fixture.itemMapper.selectList(any())).thenReturn(List.of(item));

        PageResult<SalesOrderChangeOrderRow> result = fixture.service.listOrders(
                "CUST-A", "客户A", "SO-9001", null, 1, 20);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords()).singleElement().satisfies(row -> {
            assertThat(row.getSalesOrderId()).isEqualTo(9001L);
            assertThat(row.getSalesOrderNo()).isEqualTo("SO-9001");
            assertThat(row.getCustomerCode()).isEqualTo("CUST-A");
            assertThat(row.getU9SyncStatus()).isEqualTo("已同步");
            assertThat(row.getU9OrderCode()).isEqualTo("U9-SO-9001");
            assertThat(row.getItemCount()).isEqualTo(1);
        });

        ArgumentCaptor<QueryWrapper<SalesOrderHeader>> queryCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(fixture.headerMapper).selectCount(queryCaptor.capture());
        assertThat(queryCaptor.getValue().getCustomSqlSegment()).contains("u9_sync_status");
        assertThat(queryCaptor.getValue().getParamNameValuePairs()).containsValue("已同步");
        verify(fixture.dataScopeService).applySalesOrderScope(any());
    }

    @Test
    void previewIsReadOnlyAndRejectsOrdersThatAreNotSyncedToU9() {
        Fixture fixture = fixture();
        SalesOrderHeader synced = header(9001L, "SO-9001", "已同步");
        SalesOrderHeader unsynced = header(9002L, "SO-9002", "未同步");
        SalesOrderItem item = item(5001L, synced.getId(), "P-001", "MAT-001", "已确认");

        when(fixture.headerMapper.selectById(9001L)).thenReturn(synced);
        when(fixture.headerMapper.selectById(9002L)).thenReturn(unsynced);
        when(fixture.itemMapper.selectList(any())).thenReturn(List.of(item));

        SalesOrderChangePreviewResp preview = fixture.service.preview(9001L);

        assertThat(preview.getSalesOrderId()).isEqualTo(9001L);
        assertThat(preview.getSalesOrderNo()).isEqualTo("SO-9001");
        assertThat(preview.getU9SyncStatus()).isEqualTo("已同步");
        assertThat(preview.getChangeEditable()).isTrue();
        assertThat(preview.getChangeBlockedReason()).isNull();
        assertThat(preview.getLines()).singleElement().satisfies(line -> {
            assertThat(line.getSalesOrderItemId()).isEqualTo(5001L);
            assertThat(line.getPartNo()).isEqualTo("P-001");
            assertThat(line.getMaterialNo()).isEqualTo("MAT-001");
            assertThat(line.getQty()).isEqualTo(5);
            assertThat(line.getUntaxedUnitPrice()).isEqualByComparingTo("10.92");
        });
        verify(fixture.headerMapper, never()).updateById(any());
        verify(fixture.itemMapper, never()).updateById(any());
        verify(fixture.dataScopeService).assertCanAccessSalesOrder(synced);

        assertThatThrownBy(() -> fixture.service.preview(9002L))
                .hasMessageContaining("只允许变更已同步 U9 的销售订单");
    }

    @Test
    void previewReturnsBackendPagedItemRecordsForSalesOrderChangeTable() {
        Fixture fixture = fixture();
        SalesOrderHeader synced = header(9001L, "SO-9001", "已同步");
        SalesOrderItem first = item(5001L, synced.getId(), "P-001", "MAT-001", "已确认");
        SalesOrderItem second = item(5002L, synced.getId(), "P-002", "MAT-002", "已确认");
        SalesOrderItem third = item(5003L, synced.getId(), "P-003", "MAT-003", "已确认");

        when(fixture.headerMapper.selectById(synced.getId())).thenReturn(synced);
        when(fixture.itemMapper.selectCount(any())).thenReturn(3L);
        when(fixture.itemMapper.selectList(any())).thenReturn(List.of(second));

        SalesOrderChangePreviewResp preview = fixture.service.preview(synced.getId(), 2, 1);

        assertThat(preview.getItemCount()).isEqualTo(3);
        assertThat(preview.getTotal()).isEqualTo(3);
        assertThat(preview.getPage()).isEqualTo(2);
        assertThat(preview.getPageSize()).isEqualTo(1);
        assertThat(preview.getRecords()).singleElement().satisfies(line -> {
            assertThat(line.getSalesOrderItemId()).isEqualTo(second.getId());
            assertThat(line.getPartNo()).isEqualTo("P-002");
        });
        assertThat(preview.getLines()).extracting(SalesOrderU9SyncPreviewLine::getSalesOrderItemId)
                .containsExactly(second.getId());
        verify(fixture.headerMapper, never()).updateById(any());
        verify(fixture.itemMapper, never()).updateById(any());
    }

    private Fixture fixture() {
        SalesOrderChangeService service = new SalesOrderChangeService();
        Fixture fixture = new Fixture(service);
        ReflectionTestUtils.setField(service, "headerMapper", fixture.headerMapper);
        ReflectionTestUtils.setField(service, "itemMapper", fixture.itemMapper);
        ReflectionTestUtils.setField(service, "dataScopeService", fixture.dataScopeService);
        return fixture;
    }

    private SalesOrderHeader header(Long id, String salesOrderNo, String u9SyncStatus) {
        SalesOrderHeader header = new SalesOrderHeader();
        header.setId(id);
        header.setSalesOrderNo(salesOrderNo);
        header.setDocumentNo("DOC-" + id);
        header.setCustomerCode("CUST-A");
        header.setCustomerShortName("客户简称A");
        header.setCustomerName("客户A");
        header.setSourceType("DIRECT_IMPORT");
        header.setSalesOrderDate(LocalDate.of(2026, 8, 24));
        header.setCustomerOrderNo("PO-" + id);
        header.setCustomerProjectNo("CP-" + id);
        header.setCustomerOrderTitle("客户下单抬头-" + id);
        header.setOrderType("正式订单");
        header.setInternalProjectNo("IP-" + id);
        header.setStatus("已生成提单");
        header.setU9SyncStatus(u9SyncStatus);
        header.setU9OrderCode("U9-" + salesOrderNo);
        header.setU9SyncMessage("U9 已同步");
        header.setUpdatedAt(LocalDateTime.of(2026, 8, 24, 10, 0));
        return header;
    }

    private SalesOrderItem item(Long id, Long salesOrderId, String partNo, String materialNo, String materialNoStatus) {
        SalesOrderItem item = new SalesOrderItem();
        item.setId(id);
        item.setSalesOrderId(salesOrderId);
        item.setLineNo(1);
        item.setPartNo(partNo);
        item.setVersion("A");
        item.setDescription("零件 " + partNo);
        item.setMaterialNo(materialNo);
        item.setMaterialNoStatus(materialNoStatus);
        item.setQty(5);
        item.setCustomerDeliveryDate(LocalDate.of(2026, 9, 10));
        item.setInternalDeliveryDate(LocalDate.of(2026, 9, 8));
        item.setUntaxedUnitPrice(new BigDecimal("10.92"));
        item.setUntaxedTotalPrice(new BigDecimal("54.60"));
        item.setRemark("备注");
        return item;
    }

    private static String read(Path path) throws Exception {
        assertThat(path).exists();
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static final class Fixture {
        private final SalesOrderChangeService service;
        private final SalesOrderHeaderMapper headerMapper = mock(SalesOrderHeaderMapper.class);
        private final SalesOrderItemMapper itemMapper = mock(SalesOrderItemMapper.class);
        private final DataScopeService dataScopeService = mock(DataScopeService.class);

        private Fixture(SalesOrderChangeService service) {
            this.service = service;
        }
    }
}
