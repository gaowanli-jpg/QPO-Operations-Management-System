package com.machining.quote;

import com.machining.quote.dto.wms.WmsSaleLadingReserveResp;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.WmsReservationBatch;
import com.machining.quote.entity.WmsReservationTicket;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.WmsReservationBatchMapper;
import com.machining.quote.mapper.WmsReservationTicketMapper;
import com.machining.quote.service.DataCenterCallException;
import com.machining.quote.service.DataCenterEndpointRegistry;
import com.machining.quote.service.DataScopeService;
import com.machining.quote.service.wms.WmsReservationBatchService;
import com.machining.quote.service.wms.WmsReservationPlanService;
import com.machining.quote.service.wms.WmsReservationStateService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WmsReservationBatchServiceTest {
    @Test
    @SuppressWarnings("unchecked")
    void allocationProcessesDifferentProjectGroupsIndependentlyAndContinuesAfterGroupFailure() {
        Fixture fixture = fixture();
        WmsReservationPlanService.ReservationItem firstItem = item("MAT-A", "5");
        firstItem.setCustomerProjectNo("CP-1");
        WmsReservationPlanService.ReservationGroup first = group("CO-1",
                firstItem, ticketReservation(101, "MAT-A", "2"), ticketReservation(102, "MAT-A", "3"));
        WmsReservationPlanService.ReservationItem secondItem = item("MAT-A", "4");
        secondItem.setCustomerProjectNo("CP-2");
        WmsReservationPlanService.ReservationGroup second = group("CO-1",
                secondItem, ticketReservation(201, "MAT-A", "4"));
        WmsReservationPlanService.ReservationPlan plan = plan(first, second);
        when(fixture.planService.prepare(List.of(101, 102, 201))).thenReturn(plan);
        when(fixture.stateService.reserveBatch(9001L, first)).thenReturn(context(501L, "QPO-WMS-1", first));
        when(fixture.stateService.reserveBatch(9001L, second)).thenReturn(context(502L, "QPO-WMS-2", second));
        when(fixture.stateService.sendWithReplay(501L)).thenReturn(success("CO-1", "QPO-WMS-1"));
        when(fixture.stateService.sendWithReplay(502L))
                .thenThrow(callException(DataCenterCallException.FailureCertainty.DEFINITE_FAILURE, "WMS rejected CO-1"));

        Map<String, Object> result = fixture.service.allocateBatch(List.of(101, 102, 201), 9001L);

        assertThat(result)
                .containsEntry("taskType", "WMS_ALLOCATION_BATCH")
                .containsEntry("taskId", 9001L)
                .containsEntry("totalCount", 3)
                .containsEntry("batchCount", 2)
                .containsEntry("successCount", 2)
                .containsEntry("failedCount", 1);
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("results");
        assertThat(rows).extracting(row -> row.get("ticketId"))
                .containsExactly(101, 102, 201);
        assertThat(rows).extracting(row -> row.get("status"))
                .containsExactly("SUCCESS", "SUCCESS", "FAILED");
        assertThat(rows).extracting(row -> row.get("batchId"))
                .containsExactly(501L, 501L, 502L);
        assertThat(rows).extracting(row -> row.get("externalBizId"))
                .containsExactly("QPO-WMS-1", "QPO-WMS-1", "QPO-WMS-2");
        verify(fixture.stateService).reserveBatch(9001L, first);
        verify(fixture.stateService).reserveBatch(9001L, second);
        verify(fixture.stateService, times(2)).sendWithReplay(any());
        verify(fixture.stateService).applyResponse(501L);
        verify(fixture.stateService, never()).applyResponse(502L);
    }

    @Test
    void singleTicketStillUsesBatchReservationPath() {
        Fixture fixture = fixture();
        WmsReservationPlanService.ReservationGroup group = group("SO-1",
                item("MAT-A", "1"), ticketReservation(101, "MAT-A", "1"));
        when(fixture.planService.prepare(List.of(101))).thenReturn(plan(group));
        when(fixture.stateService.reserveBatch(9002L, group)).thenReturn(context(501L, "QPO-WMS-1", group));
        when(fixture.stateService.sendWithReplay(501L)).thenReturn(success("SO-1", "QPO-WMS-1"));

        fixture.service.allocateBatch(List.of(101), 9002L);

        verify(fixture.stateService).reserveBatch(9002L, group);
        verify(fixture.stateService).sendWithReplay(501L);
        verify(fixture.stateService).applyResponse(501L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void planFailuresRemainRowLevelAndDoNotBlockValidGroups() {
        Fixture fixture = fixture();
        WmsReservationPlanService.ReservationGroup group = group("SO-1",
                item("MAT-A", "1"), ticketReservation(101, "MAT-A", "1"));
        WmsReservationPlanService.ReservationPlan plan = plan(group);
        plan.getFailures().add(new WmsReservationPlanService.RowFailure(301, "materialNo is required"));
        when(fixture.planService.prepare(List.of(101, 301))).thenReturn(plan);
        when(fixture.stateService.reserveBatch(9003L, group)).thenReturn(context(501L, "QPO-WMS-1", group));
        when(fixture.stateService.sendWithReplay(501L)).thenReturn(success("SO-1", "QPO-WMS-1"));

        Map<String, Object> result = fixture.service.allocateBatch(List.of(101, 301), 9003L);

        assertThat(result).containsEntry("successCount", 1).containsEntry("failedCount", 1);
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("results");
        assertThat(rows).extracting(row -> row.get("ticketId"))
                .containsExactly(101, 301);
        assertThat(rows).extracting(row -> row.get("status"))
                .containsExactly("SUCCESS", "FAILED");
    }

    @Test
    @SuppressWarnings("unchecked")
    void resultUnknownSelectionRestoresWholeOriginalBatchAndChecksAllTicketScopes() {
        Fixture fixture = fixture();
        WmsReservationTicket selectedActive = activeTicket(801L, 501L, 101, "RESULT_UNKNOWN");
        WmsReservationTicket sibling = activeTicket(802L, 501L, 102, "RESULT_UNKNOWN");
        when(fixture.ticketMapper.selectActiveByTicketId(101)).thenReturn(selectedActive);
        when(fixture.ticketMapper.selectByBatchId(501L)).thenReturn(List.of(selectedActive, sibling));
        when(fixture.batchMapper.selectById(501L)).thenReturn(batch(501L, "RESULT_UNKNOWN", "SO-1", "QPO-WMS-1"));
        when(fixture.ticketSourceMapper.selectById(101)).thenReturn(ticket(101));
        when(fixture.ticketSourceMapper.selectById(102)).thenReturn(ticket(102));
        when(fixture.stateService.sendWithReplay(501L)).thenReturn(success("SO-1", "QPO-WMS-1"));

        Map<String, Object> result = fixture.service.allocateBatch(List.of(101), 9004L);

        assertThat(result).containsEntry("totalCount", 2).containsEntry("successCount", 2);
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("results");
        assertThat(rows).extracting(row -> row.get("ticketId"))
                .containsExactly(101, 102);
        verify(fixture.dataScopeService).assertCanAccessDeliveryTicket(ticket(101));
        verify(fixture.dataScopeService).assertCanAccessDeliveryTicket(ticket(102));
        verify(fixture.planService, never()).prepare(any());
        verify(fixture.stateService, never()).reserveBatch(any(), any());
        verify(fixture.stateService).sendWithReplay(501L);
        verify(fixture.stateService).applyResponse(501L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void applyFailedOnlyReplaysLocalApplyAndSuccessSkipsWithoutCallingWms() {
        Fixture fixture = fixture();
        WmsReservationTicket applyFailed = activeTicket(801L, 501L, 101, "APPLY_FAILED");
        WmsReservationTicket success = activeTicket(901L, 601L, 201, "SUCCESS");
        when(fixture.ticketMapper.selectActiveByTicketId(101)).thenReturn(applyFailed);
        when(fixture.ticketMapper.selectByBatchId(501L)).thenReturn(List.of(applyFailed));
        when(fixture.batchMapper.selectById(501L)).thenReturn(batch(501L, "APPLY_FAILED", "SO-1", "QPO-WMS-1"));
        when(fixture.ticketMapper.selectActiveByTicketId(201)).thenReturn(success);
        when(fixture.ticketMapper.selectByBatchId(601L)).thenReturn(List.of(success));
        when(fixture.batchMapper.selectById(601L)).thenReturn(batch(601L, "SUCCESS", "SO-2", "QPO-WMS-2"));
        when(fixture.ticketSourceMapper.selectById(101)).thenReturn(ticket(101));
        when(fixture.ticketSourceMapper.selectById(201)).thenReturn(ticket(201));

        Map<String, Object> result = fixture.service.allocateBatch(List.of(101, 201), 9005L);

        assertThat(result).containsEntry("successCount", 1).containsEntry("skippedCount", 1);
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("results");
        assertThat(rows).extracting(row -> row.get("status"))
                .containsExactly("SUCCESS", "SKIPPED");
        verify(fixture.stateService).applyResponse(501L);
        verify(fixture.stateService, never()).sendWithReplay(any());
        verify(fixture.stateService, never()).reserveBatch(any(), any());
    }

    private Fixture fixture() {
        return new Fixture();
    }

    private WmsReservationPlanService.ReservationPlan plan(WmsReservationPlanService.ReservationGroup... groups) {
        WmsReservationPlanService.ReservationPlan plan = new WmsReservationPlanService.ReservationPlan();
        plan.getGroups().addAll(List.of(groups));
        return plan;
    }

    private WmsReservationPlanService.ReservationGroup group(
            String saleNo,
            WmsReservationPlanService.ReservationItem item,
            WmsReservationPlanService.TicketReservation... tickets) {
        WmsReservationPlanService.ReservationGroup group = new WmsReservationPlanService.ReservationGroup();
        group.setSaleOrderId(Long.parseLong(saleNo.substring(saleNo.length() - 1)));
        group.setSaleNo(saleNo);
        group.getItems().add(item);
        group.getTickets().addAll(List.of(tickets));
        group.setRequestFingerprint("0".repeat(64));
        return group;
    }

    private WmsReservationPlanService.ReservationItem item(String productCode, String qty) {
        WmsReservationPlanService.ReservationItem item = new WmsReservationPlanService.ReservationItem();
        item.setProductCode(productCode);
        item.setReserveCount(new BigDecimal(qty));
        item.setRemark("remark-" + productCode);
        return item;
    }

    private WmsReservationPlanService.TicketReservation ticketReservation(Integer ticketId, String productCode, String qty) {
        WmsReservationPlanService.TicketReservation ticket = new WmsReservationPlanService.TicketReservation();
        ticket.setTicketId(ticketId);
        ticket.setSalesOrderItemId(ticketId.longValue());
        ticket.setSalesOrderLineNo(ticketId);
        ticket.setProductCode(productCode);
        ticket.setReserveCount(new BigDecimal(qty));
        return ticket;
    }

    private WmsReservationStateService.ReservationBatchContext context(
            Long batchId,
            String externalBizId,
            WmsReservationPlanService.ReservationGroup group) {
        return new WmsReservationStateService.ReservationBatchContext(batchId, externalBizId, null,
                group.getTickets().stream()
                        .map(ticket -> activeTicket(ticket.getTicketId().longValue(), batchId, ticket.getTicketId(), "RESERVED"))
                        .toList());
    }

    private WmsReservationTicket activeTicket(Long id, Long batchId, Integer ticketId, String status) {
        WmsReservationTicket ticket = new WmsReservationTicket();
        ticket.setId(id);
        ticket.setBatchId(batchId);
        ticket.setTicketId(ticketId);
        ticket.setStatus(status);
        ticket.setProductCode("MAT-" + ticketId);
        ticket.setReserveCount(BigDecimal.ONE);
        return ticket;
    }

    private WmsReservationBatch batch(Long id, String status, String saleNo, String externalBizId) {
        WmsReservationBatch batch = new WmsReservationBatch();
        batch.setId(id);
        batch.setStatus(status);
        batch.setSaleOrderNo(saleNo);
        batch.setExternalBizId(externalBizId);
        return batch;
    }

    private OrderDeliveryTicket ticket(Integer id) {
        OrderDeliveryTicket ticket = new OrderDeliveryTicket();
        ticket.setId(id);
        ticket.setMaterialNo("MAT-" + id);
        ticket.setQty(1);
        return ticket;
    }

    private WmsSaleLadingReserveResp success(String saleNo, String externalBizId) {
        WmsSaleLadingReserveResp response = new WmsSaleLadingReserveResp();
        response.setCode(200);
        WmsSaleLadingReserveResp.Data data = new WmsSaleLadingReserveResp.Data();
        data.setSaleOrderNo(saleNo);
        data.setExternalBizId(externalBizId);
        response.setData(data);
        return response;
    }

    private DataCenterCallException callException(DataCenterCallException.FailureCertainty certainty, String message) {
        return new DataCenterCallException(DataCenterEndpointRegistry.WMS_INVENTORY_ALLOCATE, certainty, null, message, null);
    }

    private static final class Fixture {
        private final WmsReservationPlanService planService = mock(WmsReservationPlanService.class);
        private final WmsReservationStateService stateService = mock(WmsReservationStateService.class);
        private final WmsReservationTicketMapper ticketMapper = mock(WmsReservationTicketMapper.class);
        private final WmsReservationBatchMapper batchMapper = mock(WmsReservationBatchMapper.class);
        private final OrderDeliveryTicketMapper ticketSourceMapper = mock(OrderDeliveryTicketMapper.class);
        private final DataScopeService dataScopeService = mock(DataScopeService.class);
        private final WmsReservationBatchService service = new WmsReservationBatchService();

        private Fixture() {
            ReflectionTestUtils.setField(service, "planService", planService);
            ReflectionTestUtils.setField(service, "stateService", stateService);
            ReflectionTestUtils.setField(service, "ticketMapper", ticketMapper);
            ReflectionTestUtils.setField(service, "batchMapper", batchMapper);
            ReflectionTestUtils.setField(service, "ticketSourceMapper", ticketSourceMapper);
            ReflectionTestUtils.setField(service, "dataScopeService", dataScopeService);
        }
    }
}
