package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.entity.ItemAssemblyRelation;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.entity.SalesOrderItemAssemblyRelation;
import com.machining.quote.mapper.ItemAssemblyRelationMapper;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.SalesOrderItemAssemblyRelationMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.JsonSnapshotCopyService;
import com.machining.quote.service.ProcessRouteSnapshotService;
import com.machining.quote.service.SalesOrderDirtySnapshotRepairService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesOrderDirtySnapshotRepairStage9Test {
    private static final String MATCHED = "\u5df2\u5339\u914d";
    private static final String UNCONFIRMED = "\u672a\u786e\u8ba4";
    private static final String UNASSIGNED = "\u672a\u5206\u914d";

    @Test
    @SuppressWarnings("unchecked")
    void repairFillsMissingSnapshotsAndTicketsWithoutOverwritingProtectedFields() {
        Fixture fixture = fixture();
        SalesOrderItem item = matchedSalesOrderItem(501L, 56L, 901);
        item.setMaterialData(Map.of());
        item.setProcessRoute(List.of());
        item.setFeeData(null);
        ItemQuoteVersion source = sourceItem(901, "NORMAL");
        OrderDeliveryTicket ticket = salesOrderTicket(7001, item, "PENDING", UNASSIGNED);
        ticket.setMaterialData(Map.of());
        ticket.setProcessRoute(List.of());
        OrderDeliveryTicket mesProcessed = salesOrderTicket(7002, item, "MES_SUCCESS", UNASSIGNED);
        mesProcessed.setMaterialData(Map.of());
        mesProcessed.setProcessRoute(List.of());

        when(fixture.itemMapper.selectList(any())).thenReturn(List.of(item));
        when(fixture.itemMapper.selectByIdForUpdate(item.getId())).thenReturn(item);
        when(fixture.sourceMapper.selectById(source.getId())).thenReturn(source);
        when(fixture.ticketMapper.selectList(any())).thenReturn(List.of(ticket, mesProcessed));
        when(fixture.ticketMapper.selectByIdForUpdate(ticket.getId())).thenReturn(ticket);

        SalesOrderDirtySnapshotRepairService.RepairResult result = fixture.service.repair(null, "ops-user");

        assertThat(result.orderCount()).isEqualTo(1);
        assertThat(result.candidateItemCount()).isEqualTo(1);
        assertThat(result.repairedItemCount()).isEqualTo(1);
        assertThat(result.candidateTicketCount()).isEqualTo(1);
        assertThat(result.repairedTicketCount()).isEqualTo(1);
        assertThat(result.skippedReasons()).containsEntry("ticketBlockedByMes", 1);

        ArgumentCaptor<SalesOrderItem> itemCaptor = ArgumentCaptor.forClass(SalesOrderItem.class);
        verify(fixture.itemMapper).updateById(itemCaptor.capture());
        SalesOrderItem updatedItem = itemCaptor.getValue();
        assertThat(updatedItem.getEngineeringMaterialCode()).isEqualTo("EMC-HISTORY");
        assertThat(updatedItem.getEngineeringMaterialName()).isEqualTo("History material");
        assertThat(updatedItem.getMaterialGrossWeight()).isEqualTo(7.5);
        assertThat(updatedItem.getPartNetWeight()).isEqualTo(3.25);
        assertThat(updatedItem.getMaterialData()).isEqualTo(source.getMaterialData()).isNotSameAs(source.getMaterialData());
        assertThat(updatedItem.getProcessRoute()).isEqualTo(source.getProcessRoute()).isNotSameAs(source.getProcessRoute());
        assertThat(updatedItem.getFeeData()).isEqualTo(source.getFeeData()).isNotSameAs(source.getFeeData());
        assertThat(updatedItem.getDescription()).isEqualTo("Imported description");
        assertThat(updatedItem.getMaterialName()).isEqualTo("Imported material");
        assertThat(updatedItem.getSurfaceTreatment()).isEqualTo("Imported surface");
        assertThat(updatedItem.getTaxIncludedUnitPrice()).isEqualByComparingTo("88.00");
        assertThat(updatedItem.getUntaxedUnitPrice()).isEqualByComparingTo("80.00");
        assertThat(updatedItem.getMaterialNo()).isEqualTo("SO-UNCONFIRMED-MAT");
        assertThat(updatedItem.getMaterialNoStatus()).isEqualTo(UNCONFIRMED);

        ArgumentCaptor<OrderDeliveryTicket> ticketCaptor = ArgumentCaptor.forClass(OrderDeliveryTicket.class);
        verify(fixture.ticketMapper).updateById(ticketCaptor.capture());
        OrderDeliveryTicket updatedTicket = ticketCaptor.getValue();
        assertThat(updatedTicket.getEngineeringMaterialCode()).isEqualTo("EMC-HISTORY");
        assertThat(updatedTicket.getMaterialData()).isEqualTo(updatedItem.getMaterialData()).isNotSameAs(updatedItem.getMaterialData());
        assertThat(updatedTicket.getProcessRoute()).isEqualTo(updatedItem.getProcessRoute()).isNotSameAs(updatedItem.getProcessRoute());
        assertThat(updatedTicket.getMesSyncStatus()).isEqualTo("PENDING");

        ((Map<String, Object>) updatedItem.getMaterialData()).put("targetOnly", true);
        assertThat((Map<String, Object>) source.getMaterialData()).doesNotContainKey("targetOnly");
        assertThat((Map<String, Object>) updatedTicket.getMaterialData()).doesNotContainKey("targetOnly");

        ArgumentCaptor<Object> afterDataCaptor = ArgumentCaptor.forClass(Object.class);
        verify(fixture.auditService).record(
                eq("SALES_ORDER_DIRTY_SNAPSHOT_REPAIR"),
                eq("sales_order_item"),
                isNull(),
                isNull(),
                afterDataCaptor.capture());
        Map<String, Object> auditSummary = (Map<String, Object>) afterDataCaptor.getValue();
        assertThat(auditSummary)
                .containsEntry("executor", "ops-user")
                .containsEntry("orderCount", 1)
                .containsEntry("salesOrderItemCount", 1)
                .containsEntry("ticketCount", 1);
        assertThat(auditSummary.get("executedAt")).isInstanceOf(String.class);
        assertThat(auditSummary.toString()).doesNotContain("ALLOY-SECRET", "CUT-SECRET", "FEE-SECRET");
    }

    @Test
    void repairDoesNotOverwriteNonEmptySnapshotsOrPricesAndSkipsEmptyHistorySnapshots() {
        Fixture fixture = fixture();
        SalesOrderItem partiallyDirty = matchedSalesOrderItem(502L, 56L, 902);
        Object existingMaterial = Map.of("target", "keep-material");
        Object existingRoute = List.of(Map.of("processCode", "KEEP-ROUTE"));
        partiallyDirty.setMaterialData(existingMaterial);
        partiallyDirty.setProcessRoute(existingRoute);
        partiallyDirty.setFeeData(List.of());
        ItemQuoteVersion source = sourceItem(902, "NORMAL");
        SalesOrderItem emptyHistory = matchedSalesOrderItem(503L, 57L, 903);
        emptyHistory.setMaterialData(Map.of());
        emptyHistory.setProcessRoute(List.of());
        emptyHistory.setFeeData(List.of());
        ItemQuoteVersion sourceWithoutSnapshots = sourceItem(903, "NORMAL");
        sourceWithoutSnapshots.setMaterialData(Map.of());
        sourceWithoutSnapshots.setProcessRoute(List.of());
        sourceWithoutSnapshots.setFeeData(List.of());

        when(fixture.itemMapper.selectList(any())).thenReturn(List.of(partiallyDirty, emptyHistory));
        when(fixture.itemMapper.selectByIdForUpdate(partiallyDirty.getId())).thenReturn(partiallyDirty);
        when(fixture.itemMapper.selectByIdForUpdate(emptyHistory.getId())).thenReturn(emptyHistory);
        when(fixture.sourceMapper.selectById(source.getId())).thenReturn(source);
        when(fixture.sourceMapper.selectById(sourceWithoutSnapshots.getId())).thenReturn(sourceWithoutSnapshots);
        when(fixture.ticketMapper.selectList(any())).thenReturn(List.of());

        SalesOrderDirtySnapshotRepairService.RepairResult result = fixture.service.repair(null, "ops-user");

        assertThat(result.candidateItemCount()).isEqualTo(2);
        assertThat(result.repairedItemCount()).isEqualTo(1);
        assertThat(result.skippedReasons()).containsEntry("itemSourceSnapshotMissing", 1);

        ArgumentCaptor<SalesOrderItem> captor = ArgumentCaptor.forClass(SalesOrderItem.class);
        verify(fixture.itemMapper).updateById(captor.capture());
        SalesOrderItem updated = captor.getValue();
        assertThat(updated.getId()).isEqualTo(partiallyDirty.getId());
        assertThat(updated.getMaterialData()).isSameAs(existingMaterial);
        assertThat(updated.getProcessRoute()).isSameAs(existingRoute);
        assertThat(updated.getFeeData()).isEqualTo(source.getFeeData()).isNotSameAs(source.getFeeData());
        assertThat(updated.getTaxIncludedUnitPrice()).isEqualByComparingTo("88.00");
        verify(fixture.ticketMapper, never()).updateById(any());
    }

    @Test
    void assemblyNormalTargetsArePromotedAndRelationsRebuiltDuringRepair() {
        Fixture fixture = fixture();
        SalesOrderItem parent = matchedSalesOrderItem(601L, 60L, 1001);
        parent.setPartNo("ASM-001");
        parent.setVersion("A");
        parent.setQuoteMode("NORMAL");
        parent.setMaterialData(Map.of());
        SalesOrderItem child = matchedSalesOrderItem(602L, 60L, 1002);
        child.setPartNo("CH-001");
        child.setVersion("A");
        ItemQuoteVersion parentSource = sourceItem(1001, "ASSEMBLY");
        parentSource.setPartNo("ASM-001");
        parentSource.setVersion("A");
        ItemQuoteVersion childSource = sourceItem(1002, "NORMAL");
        childSource.setPartNo("CH-001");
        childSource.setVersion("A");

        when(fixture.itemMapper.selectList(any())).thenReturn(List.of(parent), List.of(child));
        when(fixture.itemMapper.selectByIdForUpdate(parent.getId())).thenReturn(parent);
        when(fixture.sourceMapper.selectById(parentSource.getId())).thenReturn(parentSource);
        when(fixture.sourceMapper.selectBatchIds(any())).thenReturn(List.of(childSource));
        when(fixture.historyRelationMapper.selectList(any())).thenReturn(List.of(historyRelation(1001, 1002)));
        when(fixture.ticketMapper.selectList(any())).thenReturn(List.of());

        fixture.service.repair(null, "ops-user");

        ArgumentCaptor<SalesOrderItem> itemCaptor = ArgumentCaptor.forClass(SalesOrderItem.class);
        verify(fixture.itemMapper).updateById(itemCaptor.capture());
        assertThat(itemCaptor.getValue().getQuoteMode()).isEqualTo("ASSEMBLY");
        verify(fixture.salesOrderAssemblyRelationMapper).delete(any(QueryWrapper.class));
        ArgumentCaptor<SalesOrderItemAssemblyRelation> relationCaptor =
                ArgumentCaptor.forClass(SalesOrderItemAssemblyRelation.class);
        verify(fixture.salesOrderAssemblyRelationMapper).insert(relationCaptor.capture());
        SalesOrderItemAssemblyRelation relation = relationCaptor.getValue();
        assertThat(relation.getSalesOrderId()).isEqualTo(60L);
        assertThat(relation.getParentItemId()).isEqualTo(601L);
        assertThat(relation.getChildItemId()).isEqualTo(602L);
        assertThat(relation.getQtyPerParent()).isEqualByComparingTo("2.50");
    }

    @Test
    void previewReportsCountsWithoutMutatingOrAuditing() {
        Fixture fixture = fixture();
        SalesOrderItem item = matchedSalesOrderItem(701L, 70L, 1101);
        item.setMaterialData(Map.of());
        ItemQuoteVersion source = sourceItem(1101, "NORMAL");
        OrderDeliveryTicket blockedByWms = salesOrderTicket(7101, item, "PENDING", "ALLOCATED");
        blockedByWms.setMaterialData(Map.of());

        when(fixture.itemMapper.selectList(any())).thenReturn(List.of(item));
        when(fixture.sourceMapper.selectById(source.getId())).thenReturn(source);
        when(fixture.ticketMapper.selectList(any())).thenReturn(List.of(blockedByWms));

        SalesOrderDirtySnapshotRepairService.RepairResult preview = fixture.service.preview(null, "ops-user");

        assertThat(preview.applied()).isFalse();
        assertThat(preview.candidateItemCount()).isEqualTo(1);
        assertThat(preview.repairedItemCount()).isZero();
        assertThat(preview.candidateTicketCount()).isZero();
        assertThat(preview.skippedReasons()).containsEntry("ticketBlockedByWms", 1);
        verify(fixture.itemMapper, never()).updateById(any());
        verify(fixture.ticketMapper, never()).updateById(any());
        verify(fixture.auditService, never()).record(any(), any(), any(), any(), any());
    }

    private Fixture fixture() {
        return new Fixture();
    }

    private SalesOrderItem matchedSalesOrderItem(Long id, Long orderId, Integer matchedItemId) {
        SalesOrderItem item = new SalesOrderItem();
        item.setId(id);
        item.setSalesOrderId(orderId);
        item.setPartNo("P-" + id);
        item.setVersion("A");
        item.setDescription("Imported description");
        item.setMaterialName("Imported material");
        item.setSurfaceTreatment("Imported surface");
        item.setQty(3);
        item.setTaxIncludedUnitPrice(new BigDecimal("88.00"));
        item.setUntaxedUnitPrice(new BigDecimal("80.00"));
        item.setTaxIncludedTotalPrice(new BigDecimal("264.00"));
        item.setUntaxedTotalPrice(new BigDecimal("240.00"));
        item.setMaterialNo("SO-UNCONFIRMED-MAT");
        item.setMaterialNoStatus(UNCONFIRMED);
        item.setMatchStatus(MATCHED);
        item.setMatchedItemId(matchedItemId);
        item.setQuoteMode("NORMAL");
        item.setItemType("Imported type");
        return item;
    }

    private ItemQuoteVersion sourceItem(Integer id, String quoteMode) {
        ItemQuoteVersion source = new ItemQuoteVersion();
        source.setId(id);
        source.setRfqId(21);
        source.setPartNo("P-" + id);
        source.setVersion("A");
        source.setEngineeringMaterialCode("EMC-HISTORY");
        source.setEngineeringMaterialName("History material");
        source.setEngineeringMaterialTypeCode("RAW-HISTORY");
        source.setEngineeringMaterialTypeName("Raw history");
        source.setEngineeringMaterialCategory("History category");
        source.setMaterialShape("Plate");
        source.setMaterialGrossWeight(7.5);
        source.setPartNetWeight(3.25);
        source.setQuoteMode(quoteMode);
        source.setAssemblyFee(12.5);
        source.setBaseCost(45.75);
        source.setTaxIncludedUnitPrice(999.0);
        source.setMaterialNo("HISTORY-CONFIRMED-MAT");
        source.setMaterialNoStatus("\u5df2\u786e\u8ba4");
        source.setMaterialData(mutableMaterialSnapshot());
        source.setProcessRoute(mutableRouteSnapshot());
        source.setFeeData(mutableFeeSnapshot());
        return source;
    }

    private OrderDeliveryTicket salesOrderTicket(Integer id, SalesOrderItem item, String mesStatus, String wmsStatus) {
        OrderDeliveryTicket ticket = new OrderDeliveryTicket();
        ticket.setId(id);
        ticket.setSourceType("SALES_ORDER");
        ticket.setSourceOrderId(item.getSalesOrderId());
        ticket.setSourceItemId(item.getId());
        ticket.setMesSyncStatus(mesStatus);
        ticket.setWmsAllocateStatus(wmsStatus);
        return ticket;
    }

    private ItemAssemblyRelation historyRelation(Integer parentItemId, Integer childItemId) {
        ItemAssemblyRelation relation = new ItemAssemblyRelation();
        relation.setParentItemId(parentItemId);
        relation.setChildItemId(childItemId);
        relation.setChildQtyPerParent(2.5);
        relation.setSortNo(7);
        return relation;
    }

    private Map<String, Object> mutableMaterialSnapshot() {
        Map<String, Object> material = new LinkedHashMap<>();
        material.put("name", "ALLOY-SECRET");
        material.put("tags", new ArrayList<>(List.of("source")));
        return material;
    }

    private List<Map<String, Object>> mutableRouteSnapshot() {
        Map<String, Object> route = new LinkedHashMap<>();
        route.put("processCode", "CUT-SECRET");
        route.put("lineCost", 15.0);
        return new ArrayList<>(List.of(route));
    }

    private List<Map<String, Object>> mutableFeeSnapshot() {
        Map<String, Object> fee = new LinkedHashMap<>();
        fee.put("feeName", "FEE-SECRET");
        fee.put("lineCost", 2.0);
        return new ArrayList<>(List.of(fee));
    }

    private static final class Fixture {
        private final SalesOrderItemMapper itemMapper = mock(SalesOrderItemMapper.class);
        private final ItemQuoteVersionMapper sourceMapper = mock(ItemQuoteVersionMapper.class);
        private final OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
        private final ItemAssemblyRelationMapper historyRelationMapper = mock(ItemAssemblyRelationMapper.class);
        private final SalesOrderItemAssemblyRelationMapper salesOrderAssemblyRelationMapper =
                mock(SalesOrderItemAssemblyRelationMapper.class);
        private final AuditService auditService = mock(AuditService.class);
        private final JsonSnapshotCopyService jsonSnapshotCopyService = new JsonSnapshotCopyService();
        private final SalesOrderDirtySnapshotRepairService service = new SalesOrderDirtySnapshotRepairService(
                itemMapper,
                sourceMapper,
                ticketMapper,
                historyRelationMapper,
                salesOrderAssemblyRelationMapper,
                jsonSnapshotCopyService,
                new ProcessRouteSnapshotService(jsonSnapshotCopyService),
                auditService
        );
    }
}
