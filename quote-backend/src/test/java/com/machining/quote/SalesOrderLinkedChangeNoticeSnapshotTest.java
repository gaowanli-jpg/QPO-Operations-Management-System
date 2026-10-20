package com.machining.quote;

import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderDeliveryTicketAllocation;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.entity.WmsReservationBatch;
import com.machining.quote.entity.WmsReservationTicket;
import com.machining.quote.service.SalesOrderLinkedChangeNoticeFactory;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SalesOrderLinkedChangeNoticeSnapshotTest {
    private final SalesOrderLinkedChangeNoticeFactory factory = new SalesOrderLinkedChangeNoticeFactory();

    @Test
    void snapshotsThreeDownstreamActionsAndDoesNotCountMaterialNumberAcquisitionAsProduction() {
        SalesOrderHeader order = order();
        SalesOrderItem item = item(10L);
        OrderDeliveryTicket ticket = ticket(20, "SUCCESS", "WMS-REQ-20");
        ticket.setMesSyncStatus("SUCCESS");
        ticket.setMaterialNo("MES-MATERIAL-20");
        OrderDeliveryTicketAllocation production = allocation(30, "PRODUCTION_REQUIRED", "SUCCESS", null, "MES-PROD-30");
        OrderDeliveryTicketAllocation stockPicking = allocation(31, "IN_STOCK_LOCK", null, "SUCCESS", null);
        stockPicking.setStockPickingBizId("STOCK-BIZ-31");

        var notice = factory.create(order, item, List.of(ticket), List.of(production, stockPicking),
                "ORDER", "event-1", "订单关联废弃", LocalDateTime.parse("2026-09-23T10:00:00"), 7L);

        assertThat(notice.getWmsSubmitted()).isTrue();
        assertThat(notice.getWmsStatus()).isEqualTo("SUCCESS");
        assertThat(notice.getMesProductionSubmitted()).isTrue();
        assertThat(notice.getMesProductionStatus()).isEqualTo("SUCCESS");
        assertThat(notice.getStockPickingSubmitted()).isTrue();
        assertThat(notice.getStockPickingStatus()).isEqualTo("SUCCESS");
        assertThat(notice.getDownstreamImpactCount()).isEqualTo(3);
        assertThat(notice.getTriggerScope()).isEqualTo("ORDER");
        assertThat(notice.getCustomerOrderNo()).isEqualTo("客户订单-001");
        assertThat(notice.getInternalProjectNo()).isEqualTo("内部项目-001");
    }

    @Test
    void materialNumberSuccessWithoutProductionAllocationDoesNotCountAsMesProduction() {
        OrderDeliveryTicket ticket = ticket(21, "PENDING", null);
        ticket.setMesSyncStatus("SUCCESS");
        ticket.setMaterialNo("MES-MATERIAL-21");

        var notice = factory.create(order(), item(11L), List.of(ticket), List.of(),
                "ITEM", "event-2", "单行关联废弃", LocalDateTime.now(), 7L);

        assertThat(notice.getMesProductionSubmitted()).isFalse();
        assertThat(notice.getMesProductionStatus()).isEqualTo("NOT_OCCURRED");
        assertThat(notice.getDownstreamImpactCount()).isZero();
    }

    @Test
    void failedAndProcessingActionsStillCountAsImpacts() {
        OrderDeliveryTicket ticket = ticket(22, "FAILED", "WMS-REQ-22");
        OrderDeliveryTicketAllocation production = allocation(32, "PRODUCTION_REQUIRED", "PROCESSING", null, "MES-PROD-32");
        OrderDeliveryTicketAllocation stockPicking = allocation(33, "IN_PRODUCTION_LOCK", null, "RESULT_UNKNOWN", null);
        stockPicking.setStockPickingBizId("STOCK-BIZ-33");

        var notice = factory.create(order(), item(12L), List.of(ticket), List.of(production, stockPicking),
                "ITEM", "event-3", "部分下发", LocalDateTime.now(), 7L);

        assertThat(notice.getWmsSubmitted()).isTrue();
        assertThat(notice.getWmsStatus()).isEqualTo("FAILED");
        assertThat(notice.getMesProductionSubmitted()).isTrue();
        assertThat(notice.getMesProductionStatus()).isEqualTo("PROCESSING");
        assertThat(notice.getStockPickingSubmitted()).isTrue();
        assertThat(notice.getStockPickingStatus()).isEqualTo("RESULT_UNKNOWN");
        assertThat(notice.getDownstreamImpactCount()).isEqualTo(3);
    }

    @Test
    void nonEmptyActionMessageIsPreservedAsOccurredEvenWhenStatusWasNotWritten() {
        OrderDeliveryTicket ticket = ticket(23, "PENDING", null);
        ticket.setWmsAllocateMessage("WMS request accepted");
        OrderDeliveryTicketAllocation production = allocation(34, "PRODUCTION_REQUIRED", "PENDING", null, null);
        production.setMesProductionSyncedAt(null);
        production.setMesProductionMessage("MES request accepted");

        var notice = factory.create(order(), item(13L), List.of(ticket), List.of(production),
                "ITEM", "event-4", "状态待确认", LocalDateTime.now(), 7L);

        assertThat(notice.getWmsSubmitted()).isTrue();
        assertThat(notice.getWmsStatus()).isEqualTo("PENDING");
        assertThat(notice.getMesProductionSubmitted()).isTrue();
        assertThat(notice.getMesProductionStatus()).isEqualTo("PENDING");
        assertThat(notice.getDownstreamImpactCount()).isEqualTo(2);
    }

    @Test
    void snapshotsAllocationBasedWmsResultAndExternalBusinessId() {
        OrderDeliveryTicket ticket = ticket(24, "PENDING", null);
        OrderDeliveryTicketAllocation locked = allocation(35, "IN_STOCK_LOCK", null, null, null);
        locked.setStockPickingSyncedAt(null);
        WmsReservationTicket reservation = reservation(88L, 35, 701L, "SUCCESS");
        WmsReservationBatch batch = batch(701L, "QPO-WMS-701");

        var notice = factory.create(order(), item(14L), List.of(ticket), List.of(locked),
                List.of(reservation), java.util.Map.of(701L, batch),
                "ITEM", "event-5", "已有锁库影响", LocalDateTime.now(), 7L);

        assertThat(notice.getWmsSubmitted()).isTrue();
        assertThat(notice.getWmsStatus()).isEqualTo("SUCCESS");
        assertThat(notice.getWmsRequestId()).isEqualTo("QPO-WMS-701");
        assertThat(notice.getDownstreamImpactCount()).isEqualTo(1);
    }

    @Test
    void allocationLinkedWmsReservationTakesPrecedenceOverLegacyTicketStatus() {
        OrderDeliveryTicket ticket = ticket(25, "SUCCESS", "legacy request");
        OrderDeliveryTicketAllocation production = allocation(36, "PRODUCTION_REQUIRED", null, null, null);
        SalesOrderItem item = item(15L);
        item.setAllocationId(36L);
        WmsReservationTicket reservation = reservation(89L, 36, 702L, "FAILED");
        WmsReservationBatch batch = batch(702L, "QPO-WMS-702");

        var notice = factory.create(order(), item, List.of(ticket), List.of(production),
                List.of(reservation), java.util.Map.of(702L, batch),
                "ITEM", "event-6", "WMS明确失败", LocalDateTime.now(), 7L);

        assertThat(notice.getWmsSubmitted()).isTrue();
        assertThat(notice.getWmsStatus()).isEqualTo("FAILED");
        assertThat(notice.getWmsRequestId()).isEqualTo("QPO-WMS-702");
    }

    @Test
    void allocationLinkedRowDoesNotFallBackToLegacyTicketReservation() {
        OrderDeliveryTicket ticket = ticket(27, "SUCCESS", "legacy request");
        OrderDeliveryTicketAllocation production = allocation(37, "PRODUCTION_REQUIRED", null, null, null);
        SalesOrderItem item = item(17L);
        item.setAllocationId(37L);
        WmsReservationTicket legacyReservation = reservation(91L, null, 704L, "RESULT_UNKNOWN");
        legacyReservation.setTicketId(27);
        WmsReservationBatch batch = batch(704L, "QPO-WMS-704");

        var notice = factory.create(order(), item, List.of(ticket), List.of(production),
                List.of(legacyReservation), java.util.Map.of(704L, batch),
                "ITEM", "event-8", "仅保留当前 allocation 影响", LocalDateTime.now(), 7L);

        assertThat(notice.getWmsSubmitted()).isFalse();
        assertThat(notice.getWmsStatus()).isEqualTo("NOT_OCCURRED");
        assertThat(notice.getWmsRequestId()).isNull();
    }

    @Test
    void legacyTicketReservationRemainsAvailableAsSnapshotFallback() {
        OrderDeliveryTicket ticket = ticket(26, "SUCCESS", null);
        WmsReservationTicket reservation = reservation(90L, null, 703L, "FAILED");
        reservation.setTicketId(26);
        WmsReservationBatch batch = batch(703L, "QPO-WMS-703");

        var notice = factory.create(order(), item(16L), List.of(ticket), List.of(),
                List.of(reservation), java.util.Map.of(703L, batch),
                "ITEM", "event-7", "兼容历史 WMS 记录", LocalDateTime.now(), 7L);

        assertThat(notice.getWmsSubmitted()).isTrue();
        assertThat(notice.getWmsStatus()).isEqualTo("FAILED");
        assertThat(notice.getWmsRequestId()).isEqualTo("QPO-WMS-703");
    }

    private SalesOrderHeader order() {
        SalesOrderHeader order = new SalesOrderHeader();
        order.setId(1L);
        order.setSalesOrderNo("SO-1");
        order.setCustomerId(2L);
        order.setCustomerCode("C-2");
        order.setCustomerName("客户二");
        return order;
    }

    private SalesOrderItem item(Long id) {
        SalesOrderItem item = new SalesOrderItem();
        item.setId(id);
        item.setSalesOrderId(1L);
        item.setLineNo(id.intValue());
        item.setPartNo("PART-" + id);
        item.setVersion("A");
        item.setDescription("零件描述");
        item.setMaterialName("材料备注");
        item.setCustomerOrderNo("客户订单-001");
        item.setInternalProjectNo("内部项目-001");
        return item;
    }

    private OrderDeliveryTicket ticket(int id, String wmsStatus, String wmsRequestId) {
        OrderDeliveryTicket ticket = new OrderDeliveryTicket();
        ticket.setId(id);
        ticket.setWmsAllocateStatus(wmsStatus);
        ticket.setWmsAllocateMessage(wmsRequestId);
        if (!"PENDING".equalsIgnoreCase(wmsStatus)) {
            ticket.setWmsAllocatedAt(LocalDateTime.parse("2026-09-22T09:00:00"));
        }
        return ticket;
    }

    private OrderDeliveryTicketAllocation allocation(
            int id, String type, String productionStatus, String stockStatus, String productionRequestId) {
        OrderDeliveryTicketAllocation allocation = new OrderDeliveryTicketAllocation();
        allocation.setId(id);
        allocation.setAllocationType(type);
        allocation.setMesProductionStatus(productionStatus);
        allocation.setStockPickingStatus(stockStatus);
        allocation.setMesProductionRequestId(productionRequestId);
        allocation.setMesProductionSyncedAt(LocalDateTime.parse("2026-09-22T09:30:00"));
        allocation.setStockPickingSyncedAt(LocalDateTime.parse("2026-09-22T09:40:00"));
        return allocation;
    }

    private WmsReservationTicket reservation(Long id, Integer allocationId, Long batchId, String status) {
        WmsReservationTicket reservation = new WmsReservationTicket();
        reservation.setId(id);
        reservation.setAllocationId(allocationId);
        reservation.setBatchId(batchId);
        reservation.setStatus(status);
        reservation.setCreatedAt(LocalDateTime.parse("2026-09-22T09:30:00"));
        return reservation;
    }

    private WmsReservationBatch batch(Long id, String externalBizId) {
        WmsReservationBatch batch = new WmsReservationBatch();
        batch.setId(id);
        batch.setExternalBizId(externalBizId);
        batch.setSubmittedAt(LocalDateTime.parse("2026-09-22T09:20:00"));
        return batch;
    }
}
