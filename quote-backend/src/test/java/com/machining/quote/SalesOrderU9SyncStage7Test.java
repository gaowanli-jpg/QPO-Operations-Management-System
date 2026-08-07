package com.machining.quote;

import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.SalesOrderU9SyncResp;
import com.machining.quote.dto.u9.U9ApiResult;
import com.machining.quote.dto.u9.U9ResultDTO;
import com.machining.quote.dto.u9.U9SalesOrderHeadDTO;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.DataScopeService;
import com.machining.quote.service.SalesOrderU9SyncService;
import com.machining.quote.service.u9.U9SalesOrderClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesOrderU9SyncStage7Test {
    @Test
    void missingConfirmedTicketMaterialNoFailsBeforeCallingU9AndWritesFailureSummary() {
        Fixture fixture = fixture();
        SalesOrderHeader header = header(9001L, "SOQPO-001", "未同步");
        SalesOrderItem item = item(5001L, header.getId(), "SALES-MAT-001", new BigDecimal("18.50"));
        item.setMaterialNo(null);
        item.setMaterialNoStatus("未确认");
        fixture.withOrder(header, List.of(item));

        SalesOrderU9SyncResp resp = fixture.service.syncSalesOrders(List.of(header.getId()));

        assertThat(resp.isSuccess()).isFalse();
        assertThat(resp.getStatus()).isEqualTo("FAILED");
        assertThat(resp.getMessage()).contains("物料号");
        assertThat(resp.getRows()).singleElement()
                .satisfies(row -> {
                    assertThat(row.isSuccess()).isFalse();
                    assertThat(row.getSalesOrderId()).isEqualTo(header.getId());
                    assertThat(row.getErrorMessage()).contains("物料号");
        });
        verify(fixture.u9Client, never()).create(any());

        SalesOrderHeader failureUpdate = fixture.lastHeaderUpdate();
        assertThat(failureUpdate.getId()).isEqualTo(header.getId());
        assertThat(failureUpdate.getU9SyncStatus()).isEqualTo("同步失败");
        assertThat(failureUpdate.getU9SyncMessage()).contains("物料号");
    }

    @Test
    void onlyUntaxedPriceFailsBeforeCallingDataCenterOrU9() {
        Fixture fixture = fixture();
        SalesOrderHeader header = header(9001L, "SOQPO-001", "未同步");
        SalesOrderItem item = item(5001L, header.getId(), "SALES-MAT-001", null);
        item.setUntaxedUnitPrice(new BigDecimal("16.37"));
        markConfirmed(item, "MAT-CONFIRMED-001", 4);
        fixture.withOrder(header, List.of(item));

        SalesOrderU9SyncResp resp = fixture.service.syncSalesOrders(List.of(header.getId()));

        assertThat(resp.isSuccess()).isFalse();
        assertThat(resp.getStatus()).isEqualTo("FAILED");
        assertThat(resp.getMessage()).contains("含税单价为空");
        assertThat(resp.getRows()).singleElement()
                .satisfies(row -> assertThat(row.getErrorMessage()).contains("含税单价为空"));
        verify(fixture.u9Client, never()).create(any());

        SalesOrderHeader failureUpdate = fixture.lastHeaderUpdate();
        assertThat(failureUpdate.getU9SyncStatus()).isEqualTo("同步失败");
        assertThat(failureUpdate.getU9SyncMessage()).contains("含税单价为空");
    }

    @Test
    void successfulSyncBuildsU9RequestFromSalesOrderItemsAndWritesU9Codes() {
        Fixture fixture = fixture();
        SalesOrderHeader header = header(9001L, "SOQPO-001", "未同步");
        header.setDocumentTypeCode("SO88");
        header.setCustomerCode("CUST-001");
        SalesOrderItem item = item(5001L, header.getId(), "SALES-MAT-001", new BigDecimal("18.50"));
        item.setUntaxedUnitPrice(new BigDecimal("16.37"));
        markConfirmed(item, "MAT-CONFIRMED-001", 4);
        fixture.withOrder(header, List.of(item));
        when(fixture.u9Client.create(any())).thenReturn(apiResult(0,
                u9Row(header.getSalesOrderNo(), true, "U9-ID-1", "U9-SO-001", "")
        ));

        SalesOrderU9SyncResp resp = fixture.service.syncSalesOrders(List.of(header.getId()));

        assertThat(resp.isSuccess()).isTrue();
        assertThat(resp.getStatus()).isEqualTo("SUCCESS");
        assertThat(resp.getSuccessOrderCount()).isEqualTo(1);
        assertThat(resp.getFailedOrderCount()).isZero();
        assertThat(resp.getRows()).singleElement()
                .satisfies(row -> {
                    assertThat(row.getU9OrderId()).isEqualTo("U9-ID-1");
                    assertThat(row.getU9OrderCode()).isEqualTo("U9-SO-001");
                });

        U9SalesOrderHeadDTO request = fixture.capturedU9Orders().get(0);
        assertThat(request.getOtherID()).isEqualTo(header.getSalesOrderNo());
        assertThat(request.getOrderBy()).isEqualTo("CUST-001");
        assertThat(request.getDocumentType()).isEqualTo("SO88");
        assertThat(request.getCustomerOrderNo()).isEqualTo(header.getCustomerOrderNo());
        assertThat(request.getSOLineDTOList()).singleElement()
                .satisfies(line -> {
                    assertThat(line.getItemCode()).isEqualTo("MAT-CONFIRMED-001");
                    assertThat(line.getCustomerItemNo()).isEqualTo("SALES-MAT-001");
                    assertThat(line.getOrderByQtyTU()).isEqualByComparingTo("4");
                    assertThat(line.getOrderPriceTC()).isEqualByComparingTo("18.50");
                    assertThat(line.getFinallyPriceTC()).isEqualByComparingTo("18.50");
                    assertThat(line.getSOShiplineDTOList()).singleElement()
                            .satisfies(shipLine -> {
                                assertThat(shipLine.getItemCode()).isEqualTo("MAT-CONFIRMED-001");
                                assertThat(shipLine.getShipPlanQtyTU()).isEqualByComparingTo("4");
                                assertThat(shipLine.getRequireDate()).isEqualTo("2026-08-08");
                            });
                });

        SalesOrderHeader successUpdate = fixture.lastHeaderUpdate();
        assertThat(successUpdate.getU9SyncStatus()).isEqualTo("已同步");
        assertThat(successUpdate.getU9OrderId()).isEqualTo("U9-ID-1");
        assertThat(successUpdate.getU9OrderCode()).isEqualTo("U9-SO-001");
        assertThat(successUpdate.getU9SyncMessage()).contains("同步成功");
    }

    @Test
    void partialU9FailureUpdatesEachSalesOrderIndependently() {
        Fixture fixture = fixture();
        SalesOrderHeader first = header(9001L, "SOQPO-001", "未同步");
        SalesOrderHeader second = header(9002L, "SOQPO-002", "未同步");
        SalesOrderItem firstItem = item(5001L, first.getId(), "SALES-MAT-001", new BigDecimal("18.50"));
        SalesOrderItem secondItem = item(5002L, second.getId(), "SALES-MAT-002", new BigDecimal("21.00"));
        markConfirmed(firstItem, "MAT-001", 2);
        markConfirmed(secondItem, "MAT-002", 3);
        fixture.withOrders(
                List.of(first, second),
                List.of(List.of(firstItem), List.of(secondItem))
        );
        when(fixture.u9Client.create(any())).thenReturn(apiResult(0,
                u9Row(first.getSalesOrderNo(), true, "U9-ID-1", "U9-SO-001", ""),
                u9Row(second.getSalesOrderNo(), false, null, null, "U9 item unavailable")
        ));

        SalesOrderU9SyncResp resp = fixture.service.syncSalesOrders(List.of(first.getId(), second.getId()));

        assertThat(resp.isSuccess()).isFalse();
        assertThat(resp.getStatus()).isEqualTo("PARTIAL_SUCCESS");
        assertThat(resp.getSuccessOrderCount()).isEqualTo(1);
        assertThat(resp.getFailedOrderCount()).isEqualTo(1);
        assertThat(resp.getRows()).extracting("salesOrderNo")
                .containsExactly("SOQPO-001", "SOQPO-002");
        assertThat(resp.getRows().get(1).getErrorMessage()).contains("U9 item unavailable");

        List<SalesOrderHeader> updates = fixture.headerUpdates();
        assertThat(updates).anySatisfy(update -> {
            assertThat(update.getId()).isEqualTo(first.getId());
            assertThat(update.getU9SyncStatus()).isEqualTo("已同步");
        });
        assertThat(updates).anySatisfy(update -> {
            assertThat(update.getId()).isEqualTo(second.getId());
            assertThat(update.getU9SyncStatus()).isEqualTo("同步失败");
            assertThat(update.getU9SyncMessage()).contains("U9 item unavailable");
        });
    }

    @Test
    void failedOrderCanBeRetriedButAlreadySyncedOrderIsNotCreatedAgain() {
        Fixture fixture = fixture();
        SalesOrderHeader failed = header(9001L, "SOQPO-001", "同步失败");
        failed.setU9SyncMessage("previous U9 timeout");
        SalesOrderHeader synced = header(9002L, "SOQPO-002", "已同步");
        synced.setU9OrderId("OLD-ID");
        synced.setU9OrderCode("OLD-CODE");
        SalesOrderItem item = item(5001L, failed.getId(), "SALES-MAT-001", new BigDecimal("18.50"));
        markConfirmed(item, "MAT-001", 1);
        fixture.withOrders(
                List.of(failed, synced),
                List.of(List.of(item), List.of())
        );
        when(fixture.u9Client.create(any())).thenReturn(apiResult(0,
                u9Row(failed.getSalesOrderNo(), true, "NEW-ID", "NEW-CODE", "")
        ));

        SalesOrderU9SyncResp resp = fixture.service.syncSalesOrders(List.of(failed.getId(), synced.getId()));

        assertThat(resp.getSuccessOrderCount()).isEqualTo(1);
        assertThat(resp.getFailedOrderCount()).isEqualTo(1);
        assertThat(fixture.capturedU9Orders()).extracting(U9SalesOrderHeadDTO::getOtherID)
                .containsExactly(failed.getSalesOrderNo());
        assertThat(resp.getRows()).anySatisfy(row -> {
            assertThat(row.getSalesOrderId()).isEqualTo(synced.getId());
            assertThat(row.isSuccess()).isFalse();
            assertThat(row.getErrorMessage()).contains("已同步");
        });
    }

    @Test
    void auditActionsAndControllerPermissionCloseStageSevenBoundary() {
        assertThat(AuditService.inferModule("SALES_ORDER_U9_SYNC")).isEqualTo("MARKET");
        assertThat(AuditService.labelForAction("SALES_ORDER_U9_SYNC")).contains("销售订单").contains("U9");
        assertThat(readSource("service/SalesOrderU9SyncService.java"))
                .doesNotContain("OrderDeliveryTicketMapper", "loadTickets(", "ticketMapper.selectList", "order_delivery_ticket");
        assertThat(readSource("controller/SalesOrderU9SyncController.java"))
                .contains("@PostMapping(\"/orders/{salesOrderId}/sync\")")
                .contains("@PostMapping(\"/sync\")")
                .contains("@RequirePermission(\"market:u9-sync:submit\")");
        assertThat(readSource("controller/SalesOrderQpoImportController.java"))
                .doesNotContain("@PostMapping(\"/u9-sync\")");
        assertThat(readSource("controller/SalesOrderU9SyncCompatibilityController.java"))
                .contains("@Deprecated")
                .contains("@RequestMapping(\"/api/market/sales-orders\")")
                .contains("@PostMapping(\"/u9-sync\")")
                .contains("@RequirePermission(\"market:u9-sync:submit\")");
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

    private SalesOrderHeader header(Long id, String salesOrderNo, String u9Status) {
        SalesOrderHeader header = new SalesOrderHeader();
        header.setId(id);
        header.setSalesOrderNo(salesOrderNo);
        header.setDocumentTypeCode("SO01");
        header.setCustomerCode("CUST-001");
        header.setCustomerName("测试客户");
        header.setCustomerOrderNo("PO-" + id);
        header.setCustomerProjectNo("CP-" + id);
        header.setInternalProjectNo("IP-" + id);
        header.setSalesOrderDate(LocalDate.of(2026, 7, 30));
        header.setStatus(QuoteStatusWorkflow.RELEASED);
        header.setU9SyncStatus(u9Status);
        return header;
    }

    private SalesOrderItem item(Long id, Long salesOrderId, String salesMaterialNo, BigDecimal price) {
        SalesOrderItem item = new SalesOrderItem();
        item.setId(id);
        item.setSalesOrderId(salesOrderId);
        item.setLineNo(7);
        item.setPartNo("P-" + id);
        item.setDescription("零件 " + id);
        item.setSalesMaterialNo(salesMaterialNo);
        item.setTaxIncludedUnitPrice(price);
        item.setVersion("A");
        item.setQty(1);
        item.setReleasedQty(1);
        item.setRemainingQty(0);
        item.setInternalDeliveryDate(LocalDate.of(2026, 8, 8));
        item.setCustomerDeliveryDate(LocalDate.of(2026, 8, 9));
        item.setStatus(QuoteStatusWorkflow.RELEASED);
        return item;
    }

    private void markConfirmed(SalesOrderItem item, String materialNo, int qty) {
        item.setMaterialNo(materialNo);
        item.setMaterialNoStatus("已确认");
        item.setMaterialNoSource("MES_TICKET");
        item.setQty(qty);
        item.setReleasedQty(qty);
        item.setRemainingQty(0);
    }

    @SafeVarargs
    private U9ApiResult<U9ResultDTO> apiResult(int resCode, U9ResultDTO... rows) {
        U9ApiResult<U9ResultDTO> result = new U9ApiResult<>();
        result.setResCode(resCode);
        result.setSuccess(resCode == 0);
        result.setResMsg(resCode == 0 ? "ok" : "failed");
        result.setData(List.of(rows));
        return result;
    }

    private U9ResultDTO u9Row(String otherId, boolean success, String u9Id, String u9Code, String error) {
        U9ResultDTO row = new U9ResultDTO();
        row.setMOtherID(otherId);
        row.setMIsSucess(success);
        row.setMID(u9Id);
        row.setMCode(u9Code);
        row.setMErrorMsg(error);
        return row;
    }

    private String readSource(String relativePath) {
        try {
            return java.nio.file.Files.readString(
                    java.nio.file.Path.of("src/main/java/com/machining/quote", relativePath),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
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

        private void withOrder(SalesOrderHeader header, List<SalesOrderItem> items) {
            withOrders(List.of(header), List.of(items));
        }

        private void withOrders(
                List<SalesOrderHeader> headers,
                List<List<SalesOrderItem>> items) {
            for (SalesOrderHeader header : headers) {
                when(headerMapper.selectByIdForUpdate(header.getId())).thenReturn(header);
            }
            when(itemMapper.selectList(any())).thenReturn(items.get(0), items.subList(1, items.size()).toArray(List[]::new));
        }

        private List<U9SalesOrderHeadDTO> capturedU9Orders() {
            ArgumentCaptor<List<U9SalesOrderHeadDTO>> captor = ArgumentCaptor.forClass(List.class);
            verify(u9Client).create(captor.capture());
            return captor.getValue();
        }

        private SalesOrderHeader lastHeaderUpdate() {
            List<SalesOrderHeader> updates = headerUpdates();
            return updates.get(updates.size() - 1);
        }

        private List<SalesOrderHeader> headerUpdates() {
            ArgumentCaptor<SalesOrderHeader> captor = ArgumentCaptor.forClass(SalesOrderHeader.class);
            verify(headerMapper, atLeastOnce()).updateById(captor.capture());
            return captor.getAllValues();
        }
    }
}
