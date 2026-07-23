package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.ReleaseItemVersionUpdateReq;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.DataScopeService;
import com.machining.quote.service.InternalTargetLeadTimeService;
import com.machining.quote.service.LeadTimeService;
import com.machining.quote.service.OrderFilePathService;
import com.machining.quote.service.OrderItemPageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReleaseVersionDrawingStage1Test {
    private static final Path MAIN_JAVA = Path.of("src", "main", "java", "com", "machining", "quote");

    @TempDir
    private Path tempDir;

    @Test
    void releaseVersionAndDrawingContractsUseReleaseCreatePermissionAndAuditLabels() throws Exception {
        assertThat(MAIN_JAVA.resolve("dto").resolve("ReleaseItemVersionUpdateReq.java")).exists();

        String marketController = source("controller", "MarketController.java");
        assertThat(marketController)
                .contains("@PostMapping(\"/release/items/{itemId}/version\")")
                .contains("@RequirePermission(\"market:release:create\")")
                .contains("@PostMapping(value = \"/release/drawings/replace\"")
                .contains("internalTargetLeadTimeService.updateReleaseItemVersion(itemId, req)")
                .contains("internalTargetLeadTimeService.replaceReleaseDrawings(parseRfqIds(rfqIdsJson), drawings)");

        String releaseService = source("service", "InternalTargetLeadTimeService.java");
        assertThat(releaseService)
                .contains("public Map<String, Object> updateReleaseItemVersion")
                .contains("public Map<String, Object> replaceReleaseDrawings")
                .contains("RELEASE_ITEM_VERSION_UPDATE")
                .contains("RELEASE_DRAWING_REPLACE")
                .contains("Release item version updated")
                .contains("Release drawings replace completed")
                .contains("Release drawings replace completed with warnings")
                .contains("Release drawings replace failed");

        assertThat(AuditService.inferModule("RELEASE_ITEM_VERSION_UPDATE")).isEqualTo("MARKET");
        assertThat(AuditService.inferModule("RELEASE_DRAWING_REPLACE")).isEqualTo("MARKET");
        assertThat(AuditService.labelForAction("RELEASE_ITEM_VERSION_UPDATE"))
                .isEqualTo("议价提单修改零件版本");
        assertThat(AuditService.labelForAction("RELEASE_DRAWING_REPLACE"))
                .isEqualTo("议价提单替换图纸");
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateReleaseItemVersionChangesOnlySelectedItemAndWritesAudit() {
        OrderRfqMapper orderMapper = mock(OrderRfqMapper.class);
        ItemQuoteVersionMapper itemMapper = mock(ItemQuoteVersionMapper.class);
        OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
        AuditService auditService = mock(AuditService.class);
        InternalTargetLeadTimeService service = releaseService(orderMapper, itemMapper, ticketMapper, auditService, null);
        OrderRfq order = releaseOrder(101, "RFQ-101");
        ItemQuoteVersion item = releaseItem(201, 101, "P-001", "A");
        when(orderMapper.selectByIdForUpdate(101)).thenReturn(order);
        when(itemMapper.selectByIdForUpdate(201)).thenReturn(item);
        when(itemMapper.selectById(201)).thenAnswer(invocation -> item);
        when(ticketMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());

        ReleaseItemVersionUpdateReq req = new ReleaseItemVersionUpdateReq();
        req.setRfqId(101);
        req.setVersion(" b ");
        Map<String, Object> result = service.updateReleaseItemVersion(201, req);

        assertThat(result).containsEntry("success", true);
        Map<String, Object> row = (Map<String, Object>) result.get("data");
        assertThat(row)
                .containsEntry("id", 201)
                .containsEntry("rfqId", 101)
                .containsEntry("version", "B");
        verify(itemMapper).updateById(item);
        assertThat(item.getVersion()).isEqualTo("B");
        verify(auditService).record(eq("RELEASE_ITEM_VERSION_UPDATE"), eq("item_quote_version"), eq(201), any(Map.class), any(Map.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void replaceReleaseDrawingsOverwritesMatchedItemsAcrossSelectedOrdersAndWritesAudit() throws Exception {
        OrderRfqMapper orderMapper = mock(OrderRfqMapper.class);
        ItemQuoteVersionMapper itemMapper = mock(ItemQuoteVersionMapper.class);
        OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
        AuditService auditService = mock(AuditService.class);
        InternalTargetLeadTimeService service = releaseService(orderMapper, itemMapper, ticketMapper, auditService, null);
        OrderFilePathService filePathService = new OrderFilePathService();
        ReflectionTestUtils.setField(filePathService, "uploadDir", tempDir.toString());
        ReflectionTestUtils.setField(service, "orderFilePathService", filePathService);

        OrderRfq firstOrder = releaseOrder(101, "RFQ-101");
        OrderRfq secondOrder = releaseOrder(102, "RFQ-102");
        ItemQuoteVersion firstItem = releaseItem(201, 101, "DRW-001", "A");
        firstItem.setDrawingUrl("/uploads/old-a.pdf");
        ItemQuoteVersion secondItem = releaseItem(202, 102, "DRW-001", "B");
        secondItem.setDrawingUrl("/uploads/old-b.pdf");
        when(orderMapper.selectByIdForUpdate(101)).thenReturn(firstOrder);
        when(orderMapper.selectByIdForUpdate(102)).thenReturn(secondOrder);
        when(itemMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(firstItem, secondItem));
        when(ticketMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());

        MockMultipartFile drawing = new MockMultipartFile(
                "drawings",
                "DRW-001.pdf",
                "application/pdf",
                "%PDF-1.4 replacement".getBytes(StandardCharsets.UTF_8)
        );

        Map<String, Object> result = service.replaceReleaseDrawings(List.of(101, 102), List.of(drawing));
        List<Map<String, Object>> replacedItems = (List<Map<String, Object>>) result.get("replacedItems");

        assertThat(result)
                .containsEntry("success", true)
                .containsEntry("uploadCount", 1)
                .containsEntry("matchedCount", 2)
                .containsEntry("replacedCount", 2)
                .containsEntry("unmatchedCount", 0)
                .containsEntry("skippedCount", 0);
        assertThat(replacedItems).extracting(row -> row.get("itemId")).containsExactlyInAnyOrder(201, 202);
        assertThat(firstItem.getDrawingUrl()).contains("/uploads/ACME/RFQ-101/");
        assertThat(secondItem.getDrawingUrl()).contains("/uploads/ACME/RFQ-102/");
        assertThat(firstItem.getDrawingUrl()).isNotEqualTo("/uploads/old-a.pdf");
        assertThat(secondItem.getDrawingUrl()).isNotEqualTo("/uploads/old-b.pdf");
        assertThat(Files.walk(tempDir).filter(Files::isRegularFile).count()).isEqualTo(2);
        verify(itemMapper).updateById(firstItem);
        verify(itemMapper).updateById(secondItem);
        verify(auditService).record(eq("RELEASE_DRAWING_REPLACE"), eq("item_quote_version"), eq("101,102"),
                isNull(), any(Map.class), eq("MARKET"), eq("SUCCESS"), contains("replacedCount=2"), isNull());
    }

    @Test
    void updateAndReplaceRejectOrdersOutsideDataScope() {
        OrderRfqMapper orderMapper = mock(OrderRfqMapper.class);
        ItemQuoteVersionMapper itemMapper = mock(ItemQuoteVersionMapper.class);
        OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
        AuditService auditService = mock(AuditService.class);
        DataScopeService dataScopeService = mock(DataScopeService.class);
        InternalTargetLeadTimeService service = releaseService(orderMapper, itemMapper, ticketMapper, auditService, dataScopeService);
        OrderRfq order = releaseOrder(101, "RFQ-101");
        when(orderMapper.selectByIdForUpdate(101)).thenReturn(order);
        doThrow(new RuntimeException("No data permission for order 101"))
                .when(dataScopeService).assertCanAccessOrder(order);

        ReleaseItemVersionUpdateReq req = new ReleaseItemVersionUpdateReq();
        req.setRfqId(101);
        req.setVersion("B");

        assertThatThrownBy(() -> service.updateReleaseItemVersion(201, req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("No data permission for order 101");
        assertThatThrownBy(() -> service.replaceReleaseDrawings(List.of(101), List.of(pdf("DRW-001.pdf"))))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("No data permission for order 101");
    }

    private InternalTargetLeadTimeService releaseService(
            OrderRfqMapper orderMapper,
            ItemQuoteVersionMapper itemMapper,
            OrderDeliveryTicketMapper ticketMapper,
            AuditService auditService,
            DataScopeService dataScopeService) {
        InternalTargetLeadTimeService service = new InternalTargetLeadTimeService();
        ReflectionTestUtils.setField(service, "orderRfqMapper", orderMapper);
        ReflectionTestUtils.setField(service, "itemMapper", itemMapper);
        ReflectionTestUtils.setField(service, "ticketMapper", ticketMapper);
        ReflectionTestUtils.setField(service, "leadTimeService", new LeadTimeService());
        ReflectionTestUtils.setField(service, "auditService", auditService);
        ReflectionTestUtils.setField(service, "orderItemPageService", mock(OrderItemPageService.class));
        ReflectionTestUtils.setField(service, "dataScopeService", dataScopeService);
        return service;
    }

    private OrderRfq releaseOrder(Integer id, String rfqNo) {
        OrderRfq order = new OrderRfq();
        order.setId(id);
        order.setRfqNo(rfqNo);
        order.setCustomerName("ACME");
        order.setStatus(QuoteStatusWorkflow.QUOTED);
        return order;
    }

    private ItemQuoteVersion releaseItem(Integer id, Integer rfqId, String partNo, String version) {
        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setId(id);
        item.setRfqId(rfqId);
        item.setPartNo(partNo);
        item.setVersion(version);
        item.setDescription("release item");
        item.setQty(1);
        item.setFinalUnitPrice(10.0);
        item.setStatus(QuoteStatusWorkflow.QUOTED);
        return item;
    }

    private MockMultipartFile pdf(String fileName) {
        return new MockMultipartFile(
                "drawings",
                fileName,
                "application/pdf",
                "%PDF-1.4".getBytes(StandardCharsets.UTF_8)
        );
    }

    private String source(String packageName, String fileName) throws Exception {
        return Files.readString(MAIN_JAVA.resolve(packageName).resolve(fileName), StandardCharsets.UTF_8);
    }
}
