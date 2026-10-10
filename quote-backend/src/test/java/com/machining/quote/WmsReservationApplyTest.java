package com.machining.quote;

import com.machining.quote.dto.wms.WmsSaleLadingReserveReq;
import com.machining.quote.dto.wms.WmsSaleLadingReserveResp;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderDeliveryTicketAllocation;
import com.machining.quote.entity.WmsReservationBatch;
import com.machining.quote.entity.WmsReservationItem;
import com.machining.quote.entity.WmsReservationTicket;
import com.machining.quote.mapper.OrderDeliveryTicketAllocationMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.WmsReservationBatchMapper;
import com.machining.quote.mapper.WmsReservationItemMapper;
import com.machining.quote.mapper.WmsReservationTicketMapper;
import com.machining.quote.service.wms.WmsReservationContractValidator;
import com.machining.quote.service.wms.WmsReservationFingerprintService;
import com.machining.quote.service.wms.WmsReservationStateService;
import com.machining.quote.service.SalesOrderAllocationLinkService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WmsReservationApplyTest {
    @Test
    void saveValidatedResponseStoresItemBucketsAndDeterministicTicketSplits() {
        Fixture fixture = fixture();
        WmsSaleLadingReserveReq request = request("QPO-WMS-1", "SO-100", requestItem("MAT-A", "7", "remark"));
        WmsReservationBatch batch = batch(501L, "SUBMITTING", request);
        WmsReservationItem item = item(701L, "MAT-A", "7", "remark");
        WmsReservationTicket first = reservationTicket(801L, 501L, 701L, 101, 1001L, 10, "MAT-A", "4");
        WmsReservationTicket second = reservationTicket(802L, 501L, 701L, 102, 1002L, 20, "MAT-A", "3");
        when(fixture.batchMapper.selectById(501L)).thenReturn(batch);
        when(fixture.itemMapper.selectList(any())).thenReturn(List.of(item));
        when(fixture.ticketMapper.selectByBatchId(501L)).thenReturn(List.of(first, second));

        fixture.service.saveValidatedResponse(501L,
                success("QPO-WMS-1", "SO-100", responseItem("MAT-A", "7", "3", "2", "5", "2")));

        ArgumentCaptor<WmsReservationItem> itemUpdate = ArgumentCaptor.forClass(WmsReservationItem.class);
        verify(fixture.itemMapper).updateById(itemUpdate.capture());
        assertThat(itemUpdate.getValue().getStatus()).isEqualTo("RESPONSE_RECEIVED");
        assertThat(itemUpdate.getValue().getFrozenCount()).isEqualByComparingTo("5");
        assertThat(itemUpdate.getValue().getInProductionCount()).isEqualByComparingTo("2");

        ArgumentCaptor<WmsReservationTicket> ticketUpdate = ArgumentCaptor.forClass(WmsReservationTicket.class);
        verify(fixture.ticketMapper, times(2)).updateById(ticketUpdate.capture());
        assertThat(ticketUpdate.getAllValues()).extracting(WmsReservationTicket::getTicketId)
                .containsExactly(101, 102);
        assertThat(ticketUpdate.getAllValues()).extracting(ticket -> ticket.getStockFrozenCount().toPlainString()
                        + ":" + ticket.getInProductionCount().toPlainString()
                        + ":" + ticket.getSurplusCount().toPlainString())
                .containsExactly("3:1:0", "0:1:2");

        ArgumentCaptor<WmsReservationBatch> batchUpdate = ArgumentCaptor.forClass(WmsReservationBatch.class);
        verify(fixture.batchMapper).updateById(batchUpdate.capture());
        assertThat(batchUpdate.getValue().getStatus()).isEqualTo("RESPONSE_RECEIVED");
        assertThat(batchUpdate.getValue().getResponseCode()).isEqualTo("200");
        assertThat(batchUpdate.getValue().getResponseMsg()).isEqualTo("ok");
    }

    @Test
    void applyResponseWritesPositiveAllocationRowsAndMarksBatchSuccess() {
        Fixture fixture = fixture();
        WmsSaleLadingReserveReq request = request("QPO-WMS-1", "SO-100", requestItem("MAT-A", "5", "remark"));
        WmsReservationBatch batch = batch(501L, "RESPONSE_RECEIVED", request);
        WmsReservationItem item = item(701L, "MAT-A", "5", "remark");
        WmsReservationTicket ticket = reservationTicket(801L, 501L, 701L, 101, 1001L, 10, "MAT-A", "5");
        ticket.setStockFrozenCount(new BigDecimal("2"));
        ticket.setInProductionCount(new BigDecimal("1"));
        ticket.setSurplusCount(new BigDecimal("2"));
        when(fixture.batchMapper.selectById(501L)).thenReturn(batch);
        when(fixture.itemMapper.selectList(any())).thenReturn(List.of(item));
        when(fixture.ticketMapper.selectByBatchId(501L)).thenReturn(List.of(ticket));
        when(fixture.ticketSourceMapper.selectByIdForUpdate(101)).thenReturn(deliveryTicket(101, "MAT-A", 5));
        when(fixture.allocationMapper.selectByWmsReservationTicketId(801L)).thenReturn(List.of());
        OrderDeliveryTicketAllocation defaultProduction = productionAllocation(901, 101, 5);
        when(fixture.allocationMapper.selectByTicketIdForUpdate(101)).thenReturn(List.of(defaultProduction));

        fixture.service.applyResponse(501L);

        ArgumentCaptor<OrderDeliveryTicketAllocation> allocationCaptor = ArgumentCaptor.forClass(OrderDeliveryTicketAllocation.class);
        verify(fixture.allocationMapper, times(2)).insert(allocationCaptor.capture());
        assertThat(allocationCaptor.getAllValues()).extracting(OrderDeliveryTicketAllocation::getWmsReservationTicketId)
                .containsOnly(801L);
        assertThat(allocationCaptor.getAllValues()).extracting(OrderDeliveryTicketAllocation::getAllocationType)
                .containsExactly("IN_STOCK_LOCK", "IN_PRODUCTION_LOCK");
        assertThat(allocationCaptor.getAllValues()).extracting(OrderDeliveryTicketAllocation::getQty)
                .containsExactly(2, 1);
        assertThat(allocationCaptor.getAllValues()).allSatisfy(row -> {
            assertThat(row.getTicketId()).isEqualTo(101);
            assertThat(row.getMaterialNo()).isEqualTo("MAT-A");
            assertThat(row.getTicketQty()).isEqualTo(5);
            assertThat(row.getProcessRoute()).isEqualTo(List.of(Map.of("processCode", "CUT")));
            assertThat(row.getProcessRoute()).isNotSameAs(List.of(Map.of("processCode", "CUT")));
            assertThat(row.getMaterialData()).isEqualTo(Map.of("shape", "plate"));
            assertThat(row.getMesProductionStatus()).isEqualTo("PENDING");
            assertThat(row.getStatus()).isEqualTo("ACTIVE");
        });

        ArgumentCaptor<OrderDeliveryTicketAllocation> productionUpdate = ArgumentCaptor.forClass(OrderDeliveryTicketAllocation.class);
        verify(fixture.allocationMapper).updateById(productionUpdate.capture());
        assertThat(productionUpdate.getValue().getId()).isEqualTo(901);
        assertThat(productionUpdate.getValue().getQty()).isEqualTo(2);
        assertThat(productionUpdate.getValue().getAllocationSource()).isEqualTo("MES_MATERIAL");
        assertThat(allocationCaptor.getAllValues().stream().mapToInt(OrderDeliveryTicketAllocation::getQty).sum()
                + productionUpdate.getValue().getQty()).isEqualTo(5);

        ArgumentCaptor<OrderDeliveryTicket> ticketUpdate = ArgumentCaptor.forClass(OrderDeliveryTicket.class);
        verify(fixture.ticketSourceMapper).updateById(ticketUpdate.capture());
        assertThat(ticketUpdate.getValue().getWmsAllocateStatus()).isEqualTo("SUCCESS");
        assertThat(ticketUpdate.getValue().getWmsAllocatedAt()).isNotNull();

        ArgumentCaptor<WmsReservationTicket> reservationTicketUpdate = ArgumentCaptor.forClass(WmsReservationTicket.class);
        verify(fixture.ticketMapper).updateById(reservationTicketUpdate.capture());
        assertThat(reservationTicketUpdate.getValue().getStatus()).isEqualTo("SUCCESS");
        assertThat(reservationTicketUpdate.getValue().getAppliedAt()).isNotNull();

        ArgumentCaptor<WmsReservationBatch> batchUpdate = ArgumentCaptor.forClass(WmsReservationBatch.class);
        verify(fixture.batchMapper).updateById(batchUpdate.capture());
        assertThat(batchUpdate.getValue().getStatus()).isEqualTo("SUCCESS");
        assertThat(batchUpdate.getValue().getCompletedAt()).isNotNull();
    }

    @Test
    void fullInventoryCoverageKeepsDefaultProductionRowAtZeroWithoutCreatingZeroQuantityRow() {
        Fixture fixture = fixture();
        WmsSaleLadingReserveReq request = request("QPO-WMS-2", "SO-100", requestItem("MAT-A", "5", "remark"));
        WmsReservationBatch batch = batch(502L, "RESPONSE_RECEIVED", request);
        WmsReservationItem item = item(702L, "MAT-A", "5", "remark");
        WmsReservationTicket ticket = reservationTicket(802L, 502L, 702L, 102, 1002L, 20, "MAT-A", "5");
        ticket.setStockFrozenCount(new BigDecimal("3"));
        ticket.setInProductionCount(new BigDecimal("2"));
        ticket.setSurplusCount(BigDecimal.ZERO);
        when(fixture.batchMapper.selectById(502L)).thenReturn(batch);
        when(fixture.itemMapper.selectList(any())).thenReturn(List.of(item));
        when(fixture.ticketMapper.selectByBatchId(502L)).thenReturn(List.of(ticket));
        when(fixture.ticketSourceMapper.selectByIdForUpdate(102)).thenReturn(deliveryTicket(102, "MAT-A", 5));
        when(fixture.allocationMapper.selectByWmsReservationTicketId(802L)).thenReturn(List.of());
        when(fixture.allocationMapper.selectByTicketIdForUpdate(102))
                .thenReturn(List.of(productionAllocation(902, 102, 5)));

        fixture.service.applyResponse(502L);

        ArgumentCaptor<OrderDeliveryTicketAllocation> newRows = ArgumentCaptor.forClass(OrderDeliveryTicketAllocation.class);
        verify(fixture.allocationMapper, times(2)).insert(newRows.capture());
        assertThat(newRows.getAllValues()).extracting(OrderDeliveryTicketAllocation::getAllocationType)
                .containsExactly("IN_STOCK_LOCK", "IN_PRODUCTION_LOCK");
        assertThat(newRows.getAllValues()).extracting(OrderDeliveryTicketAllocation::getQty).containsExactly(3, 2);
        ArgumentCaptor<OrderDeliveryTicketAllocation> replacedRow = ArgumentCaptor.forClass(OrderDeliveryTicketAllocation.class);
        verify(fixture.allocationMapper).updateById(replacedRow.capture());
        assertThat(replacedRow.getValue().getId()).isEqualTo(902);
        assertThat(replacedRow.getValue().getStatus()).isEqualTo("ACTIVE");
        assertThat(replacedRow.getValue().getQty()).isEqualTo(0);
        assertThat(newRows.getAllValues().stream().mapToInt(OrderDeliveryTicketAllocation::getQty).sum()).isEqualTo(5);
    }

    @Test
    void fullInventoryCoverageRepairsMissingProductionRowAtZero() {
        Fixture fixture = fixture();
        WmsSaleLadingReserveReq request = request("QPO-WMS-3", "SO-101", requestItem("MAT-B", "5", "remark"));
        WmsReservationBatch batch = batch(503L, "RESPONSE_RECEIVED", request);
        WmsReservationItem item = item(703L, "MAT-B", "5", "remark");
        WmsReservationTicket ticket = reservationTicket(803L, 503L, 703L, 103, 1003L, 21, "MAT-B", "5");
        ticket.setStockFrozenCount(new BigDecimal("5"));
        ticket.setInProductionCount(BigDecimal.ZERO);
        ticket.setSurplusCount(BigDecimal.ZERO);
        when(fixture.batchMapper.selectById(503L)).thenReturn(batch);
        when(fixture.itemMapper.selectList(any())).thenReturn(List.of(item));
        when(fixture.ticketMapper.selectByBatchId(503L)).thenReturn(List.of(ticket));
        when(fixture.ticketSourceMapper.selectByIdForUpdate(103)).thenReturn(deliveryTicket(103, "MAT-B", 5));
        when(fixture.allocationMapper.selectByWmsReservationTicketId(803L)).thenReturn(List.of());
        when(fixture.allocationMapper.selectByTicketIdForUpdate(103)).thenReturn(List.of());

        fixture.service.applyResponse(503L);

        ArgumentCaptor<OrderDeliveryTicketAllocation> newRows = ArgumentCaptor.forClass(OrderDeliveryTicketAllocation.class);
        verify(fixture.allocationMapper, times(2)).insert(newRows.capture());
        assertThat(newRows.getAllValues()).extracting(OrderDeliveryTicketAllocation::getAllocationType)
                .containsExactly("IN_STOCK_LOCK", "PRODUCTION_REQUIRED");
        OrderDeliveryTicketAllocation production = newRows.getAllValues().get(1);
        assertThat(production.getQty()).isZero();
        assertThat(production.getWmsReservationTicketId()).isEqualTo(803L);
    }

    @Test
    void applyFailureMarksApplyFailedAndReplaySkipsExistingAllocationRows() {
        Fixture fixture = fixture();
        WmsSaleLadingReserveReq request = request("QPO-WMS-1", "SO-100", requestItem("MAT-A", "5", "remark"));
        WmsReservationBatch batch = batch(501L, "RESPONSE_RECEIVED", request);
        WmsReservationItem item = item(701L, "MAT-A", "5", "remark");
        WmsReservationTicket ticket = reservationTicket(801L, 501L, 701L, 101, 1001L, 10, "MAT-A", "5");
        ticket.setStockFrozenCount(new BigDecimal("2"));
        ticket.setInProductionCount(BigDecimal.ZERO);
        ticket.setSurplusCount(new BigDecimal("3"));
        when(fixture.batchMapper.selectById(501L)).thenReturn(batch);
        when(fixture.itemMapper.selectList(any())).thenReturn(List.of(item));
        when(fixture.ticketMapper.selectByBatchId(501L)).thenReturn(List.of(ticket));
        when(fixture.ticketSourceMapper.selectByIdForUpdate(101)).thenReturn(deliveryTicket(101, "MAT-A", 5));
        when(fixture.allocationMapper.selectByWmsReservationTicketId(801L)).thenReturn(List.of());
        when(fixture.allocationMapper.selectByTicketIdForUpdate(101))
                .thenReturn(List.of(productionAllocation(901, 101, 5)));
        doThrow(new RuntimeException("insert failed")).when(fixture.allocationMapper).insert(any(OrderDeliveryTicketAllocation.class));

        assertThatThrownBy(() -> fixture.service.applyResponse(501L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("insert failed");
        ArgumentCaptor<WmsReservationBatch> failedUpdate = ArgumentCaptor.forClass(WmsReservationBatch.class);
        verify(fixture.batchMapper).updateById(failedUpdate.capture());
        assertThat(failedUpdate.getValue().getStatus()).isEqualTo("APPLY_FAILED");

        Fixture replayFixture = fixture();
        when(replayFixture.batchMapper.selectById(501L)).thenReturn(batch(501L, "APPLY_FAILED", request));
        when(replayFixture.itemMapper.selectList(any())).thenReturn(List.of(item));
        when(replayFixture.ticketMapper.selectByBatchId(501L)).thenReturn(List.of(ticket));
        when(replayFixture.ticketSourceMapper.selectByIdForUpdate(101)).thenReturn(deliveryTicket(101, "MAT-A", 5));
        when(replayFixture.allocationMapper.selectByTicketIdForUpdate(101))
                .thenReturn(List.of(productionAllocation(901, 101, 5)));
        OrderDeliveryTicketAllocation existing = new OrderDeliveryTicketAllocation();
        existing.setWmsReservationTicketId(801L);
        existing.setAllocationType("IN_STOCK_LOCK");
        when(replayFixture.allocationMapper.selectByWmsReservationTicketId(801L)).thenReturn(List.of(existing));

        replayFixture.service.applyResponse(501L);

        ArgumentCaptor<OrderDeliveryTicketAllocation> replayUpdate = ArgumentCaptor.forClass(OrderDeliveryTicketAllocation.class);
        verify(replayFixture.allocationMapper).updateById(replayUpdate.capture());
        assertThat(replayUpdate.getValue().getAllocationType()).isEqualTo("PRODUCTION_REQUIRED");
        assertThat(replayUpdate.getValue().getQty()).isEqualTo(3);
    }

    private Fixture fixture() {
        return new Fixture();
    }

    private WmsReservationBatch batch(Long id, String status, WmsSaleLadingReserveReq request) {
        WmsReservationBatch batch = new WmsReservationBatch();
        batch.setId(id);
        batch.setExternalBizId(request.getExternalBizId());
        batch.setSaleOrderId(301L);
        batch.setSaleOrderNo(request.getSaleNo());
        batch.setStatus(status);
        batch.setAttemptNo(1);
        batch.setRequestPayload(request);
        batch.setRequestFingerprint(new WmsReservationFingerprintService().fingerprint(request.getSaleNo(), request.getItems()));
        return batch;
    }

    private WmsReservationItem item(Long id, String productCode, String reserveCount, String remark) {
        WmsReservationItem item = new WmsReservationItem();
        item.setId(id);
        item.setBatchId(501L);
        item.setProductCode(productCode);
        item.setReserveCount(new BigDecimal(reserveCount));
        item.setRemark(remark);
        item.setStatus("RESERVED");
        return item;
    }

    private WmsReservationTicket reservationTicket(Long id,
                                                   Long batchId,
                                                   Long itemId,
                                                   Integer ticketId,
                                                   Long salesOrderItemId,
                                                   Integer lineNo,
                                                   String productCode,
                                                   String reserveCount) {
        WmsReservationTicket ticket = new WmsReservationTicket();
        ticket.setId(id);
        ticket.setBatchId(batchId);
        ticket.setBatchItemId(itemId);
        ticket.setTicketId(ticketId);
        ticket.setSalesOrderItemId(salesOrderItemId);
        ticket.setSalesOrderLineNo(lineNo);
        ticket.setProductCode(productCode);
        ticket.setReserveCount(new BigDecimal(reserveCount));
        ticket.setStatus("RESPONSE_RECEIVED");
        return ticket;
    }

    private OrderDeliveryTicket deliveryTicket(Integer id, String materialNo, Integer qty) {
        OrderDeliveryTicket ticket = new OrderDeliveryTicket();
        ticket.setId(id);
        ticket.setRfqId(88);
        ticket.setRfqNo("RFQ-88");
        ticket.setCustomerName("ACME");
        ticket.setItemId(77);
        ticket.setSourceType("SALES_ORDER");
        ticket.setSourceOrderId(301L);
        ticket.setSourceItemId(1001L);
        ticket.setOrderType("SALE");
        ticket.setItemType("PART");
        ticket.setPartNo("P-100");
        ticket.setVersion("A");
        ticket.setQty(qty);
        ticket.setMaterialNo(materialNo);
        ticket.setInternalProjectNo("IP-9");
        ticket.setCustomerOrderNo("CO-9");
        ticket.setCustomerProjectNo("CP-9");
        ticket.setMaterialName("6061");
        ticket.setEngineeringMaterialCode("EMC");
        ticket.setEngineeringMaterialName("7075");
        ticket.setEngineeringMaterialTypeCode("RAW");
        ticket.setEngineeringMaterialTypeName("Raw Material");
        ticket.setEngineeringMaterialCategory("ALUMINUM");
        ticket.setMaterialShape("plate");
        ticket.setSurfaceTreatment("Anodized");
        ticket.setRemark("Rush note");
        ticket.setDescription("Bracket");
        ticket.setDeliveryDate(LocalDate.of(2026, 8, 14));
        ticket.setProcessRoute(List.of(Map.of("processCode", "CUT")));
        ticket.setMaterialData(Map.of("shape", "plate"));
        ticket.setMaterialGrossWeight(12.3);
        ticket.setPartNetWeight(10.2);
        return ticket;
    }

    private OrderDeliveryTicketAllocation productionAllocation(Integer id, Integer ticketId, Integer qty) {
        OrderDeliveryTicketAllocation allocation = new OrderDeliveryTicketAllocation();
        allocation.setId(id);
        allocation.setTicketId(ticketId);
        allocation.setAllocationType("PRODUCTION_REQUIRED");
        allocation.setAllocationSource("MES_MATERIAL");
        allocation.setQty(qty);
        allocation.setTicketQty(qty);
        allocation.setStatus("ACTIVE");
        allocation.setMesProductionStatus("PENDING");
        return allocation;
    }

    private WmsSaleLadingReserveReq request(String externalBizId, String saleNo, WmsSaleLadingReserveReq.Item... items) {
        WmsSaleLadingReserveReq request = new WmsSaleLadingReserveReq();
        request.setExternalBizId(externalBizId);
        request.setSaleNo(saleNo);
        request.setItems(List.of(items));
        return request;
    }

    private WmsSaleLadingReserveReq.Item requestItem(String productCode, String reserveCount, String remark) {
        WmsSaleLadingReserveReq.Item item = new WmsSaleLadingReserveReq.Item();
        item.setProductCode(productCode);
        item.setReserveCount(new BigDecimal(reserveCount));
        item.setRemark(remark);
        return item;
    }

    private WmsSaleLadingReserveResp success(String externalBizId, String saleNo, WmsSaleLadingReserveResp.Item... items) {
        WmsSaleLadingReserveResp response = new WmsSaleLadingReserveResp();
        response.setCode(200);
        response.setMsg("ok");
        WmsSaleLadingReserveResp.Data data = new WmsSaleLadingReserveResp.Data();
        data.setExternalBizId(externalBizId);
        data.setSaleOrderNo(saleNo);
        data.setItems(List.of(items));
        response.setData(data);
        return response;
    }

    private WmsSaleLadingReserveResp.Item responseItem(String productCode,
                                                       String reserveCount,
                                                       String stockCount,
                                                       String inProductionCount,
                                                       String frozenCount,
                                                       String surplusCount) {
        WmsSaleLadingReserveResp.Item item = new WmsSaleLadingReserveResp.Item();
        item.setProductCode(productCode);
        item.setReserveCount(new BigDecimal(reserveCount));
        item.setStockCount(new BigDecimal(stockCount));
        item.setInProductionCount(new BigDecimal(inProductionCount));
        item.setFrozenCount(new BigDecimal(frozenCount));
        item.setSurplusCount(new BigDecimal(surplusCount));
        return item;
    }

    private static final class Fixture {
        private final WmsReservationBatchMapper batchMapper = mock(WmsReservationBatchMapper.class);
        private final WmsReservationItemMapper itemMapper = mock(WmsReservationItemMapper.class);
        private final WmsReservationTicketMapper ticketMapper = mock(WmsReservationTicketMapper.class);
        private final OrderDeliveryTicketMapper ticketSourceMapper = mock(OrderDeliveryTicketMapper.class);
        private final OrderDeliveryTicketAllocationMapper allocationMapper = mock(OrderDeliveryTicketAllocationMapper.class);
        private final WmsReservationStateService service = new WmsReservationStateService();

        private Fixture() {
            ReflectionTestUtils.setField(service, "batchMapper", batchMapper);
            ReflectionTestUtils.setField(service, "itemMapper", itemMapper);
            ReflectionTestUtils.setField(service, "ticketMapper", ticketMapper);
            ReflectionTestUtils.setField(service, "ticketSourceMapper", ticketSourceMapper);
            ReflectionTestUtils.setField(service, "allocationMapper", allocationMapper);
            ReflectionTestUtils.setField(service, "validator", new WmsReservationContractValidator());
            ReflectionTestUtils.setField(service, "fingerprintService", new WmsReservationFingerprintService());
            ReflectionTestUtils.setField(service, "salesOrderAllocationLinkService", mock(SalesOrderAllocationLinkService.class));
            doAnswer(invocation -> 1).when(batchMapper).updateById(any(WmsReservationBatch.class));
            doAnswer(invocation -> 1).when(itemMapper).updateById(any(WmsReservationItem.class));
            doAnswer(invocation -> 1).when(ticketMapper).updateById(any(WmsReservationTicket.class));
            doAnswer(invocation -> 1).when(ticketSourceMapper).updateById(any(OrderDeliveryTicket.class));
        }
    }
}
