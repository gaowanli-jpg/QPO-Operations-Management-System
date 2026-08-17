package com.machining.quote;

import com.machining.quote.dto.wms.WmsSaleLadingReserveReq;
import com.machining.quote.dto.wms.WmsSaleLadingReserveResp;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.WmsReservationBatch;
import com.machining.quote.entity.WmsReservationItem;
import com.machining.quote.entity.WmsReservationTicket;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.WmsReservationBatchMapper;
import com.machining.quote.mapper.WmsReservationItemMapper;
import com.machining.quote.mapper.WmsReservationTicketMapper;
import com.machining.quote.service.DataCenterCallException;
import com.machining.quote.service.DataCenterEndpointRegistry;
import com.machining.quote.service.wms.WmsReservationClient;
import com.machining.quote.service.wms.WmsReservationContractValidator;
import com.machining.quote.service.wms.WmsReservationFingerprintService;
import com.machining.quote.service.wms.WmsReservationPlanService;
import com.machining.quote.service.wms.WmsReservationStateService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WmsReservationStateServiceTest {
    @Test
    void reserveBatchPersistsRequestSnapshotItemsAndTicketsBeforeReturningRequest() {
        Fixture fixture = fixture();
        WmsReservationPlanService.ReservationGroup group = group("SO-100",
                item("MAT-A", "5", "first remark", "CP-001", "CO-001"),
                item("MAT-B", "2", "second remark", "CP-002", "CO-002"));
        group.getTickets().add(ticketReservation(101, 1001L, 10, "MAT-A", "3"));
        group.getTickets().add(ticketReservation(102, 1002L, 20, "MAT-A", "2"));
        group.getTickets().add(ticketReservation(103, 1003L, 30, "MAT-B", "2"));

        WmsReservationStateService.ReservationBatchContext context = fixture.service.reserveBatch(9001L, group);

        assertThat(context.batchId()).isEqualTo(501L);
        assertThat(context.request().getExternalBizId()).startsWith("QPO-WMS-");
        assertThat(context.request().getSaleNo()).isEqualTo("SO-100");
        assertThat(context.request().getItems()).extracting(WmsSaleLadingReserveReq.Item::getProductCode)
                .containsExactly("MAT-A", "MAT-B");
        assertThat(context.request().getItems()).extracting(WmsSaleLadingReserveReq.Item::getCustomerProjectNo)
                .containsExactly("CP-001", "CP-002");
        assertThat(context.request().getItems()).extracting(WmsSaleLadingReserveReq.Item::getCustomerOrderNo)
                .containsExactly("CO-001", "CO-002");

        ArgumentCaptor<WmsReservationBatch> batchCaptor = ArgumentCaptor.forClass(WmsReservationBatch.class);
        verify(fixture.batchMapper).insert(batchCaptor.capture());
        WmsReservationBatch batch = batchCaptor.getValue();
        assertThat(batch.getSystemTaskId()).isEqualTo(9001L);
        assertThat(batch.getSaleOrderNo()).isEqualTo("SO-100");
        assertThat(batch.getStatus()).isEqualTo("RESERVED");
        assertThat(batch.getAttemptNo()).isEqualTo(1);
        assertThat(batch.getItemCount()).isEqualTo(2);
        assertThat(batch.getTicketCount()).isEqualTo(3);
        assertThat(batch.getRequestPayload()).isInstanceOf(WmsSaleLadingReserveReq.class);
        WmsSaleLadingReserveReq persistedRequest = (WmsSaleLadingReserveReq) batch.getRequestPayload();
        assertThat(persistedRequest.getExternalBizId()).isEqualTo(context.request().getExternalBizId());
        assertThat(batch.getRequestFingerprint()).isEqualTo(new WmsReservationFingerprintService()
                .fingerprint("SO-100", persistedRequest.getItems()));

        ArgumentCaptor<WmsReservationItem> itemCaptor = ArgumentCaptor.forClass(WmsReservationItem.class);
        verify(fixture.itemMapper, times(2)).insert(itemCaptor.capture());
        assertThat(itemCaptor.getAllValues()).extracting(WmsReservationItem::getProductCode)
                .containsExactly("MAT-A", "MAT-B");
        assertThat(itemCaptor.getAllValues()).extracting(WmsReservationItem::getStatus)
                .containsOnly("RESERVED");

        ArgumentCaptor<WmsReservationTicket> ticketCaptor = ArgumentCaptor.forClass(WmsReservationTicket.class);
        verify(fixture.ticketMapper, times(3)).insert(ticketCaptor.capture());
        assertThat(ticketCaptor.getAllValues()).extracting(WmsReservationTicket::getTicketId)
                .containsExactly(101, 102, 103);
        assertThat(ticketCaptor.getAllValues()).extracting(WmsReservationTicket::getBatchItemId)
                .containsExactly(701L, 701L, 702L);
        assertThat(ticketCaptor.getAllValues()).extracting(WmsReservationTicket::getStatus)
                .containsOnly("RESERVED");
    }

    @Test
    void reserveBatchRejectsActiveTicketBeforeCreatingAnotherBatch() {
        Fixture fixture = fixture();
        WmsReservationTicket active = new WmsReservationTicket();
        active.setId(900L);
        active.setTicketId(101);
        active.setStatus("RESULT_UNKNOWN");
        when(fixture.ticketMapper.selectActiveByTicketId(101)).thenReturn(active);
        WmsReservationPlanService.ReservationGroup group = group("SO-100", item("MAT-A", "5", "remark"));
        group.getTickets().add(ticketReservation(101, 1001L, 10, "MAT-A", "5"));

        assertThatThrownBy(() -> fixture.service.reserveBatch(9001L, group))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("active WMS reservation");

        verify(fixture.batchMapper, never()).insert(any());
        verify(fixture.itemMapper, never()).insert(any());
        verify(fixture.ticketMapper, never()).insert(any());
    }

    @Test
    void stateTransitionsAreWhitelistedAndSendAttemptsRecordLastErrorType() {
        Fixture fixture = fixture();
        when(fixture.batchMapper.selectById(501L)).thenReturn(batch(501L, "RESULT_UNKNOWN", 1));

        fixture.service.markSubmitting(501L, "TRANSPORT_REPLAY");

        ArgumentCaptor<WmsReservationBatch> updateCaptor = ArgumentCaptor.forClass(WmsReservationBatch.class);
        verify(fixture.batchMapper).updateById(updateCaptor.capture());
        assertThat(updateCaptor.getValue().getStatus()).isEqualTo("SUBMITTING");
        assertThat(updateCaptor.getValue().getAttemptNo()).isEqualTo(2);
        assertThat(updateCaptor.getValue().getLastErrorType()).isEqualTo("TRANSPORT_REPLAY");

        when(fixture.batchMapper.selectById(501L)).thenReturn(batch(501L, "SUCCESS", 2));
        assertThatThrownBy(() -> fixture.service.transitionBatchStatus(501L, "RESULT_UNKNOWN", "illegal"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Illegal WMS reservation status transition");
    }

    @Test
    void loadReplayRequestUsesStoredSnapshotAndRejectsFingerprintMismatch() {
        Fixture fixture = fixture();
        WmsSaleLadingReserveReq original = request("QPO-WMS-1", "SO-100", requestItem("MAT-A", "5", "old remark"));
        WmsReservationBatch batch = batch(501L, "RESULT_UNKNOWN", 1);
        batch.setRequestPayload(original);
        batch.setRequestFingerprint(new WmsReservationFingerprintService().fingerprint(original.getSaleNo(), original.getItems()));
        when(fixture.batchMapper.selectById(501L)).thenReturn(batch);

        WmsSaleLadingReserveReq replay = fixture.service.loadReplayRequest(501L);

        assertThat(replay.getExternalBizId()).isEqualTo("QPO-WMS-1");
        assertThat(replay.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getProductCode()).isEqualTo("MAT-A");
            assertThat(item.getReserveCount()).isEqualByComparingTo("5");
            assertThat(item.getRemark()).isEqualTo("old remark");
        });

        original.getItems().get(0).setRemark("changed after reservation");
        assertThatThrownBy(() -> fixture.service.loadReplayRequest(501L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fingerprint");
    }

    @Test
    void resultUnknownTransportReplaysSameRequestOnceAndDefiniteFailureMarksFailed() {
        Fixture fixture = fixture();
        WmsSaleLadingReserveReq request = request("QPO-WMS-1", "SO-100", requestItem("MAT-A", "5", "remark"));
        WmsReservationBatch first = batch(501L, "RESERVED", 1);
        first.setRequestPayload(request);
        first.setRequestFingerprint(new WmsReservationFingerprintService().fingerprint("SO-100", request.getItems()));
        WmsReservationBatch replay = batch(501L, "RESULT_UNKNOWN", 1);
        replay.setRequestPayload(request);
        replay.setRequestFingerprint(first.getRequestFingerprint());
        when(fixture.batchMapper.selectById(501L)).thenReturn(first, first, replay, replay, replay);
        when(fixture.client.reserve(any()))
                .thenThrow(callException(DataCenterCallException.FailureCertainty.RESULT_UNKNOWN, "read timeout"))
                .thenReturn(success("QPO-WMS-1", "SO-100", responseItem("MAT-A", "5", "3", "1", "4", "1")));

        WmsSaleLadingReserveResp response = fixture.service.sendWithReplay(501L);

        assertThat(response.getCode()).isEqualTo(200);
        ArgumentCaptor<WmsSaleLadingReserveReq> requestCaptor = ArgumentCaptor.forClass(WmsSaleLadingReserveReq.class);
        verify(fixture.client, times(2)).reserve(requestCaptor.capture());
        assertThat(requestCaptor.getAllValues()).extracting(WmsSaleLadingReserveReq::getExternalBizId)
                .containsExactly("QPO-WMS-1", "QPO-WMS-1");

        Fixture failedFixture = fixture();
        WmsReservationBatch failedBatch = batch(502L, "RESERVED", 1);
        failedBatch.setRequestPayload(request);
        failedBatch.setRequestFingerprint(first.getRequestFingerprint());
        when(failedFixture.batchMapper.selectById(502L)).thenReturn(failedBatch, failedBatch);
        when(failedFixture.client.reserve(any()))
                .thenThrow(callException(DataCenterCallException.FailureCertainty.DEFINITE_FAILURE, "business failed"));

        assertThatThrownBy(() -> failedFixture.service.sendWithReplay(502L))
                .isInstanceOf(DataCenterCallException.class)
                .hasMessageContaining("business failed");
        ArgumentCaptor<WmsReservationBatch> failedUpdate = ArgumentCaptor.forClass(WmsReservationBatch.class);
        verify(failedFixture.batchMapper, times(2)).updateById(failedUpdate.capture());
        assertThat(failedUpdate.getAllValues()).extracting(WmsReservationBatch::getStatus)
                .contains("FAILED");
    }

    @Test
    void definiteFailureMarksPersistedRowsFailedSoNextSubmitCanCreateNewBatch() {
        Fixture fixture = fixture();
        WmsSaleLadingReserveReq request = request("QPO-WMS-1", "SO-100", requestItem("MAT-A", "5", "remark"));
        WmsReservationBatch failedBatch = batch(502L, "RESERVED", 1);
        failedBatch.setRequestPayload(request);
        failedBatch.setRequestFingerprint(new WmsReservationFingerprintService().fingerprint("SO-100", request.getItems()));
        WmsReservationItem item = reservationItem(702L, 502L, "MAT-A", "5");
        WmsReservationTicket ticket = reservationTicket(802L, 502L, 702L, 101, 1001L, 10, "MAT-A", "5");
        when(fixture.batchMapper.selectById(502L)).thenReturn(failedBatch, failedBatch);
        when(fixture.itemMapper.selectList(any())).thenReturn(List.of(item));
        when(fixture.ticketMapper.selectByBatchId(502L)).thenReturn(List.of(ticket));
        when(fixture.client.reserve(any()))
                .thenThrow(callException(DataCenterCallException.FailureCertainty.DEFINITE_FAILURE, "business failed"));

        assertThatThrownBy(() -> fixture.service.sendWithReplay(502L))
                .isInstanceOf(DataCenterCallException.class)
                .hasMessageContaining("business failed");

        ArgumentCaptor<WmsReservationItem> itemUpdate = ArgumentCaptor.forClass(WmsReservationItem.class);
        verify(fixture.itemMapper).updateById(itemUpdate.capture());
        assertThat(itemUpdate.getValue().getId()).isEqualTo(702L);
        assertThat(itemUpdate.getValue().getStatus()).isEqualTo("FAILED");

        ArgumentCaptor<WmsReservationTicket> ticketUpdate = ArgumentCaptor.forClass(WmsReservationTicket.class);
        verify(fixture.ticketMapper).updateById(ticketUpdate.capture());
        assertThat(ticketUpdate.getValue().getId()).isEqualTo(802L);
        assertThat(ticketUpdate.getValue().getStatus()).isEqualTo("FAILED");
        assertThat(ticketUpdate.getValue().getMessage()).contains("business failed");

        ArgumentCaptor<OrderDeliveryTicket> deliveryTicketUpdate = ArgumentCaptor.forClass(OrderDeliveryTicket.class);
        verify(fixture.ticketSourceMapper).updateById(deliveryTicketUpdate.capture());
        assertThat(deliveryTicketUpdate.getValue().getId()).isEqualTo(101);
        assertThat(deliveryTicketUpdate.getValue().getWmsAllocateStatus()).isEqualTo("FAILED");
        assertThat(deliveryTicketUpdate.getValue().getWmsAllocateMessage()).contains("business failed");
    }

    private Fixture fixture() {
        return new Fixture();
    }

    private WmsReservationPlanService.ReservationGroup group(String saleNo, WmsReservationPlanService.ReservationItem... items) {
        WmsReservationPlanService.ReservationGroup group = new WmsReservationPlanService.ReservationGroup();
        group.setSaleOrderId(301L);
        group.setSaleNo(saleNo);
        group.getItems().addAll(List.of(items));
        WmsSaleLadingReserveReq request = request(null, saleNo,
                List.of(items).stream().map(item -> requestItem(
                                item.getProductCode(),
                                item.getReserveCount().toPlainString(),
                                item.getRemark(),
                                item.getCustomerProjectNo(),
                                item.getCustomerOrderNo()))
                        .toArray(WmsSaleLadingReserveReq.Item[]::new));
        group.setRequest(request);
        group.setRequestFingerprint(new WmsReservationFingerprintService().fingerprint(saleNo, request.getItems()));
        return group;
    }

    private WmsReservationPlanService.ReservationItem item(String productCode, String reserveCount, String remark) {
        return item(productCode, reserveCount, remark, null, null);
    }

    private WmsReservationPlanService.ReservationItem item(
            String productCode,
            String reserveCount,
            String remark,
            String customerProjectNo,
            String customerOrderNo) {
        WmsReservationPlanService.ReservationItem item = new WmsReservationPlanService.ReservationItem();
        item.setProductCode(productCode);
        item.setReserveCount(new BigDecimal(reserveCount));
        item.setRemark(remark);
        item.setCustomerProjectNo(customerProjectNo);
        item.setCustomerOrderNo(customerOrderNo);
        return item;
    }

    private WmsReservationPlanService.TicketReservation ticketReservation(Integer ticketId,
                                                                          Long salesOrderItemId,
                                                                          Integer lineNo,
                                                                          String productCode,
                                                                          String reserveCount) {
        WmsReservationPlanService.TicketReservation ticket = new WmsReservationPlanService.TicketReservation();
        ticket.setTicketId(ticketId);
        ticket.setSalesOrderItemId(salesOrderItemId);
        ticket.setSalesOrderLineNo(lineNo);
        ticket.setProductCode(productCode);
        ticket.setReserveCount(new BigDecimal(reserveCount));
        return ticket;
    }

    private WmsSaleLadingReserveReq request(String externalBizId, String saleNo, WmsSaleLadingReserveReq.Item... items) {
        WmsSaleLadingReserveReq request = new WmsSaleLadingReserveReq();
        request.setExternalBizId(externalBizId);
        request.setSaleNo(saleNo);
        request.setItems(List.of(items));
        return request;
    }

    private WmsSaleLadingReserveReq.Item requestItem(String productCode, String reserveCount, String remark) {
        return requestItem(productCode, reserveCount, remark, null, null);
    }

    private WmsSaleLadingReserveReq.Item requestItem(
            String productCode,
            String reserveCount,
            String remark,
            String customerProjectNo,
            String customerOrderNo) {
        WmsSaleLadingReserveReq.Item item = new WmsSaleLadingReserveReq.Item();
        item.setProductCode(productCode);
        item.setReserveCount(new BigDecimal(reserveCount));
        item.setRemark(remark);
        item.setCustomerProjectNo(customerProjectNo);
        item.setCustomerOrderNo(customerOrderNo);
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

    private WmsReservationItem reservationItem(Long id, Long batchId, String productCode, String reserveCount) {
        WmsReservationItem item = new WmsReservationItem();
        item.setId(id);
        item.setBatchId(batchId);
        item.setProductCode(productCode);
        item.setReserveCount(new BigDecimal(reserveCount));
        item.setRemark("remark");
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
        ticket.setStatus("RESERVED");
        return ticket;
    }

    private WmsReservationBatch batch(Long id, String status, Integer attemptNo) {
        WmsReservationBatch batch = new WmsReservationBatch();
        batch.setId(id);
        batch.setExternalBizId("QPO-WMS-1");
        batch.setSaleOrderId(301L);
        batch.setSaleOrderNo("SO-100");
        batch.setStatus(status);
        batch.setAttemptNo(attemptNo);
        return batch;
    }

    private DataCenterCallException callException(DataCenterCallException.FailureCertainty certainty, String message) {
        return new DataCenterCallException(DataCenterEndpointRegistry.WMS_INVENTORY_ALLOCATE, certainty, null, message, null);
    }

    private static final class Fixture {
        private final WmsReservationBatchMapper batchMapper = mock(WmsReservationBatchMapper.class);
        private final WmsReservationItemMapper itemMapper = mock(WmsReservationItemMapper.class);
        private final WmsReservationTicketMapper ticketMapper = mock(WmsReservationTicketMapper.class);
        private final OrderDeliveryTicketMapper ticketSourceMapper = mock(OrderDeliveryTicketMapper.class);
        private final WmsReservationClient client = mock(WmsReservationClient.class);
        private final WmsReservationStateService service = new WmsReservationStateService();

        private Fixture() {
            ReflectionTestUtils.setField(service, "batchMapper", batchMapper);
            ReflectionTestUtils.setField(service, "itemMapper", itemMapper);
            ReflectionTestUtils.setField(service, "ticketMapper", ticketMapper);
            ReflectionTestUtils.setField(service, "ticketSourceMapper", ticketSourceMapper);
            ReflectionTestUtils.setField(service, "client", client);
            ReflectionTestUtils.setField(service, "validator", new WmsReservationContractValidator());
            ReflectionTestUtils.setField(service, "fingerprintService", new WmsReservationFingerprintService());
            doAnswer(invocation -> {
                WmsReservationBatch batch = invocation.getArgument(0);
                batch.setId(501L);
                return 1;
            }).when(batchMapper).insert(any(WmsReservationBatch.class));
            long[] itemId = {700L};
            doAnswer(invocation -> {
                WmsReservationItem item = invocation.getArgument(0);
                item.setId(++itemId[0]);
                return 1;
            }).when(itemMapper).insert(any(WmsReservationItem.class));
            long[] ticketId = {800L};
            doAnswer(invocation -> {
                WmsReservationTicket ticket = invocation.getArgument(0);
                ticket.setId(++ticketId[0]);
                return 1;
            }).when(ticketMapper).insert(any(WmsReservationTicket.class));
        }
    }
}
