package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.DeliveryGenerateReq;
import com.machining.quote.dto.InternalTargetLeadTimeUpdateReq;
import com.machining.quote.dto.ReleaseItemFieldUpdateReq;
import com.machining.quote.dto.ReleaseSourceType;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.BusinessFileService;
import com.machining.quote.service.DataScopeService;
import com.machining.quote.service.InternalTargetLeadTimeService;
import com.machining.quote.service.LeadTimeService;
import com.machining.quote.service.OrderItemPageService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReleaseItemDataReplacementStage1CharacterizationTest {
    private static final Path BASELINE_SCHEMA = Path.of(
            "src", "main", "resources", "db", "migration", "V1__qpo_baseline_schema.sql");

    @Test
    @SuppressWarnings("unchecked")
    void internalTargetLeadTimeSingleRowSaveUpdatesOnlyRfqMasterOnPartialOrder() {
        TestContext context = context();
        OrderRfq order = order(101, QuoteStatusWorkflow.PARTIALLY_RELEASED);
        ItemQuoteVersion item = item(201, 101, 10);
        item.setInternalTargetLeadTime("2026-08-15");
        ItemQuoteVersion saved = item(201, 101, 10);
        saved.setInternalTargetLeadTime("2026-08-20");
        OrderDeliveryTicket historicalTicket = ticket(9001, 201, 4);
        historicalTicket.setDeliveryDate(LocalDate.of(2026, 8, 15));

        when(context.orderMapper.selectByIdForUpdate(101)).thenReturn(order);
        when(context.itemMapper.selectByIdForUpdate(201)).thenReturn(item);
        when(context.itemMapper.selectById(201)).thenReturn(saved);

        InternalTargetLeadTimeUpdateReq request = new InternalTargetLeadTimeUpdateReq();
        request.setRfqId(101);
        request.setItemId(201);
        request.setInternalTargetLeadTime("2026-08-20");

        Map<String, Object> result = context.service.updateLeadTime(request);

        assertThat(result).containsEntry("success", true);
        assertThat((Map<String, Object>) result.get("data"))
                .containsEntry("internalTargetLeadTime", "2026-08-20");
        verify(context.itemMapper).update(isNull(), any(UpdateWrapper.class));
        verify(context.auditService).record(
                eq("INTERNAL_TARGET_LEAD_TIME_UPDATE"),
                eq("item_quote_version"),
                eq(201),
                any(Map.class),
                any(Map.class));
        verify(context.ticketMapper, never()).updateById(any());
        assertThat(historicalTicket.getDeliveryDate()).isEqualTo(LocalDate.of(2026, 8, 15));
    }

    @Test
    void editablePartFieldsRemainMutableAfterPartialReleaseWithoutChangingTicketSnapshot() {
        TestContext context = context();
        OrderRfq order = order(101, QuoteStatusWorkflow.PARTIALLY_RELEASED);
        ItemQuoteVersion item = item(201, 101, 10);
        item.setDescription("old description");
        item.setSurfaceTreatment("old surface");
        item.setRemark("old remark");
        OrderDeliveryTicket historicalTicket = ticket(9001, 201, 4);
        historicalTicket.setDescription("released description");
        historicalTicket.setSurfaceTreatment("released surface");
        historicalTicket.setRemark("released remark");

        when(context.orderMapper.selectByIdForUpdate(101)).thenReturn(order);
        when(context.itemMapper.selectByIdForUpdate(201)).thenReturn(item);
        when(context.itemMapper.selectById(201)).thenReturn(item);
        when(context.ticketMapper.selectList(any())).thenReturn(List.of(historicalTicket));

        context.service.updateReleaseItemFields(201L, fieldRequest());

        assertThat(item.getDescription()).isEqualTo("new description");
        assertThat(item.getSurfaceTreatment()).isEqualTo("new surface");
        assertThat(item.getRemark()).isEqualTo("new remark");
        assertThat(historicalTicket.getQty()).isEqualTo(4);
        assertThat(historicalTicket.getDescription()).isEqualTo("released description");
        assertThat(historicalTicket.getSurfaceTreatment()).isEqualTo("released surface");
        assertThat(historicalTicket.getRemark()).isEqualTo("released remark");
        verify(context.itemMapper).updateById(item);
        verify(context.ticketMapper, never()).updateById(any());
    }

    @Test
    void editablePartFieldsRejectFullyReleasedItemWithoutChangingMasterOrTicket() {
        TestContext context = context();
        OrderRfq order = order(101, QuoteStatusWorkflow.PARTIALLY_RELEASED);
        ItemQuoteVersion item = item(201, 101, 10);
        item.setDescription("old description");
        OrderDeliveryTicket historicalTicket = ticket(9001, 201, 10);
        historicalTicket.setDescription("released description");

        when(context.orderMapper.selectByIdForUpdate(101)).thenReturn(order);
        when(context.itemMapper.selectByIdForUpdate(201)).thenReturn(item);
        when(context.ticketMapper.selectList(any())).thenReturn(List.of(historicalTicket));

        assertThatThrownBy(() -> context.service.updateReleaseItemFields(201L, fieldRequest()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("fully released");

        assertThat(item.getDescription()).isEqualTo("old description");
        assertThat(historicalTicket.getDescription()).isEqualTo("released description");
        verify(context.itemMapper, never()).updateById(any());
        verify(context.ticketMapper, never()).updateById(any());
        verify(context.auditService, never()).record(
                eq("RELEASE_ITEM_FIELD_UPDATE"), any(), any(), any(), any());
    }

    @Test
    void dataScopeDenialStopsSingleRowLeadTimeAndPartFieldWritesBeforeItemLookup() {
        TestContext leadTimeContext = deniedContext();
        InternalTargetLeadTimeUpdateReq leadTimeRequest = new InternalTargetLeadTimeUpdateReq();
        leadTimeRequest.setRfqId(101);
        leadTimeRequest.setItemId(201);
        leadTimeRequest.setInternalTargetLeadTime("2026-08-20");

        assertThatThrownBy(() -> leadTimeContext.service.updateLeadTime(leadTimeRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("No data permission");
        verify(leadTimeContext.itemMapper, never()).selectByIdForUpdate(any());

        TestContext fieldContext = deniedContext();
        assertThatThrownBy(() -> fieldContext.service.updateReleaseItemFields(201L, fieldRequest()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("No data permission");
        verify(fieldContext.itemMapper, never()).selectByIdForUpdate(any());
    }

    @Test
    void acceptedQuantityBelongsToFormalReleaseRequestAndNotRfqMasterPersistence() throws Exception {
        assertThat(Arrays.stream(ItemQuoteVersion.class.getDeclaredFields()).map(Field::getName))
                .doesNotContain("acceptedQty");
        assertThat(DeliveryGenerateReq.DeliveryItemReq.class.getDeclaredField("acceptedQty").getType())
                .isEqualTo(Integer.class);

        String schema = Files.readString(BASELINE_SCHEMA, StandardCharsets.UTF_8);
        int tableStart = schema.indexOf("CREATE TABLE item_quote_version (");
        int tableEnd = schema.indexOf("\n);", tableStart);
        assertThat(tableStart).isGreaterThanOrEqualTo(0);
        assertThat(tableEnd).isGreaterThan(tableStart);
        assertThat(schema.substring(tableStart, tableEnd)).doesNotContain("accepted_qty");
    }

    private TestContext deniedContext() {
        TestContext context = context();
        OrderRfq order = order(101, QuoteStatusWorkflow.QUOTED);
        when(context.orderMapper.selectByIdForUpdate(101)).thenReturn(order);
        doThrow(new RuntimeException("No data permission for order 101"))
                .when(context.dataScopeService).assertCanAccessOrder(order);
        return context;
    }

    private TestContext context() {
        OrderRfqMapper orderMapper = mock(OrderRfqMapper.class);
        ItemQuoteVersionMapper itemMapper = mock(ItemQuoteVersionMapper.class);
        OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
        AuditService auditService = mock(AuditService.class);
        DataScopeService dataScopeService = mock(DataScopeService.class);
        InternalTargetLeadTimeService service = new InternalTargetLeadTimeService();
        ReflectionTestUtils.setField(service, "orderRfqMapper", orderMapper);
        ReflectionTestUtils.setField(service, "itemMapper", itemMapper);
        ReflectionTestUtils.setField(service, "ticketMapper", ticketMapper);
        ReflectionTestUtils.setField(service, "leadTimeService", new LeadTimeService());
        ReflectionTestUtils.setField(service, "auditService", auditService);
        ReflectionTestUtils.setField(service, "orderItemPageService", mock(OrderItemPageService.class));
        ReflectionTestUtils.setField(service, "dataScopeService", dataScopeService);
        ReflectionTestUtils.setField(service, "businessFileService", mock(BusinessFileService.class));
        return new TestContext(service, orderMapper, itemMapper, ticketMapper, auditService, dataScopeService);
    }

    private ReleaseItemFieldUpdateReq fieldRequest() {
        ReleaseItemFieldUpdateReq request = new ReleaseItemFieldUpdateReq();
        request.setSourceType(ReleaseSourceType.RFQ);
        request.setRfqId(101);
        request.setDescription(" new description ");
        request.setSurfaceTreatment(" new surface ");
        request.setRemark(" new remark ");
        return request;
    }

    private OrderRfq order(int id, String status) {
        OrderRfq order = new OrderRfq();
        order.setId(id);
        order.setRfqNo("RFQ-" + id);
        order.setCustomerName("ACME");
        order.setStatus(status);
        return order;
    }

    private ItemQuoteVersion item(int id, int rfqId, int qty) {
        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setId(id);
        item.setRfqId(rfqId);
        item.setPartNo("P-" + id);
        item.setVersion("A");
        item.setQty(qty);
        item.setTaxIncludedUnitPrice(10.0);
        item.setStatus(QuoteStatusWorkflow.QUOTED);
        return item;
    }

    private OrderDeliveryTicket ticket(int id, int itemId, int qty) {
        OrderDeliveryTicket ticket = new OrderDeliveryTicket();
        ticket.setId(id);
        ticket.setRfqId(101);
        ticket.setItemId(itemId);
        ticket.setSourceType(ReleaseSourceType.RFQ.name());
        ticket.setSourceOrderId(101L);
        ticket.setSourceItemId((long) itemId);
        ticket.setQty(qty);
        return ticket;
    }

    private record TestContext(
            InternalTargetLeadTimeService service,
            OrderRfqMapper orderMapper,
            ItemQuoteVersionMapper itemMapper,
            OrderDeliveryTicketMapper ticketMapper,
            AuditService auditService,
            DataScopeService dataScopeService) {
    }
}
