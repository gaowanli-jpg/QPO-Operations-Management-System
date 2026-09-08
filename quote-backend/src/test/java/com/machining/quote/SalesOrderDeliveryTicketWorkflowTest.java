package com.machining.quote;

import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.DeliveryGenerateReq;
import com.machining.quote.dto.ReleaseSourceType;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.DataScopeService;
import com.machining.quote.service.DeliveryService;
import com.machining.quote.service.LeadTimeService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesOrderDeliveryTicketWorkflowTest {
    private static final Path BACKEND = Path.of("src", "main");
    private static final Path MAIN_JAVA = BACKEND.resolve(Path.of("java", "com", "machining", "quote"));
    private static final Path MIGRATION = BACKEND.resolve(Path.of("resources", "db", "migration",
            "V1__qpo_baseline_schema.sql"));

    @Test
    void migrationAndEntitiesAddSourceIdentityToDeliveryTickets() throws Exception {
        String migration = read(MIGRATION);
        String ticket = read(MAIN_JAVA.resolve(Path.of("entity", "OrderDeliveryTicket.java")));
        String allocation = read(MAIN_JAVA.resolve(Path.of("entity", "OrderDeliveryTicketAllocation.java")));
        String deliveredDto = read(MAIN_JAVA.resolve(Path.of("dto", "DeliveredItemDTO.java")));
        String lockedDto = read(MAIN_JAVA.resolve(Path.of("dto", "LockedItemDTO.java")));

        assertThat(migration)
                .contains(
                        "CREATE TABLE order_delivery_ticket",
                        "source_type character varying(30) DEFAULT 'RFQ'::character varying NOT NULL",
                        "source_order_id bigint",
                        "source_item_id bigint",
                        "drawing_url character varying(500)",
                        "idx_order_delivery_ticket_source",
                        "CREATE TABLE order_delivery_ticket_allocation",
                        "source_type character varying(30)",
                        "source_order_id bigint",
                        "source_item_id bigint"
                );
        assertThat(ticket)
                .contains(
                        "private String sourceType;",
                        "private Long sourceOrderId;",
                        "private Long sourceItemId;",
                        "private String drawingUrl;"
                );
        assertThat(allocation)
                .contains(
                        "private String sourceType;",
                        "private Long sourceOrderId;",
                        "private Long sourceItemId;"
                );
        assertThat(deliveredDto)
                .contains(
                        "private String sourceType;",
                        "private Long sourceOrderId;",
                        "private Long sourceItemId;"
                );
        assertThat(lockedDto)
                .contains(
                        "private String sourceType;",
                        "private Long sourceOrderId;",
                        "private Long sourceItemId;"
                );
        assertThat(ticket + allocation + deliveredDto + lockedDto + migration)
                .doesNotContain("customer" + "Requirement", "customer" + "_requirement");
    }

    @Test
    void rfqGenerationWritesSourceIdentityForBackwardCompatibleTickets() throws Exception {
        String releaseSalesOrderService = read(MAIN_JAVA.resolve(Path.of("service", "RfqReleaseSalesOrderService.java")));

        assertThat(releaseSalesOrderService)
                .contains(
                        "ticket.setSourceType(SOURCE_RFQ)",
                        "ticket.setSourceOrderId(order.getId().longValue())",
                        "ticket.setSourceItemId(item.getId().longValue())"
                );
    }

    @Test
    void generatesSalesOrderTicketsAndRollsHeaderStatusFromPartialToReleased() {
        DeliveryService service = new DeliveryService();
        SalesOrderHeaderMapper headerMapper = mock(SalesOrderHeaderMapper.class);
        SalesOrderItemMapper itemMapper = mock(SalesOrderItemMapper.class);
        OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
        AuditService auditService = mock(AuditService.class);
        DataScopeService dataScopeService = mock(DataScopeService.class);
        LeadTimeService leadTimeService = new LeadTimeService();
        List<OrderDeliveryTicket> insertedTickets = new ArrayList<>();
        SalesOrderHeader header = salesOrderHeader();
        SalesOrderItem first = salesOrderItem(1001L, 4, 0, "SO-P-1");
        SalesOrderItem second = salesOrderItem(1002L, 3, 0, "SO-P-2");
        second.setEngineeringMaterialName(null);
        second.setMaterialData(Map.of("shape", "plate"));

        when(headerMapper.selectByIdForUpdate(9001L)).thenReturn(header);
        when(headerMapper.selectById(9001L)).thenReturn(header);
        when(itemMapper.selectByIdForUpdate(1001L)).thenReturn(first);
        when(itemMapper.selectByIdForUpdate(1002L)).thenReturn(second);
        when(itemMapper.selectList(any())).thenReturn(List.of(first, second));
        when(ticketMapper.selectList(any())).thenAnswer(invocation -> List.copyOf(insertedTickets));
        doAnswer(invocation -> {
            OrderDeliveryTicket ticket = invocation.getArgument(0);
            ticket.setId(7000 + insertedTickets.size());
            insertedTickets.add(ticket);
            return 1;
        }).when(ticketMapper).insert(any(OrderDeliveryTicket.class));

        wireDeliveryService(service, headerMapper, itemMapper, ticketMapper, auditService, dataScopeService, leadTimeService);

        List<OrderDeliveryTicket> partialTickets = service.generateTickets(salesOrderReq(1001L, 2));

        assertThat(partialTickets).hasSize(1);
        OrderDeliveryTicket partialTicket = partialTickets.get(0);
        assertThat(partialTicket.getSourceType()).isEqualTo(ReleaseSourceType.SALES_ORDER.name());
        assertThat(partialTicket.getSourceOrderId()).isEqualTo(9001L);
        assertThat(partialTicket.getSourceItemId()).isEqualTo(1001L);
        assertThat(partialTicket.getRfqId()).isNull();
        assertThat(partialTicket.getItemId()).isNull();
        assertThat(partialTicket.getCustomerName()).isEqualTo("ACME");
        assertThat(partialTicket.getRfqNo()).isEqualTo("SO-QPO-001");
        assertThat(partialTicket.getMaterialNo()).isEqualTo("MAT-1001");
        assertThat(partialTicket.getMaterialName()).isEqualTo("6061");
        assertSnapshot(partialTicket, "getEngineeringMaterialCode", "EMC-1001");
        assertSnapshot(partialTicket, "getEngineeringMaterialTypeCode", "RAW_MATERIAL");
        assertSnapshot(partialTicket, "getEngineeringMaterialTypeName", "Raw Material");
        assertSnapshot(partialTicket, "getEngineeringMaterialCategory", "ALUMINUM");
        assertThat(partialTicket.getEngineeringMaterialName()).isEqualTo("7075");
        assertThat(partialTicket.getMaterialData()).isEqualTo(Map.of("name", "7075"));
        assertThat(partialTicket.getProcessRoute()).isEqualTo(first.getProcessRoute());
        assertThat(partialTicket.getProcessRoute()).isNotSameAs(first.getProcessRoute());
        assertThat(partialTicket.getDrawingUrl()).isEqualTo("/drawings/SO-P-1.pdf");
        assertThat(partialTicket.getRemark()).isEqualTo("Sales order remark SO-P-1");
        assertThat(first.getReleasedQty()).isEqualTo(2);
        assertThat(first.getRemainingQty()).isEqualTo(2);
        assertThat(first.getStatus()).isEqualTo(QuoteStatusWorkflow.PARTIALLY_RELEASED);
        assertThat(header.getStatus()).isEqualTo(QuoteStatusWorkflow.PARTIALLY_RELEASED);

        service.generateTickets(salesOrderReq(Map.of(1001L, 2, 1002L, 3)));

        assertThat(first.getReleasedQty()).isEqualTo(4);
        assertThat(first.getRemainingQty()).isZero();
        assertThat(first.getStatus()).isEqualTo(QuoteStatusWorkflow.RELEASED);
        assertThat(second.getReleasedQty()).isEqualTo(3);
        assertThat(second.getRemainingQty()).isZero();
        assertThat(second.getStatus()).isEqualTo(QuoteStatusWorkflow.RELEASED);
        OrderDeliveryTicket fallbackTicket = insertedTickets.stream()
                .filter(ticket -> Long.valueOf(1002L).equals(ticket.getSourceItemId()))
                .findFirst()
                .orElseThrow();
        assertThat(fallbackTicket.getMaterialName()).isEqualTo("6061");
        assertThat(fallbackTicket.getEngineeringMaterialName()).isEqualTo("6061");
        assertThat(fallbackTicket.getMaterialData()).isEqualTo(Map.of("shape", "plate"));
        assertThat(header.getStatus()).isEqualTo(QuoteStatusWorkflow.RELEASED);
        verify(dataScopeService, atLeastOnce()).assertCanAccessSalesOrder(header);
        verify(auditService, atLeastOnce()).record(eq("DELIVERY_TICKET_GENERATE"), eq("sales_order_header"), eq(9001L), isNull(), any());
    }

    @Test
    void importAutoSalesOrderTicketsUseItemSnapshotsBeforeHeaderFallback() {
        DeliveryService service = new DeliveryService();
        SalesOrderHeaderMapper headerMapper = mock(SalesOrderHeaderMapper.class);
        SalesOrderItemMapper itemMapper = mock(SalesOrderItemMapper.class);
        OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
        AuditService auditService = mock(AuditService.class);
        DataScopeService dataScopeService = mock(DataScopeService.class);
        LeadTimeService leadTimeService = new LeadTimeService();
        List<OrderDeliveryTicket> insertedTickets = new ArrayList<>();
        SalesOrderHeader header = salesOrderHeader();
        header.setSalesOrderDate(LocalDate.of(2026, 7, 1));
        header.setCustomerOrderNo("A-PO");
        header.setCustomerProjectNo("A-CP");
        header.setOrderType("\u6b63\u5f0f\u8ba2\u5355");
        header.setInternalProjectNo("A-IP");
        SalesOrderItem override = salesOrderItem(2001L, 5, 0, "SO-P-OVERRIDE");
        override.setSalesOrderDate(LocalDate.of(2026, 7, 2));
        override.setCustomerOrderNo("B-PO");
        override.setCustomerProjectNo("B-CP");
        override.setCustomerOrderTitle("B-TITLE");
        override.setOrderType("\u9884\u6d4b\u8ba2\u5355");
        override.setInternalProjectNo("B-IP");
        SalesOrderItem fallback = salesOrderItem(2002L, 4, 0, "SO-P-FALLBACK");
        fallback.setSalesOrderDate(null);
        fallback.setCustomerOrderNo(null);
        fallback.setCustomerProjectNo(null);
        fallback.setCustomerOrderTitle(null);
        fallback.setOrderType(null);
        fallback.setInternalProjectNo(null);

        when(headerMapper.selectByIdForUpdate(9001L)).thenReturn(header);
        when(headerMapper.selectById(9001L)).thenReturn(header);
        when(itemMapper.selectList(any())).thenReturn(List.of(override, fallback));
        doAnswer(invocation -> {
            OrderDeliveryTicket ticket = invocation.getArgument(0);
            ticket.setId(7200 + insertedTickets.size());
            insertedTickets.add(ticket);
            return 1;
        }).when(ticketMapper).insert(any(OrderDeliveryTicket.class));

        wireDeliveryService(service, headerMapper, itemMapper, ticketMapper, auditService, dataScopeService, leadTimeService);

        List<OrderDeliveryTicket> tickets = service.generateImportedSalesOrderTickets(9001L);

        assertThat(tickets).hasSize(2);
        OrderDeliveryTicket overrideTicket = insertedTickets.stream()
                .filter(ticket -> Long.valueOf(2001L).equals(ticket.getSourceItemId()))
                .findFirst()
                .orElseThrow();
        assertThat(overrideTicket.getStartDate()).isEqualTo(LocalDate.of(2026, 7, 2));
        assertThat(overrideTicket.getCustomerOrderNo()).isEqualTo("B-PO");
        assertThat(overrideTicket.getCustomerProjectNo()).isEqualTo("B-CP");
        assertThat(overrideTicket.getOrderType()).isEqualTo("\u9884\u6d4b\u8ba2\u5355");
        assertThat(overrideTicket.getInternalProjectNo()).isEqualTo("B-IP");

        OrderDeliveryTicket fallbackTicket = insertedTickets.stream()
                .filter(ticket -> Long.valueOf(2002L).equals(ticket.getSourceItemId()))
                .findFirst()
                .orElseThrow();
        assertThat(fallbackTicket.getStartDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(fallbackTicket.getCustomerOrderNo()).isEqualTo("A-PO");
        assertThat(fallbackTicket.getCustomerProjectNo()).isEqualTo("A-CP");
        assertThat(fallbackTicket.getOrderType()).isEqualTo("\u6b63\u5f0f\u8ba2\u5355");
        assertThat(fallbackTicket.getInternalProjectNo()).isEqualTo("A-IP");
    }

    @Test
    void importAutoSalesOrderTicketsRejectsBlankEffectiveOrderIdentity() {
        DeliveryService service = new DeliveryService();
        SalesOrderHeaderMapper headerMapper = mock(SalesOrderHeaderMapper.class);
        SalesOrderItemMapper itemMapper = mock(SalesOrderItemMapper.class);
        OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
        AuditService auditService = mock(AuditService.class);
        DataScopeService dataScopeService = mock(DataScopeService.class);
        LeadTimeService leadTimeService = new LeadTimeService();
        SalesOrderHeader header = salesOrderHeader();
        header.setSalesOrderDate(null);
        header.setCustomerOrderNo(null);
        header.setCustomerProjectNo(null);
        header.setOrderType(null);
        header.setInternalProjectNo(null);
        SalesOrderItem blank = salesOrderItem(3001L, 2, 0, "SO-P-BLANK");
        blank.setSalesOrderDate(null);
        blank.setCustomerOrderNo(null);
        blank.setCustomerProjectNo(null);
        blank.setOrderType(null);
        blank.setInternalProjectNo(null);

        when(headerMapper.selectByIdForUpdate(9001L)).thenReturn(header);
        when(headerMapper.selectById(9001L)).thenReturn(header);
        when(itemMapper.selectList(any())).thenReturn(List.of(blank));

        wireDeliveryService(service, headerMapper, itemMapper, ticketMapper, auditService, dataScopeService, leadTimeService);

        assertThatThrownBy(() -> service.generateImportedSalesOrderTickets(9001L))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void importAutoSalesOrderTicketsRejectsBlankCustomerOrderNoWhenInternalProjectExists() {
        DeliveryService service = new DeliveryService();
        SalesOrderHeaderMapper headerMapper = mock(SalesOrderHeaderMapper.class);
        SalesOrderItemMapper itemMapper = mock(SalesOrderItemMapper.class);
        OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
        AuditService auditService = mock(AuditService.class);
        DataScopeService dataScopeService = mock(DataScopeService.class);
        LeadTimeService leadTimeService = new LeadTimeService();
        SalesOrderHeader header = salesOrderHeader();
        header.setSalesOrderDate(null);
        header.setCustomerOrderNo(null);
        header.setCustomerProjectNo("A-CP");
        header.setOrderType("\u6b63\u5f0f\u8ba2\u5355");
        header.setInternalProjectNo("A-IP");
        SalesOrderItem blank = salesOrderItem(3002L, 2, 0, "SO-P-BLANK-2");
        blank.setSalesOrderDate(null);
        blank.setCustomerOrderNo(null);
        blank.setCustomerProjectNo(null);
        blank.setOrderType(null);
        blank.setInternalProjectNo("A-IP");

        when(headerMapper.selectByIdForUpdate(9001L)).thenReturn(header);
        when(headerMapper.selectById(9001L)).thenReturn(header);
        when(itemMapper.selectList(any())).thenReturn(List.of(blank));

        wireDeliveryService(service, headerMapper, itemMapper, ticketMapper, auditService, dataScopeService, leadTimeService);

        assertThatThrownBy(() -> service.generateImportedSalesOrderTickets(9001L))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void importAutoSalesOrderTicketsAllowBlankPriceAndAreIdempotent() {
        DeliveryService service = new DeliveryService();
        SalesOrderHeaderMapper headerMapper = mock(SalesOrderHeaderMapper.class);
        SalesOrderItemMapper itemMapper = mock(SalesOrderItemMapper.class);
        OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
        AuditService auditService = mock(AuditService.class);
        DataScopeService dataScopeService = mock(DataScopeService.class);
        LeadTimeService leadTimeService = new LeadTimeService();
        List<OrderDeliveryTicket> insertedTickets = new ArrayList<>();
        SalesOrderHeader header = salesOrderHeader();
        SalesOrderItem blankPrice = salesOrderItem(1001L, 4, 0, "SO-P-1");
        blankPrice.setTaxIncludedUnitPrice(null);
        blankPrice.setTaxIncludedTotalPrice(null);
        SalesOrderItem priced = salesOrderItem(1002L, 3, 0, "SO-P-2");

        when(headerMapper.selectByIdForUpdate(9001L)).thenReturn(header);
        when(itemMapper.selectList(any())).thenReturn(List.of(blankPrice, priced));
        doAnswer(invocation -> {
            OrderDeliveryTicket ticket = invocation.getArgument(0);
            ticket.setId(7100 + insertedTickets.size());
            insertedTickets.add(ticket);
            return 1;
        }).when(ticketMapper).insert(any(OrderDeliveryTicket.class));

        wireDeliveryService(service, headerMapper, itemMapper, ticketMapper, auditService, dataScopeService, leadTimeService);

        List<OrderDeliveryTicket> tickets = service.generateImportedSalesOrderTickets(9001L);

        assertThat(tickets).hasSize(2);
        assertThat(insertedTickets).hasSize(2);
        assertThat(insertedTickets)
                .extracting(OrderDeliveryTicket::getSourceType, OrderDeliveryTicket::getSourceOrderId, OrderDeliveryTicket::getSourceItemId)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(ReleaseSourceType.SALES_ORDER.name(), 9001L, 1001L),
                        org.assertj.core.groups.Tuple.tuple(ReleaseSourceType.SALES_ORDER.name(), 9001L, 1002L));
        assertThat(insertedTickets).extracting(OrderDeliveryTicket::getQty).containsExactly(4, 3);
        assertThat(blankPrice.getReleasedQty()).isEqualTo(4);
        assertThat(blankPrice.getRemainingQty()).isZero();
        assertThat(blankPrice.getStatus()).isEqualTo(QuoteStatusWorkflow.RELEASED);
        assertThat(priced.getReleasedQty()).isEqualTo(3);
        assertThat(priced.getRemainingQty()).isZero();
        assertThat(header.getStatus()).isEqualTo(QuoteStatusWorkflow.RELEASED);

        List<OrderDeliveryTicket> duplicateRun = service.generateImportedSalesOrderTickets(9001L);

        assertThat(duplicateRun).isEmpty();
        assertThat(insertedTickets).hasSize(2);
        ArgumentCaptor<Object> afterDataCaptor = ArgumentCaptor.forClass(Object.class);
        verify(auditService, atLeastOnce()).record(
                eq("DELIVERY_TICKET_GENERATE"),
                eq("sales_order_header"),
                eq(9001L),
                isNull(),
                afterDataCaptor.capture());
        assertThat(afterDataCaptor.getAllValues()).anySatisfy(afterData -> assertThat((Map<String, Object>) afterData)
                .containsEntry("trigger", "SALES_ORDER_IMPORT_AUTO")
                .containsEntry("ticketCount", 2)
                .containsEntry("sourceType", ReleaseSourceType.SALES_ORDER.name()));
    }

    @Test
    void importAutoSalesOrderTicketsDeepCopyFinalSalesOrderSnapshotsAndCountRoutes() {
        DeliveryService service = new DeliveryService();
        SalesOrderHeaderMapper headerMapper = mock(SalesOrderHeaderMapper.class);
        SalesOrderItemMapper itemMapper = mock(SalesOrderItemMapper.class);
        OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
        AuditService auditService = mock(AuditService.class);
        DataScopeService dataScopeService = mock(DataScopeService.class);
        LeadTimeService leadTimeService = new LeadTimeService();
        List<OrderDeliveryTicket> insertedTickets = new ArrayList<>();
        SalesOrderHeader header = salesOrderHeader();
        SalesOrderItem confirmed = salesOrderItem(4001L, 2, 0, "SO-P-SNAP-1");
        Map<String, Object> materialSnapshot = mutableMaterialSnapshot("7075-T651");
        List<Map<String, Object>> routeSnapshot = mutableProcessRouteSnapshot("MILLING");
        confirmed.setMaterialData(materialSnapshot);
        confirmed.setProcessRoute(routeSnapshot);
        SalesOrderItem unconfirmed = salesOrderItem(4002L, 1, 0, "SO-P-SNAP-2");
        unconfirmed.setMaterialNo("MAT-UNCONFIRMED");
        unconfirmed.setMaterialNoStatus("UNCONFIRMED");
        unconfirmed.setMaterialData(mutableMaterialSnapshot("6061-T6"));
        unconfirmed.setProcessRoute(mutableProcessRouteSnapshot("TURNING"));

        when(headerMapper.selectByIdForUpdate(9001L)).thenReturn(header);
        when(headerMapper.selectById(9001L)).thenReturn(header);
        when(itemMapper.selectList(any())).thenReturn(List.of(confirmed, unconfirmed));
        doAnswer(invocation -> {
            OrderDeliveryTicket ticket = invocation.getArgument(0);
            ticket.setId(7300 + insertedTickets.size());
            insertedTickets.add(ticket);
            return 1;
        }).when(ticketMapper).insert(any(OrderDeliveryTicket.class));

        wireDeliveryService(service, headerMapper, itemMapper, ticketMapper, auditService, dataScopeService, leadTimeService);

        List<OrderDeliveryTicket> tickets = service.generateImportedSalesOrderTickets(9001L);

        assertThat(tickets).hasSize(2);
        OrderDeliveryTicket confirmedTicket = insertedTickets.stream()
                .filter(ticket -> Long.valueOf(4001L).equals(ticket.getSourceItemId()))
                .findFirst()
                .orElseThrow();
        assertThat(confirmedTicket.getMaterialData()).isEqualTo(materialSnapshot).isNotSameAs(materialSnapshot);
        assertThat(confirmedTicket.getProcessRoute()).isEqualTo(routeSnapshot).isNotSameAs(routeSnapshot);
        assertThat(confirmedTicket.getMesSyncStatus()).isEqualTo("SUCCESS");
        assertThat(confirmedTicket.getMaterialNo()).isEqualTo("MAT-4001");
        @SuppressWarnings("unchecked")
        Map<String, Object> copiedMaterial = (Map<String, Object>) confirmedTicket.getMaterialData();
        @SuppressWarnings("unchecked")
        List<String> copiedTags = (List<String>) copiedMaterial.get("tags");
        copiedTags.add("ticket-only");
        @SuppressWarnings("unchecked")
        List<String> sourceTags = (List<String>) materialSnapshot.get("tags");
        assertThat(sourceTags).containsExactly("source-tag");

        OrderDeliveryTicket pendingTicket = insertedTickets.stream()
                .filter(ticket -> Long.valueOf(4002L).equals(ticket.getSourceItemId()))
                .findFirst()
                .orElseThrow();
        assertThat(pendingTicket.getMesSyncStatus()).isEqualTo("PENDING");
        assertThat(pendingTicket.getMaterialNo()).isNull();
        assertThat(pendingTicket.getProcessRoute()).isNotSameAs(unconfirmed.getProcessRoute());

        ArgumentCaptor<Object> afterDataCaptor = ArgumentCaptor.forClass(Object.class);
        verify(auditService, atLeastOnce()).record(
                eq("DELIVERY_TICKET_GENERATE"),
                eq("sales_order_header"),
                eq(9001L),
                isNull(),
                afterDataCaptor.capture());
        assertThat(afterDataCaptor.getAllValues()).anySatisfy(afterData -> assertThat((Map<String, Object>) afterData)
                .containsEntry("trigger", "SALES_ORDER_IMPORT_AUTO")
                .containsEntry("ticketCount", 2)
                .containsEntry("processRouteCount", 2));
    }


    @Test
    void downstreamServicesUseTicketSnapshotsAndSourceAwareDataScope() throws Exception {
        String deliveredService = read(MAIN_JAVA.resolve(Path.of("service", "DeliveredItemService.java")));
        String lockedService = read(MAIN_JAVA.resolve(Path.of("service", "LockedItemService.java")));
        String ticketMapper = read(MAIN_JAVA.resolve(Path.of("mapper", "OrderDeliveryTicketMapper.java")));
        String allocationMapper = read(MAIN_JAVA.resolve(Path.of("mapper", "OrderDeliveryTicketAllocationMapper.java")));
        String dataScope = read(MAIN_JAVA.resolve(Path.of("service", "DataScopeService.java")));

        assertThat(deliveredService)
                .contains("dataScopeService.applyDeliveryTicketScope(query, \"ticket\")")
                .contains("dataScopeService.assertCanAccessDeliveryTicket");
        assertThat(lockedService)
                .contains("dataScopeService.applyDeliveryTicketScope(query, \"ticket\")");
        assertThat(ticketMapper)
                .contains(
                        "ticket.source_type",
                        "ticket.source_order_id",
                        "ticket.source_item_id",
                        "LEFT JOIN sales_order_item direct_sales_item",
                        "LEFT JOIN sales_order_item rfq_sales_item ON rfq_sales_item.source_ticket_id = ticket.id",
                        "COALESCE(ticket.drawing_url, direct_sales_item.drawing_url, rfq_sales_item.drawing_url, item.drawing_url) AS drawing_url"
                );
        assertThat(allocationMapper)
                .contains(
                        "alloc.source_type",
                        "alloc.source_order_id",
                        "alloc.source_item_id"
                );
        assertThat(dataScope)
                .contains(
                        "applyDeliveryTicketScope",
                        "assertCanAccessDeliveryTicket",
                        "assertCanAccessSalesOrder",
                        "sales_order_header"
                );
    }

    @Test
    void mesAndWmsPropagateSalesOrderSourceAndMaterialNumber() throws Exception {
        String mesService = read(MAIN_JAVA.resolve(Path.of("service", "mes", "MesMaterialSyncService.java")));
        String sourceItemService = read(MAIN_JAVA.resolve(Path.of("service", "SourceItemMaterialNoSyncService.java")));
        String wmsService = read(MAIN_JAVA.resolve(Path.of("service", "wms", "WmsReservationStateService.java")));
        String taskService = read(MAIN_JAVA.resolve(Path.of("service", "SystemTaskService.java")));
        String batchService = read(MAIN_JAVA.resolve(Path.of("service", "DeliveryIntegrationBatchService.java")));

        assertThat(mesService)
                .contains(
                        "SourceItemMaterialNoSyncService",
                        "sourceItemMaterialNoSyncService",
                        "syncSourceItemMaterialNo(ticket, materialNo, syncMessage)",
                        "return sourceItemMaterialNoSyncService.syncConfirmedMaterialNo(ticket, materialNo, message)",
                        "SalesOrderItemMapper",
                        "salesOrderItemMapper"
                );
        assertThat(sourceItemService)
                .contains(
                        "SalesOrderItemMapper",
                        "syncSalesOrderItem",
                        "update.setMaterialNoStatus(MATERIAL_CONFIRMED)",
                        "update.setMaterialNoSource(MATERIAL_SOURCE_MES_TICKET)",
                        "update.setMaterialNoSourceTicketId(sourceTicketId)",
                        "selectBySourceTicketIdForUpdate",
                        "ReleaseSourceType.SALES_ORDER.name().equals(sourceType)",
                        "salesOrderItemMapper.updateById(update)"
                );
        assertThat(wmsService)
                .contains(
                        "row.setSourceType(ticket.getSourceType())",
                        "row.setSourceOrderId(ticket.getSourceOrderId())",
                        "row.setSourceItemId(ticket.getSourceItemId())"
                );
        assertThat(taskService)
                .contains("deliveryIntegrationBatchService.assertCanAccessTickets(normalizedTicketIds)");
        assertThat(batchService)
                .contains("dataScopeService.assertCanAccessDeliveryTicket(ticket)");
    }

    private void wireDeliveryService(
            DeliveryService service,
            SalesOrderHeaderMapper headerMapper,
            SalesOrderItemMapper itemMapper,
            OrderDeliveryTicketMapper ticketMapper,
            AuditService auditService,
            DataScopeService dataScopeService,
            LeadTimeService leadTimeService) {
        ReflectionTestUtils.setField(service, "salesOrderHeaderMapper", headerMapper);
        ReflectionTestUtils.setField(service, "salesOrderItemMapper", itemMapper);
        ReflectionTestUtils.setField(service, "ticketMapper", ticketMapper);
        ReflectionTestUtils.setField(service, "auditService", auditService);
        ReflectionTestUtils.setField(service, "dataScopeService", dataScopeService);
        ReflectionTestUtils.setField(service, "leadTimeService", leadTimeService);
    }


    private DeliveryGenerateReq salesOrderReq(Long sourceItemId, int acceptedQty) {
        return salesOrderReq(Map.of(sourceItemId, acceptedQty));
    }

    private DeliveryGenerateReq salesOrderReq(Map<Long, Integer> itemQtyById) {
        DeliveryGenerateReq req = new DeliveryGenerateReq();
        req.setOrderType("\u6b63\u5f0f\u8ba2\u5355");
        req.setInternalProjectNo("IP-SO-1");
        req.setCustomerOrderNo("CO-SO-1");
        req.setCustomerProjectNo("CP-SO-1");
        req.setSalesOrderDate(LocalDate.of(2026, 8, 1));
        req.setArrangeType("\u8ba1\u5212");
        req.setItems(itemQtyById.entrySet().stream()
                .map(entry -> {
                    DeliveryGenerateReq.DeliveryItemReq item = new DeliveryGenerateReq.DeliveryItemReq();
                    item.setSourceType(ReleaseSourceType.SALES_ORDER);
                    item.setSourceOrderId(9001L);
                    item.setSourceItemId(entry.getKey());
                    item.setAcceptedQty(entry.getValue());
                    return item;
                })
                .toList());
        return req;
    }

    private SalesOrderHeader salesOrderHeader() {
        SalesOrderHeader header = new SalesOrderHeader();
        header.setId(9001L);
        header.setSalesOrderNo("SO-QPO-001");
        header.setCustomerName("ACME");
        header.setOrderType("\u6b63\u5f0f\u8ba2\u5355");
        header.setInternalProjectNo("IP-SO-1");
        header.setCustomerOrderNo("CO-SO-1");
        header.setCustomerProjectNo("CP-SO-1");
        header.setStatus(QuoteStatusWorkflow.QUOTED);
        return header;
    }

    private SalesOrderItem salesOrderItem(Long id, int qty, int releasedQty, String partNo) {
        SalesOrderItem item = new SalesOrderItem();
        item.setId(id);
        item.setSalesOrderId(9001L);
        item.setPartNo(partNo);
        item.setVersion("A");
        item.setDescription("Sales order item " + partNo);
        item.setQty(qty);
        item.setReleasedQty(releasedQty);
        item.setRemainingQty(qty - releasedQty);
        item.setMaterialNo("MAT-" + id);
        item.setMaterialNoStatus("\u5df2\u786e\u8ba4");
        item.setDrawingUrl("/drawings/" + partNo + ".pdf");
        item.setMaterialName("6061");
        item.setEngineeringMaterialCode("EMC-" + id);
        item.setEngineeringMaterialTypeCode("RAW_MATERIAL");
        item.setEngineeringMaterialTypeName("Raw Material");
        item.setEngineeringMaterialCategory("ALUMINUM");
        item.setEngineeringMaterialName("7075");
        item.setSurfaceTreatment("Anodized");
        item.setTaxIncludedUnitPrice(BigDecimal.valueOf(12.34));
        item.setTaxIncludedTotalPrice(BigDecimal.valueOf(12.34 * qty));
        item.setInternalDeliveryDate(LocalDate.of(2026, 8, 15));
        item.setProcessRoute(List.of(Map.ofEntries(
                Map.entry("routeType", "PROCESS"),
                Map.entry("sourceType", "process"),
                Map.entry("sortNo", 10),
                Map.entry("processCategoryId", 3L),
                Map.entry("processDetailId", 7L),
                Map.entry("processCode", "PACKAGING"),
                Map.entry("categoryName", "Packaging"),
                Map.entry("detailName", "Fine packaging"),
                Map.entry("hourlyRate", 10.0),
                Map.entry("unitWorkHours", 1.5),
                Map.entry("lineCost", 15.0))));
        item.setMaterialData(Map.of("name", "7075"));
        item.setMaterialGrossWeight(2.4);
        item.setPartNetWeight(1.8);
        item.setItemType("PART");
        item.setRemark("Sales order remark " + partNo);
        item.setStatus(QuoteStatusWorkflow.QUOTED);
        return item;
    }


    private Map<String, Object> mutableMaterialSnapshot(String name) {
        Map<String, Object> material = new LinkedHashMap<>();
        material.put("name", name);
        material.put("shape", "plate");
        material.put("tags", new ArrayList<>(List.of("source-tag")));
        return material;
    }

    private List<Map<String, Object>> mutableProcessRouteSnapshot(String processCode) {
        Map<String, Object> route = new LinkedHashMap<>();
        route.put("routeType", "PROCESS");
        route.put("sourceType", "process");
        route.put("sortNo", 10);
        route.put("processCode", processCode);
        route.put("categoryName", "machining");
        route.put("detailName", "cnc");
        route.put("hourlyRate", 12.0);
        route.put("unitWorkHours", 1.0);
        route.put("lineCost", 12.0);
        return new ArrayList<>(List.of(route));
    }

    private void assertSnapshot(Object row, String getterName, Object expectedValue) {
        try {
            Object actual = row.getClass().getMethod(getterName).invoke(row);
            assertThat(actual).isEqualTo(expectedValue);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Snapshot getter missing or inaccessible: " + getterName, error);
        }
    }

    private String read(Path path) throws Exception {
        assertThat(Files.exists(path))
                .as("%s should exist", path)
                .isTrue();
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
