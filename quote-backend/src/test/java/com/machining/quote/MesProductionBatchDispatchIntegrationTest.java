package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.machining.quote.dto.mes.MesProductionDispatchReq;
import com.machining.quote.dto.mes.MesProductionDispatchResp;
import com.machining.quote.dto.mes.MesProductionProcessRouteDTO;
import com.machining.quote.entity.MesIntegrationBatch;
import com.machining.quote.entity.MesProductionBatchItem;
import com.machining.quote.entity.OrderDeliveryTicketAllocation;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.MesIntegrationBatchMapper;
import com.machining.quote.mapper.MesProductionBatchItemMapper;
import com.machining.quote.mapper.OrderDeliveryTicketAllocationMapper;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.service.mes.MesBatchStateService;
import com.machining.quote.service.mes.MesProductionDispatchClient;
import com.machining.quote.service.mes.MesProductionDispatchService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MesProductionBatchDispatchIntegrationTest {
    @Test
    @SuppressWarnings("unchecked")
    void activeWmsTicketIsSkippedWithoutBlockingOtherLinesInTheSameMesGroup() {
        Fixture fixture = fixture();
        OrderDeliveryTicketAllocation wmsBusy = allocation(20, 120, "MAT-WMS-BUSY", 4, 9820L);
        OrderDeliveryTicketAllocation available = allocation(21, 121, "MAT-AVAILABLE", 6, 9821L);
        when(fixture.allocationMapper.selectById(20)).thenReturn(wmsBusy);
        when(fixture.allocationMapper.selectById(21)).thenReturn(available);
        when(fixture.salesItemMapper.selectByIdForUpdate(9820L)).thenReturn(salesItem(9820L, 10, 3001L, null));
        when(fixture.salesItemMapper.selectByIdForUpdate(9821L)).thenReturn(salesItem(9821L, 20, 3001L, null));
        when(fixture.headerMapper.selectById(3001L)).thenReturn(header(3001L, "SO-DOC-1"));
        when(fixture.batchStateService.reserveProductionBatch(any(String.class), any(MesBatchStateService.ProductionBatchReservation.class)))
                .thenThrow(new IllegalStateException("WMS inventory operation is still active for ticketId=120"))
                .thenReturn(reservation("QPO-DEM-AVAILABLE", line(21, "QPO-DEML-AVAILABLE", "MAT-AVAILABLE")));
        when(fixture.client.dispatch(any())).thenReturn(success("QPO-DEM-AVAILABLE",
                item("SO-DOC-1", "20", "MAT-AVAILABLE", "PLAN-AVAILABLE")));

        Map<String, Object> result = fixture.service.dispatchBatch(List.of(20, 21), 9920L);

        assertThat(result)
                .containsEntry("successCount", 1)
                .containsEntry("skippedCount", 1)
                .containsEntry("failedCount", 0);
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("rows");
        assertThat(rows).anySatisfy(row -> assertThat(row)
                .containsEntry("allocationId", 20)
                .containsEntry("status", "SKIPPED"));
        ArgumentCaptor<MesProductionDispatchReq> requestCaptor = ArgumentCaptor.forClass(MesProductionDispatchReq.class);
        verify(fixture.client).dispatch(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getItems()).extracting(MesProductionDispatchReq.Item::getProductCode)
                .containsExactly("MAT-AVAILABLE");
        verify(fixture.batchStateService, times(2))
                .reserveProductionBatch(any(String.class), any(MesBatchStateService.ProductionBatchReservation.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void dispatchesSameSalesOrderGroupInOneRealDemandBatchAndWritesPlanNumbers() {
        Fixture fixture = fixture();
        OrderDeliveryTicketAllocation first = allocation(1, 101, "MAT-001", 4, 9001L);
        OrderDeliveryTicketAllocation second = allocation(2, 102, "MAT-002", 6, 9002L);
        when(fixture.allocationMapper.selectById(1)).thenReturn(first);
        when(fixture.allocationMapper.selectById(2)).thenReturn(second);
        when(fixture.salesItemMapper.selectByIdForUpdate(9001L)).thenReturn(salesItem(9001L, 10, 3001L, null));
        when(fixture.salesItemMapper.selectByIdForUpdate(9002L)).thenReturn(salesItem(9002L, 20, 3001L, null));
        when(fixture.headerMapper.selectById(3001L)).thenReturn(header(3001L, "SO-DOC-1"));
        mockReserve(fixture, "QPO-DEM-BATCH-1", List.of(line(1, "QPO-DEML-1", "MAT-001"), line(2, "QPO-DEML-2", "MAT-002")));
        when(fixture.client.dispatch(any())).thenReturn(success("QPO-DEM-BATCH-1",
                item("SO-DOC-1", "10", "MAT-001", "PLAN-1"),
                item("SO-DOC-1", "20", "MAT-002", "PLAN-2")));

        Map<String, Object> result = fixture.service.dispatchBatch(List.of(1, 2), 9901L);

        assertThat(result)
                .containsEntry("successCount", 2)
                .containsEntry("failedCount", 0)
                .containsEntry("unknownCount", 0)
                .containsEntry("batchCount", 1);
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("rows");
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).get("result")).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("bizId", "QPO-DEM-BATCH-1")
                .containsEntry("externalId", "PLAN-1");
        ArgumentCaptor<MesProductionDispatchReq> requestCaptor = ArgumentCaptor.forClass(MesProductionDispatchReq.class);
        verify(fixture.client).dispatch(requestCaptor.capture());
        MesProductionDispatchReq request = requestCaptor.getValue();
        assertThat(request.getBizId()).isEqualTo("QPO-DEM-BATCH-1");
        assertThat(request.getCustomerCode()).isEqualTo("C001");
        assertThat(request.getCustomerName()).isEqualTo("ACME");
        assertThat(request.getInternalProjectNo()).isEqualTo("IP-9");
        assertThat(request.getItems()).hasSize(2);
        assertThat(request.getItems()).extracting(MesProductionDispatchReq.Item::getSaleOrderNo)
                .containsExactly("SO-DOC-1", "SO-DOC-1");
        assertThat(request.getItems()).extracting(MesProductionDispatchReq.Item::getSaleOrderItemNo)
                .containsExactly("10", "20");
        assertThat(request.getItems()).extracting(MesProductionDispatchReq.Item::getProductCode)
                .containsExactly("MAT-001", "MAT-002");
        assertThat(request.getItems()).extracting(MesProductionDispatchReq.Item::getCustomerRequirements)
                .containsExactly(
                        "材料名称：Material note P-1\n表面处理：Anodized",
                        "材料名称：Material note P-2\n表面处理：Anodized");
        assertThat(request.getItems()).allSatisfy(row -> {
            assertThat(row.getDemandQty()).isEqualTo(row.getMakeQty());
            assertThat(row.getBuyQty()).isZero();
            assertThat(row.getStockQty()).isZero();
            assertThat(row.getDemandQty()).isPositive();
            assertThat(row.getRequiredDate().toLocalTime()).isEqualTo(LocalDateTime.of(2026, 8, 20, 23, 59, 59).toLocalTime());
        });

        ArgumentCaptor<MesBatchStateService.ProductionBatchReservation> reservationCaptor = ArgumentCaptor.forClass(MesBatchStateService.ProductionBatchReservation.class);
        verify(fixture.batchStateService).reserveProductionBatch(any(String.class), reservationCaptor.capture());
        assertThat(reservationCaptor.getValue().items()).hasSize(2);
        assertThat(reservationCaptor.getValue().items()).extracting(MesBatchStateService.ProductionItemReservation::saleOrderNo)
                .containsExactly("SO-DOC-1", "SO-DOC-1");

        ArgumentCaptor<OrderDeliveryTicketAllocation> allocationCaptor = ArgumentCaptor.forClass(OrderDeliveryTicketAllocation.class);
        verify(fixture.allocationMapper, times(2)).updateById(allocationCaptor.capture());
        assertThat(allocationCaptor.getAllValues())
                .anySatisfy(update -> {
                    assertThat(update.getId()).isEqualTo(1);
                    assertThat(update.getMesProductionStatus()).isEqualTo("SUCCESS");
                    assertThat(update.getMesProductionRequestId()).isEqualTo("QPO-DEML-1");
                    assertThat(update.getMesProductionExternalId()).isEqualTo("PLAN-1");
                })
                .anySatisfy(update -> {
                    assertThat(update.getId()).isEqualTo(2);
                    assertThat(update.getMesProductionStatus()).isEqualTo("SUCCESS");
                    assertThat(update.getMesProductionRequestId()).isEqualTo("QPO-DEML-2");
                    assertThat(update.getMesProductionExternalId()).isEqualTo("PLAN-2");
                });
    }

    @Test
    void dispatchesSalesOrderItemWithMaterialNoWhenBomSnapshotIsMissing() {
        Fixture fixture = fixture();
        OrderDeliveryTicketAllocation allocation = allocation(16, 116, "MAT-NO-BOM", 3, 9816L);
        allocation.setEngineeringMaterialCode(null);
        allocation.setMaterialGrossWeight(null);
        allocation.setProcessRoute(null);
        allocation.setSourceOrderId(3816L);
        when(fixture.allocationMapper.selectById(16)).thenReturn(allocation);
        when(fixture.salesItemMapper.selectByIdForUpdate(9816L)).thenReturn(salesItem(9816L, 160, 3816L, null));
        when(fixture.headerMapper.selectById(3816L)).thenReturn(header(3816L, "SO-DOC-NO-BOM"));
        mockReserve(fixture, "QPO-DEM-NO-BOM", List.of(line(16, "QPO-DEML-NO-BOM", "MAT-NO-BOM")));
        when(fixture.client.dispatch(any())).thenReturn(success("QPO-DEM-NO-BOM",
                item("SO-DOC-NO-BOM", "160", "MAT-NO-BOM", "PLAN-NO-BOM")));

        Map<String, Object> result = fixture.service.dispatchBatch(List.of(16), 9916L);

        assertThat(result).containsEntry("successCount", 1).containsEntry("failedCount", 0);
        ArgumentCaptor<MesProductionDispatchReq> requestCaptor = ArgumentCaptor.forClass(MesProductionDispatchReq.class);
        verify(fixture.client).dispatch(requestCaptor.capture());
        MesProductionDispatchReq.Item row = requestCaptor.getValue().getItems().get(0);
        assertThat(row.getProductCode()).isEqualTo("MAT-NO-BOM");
        assertThat(row.getDemandQty()).isEqualTo(3);
        assertThat(row.getBom()).isEmpty();
        assertThat(row.getProcessRoute()).isEmpty();
    }

    @Test
    void dispatchesExtraFeeProcessRowsAsMesProcessRoutesFromLockedSnapshot() {
        Fixture fixture = fixture();
        OrderDeliveryTicketAllocation allocation = allocation(17, 117, "MAT-WITH-ROUTE", 2, 9817L);
        allocation.setSourceOrderId(3817L);
        allocation.setProcessRoute(List.of(
                Map.of(
                        "routeType", "PROCESS",
                        "sourceType", "process",
                        "sortNo", 10,
                        "processCode", "CNC_FINISH",
                        "detailName", "CNC finish",
                        "unitWorkHours", 1.5),
                Map.of(
                        "routeType", "FEE",
                        "sourceType", "extra",
                        "sortNo", 20,
                        "feeCode", "SURFACE_TREATMENT",
                        "feeName", "Surface treatment",
                        "processName", "Surface treatment",
                        "inputVal", 18.0,
                        "lineCost", 18.0)));
        when(fixture.allocationMapper.selectById(17)).thenReturn(allocation);
        when(fixture.salesItemMapper.selectByIdForUpdate(9817L)).thenReturn(salesItem(9817L, 170, 3817L, null));
        when(fixture.headerMapper.selectById(3817L)).thenReturn(header(3817L, "SO-DOC-ROUTE"));
        mockReserve(fixture, "QPO-DEM-ROUTE", List.of(line(17, "QPO-DEML-ROUTE", "MAT-WITH-ROUTE")));
        when(fixture.client.dispatch(any())).thenReturn(success("QPO-DEM-ROUTE",
                item("SO-DOC-ROUTE", "170", "MAT-WITH-ROUTE", "PLAN-ROUTE")));

        fixture.service.dispatchBatch(List.of(17), 9917L);

        ArgumentCaptor<MesProductionDispatchReq> requestCaptor = ArgumentCaptor.forClass(MesProductionDispatchReq.class);
        verify(fixture.client).dispatch(requestCaptor.capture());
        List<MesProductionProcessRouteDTO> routes = requestCaptor.getValue().getItems().get(0).getProcessRoute();
        assertThat(routes).hasSize(2);
        assertThat(routes.get(0)).satisfies(route -> {
            assertThat(route.getProgramNum()).isEqualTo("10");
            assertThat(route.getProcessTypeCode()).isEqualTo("CNC_FINISH");
            assertThat(route.getProcessName()).isEqualTo("CNC finish");
            assertThat(route.getWorkHour()).isEqualByComparingTo("1.5");
        });
        assertThat(routes.get(1)).satisfies(route -> {
            assertThat(route.getProgramNum()).isEqualTo("20");
            assertThat(route.getProcessTypeCode()).isEqualTo("SURFACE_TREATMENT");
            assertThat(route.getProcessName()).isEqualTo("Surface treatment");
            assertThat(route.getWorkHour()).isNull();
        });
    }

    @Test
    void rfqDerivedAllocationResolvesOfficialSalesOrderBySourceTicketId() {
        Fixture fixture = fixture();
        OrderDeliveryTicketAllocation allocation = allocation(3, 203, "MAT-RFQ", 3, 501L);
        allocation.setSourceType("RFQ");
        allocation.setRfqNo("RFQ-SHOULD-NOT-BE-SENT");
        when(fixture.allocationMapper.selectById(3)).thenReturn(allocation);
        when(fixture.salesItemMapper.selectBySourceTicketIdForUpdate(203)).thenReturn(salesItem(9303L, 30, 3303L, 203));
        when(fixture.headerMapper.selectById(3303L)).thenReturn(header(3303L, "SO-FROM-TICKET"));
        mockReserve(fixture, "QPO-DEM-RFQ", List.of(line(3, "QPO-DEML-RFQ", "MAT-RFQ")));
        when(fixture.client.dispatch(any())).thenReturn(success("QPO-DEM-RFQ", item("SO-FROM-TICKET", "30", "MAT-RFQ", "PLAN-RFQ")));

        fixture.service.dispatchBatch(List.of(3), 9903L);

        ArgumentCaptor<MesProductionDispatchReq> requestCaptor = ArgumentCaptor.forClass(MesProductionDispatchReq.class);
        verify(fixture.client).dispatch(requestCaptor.capture());
        MesProductionDispatchReq.Item row = requestCaptor.getValue().getItems().get(0);
        assertThat(row.getSaleOrderNo()).isEqualTo("SO-FROM-TICKET");
        assertThat(row.getSaleOrderNo()).isNotEqualTo("RFQ-SHOULD-NOT-BE-SENT");
        assertThat(row.getSaleOrderItemNo()).isEqualTo("30");
    }

    @Test
    @SuppressWarnings("unchecked")
    void invalidRowsAreFailedIndividuallyAndDoNotBlockOtherValidRows() {
        Fixture fixture = fixture();
        OrderDeliveryTicketAllocation valid = allocation(4, 104, "MAT-OK", 2, 9404L);
        OrderDeliveryTicketAllocation missingLine = allocation(5, 105, "MAT-BAD", 2, 9405L);
        OrderDeliveryTicketAllocation missingMaterial = allocation(6, 106, null, 2, 9406L);
        OrderDeliveryTicketAllocation missingDate = allocation(7, 107, "MAT-NODATE", 2, 9407L);
        valid.setSourceOrderId(3404L);
        missingLine.setSourceOrderId(3404L);
        missingMaterial.setSourceOrderId(3404L);
        missingDate.setSourceOrderId(3404L);
        missingDate.setDeliveryDate(null);
        when(fixture.allocationMapper.selectById(4)).thenReturn(valid);
        when(fixture.allocationMapper.selectById(5)).thenReturn(missingLine);
        when(fixture.allocationMapper.selectById(6)).thenReturn(missingMaterial);
        when(fixture.allocationMapper.selectById(7)).thenReturn(missingDate);
        when(fixture.salesItemMapper.selectByIdForUpdate(9404L)).thenReturn(salesItem(9404L, 40, 3404L, null));
        when(fixture.salesItemMapper.selectByIdForUpdate(9405L)).thenReturn(salesItem(9405L, null, 3404L, null));
        when(fixture.salesItemMapper.selectByIdForUpdate(9406L)).thenReturn(salesItem(9406L, 60, 3404L, null));
        when(fixture.salesItemMapper.selectByIdForUpdate(9407L)).thenReturn(salesItem(9407L, 70, 3404L, null));
        when(fixture.headerMapper.selectById(3404L)).thenReturn(header(3404L, "SO-DOC-VALID"));
        mockReserve(fixture, "QPO-DEM-VALID", List.of(line(4, "QPO-DEML-4", "MAT-OK")));
        when(fixture.client.dispatch(any())).thenReturn(success("QPO-DEM-VALID", item("SO-DOC-VALID", "40", "MAT-OK", "PLAN-OK")));

        Map<String, Object> result = fixture.service.dispatchBatch(List.of(4, 5, 6, 7), 9904L);

        assertThat(result).containsEntry("successCount", 1).containsEntry("failedCount", 3);
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("rows");
        assertThat(rows).extracting(row -> row.get("status"))
                .containsExactly("SUCCESS", "FAILED", "FAILED", "FAILED");
        verify(fixture.client).dispatch(any());
        ArgumentCaptor<OrderDeliveryTicketAllocation> allocationCaptor = ArgumentCaptor.forClass(OrderDeliveryTicketAllocation.class);
        verify(fixture.allocationMapper, times(4)).updateById(allocationCaptor.capture());
        assertThat(allocationCaptor.getAllValues()).anySatisfy(update -> {
            assertThat(update.getId()).isEqualTo(5);
            assertThat(update.getMesProductionStatus()).isEqualTo("FAILED");
            assertThat(update.getMesProductionMessage()).contains("sales order line number");
        });
    }

    @Test
    void duplicateProductCodesWithDistinctSalesOrderLinesStayInOneBatch() {
        Fixture fixture = fixture();
        OrderDeliveryTicketAllocation first = allocation(8, 108, "MAT-DUP", 1, 9801L);
        OrderDeliveryTicketAllocation second = allocation(9, 109, "MAT-DUP", 1, 9802L);
        first.setSourceOrderId(3808L);
        second.setSourceOrderId(3808L);
        when(fixture.allocationMapper.selectById(8)).thenReturn(first);
        when(fixture.allocationMapper.selectById(9)).thenReturn(second);
        when(fixture.salesItemMapper.selectByIdForUpdate(9801L)).thenReturn(salesItem(9801L, 80, 3808L, null));
        when(fixture.salesItemMapper.selectByIdForUpdate(9802L)).thenReturn(salesItem(9802L, 90, 3808L, null));
        when(fixture.headerMapper.selectById(3808L)).thenReturn(header(3808L, "SO-DOC-DUP"));
        mockReserve(fixture, "QPO-DEM-DUP", List.of(
                line(8, "QPO-DEML-DUP-1", "MAT-DUP"),
                line(9, "QPO-DEML-DUP-2", "MAT-DUP")));
        when(fixture.client.dispatch(any())).thenReturn(success("QPO-DEM-DUP",
                item("SO-DOC-DUP", "80", "MAT-DUP", "PLAN-DUP-1"),
                item("SO-DOC-DUP", "90", "MAT-DUP", "PLAN-DUP-2")));

        Map<String, Object> result = fixture.service.dispatchBatch(List.of(8, 9), 9908L);

        assertThat(result).containsEntry("successCount", 2).containsEntry("batchCount", 1);
        ArgumentCaptor<MesProductionDispatchReq> requestCaptor = ArgumentCaptor.forClass(MesProductionDispatchReq.class);
        verify(fixture.client).dispatch(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getItems()).hasSize(2);
    }

    @Test
    void responseRowsAreMatchedBySalesOrderLineEvenWhenProductCodesCross() {
        Fixture fixture = fixture();
        OrderDeliveryTicketAllocation first = allocation(11, 111, "MAT-LINE-A", 1, 9811L);
        OrderDeliveryTicketAllocation second = allocation(12, 112, "MAT-LINE-B", 1, 9812L);
        first.setSourceOrderId(3811L);
        second.setSourceOrderId(3811L);
        when(fixture.allocationMapper.selectById(11)).thenReturn(first);
        when(fixture.allocationMapper.selectById(12)).thenReturn(second);
        when(fixture.salesItemMapper.selectByIdForUpdate(9811L)).thenReturn(salesItem(9811L, 10, 3811L, null));
        when(fixture.salesItemMapper.selectByIdForUpdate(9812L)).thenReturn(salesItem(9812L, 20, 3811L, null));
        when(fixture.headerMapper.selectById(3811L)).thenReturn(header(3811L, "SO-DOC-LINE"));
        mockReserve(fixture, "QPO-DEM-LINE", List.of(
                line(11, "QPO-DEML-LINE-10", "MAT-LINE-A"),
                line(12, "QPO-DEML-LINE-20", "MAT-LINE-B")));
        when(fixture.client.dispatch(any())).thenReturn(success("QPO-DEM-LINE",
                item("SO-DOC-LINE", "10", "MAT-LINE-B", "PLAN-LINE-10"),
                item("SO-DOC-LINE", "20", "MAT-LINE-A", "PLAN-LINE-20")));

        Map<String, Object> result = fixture.service.dispatchBatch(List.of(11, 12), 9911L);

        assertThat(result).containsEntry("successCount", 2).containsEntry("unknownCount", 0);
        ArgumentCaptor<OrderDeliveryTicketAllocation> allocationCaptor = ArgumentCaptor.forClass(OrderDeliveryTicketAllocation.class);
        verify(fixture.allocationMapper, times(2)).updateById(allocationCaptor.capture());
        assertThat(allocationCaptor.getAllValues())
                .anySatisfy(update -> {
                    assertThat(update.getId()).isEqualTo(11);
                    assertThat(update.getMesProductionExternalId()).isEqualTo("PLAN-LINE-10");
                })
                .anySatisfy(update -> {
                    assertThat(update.getId()).isEqualTo(12);
                    assertThat(update.getMesProductionExternalId()).isEqualTo("PLAN-LINE-20");
                });
    }

    @Test
    void duplicateSalesOrderLinesInSameGroupAreSplitIntoSeparateBatches() {
        Fixture fixture = fixture();
        OrderDeliveryTicketAllocation first = allocation(13, 113, "MAT-SO-LINE-A", 1, 9813L);
        OrderDeliveryTicketAllocation second = allocation(14, 114, "MAT-SO-LINE-B", 1, 9814L);
        first.setSourceOrderId(3813L);
        second.setSourceOrderId(3813L);
        when(fixture.allocationMapper.selectById(13)).thenReturn(first);
        when(fixture.allocationMapper.selectById(14)).thenReturn(second);
        when(fixture.salesItemMapper.selectByIdForUpdate(9813L)).thenReturn(salesItem(9813L, 10, 3813L, null));
        when(fixture.salesItemMapper.selectByIdForUpdate(9814L)).thenReturn(salesItem(9814L, 10, 3813L, null));
        when(fixture.headerMapper.selectById(3813L)).thenReturn(header(3813L, "SO-DOC-DUP-LINE"));
        mockReserveSequence(fixture,
                reservation("QPO-DEM-SO-LINE-1", line(13, "QPO-DEML-SO-LINE-1", "MAT-SO-LINE-A")),
                reservation("QPO-DEM-SO-LINE-2", line(14, "QPO-DEML-SO-LINE-2", "MAT-SO-LINE-B")));
        when(fixture.client.dispatch(any()))
                .thenReturn(success("QPO-DEM-SO-LINE-1", item("SO-DOC-DUP-LINE", "10", "MAT-SO-LINE-A", "PLAN-SO-LINE-1")))
                .thenReturn(success("QPO-DEM-SO-LINE-2", item("SO-DOC-DUP-LINE", "10", "MAT-SO-LINE-B", "PLAN-SO-LINE-2")));

        Map<String, Object> result = fixture.service.dispatchBatch(List.of(13, 14), 9913L);

        assertThat(result).containsEntry("successCount", 2).containsEntry("batchCount", 2);
        ArgumentCaptor<MesProductionDispatchReq> requestCaptor = ArgumentCaptor.forClass(MesProductionDispatchReq.class);
        verify(fixture.client, times(2)).dispatch(requestCaptor.capture());
        assertThat(requestCaptor.getAllValues()).allSatisfy(request -> {
            assertThat(request.getItems()).hasSize(1);
            assertThat(request.getItems().get(0).getSaleOrderNo()).isEqualTo("SO-DOC-DUP-LINE");
            assertThat(request.getItems().get(0).getSaleOrderItemNo()).isEqualTo("10");
        });
    }

    @Test
    void missingResponseRowMarksBatchResultUnknownWithoutRetryingWithNewBizId() {
        Fixture fixture = fixture();
        OrderDeliveryTicketAllocation allocation = allocation(10, 110, "MAT-UNKNOWN", 5, 9910L);
        allocation.setSourceOrderId(3910L);
        when(fixture.allocationMapper.selectById(10)).thenReturn(allocation);
        when(fixture.salesItemMapper.selectByIdForUpdate(9910L)).thenReturn(salesItem(9910L, 100, 3910L, null));
        when(fixture.headerMapper.selectById(3910L)).thenReturn(header(3910L, "SO-DOC-UNKNOWN"));
        mockReserve(fixture, "QPO-DEM-UNKNOWN", List.of(line(10, "QPO-DEML-UNKNOWN", "MAT-UNKNOWN")));
        when(fixture.client.dispatch(any())).thenReturn(success("QPO-DEM-UNKNOWN"));

        Map<String, Object> result = fixture.service.dispatchBatch(List.of(10), 9910L);

        assertThat(result).containsEntry("unknownCount", 1).containsEntry("successCount", 0).containsEntry("failedCount", 0);
        ArgumentCaptor<OrderDeliveryTicketAllocation> allocationCaptor = ArgumentCaptor.forClass(OrderDeliveryTicketAllocation.class);
        verify(fixture.allocationMapper).updateById(allocationCaptor.capture());
        assertThat(allocationCaptor.getValue().getMesProductionStatus()).isEqualTo("RESULT_UNKNOWN");
        assertThat(allocationCaptor.getValue().getMesProductionRequestId()).isEqualTo("QPO-DEML-UNKNOWN");
        assertThat(allocationCaptor.getValue().getMesProductionMessage()).contains("missing");
        verify(fixture.client).dispatch(any());
    }

    @Test
    void responseWithoutSalesOrderLineIsUnknownEvenWhenProductCodeMatches() {
        Fixture fixture = fixture();
        OrderDeliveryTicketAllocation allocation = allocation(15, 115, "MAT-STRICT", 5, 9915L);
        allocation.setSourceOrderId(3915L);
        when(fixture.allocationMapper.selectById(15)).thenReturn(allocation);
        when(fixture.salesItemMapper.selectByIdForUpdate(9915L)).thenReturn(salesItem(9915L, 150, 3915L, null));
        when(fixture.headerMapper.selectById(3915L)).thenReturn(header(3915L, "SO-DOC-STRICT"));
        mockReserve(fixture, "QPO-DEM-STRICT", List.of(line(15, "QPO-DEML-STRICT", "MAT-STRICT")));
        when(fixture.client.dispatch(any())).thenReturn(success("QPO-DEM-STRICT", productOnlyItem("MAT-STRICT", "PLAN-SHOULD-NOT-MATCH")));

        Map<String, Object> result = fixture.service.dispatchBatch(List.of(15), 9915L);

        assertThat(result).containsEntry("unknownCount", 1).containsEntry("successCount", 0).containsEntry("failedCount", 0);
        ArgumentCaptor<OrderDeliveryTicketAllocation> allocationCaptor = ArgumentCaptor.forClass(OrderDeliveryTicketAllocation.class);
        verify(fixture.allocationMapper).updateById(allocationCaptor.capture());
        assertThat(allocationCaptor.getValue().getMesProductionStatus()).isEqualTo("RESULT_UNKNOWN");
        assertThat(allocationCaptor.getValue().getMesProductionExternalId()).isNull();
        assertThat(allocationCaptor.getValue().getMesProductionMessage()).contains("saleOrderNo/saleOrderItemNo");
    }

    private Fixture fixture() {
        Fixture fixture = new Fixture();
        fixture.service = new MesProductionDispatchService();
        fixture.allocationMapper = mock(OrderDeliveryTicketAllocationMapper.class);
        fixture.client = mock(MesProductionDispatchClient.class);
        fixture.salesItemMapper = mock(SalesOrderItemMapper.class);
        fixture.headerMapper = mock(SalesOrderHeaderMapper.class);
        fixture.batchStateService = mock(MesBatchStateService.class);
        fixture.productionBatchItemMapper = mock(MesProductionBatchItemMapper.class);
        fixture.batchMapper = mock(MesIntegrationBatchMapper.class);
        ReflectionTestUtils.setField(fixture.service, "allocationMapper", fixture.allocationMapper);
        ReflectionTestUtils.setField(fixture.service, "mesProductionDispatchClient", fixture.client);
        ReflectionTestUtils.setField(fixture.service, "salesOrderItemMapper", fixture.salesItemMapper);
        ReflectionTestUtils.setField(fixture.service, "salesOrderHeaderMapper", fixture.headerMapper);
        ReflectionTestUtils.setField(fixture.service, "mesBatchStateService", fixture.batchStateService);
        ReflectionTestUtils.setField(fixture.service, "mesProductionBatchItemMapper", fixture.productionBatchItemMapper);
        ReflectionTestUtils.setField(fixture.service, "mesIntegrationBatchMapper", fixture.batchMapper);
        return fixture;
    }

    private void mockReserve(Fixture fixture, String bizId, List<MesProductionBatchItem> lines) {
        when(fixture.batchStateService.reserveProductionBatch(any(String.class), any(MesBatchStateService.ProductionBatchReservation.class)))
                .thenReturn(new MesBatchStateService.ProductionBatchReservationResult(batch(bizId), lines));
    }

    private void mockReserveSequence(Fixture fixture, MesBatchStateService.ProductionBatchReservationResult... results) {
        org.mockito.stubbing.OngoingStubbing<MesBatchStateService.ProductionBatchReservationResult> stubbing =
                when(fixture.batchStateService.reserveProductionBatch(any(String.class), any(MesBatchStateService.ProductionBatchReservation.class)));
        for (MesBatchStateService.ProductionBatchReservationResult result : results) {
            stubbing = stubbing.thenReturn(result);
        }
    }

    private MesBatchStateService.ProductionBatchReservationResult reservation(String bizId, MesProductionBatchItem item) {
        return new MesBatchStateService.ProductionBatchReservationResult(batch(bizId), List.of(item));
    }

    private MesIntegrationBatch batch(String bizId) {
        MesIntegrationBatch batch = new MesIntegrationBatch();
        batch.setId(Math.abs((long) bizId.hashCode()));
        batch.setBatchType("PRODUCTION_DEMAND");
        batch.setBizId(bizId);
        batch.setStatus("RESERVED");
        return batch;
    }

    private MesProductionBatchItem line(Integer allocationId, String lineRequestId, String productCode) {
        MesProductionBatchItem item = new MesProductionBatchItem();
        item.setId(Math.abs((long) lineRequestId.hashCode()));
        item.setBatchId(100L);
        item.setAllocationId(allocationId);
        item.setLineRequestId(lineRequestId);
        item.setProductCode(productCode);
        item.setStatus("RESERVED");
        return item;
    }

    private OrderDeliveryTicketAllocation allocation(Integer id, Integer ticketId, String materialNo, Integer qty, Long sourceItemId) {
        OrderDeliveryTicketAllocation allocation = new OrderDeliveryTicketAllocation();
        allocation.setId(id);
        allocation.setTicketId(ticketId);
        allocation.setSourceType("SALES_ORDER");
        allocation.setSourceOrderId(3001L);
        allocation.setSourceItemId(sourceItemId);
        allocation.setAllocationType("PRODUCTION_REQUIRED");
        allocation.setQty(qty);
        allocation.setTicketQty(10);
        allocation.setMaterialNo(materialNo);
        if (materialNo != null) {
            allocation.setEngineeringMaterialCode("RAW-" + materialNo);
            allocation.setEngineeringMaterialName("Raw material for " + materialNo);
            allocation.setMaterialGrossWeight(1.25);
        }
        allocation.setRfqNo("RFQ-NOT-ORDER-NO");
        allocation.setCustomerName("ACME");
        allocation.setInternalProjectNo("IP-9");
        allocation.setPartNo("P-" + id);
        allocation.setVersion("A");
        allocation.setDescription("Bracket");
        allocation.setMaterialName("Material note P-" + id);
        allocation.setSurfaceTreatment("Anodized");
        allocation.setRemark("Rush");
        allocation.setDeliveryDate(LocalDate.of(2026, 8, 20));
        allocation.setMesProductionStatus("PENDING");
        return allocation;
    }

    private SalesOrderItem salesItem(Long id, Integer lineNo, Long salesOrderId, Integer sourceTicketId) {
        SalesOrderItem item = new SalesOrderItem();
        item.setId(id);
        item.setSalesOrderId(salesOrderId);
        item.setLineNo(lineNo);
        item.setSourceTicketId(sourceTicketId);
        return item;
    }

    private SalesOrderHeader header(Long id, String documentNo) {
        SalesOrderHeader header = new SalesOrderHeader();
        header.setId(id);
        header.setDocumentNo(documentNo);
        header.setSalesOrderNo("LEGACY-" + documentNo);
        header.setCustomerCode("C001");
        header.setCustomerName("ACME");
        return header;
    }

    private MesProductionDispatchResp success(String bizId, MesProductionDispatchResp.Item... items) {
        MesProductionDispatchResp resp = new MesProductionDispatchResp();
        resp.setSuccess(true);
        MesProductionDispatchResp.DataEnvelope data = new MesProductionDispatchResp.DataEnvelope();
        data.setBizId(bizId);
        data.setItems(List.of(items));
        resp.setData(data);
        resp.setMessage("accepted");
        return resp;
    }

    private MesProductionDispatchResp.Item productOnlyItem(String productCode, String planNo) {
        MesProductionDispatchResp.Item item = new MesProductionDispatchResp.Item();
        item.setProductCode(productCode);
        item.setPlanNo(planNo);
        item.setStatus(0);
        return item;
    }

    private MesProductionDispatchResp.Item item(String saleOrderNo, String saleOrderItemNo, String productCode, String planNo) {
        MesProductionDispatchResp.Item item = productOnlyItem(productCode, planNo);
        item.setSaleOrderNo(saleOrderNo);
        item.setSaleOrderItemNo(saleOrderItemNo);
        return item;
    }

    private static class Fixture {
        private MesProductionDispatchService service;
        private OrderDeliveryTicketAllocationMapper allocationMapper;
        private MesProductionDispatchClient client;
        private SalesOrderItemMapper salesItemMapper;
        private SalesOrderHeaderMapper headerMapper;
        private MesBatchStateService batchStateService;
        private MesProductionBatchItemMapper productionBatchItemMapper;
        private MesIntegrationBatchMapper batchMapper;
    }
}
