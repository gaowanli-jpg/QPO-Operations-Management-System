package com.machining.quote;

import com.machining.quote.config.AuthContext;
import com.machining.quote.controller.SalesOrderQpoImportController;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.ReleaseSourceType;
import com.machining.quote.dto.SalesOrderQpoExcelLineDTO;
import com.machining.quote.dto.SalesOrderQpoExcelParseResult;
import com.machining.quote.dto.SalesOrderQpoImportResp;
import com.machining.quote.dto.SalesOrderQpoOrderGroupDTO;
import com.machining.quote.dto.SalesOrderAutoSnapshotCompletionResult;
import com.machining.quote.entity.CustomerMaster;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderImportBatch;
import com.machining.quote.entity.SalesOrderImportRecord;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.CustomerMasterMapper;
import com.machining.quote.mapper.ItemAssemblyRelationMapper;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderImportBatchMapper;
import com.machining.quote.mapper.SalesOrderImportRecordMapper;
import com.machining.quote.mapper.SalesOrderItemAssemblyRelationMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.BusinessFileService;
import com.machining.quote.service.DataScopeService;
import com.machining.quote.service.DeliveryAutoMesMaterialSyncService;
import com.machining.quote.service.DeliveryService;
import com.machining.quote.service.SalesOrderQpoExcelParser;
import com.machining.quote.service.SalesOrderQpoImportService;
import com.machining.quote.service.SalesOrderAutoSnapshotCompletionService;
import com.machining.quote.service.SalesOrderNumberService;
import com.machining.quote.storage.StorageType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Answers;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesOrderQpoImportAutoDeliveryWorkflowTest extends SalesOrderQpoImportTestSupport {

    @Test
    void serviceRunsAutomaticDeliveryTicketGenerationAfterImportAutoMatchAndAuditsTrigger() throws Exception {
        SalesOrderQpoExcelParser parser = mock(SalesOrderQpoExcelParser.class);
        CustomerMasterMapper customerMapper = mock(CustomerMasterMapper.class);
        SalesOrderImportBatchMapper batchMapper = mock(SalesOrderImportBatchMapper.class);
        SalesOrderImportRecordMapper recordMapper = mock(SalesOrderImportRecordMapper.class);
        SalesOrderHeaderMapper headerMapper = mock(SalesOrderHeaderMapper.class);
        SalesOrderItemMapper itemMapper = mock(SalesOrderItemMapper.class);
        SalesOrderAutoSnapshotCompletionService matchService = mock(SalesOrderAutoSnapshotCompletionService.class);
        DeliveryService deliveryService = mock(DeliveryService.class);
        DeliveryAutoMesMaterialSyncService autoMesMaterialSyncService = mock(DeliveryAutoMesMaterialSyncService.class);
        AuditService auditService = mock(AuditService.class);
        SalesOrderQpoImportService service = service(
                parser,
                customerMapper,
                batchMapper,
                recordMapper,
                headerMapper,
                itemMapper,
                mock(DataScopeService.class),
                auditService,
                matchService,
                deliveryService
        );
        ReflectionTestUtils.setField(service, "autoMesMaterialSyncService", autoMesMaterialSyncService);
        when(customerMapper.selectById(7L)).thenReturn(customer());
        when(parser.parse(any(InputStream.class))).thenReturn(parseResultWithBlankPrices());
        when(headerMapper.selectCount(any())).thenReturn(0L);
        doAnswer(invocation -> {
            SalesOrderImportBatch batch = invocation.getArgument(0);
            batch.setId(201L);
            return 1;
        }).when(batchMapper).insert(any(SalesOrderImportBatch.class));
        doAnswer(invocation -> {
            SalesOrderHeader header = invocation.getArgument(0);
            header.setId(301L);
            return 1;
        }).when(headerMapper).insert(any(SalesOrderHeader.class));
        doAnswer(invocation -> {
            SalesOrderImportRecord record = invocation.getArgument(0);
            record.setId(401L);
            return 1;
        }).when(recordMapper).insert(any(SalesOrderImportRecord.class));
        doAnswer(invocation -> {
            SalesOrderItem item = invocation.getArgument(0);
            item.setId("P-BLANK".equals(item.getPartNo()) ? 501L : 502L);
            return 1;
        }).when(itemMapper).insert(any(SalesOrderItem.class));
        when(matchService.completeImportedOrder(301L)).thenReturn(matchResp(2, 1, 1, "auto unmatched 1"));
        OrderDeliveryTicket ticket = new OrderDeliveryTicket();
        ticket.setId(9001);
        ticket.setSourceOrderId(301L);
        when(deliveryService.generateImportedSalesOrderTickets(301L)).thenReturn(List.of(ticket));

        SalesOrderQpoImportResp resp = service.importQpo(
                new MockMultipartFile(
                        "excelFile",
                        "sales-order.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "xlsx".getBytes(StandardCharsets.UTF_8)
                ),
                7L,
                "SO01"
        );

        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(itemMapper, matchService, deliveryService);
        inOrder.verify(itemMapper, org.mockito.Mockito.times(2)).insert(any(SalesOrderItem.class));
        inOrder.verify(matchService).completeImportedOrder(301L);
        inOrder.verify(deliveryService).generateImportedSalesOrderTickets(301L);
        verify(autoMesMaterialSyncService).submitAfterCommit(
                eq(ReleaseSourceType.SALES_ORDER),
                eq(301L),
                eq(List.of(9001)),
                eq("SALES_ORDER_IMPORT"));
        assertThat(resp.isSuccess()).isTrue();
        assertThat(resp.getRows().get(0).getMatchedItemCount()).isEqualTo(1);
        assertThat(resp.getRows().get(0).getUnmatchedItemCount()).isEqualTo(1);

        ArgumentCaptor<Object> afterDataCaptor = ArgumentCaptor.forClass(Object.class);
        verify(auditService).record(
                eq("SALES_ORDER_IMPORT_QPO"),
                eq("sales_order_import_batch"),
                eq(201L),
                isNull(),
                afterDataCaptor.capture(),
                eq("MARKET"),
                eq("SUCCESS"),
                any(String.class),
                isNull()
        );
        assertThat((Map<String, Object>) afterDataCaptor.getValue())
                .containsEntry("autoMatchTrigger", "IMPORT_AUTO")
                .containsEntry("autoDeliveryTrigger", "SALES_ORDER_IMPORT_AUTO")
                .containsEntry("autoDeliveryTicketCount", 1);
    }

    @Test
    void autoMesSubmitFailureDoesNotAffectSalesOrderImportAutoDeliveryResult() throws Exception {
        SalesOrderQpoExcelParser parser = mock(SalesOrderQpoExcelParser.class);
        CustomerMasterMapper customerMapper = mock(CustomerMasterMapper.class);
        SalesOrderImportBatchMapper batchMapper = mock(SalesOrderImportBatchMapper.class);
        SalesOrderImportRecordMapper recordMapper = mock(SalesOrderImportRecordMapper.class);
        SalesOrderHeaderMapper headerMapper = mock(SalesOrderHeaderMapper.class);
        SalesOrderItemMapper itemMapper = mock(SalesOrderItemMapper.class);
        SalesOrderAutoSnapshotCompletionService matchService = mock(SalesOrderAutoSnapshotCompletionService.class);
        DeliveryService deliveryService = mock(DeliveryService.class);
        DeliveryAutoMesMaterialSyncService autoMesMaterialSyncService = mock(DeliveryAutoMesMaterialSyncService.class);
        AuditService auditService = mock(AuditService.class);
        SalesOrderQpoImportService service = service(
                parser,
                customerMapper,
                batchMapper,
                recordMapper,
                headerMapper,
                itemMapper,
                mock(DataScopeService.class),
                auditService,
                matchService,
                deliveryService
        );
        ReflectionTestUtils.setField(service, "autoMesMaterialSyncService", autoMesMaterialSyncService);
        when(customerMapper.selectById(7L)).thenReturn(customer());
        when(parser.parse(any(InputStream.class))).thenReturn(parseResultWithBlankPrices());
        when(headerMapper.selectCount(any())).thenReturn(0L);
        doAnswer(invocation -> {
            SalesOrderImportBatch batch = invocation.getArgument(0);
            batch.setId(201L);
            return 1;
        }).when(batchMapper).insert(any(SalesOrderImportBatch.class));
        doAnswer(invocation -> {
            SalesOrderHeader header = invocation.getArgument(0);
            header.setId(301L);
            return 1;
        }).when(headerMapper).insert(any(SalesOrderHeader.class));
        doAnswer(invocation -> {
            SalesOrderImportRecord record = invocation.getArgument(0);
            record.setId(401L);
            return 1;
        }).when(recordMapper).insert(any(SalesOrderImportRecord.class));
        doAnswer(invocation -> {
            SalesOrderItem item = invocation.getArgument(0);
            item.setId("P-BLANK".equals(item.getPartNo()) ? 501L : 502L);
            return 1;
        }).when(itemMapper).insert(any(SalesOrderItem.class));
        when(matchService.completeImportedOrder(301L)).thenReturn(matchResp(2, 1, 1, "auto unmatched 1"));
        OrderDeliveryTicket ticket = new OrderDeliveryTicket();
        ticket.setId(9001);
        ticket.setSourceOrderId(301L);
        when(deliveryService.generateImportedSalesOrderTickets(301L)).thenReturn(List.of(ticket));
        doThrow(new RuntimeException("auto mes submit failed"))
                .when(autoMesMaterialSyncService)
                .submitAfterCommit(eq(ReleaseSourceType.SALES_ORDER), eq(301L), eq(List.of(9001)), eq("SALES_ORDER_IMPORT"));

        SalesOrderQpoImportResp resp = service.importQpo(
                new MockMultipartFile(
                        "excelFile",
                        "sales-order.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "xlsx".getBytes(StandardCharsets.UTF_8)
                ),
                7L,
                "SO01"
        );

        assertThat(resp.isSuccess()).isTrue();
        assertThat(resp.getRows()).singleElement().satisfies(row -> {
            assertThat(row.getAutoDeliveryTicketCount()).isEqualTo(1);
            assertThat(row.getMatchedItemCount()).isEqualTo(1);
            assertThat(row.getUnmatchedItemCount()).isEqualTo(1);
        });
        verify(autoMesMaterialSyncService).submitAfterCommit(
                eq(ReleaseSourceType.SALES_ORDER),
                eq(301L),
                eq(List.of(9001)),
                eq("SALES_ORDER_IMPORT"));
    }

    @Test
    void importWithMultipleSalesOrdersSubmitsAutoMesOncePerOrderTicketBatch() throws Exception {
        SalesOrderQpoExcelParser parser = mock(SalesOrderQpoExcelParser.class);
        CustomerMasterMapper customerMapper = mock(CustomerMasterMapper.class);
        SalesOrderImportBatchMapper batchMapper = mock(SalesOrderImportBatchMapper.class);
        SalesOrderImportRecordMapper recordMapper = mock(SalesOrderImportRecordMapper.class);
        SalesOrderHeaderMapper headerMapper = mock(SalesOrderHeaderMapper.class);
        SalesOrderItemMapper itemMapper = mock(SalesOrderItemMapper.class);
        SalesOrderAutoSnapshotCompletionService matchService = mock(SalesOrderAutoSnapshotCompletionService.class);
        DeliveryService deliveryService = mock(DeliveryService.class);
        DeliveryAutoMesMaterialSyncService autoMesMaterialSyncService = mock(DeliveryAutoMesMaterialSyncService.class);
        AuditService auditService = mock(AuditService.class);
        SalesOrderQpoImportService service = service(
                parser,
                customerMapper,
                batchMapper,
                recordMapper,
                headerMapper,
                itemMapper,
                mock(DataScopeService.class),
                auditService,
                matchService,
                deliveryService
        );
        ReflectionTestUtils.setField(service, "autoMesMaterialSyncService", autoMesMaterialSyncService);
        when(customerMapper.selectById(7L)).thenReturn(customer());
        when(parser.parse(any(InputStream.class))).thenReturn(parseResult());
        when(headerMapper.selectCount(any())).thenReturn(0L);
        doAnswer(invocation -> {
            SalesOrderImportBatch batch = invocation.getArgument(0);
            batch.setId(201L);
            return 1;
        }).when(batchMapper).insert(any(SalesOrderImportBatch.class));
        doAnswer(invocation -> {
            SalesOrderHeader header = invocation.getArgument(0);
            header.setId("PO-001".equals(header.getCustomerOrderNo()) ? 301L : 302L);
            return 1;
        }).when(headerMapper).insert(any(SalesOrderHeader.class));
        doAnswer(invocation -> {
            SalesOrderImportRecord record = invocation.getArgument(0);
            record.setId("PO-001".equals(record.getCustomerOrderNo()) ? 401L : 402L);
            return 1;
        }).when(recordMapper).insert(any(SalesOrderImportRecord.class));
        doAnswer(invocation -> {
            SalesOrderItem item = invocation.getArgument(0);
            item.setId(item.getPartNo().equals("P-001") ? 501L : item.getPartNo().equals("P-002") ? 502L : 503L);
            return 1;
        }).when(itemMapper).insert(any(SalesOrderItem.class));
        when(matchService.completeImportedOrder(301L)).thenReturn(matchResp(2, 2, 0, ""));
        when(matchService.completeImportedOrder(302L)).thenReturn(matchResp(1, 1, 0, ""));
        when(deliveryService.generateImportedSalesOrderTickets(301L)).thenReturn(List.of(ticket(9101, 301L), ticket(9102, 301L)));
        when(deliveryService.generateImportedSalesOrderTickets(302L)).thenReturn(List.of(ticket(9201, 302L)));

        SalesOrderQpoImportResp resp = service.importQpo(
                new MockMultipartFile(
                        "excelFile",
                        "sales-order.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "xlsx".getBytes(StandardCharsets.UTF_8)
                ),
                7L,
                "SO01"
        );

        assertThat(resp.isSuccess()).isTrue();
        assertThat(resp.getRows()).hasSize(2);
        verify(autoMesMaterialSyncService).submitAfterCommit(
                eq(ReleaseSourceType.SALES_ORDER),
                eq(301L),
                eq(List.of(9101, 9102)),
                eq("SALES_ORDER_IMPORT"));
        verify(autoMesMaterialSyncService).submitAfterCommit(
                eq(ReleaseSourceType.SALES_ORDER),
                eq(302L),
                eq(List.of(9201)),
                eq("SALES_ORDER_IMPORT"));
        verify(autoMesMaterialSyncService, times(2)).submitAfterCommit(any(), any(), any(), any());
    }

    @Test
    void serviceRunsParseAutoMatchAndAutoDeliveryInOneChainWithSnapshotsAndImportedFields() throws Exception {
        SalesOrderQpoExcelParser parser = mock(SalesOrderQpoExcelParser.class);
        CustomerMasterMapper customerMapper = mock(CustomerMasterMapper.class);
        SalesOrderImportBatchMapper batchMapper = mock(SalesOrderImportBatchMapper.class);
        SalesOrderImportRecordMapper recordMapper = mock(SalesOrderImportRecordMapper.class);
        SalesOrderHeaderMapper headerMapper = mock(SalesOrderHeaderMapper.class);
        SalesOrderItemMapper itemMapper = mock(SalesOrderItemMapper.class);
        ItemQuoteVersionMapper sourceMapper = mock(ItemQuoteVersionMapper.class);
        ItemAssemblyRelationMapper historyRelationMapper = mock(ItemAssemblyRelationMapper.class);
        SalesOrderItemAssemblyRelationMapper salesOrderRelationMapper = mock(SalesOrderItemAssemblyRelationMapper.class);
        OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
        AuditService auditService = mock(AuditService.class);
        DataScopeService dataScopeService = mock(DataScopeService.class);
        SalesOrderAutoSnapshotCompletionService matchService = new SalesOrderAutoSnapshotCompletionService();
        DeliveryService deliveryService = new DeliveryService();
        SalesOrderQpoImportService service = service(
                parser,
                customerMapper,
                batchMapper,
                recordMapper,
                headerMapper,
                itemMapper,
                dataScopeService,
                auditService,
                matchService,
                deliveryService
        );
        ReflectionTestUtils.setField(service, "businessFileService", salesOrderImportDrawingStorage());
        wireSalesOrderAutoSnapshotCompletionService(matchService, headerMapper, itemMapper, sourceMapper,
                historyRelationMapper, salesOrderRelationMapper);
        List<OrderDeliveryTicket> insertedTickets = new ArrayList<>();
        wireImportAutoDeliveryService(deliveryService, headerMapper, itemMapper, ticketMapper, auditService, insertedTickets);

        Map<Long, SalesOrderHeader> headers = new LinkedHashMap<>();
        Map<Long, SalesOrderItem> items = new LinkedHashMap<>();
        Map<Long, SalesOrderImportRecord> records = new LinkedHashMap<>();
        long[] nextHeaderId = {301L};
        long[] nextItemId = {501L};
        long[] nextRecordId = {401L};

        when(customerMapper.selectById(7L)).thenReturn(customer());
        when(parser.parse(any(InputStream.class))).thenReturn(parseResultWithMatchedUnmatchedImportFacts());
        when(headerMapper.selectCount(any())).thenReturn(0L);
        doAnswer(invocation -> {
            SalesOrderImportBatch batch = invocation.getArgument(0);
            batch.setId(201L);
            return 1;
        }).when(batchMapper).insert(any(SalesOrderImportBatch.class));
        doAnswer(invocation -> {
            SalesOrderHeader header = invocation.getArgument(0);
            header.setId(nextHeaderId[0]++);
            headers.put(header.getId(), header);
            return 1;
        }).when(headerMapper).insert(any(SalesOrderHeader.class));
        doAnswer(invocation -> {
            SalesOrderHeader update = invocation.getArgument(0);
            mergeNonNull(headers.get(update.getId()), update);
            return 1;
        }).when(headerMapper).updateById(any(SalesOrderHeader.class));
        when(headerMapper.selectById(any())).thenAnswer(invocation -> headers.get(longValue(invocation.getArgument(0))));
        when(headerMapper.selectByIdForUpdate(any())).thenAnswer(invocation -> headers.get(longValue(invocation.getArgument(0))));
        doAnswer(invocation -> {
            SalesOrderImportRecord record = invocation.getArgument(0);
            record.setId(nextRecordId[0]++);
            records.put(record.getId(), record);
            return 1;
        }).when(recordMapper).insert(any(SalesOrderImportRecord.class));
        doAnswer(invocation -> {
            SalesOrderImportRecord update = invocation.getArgument(0);
            mergeNonNull(records.get(update.getId()), update);
            return 1;
        }).when(recordMapper).updateById(any(SalesOrderImportRecord.class));
        doAnswer(invocation -> {
            SalesOrderItem item = invocation.getArgument(0);
            item.setId(nextItemId[0]++);
            items.put(item.getId(), item);
            return 1;
        }).when(itemMapper).insert(any(SalesOrderItem.class));
        doAnswer(invocation -> {
            SalesOrderItem update = invocation.getArgument(0);
            mergeNonNull(items.get(update.getId()), update);
            return 1;
        }).when(itemMapper).updateById(any(SalesOrderItem.class));
        when(itemMapper.selectByIdForUpdate(any())).thenAnswer(invocation -> items.get(longValue(invocation.getArgument(0))));
        when(itemMapper.selectList(any())).thenAnswer(invocation -> sortedItems(items));
        ItemQuoteVersion releasedSource = releasedProductionSnapshotSource();
        when(sourceMapper.selectLatestReleasedRepeatSources(isNull(), any())).thenReturn(List.of(releasedSource));
        when(historyRelationMapper.selectList(any())).thenReturn(List.of());

        SalesOrderQpoImportResp resp = service.importQpo(
                new MockMultipartFile(
                        "excelFile",
                        "sales-order.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "xlsx".getBytes(StandardCharsets.UTF_8)
                ),
                7L,
                "SO01",
                List.of(new MockMultipartFile("drawings", "P-MATCH.pdf", "application/pdf", "pdf".getBytes(StandardCharsets.UTF_8)))
        );

        assertThat(resp.isSuccess()).isTrue();
        assertThat(resp.getRows()).singleElement().satisfies(row -> {
            assertThat(row.getMatchedItemCount()).isEqualTo(1);
            assertThat(row.getUnmatchedItemCount()).isEqualTo(1);
            assertThat(row.getMissingPriceCount()).isEqualTo(1);
            assertThat(row.getAutoDeliveryTicketCount()).isEqualTo(2);
            assertThat(row.getWarningMessage()).contains("1");
        });
        assertThat(insertedTickets).hasSize(2);

        SalesOrderItem matchedItem = items.values().stream()
                .filter(item -> "P-MATCH".equals(item.getPartNo()))
                .findFirst()
                .orElseThrow();
        assertThat(matchedItem.getMaterialData()).isEqualTo(releasedSource.getMaterialData()).isNotSameAs(releasedSource.getMaterialData());
        assertThat(matchedItem.getProcessRoute()).isEqualTo(releasedSource.getProcessRoute()).isNotSameAs(releasedSource.getProcessRoute());
        assertThat(matchedItem.getFeeData()).isEqualTo(releasedSource.getFeeData()).isNotSameAs(releasedSource.getFeeData());
        assertThat(matchedItem.getMatchStatus()).isEqualTo("已匹配");
        assertThat(matchedItem.getMaterialNo()).isEqualTo("MAT-HIST-001");
        assertThat(matchedItem.getStatus()).isEqualTo(QuoteStatusWorkflow.RELEASED);

        OrderDeliveryTicket matchedTicket = insertedTickets.stream()
                .filter(ticket -> Long.valueOf(matchedItem.getId()).equals(ticket.getSourceItemId()))
                .findFirst()
                .orElseThrow();
        assertThat(matchedTicket.getSourceType()).isEqualTo(ReleaseSourceType.SALES_ORDER.name());
        assertThat(matchedTicket.getDescription()).isEqualTo("imported matched description");
        assertThat(matchedTicket.getMaterialName()).isEqualTo("imported material");
        assertThat(matchedTicket.getSurfaceTreatment()).isEqualTo("imported surface");
        assertThat(matchedTicket.getRemark()).isEqualTo("imported matched remark");
        assertThat(matchedTicket.getDrawingUrl()).isEqualTo("/uploads/ACME/SOQPO/P-MATCH.pdf");
        assertThat(matchedTicket.getDeliveryDate()).isEqualTo(LocalDate.of(2026, 9, 18));
        assertThat(matchedTicket.getMaterialData()).isEqualTo(matchedItem.getMaterialData()).isNotSameAs(matchedItem.getMaterialData());
        assertThat(matchedTicket.getProcessRoute()).isEqualTo(matchedItem.getProcessRoute()).isNotSameAs(matchedItem.getProcessRoute());
        assertThat(matchedTicket.getMesSyncStatus()).isEqualTo("SUCCESS");

        OrderDeliveryTicket unmatchedTicket = insertedTickets.stream()
                .filter(ticket -> "P-NO-HISTORY".equals(ticket.getPartNo()))
                .findFirst()
                .orElseThrow();
        assertThat(unmatchedTicket.getDescription()).isEqualTo("unmatched imported description");
        assertThat(unmatchedTicket.getMesSyncStatus()).isEqualTo("PENDING");

        ArgumentCaptor<Object> afterDataCaptor = ArgumentCaptor.forClass(Object.class);
        verify(auditService).record(
                eq("SALES_ORDER_IMPORT_QPO"),
                eq("sales_order_import_batch"),
                eq(201L),
                isNull(),
                afterDataCaptor.capture(),
                eq("MARKET"),
                eq("SUCCESS"),
                any(String.class),
                isNull()
        );
        assertThat((Map<String, Object>) afterDataCaptor.getValue())
                .containsEntry("matchedItemCount", 1)
                .containsEntry("unmatchedItemCount", 1)
                .containsEntry("missingPriceCount", 1)
                .containsEntry("autoDeliveryTicketCount", 2)
                .containsEntry("autoMatchTrigger", "IMPORT_AUTO")
                .containsEntry("autoDeliveryTrigger", "SALES_ORDER_IMPORT_AUTO");
    }

    private OrderDeliveryTicket ticket(Integer id, Long sourceOrderId) {
        OrderDeliveryTicket ticket = new OrderDeliveryTicket();
        ticket.setId(id);
        ticket.setSourceOrderId(sourceOrderId);
        return ticket;
    }
}
