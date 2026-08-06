package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.ReleaseOrderSource;
import com.machining.quote.dto.ReleaseSourceType;
import com.machining.quote.dto.ReleaseItemFieldUpdateReq;
import com.machining.quote.dto.ReleaseItemVersionUpdateReq;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.BusinessFileService;
import com.machining.quote.service.DataScopeService;
import com.machining.quote.service.InternalTargetLeadTimeService;
import com.machining.quote.service.LeadTimeService;
import com.machining.quote.service.OrderItemPageService;
import com.machining.quote.storage.StorageType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

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
                .contains("@PostMapping(\"/release/items/{itemId}/fields\")")
                .contains("@RequirePermission(\"market:release:create\")")
                .contains("@PostMapping(value = \"/release/drawings/replace\"")
                .contains("internalTargetLeadTimeService.updateReleaseItemVersion(itemId, req)")
                .contains("internalTargetLeadTimeService.updateReleaseItemFields(itemId, req)")
                .contains("parseReleaseSources(sourcesJson, rfqIdsJson)");

        String releaseService = source("service", "InternalTargetLeadTimeService.java");
        assertThat(releaseService)
                .contains("public Map<String, Object> updateReleaseItemVersion")
                .contains("public Map<String, Object> updateReleaseItemFields")
                .contains("public Map<String, Object> replaceReleaseDrawings")
                .contains("RELEASE_ITEM_VERSION_UPDATE")
                .contains("RELEASE_ITEM_FIELD_UPDATE")
                .contains("RELEASE_DRAWING_REPLACE")
                .contains("Release item version updated")
                .contains("Release item fields updated")
                .contains("Release drawings replace completed")
                .contains("Release drawings replace completed with warnings")
                .contains("Release drawings replace failed");

        assertThat(AuditService.inferModule("RELEASE_ITEM_VERSION_UPDATE")).isEqualTo("MARKET");
        assertThat(AuditService.inferModule("RELEASE_ITEM_FIELD_UPDATE")).isEqualTo("MARKET");
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
    void updateReleaseItemFieldsUpdatesRfqItemEditablePartFields() {
        OrderRfqMapper orderMapper = mock(OrderRfqMapper.class);
        ItemQuoteVersionMapper itemMapper = mock(ItemQuoteVersionMapper.class);
        OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
        AuditService auditService = mock(AuditService.class);
        InternalTargetLeadTimeService service = releaseService(orderMapper, itemMapper, ticketMapper, auditService, null);
        OrderRfq order = releaseOrder(101, "RFQ-101");
        ItemQuoteVersion item = releaseItem(201, 101, "P-001", "A");
        item.setDescription("old description");
        item.setSurfaceTreatment("old surface");
        item.setRemark("old remark");
        item.setCustomerRequirement("old requirement");
        when(orderMapper.selectByIdForUpdate(101)).thenReturn(order);
        when(itemMapper.selectByIdForUpdate(201)).thenReturn(item);
        when(itemMapper.selectById(201)).thenAnswer(invocation -> item);
        when(ticketMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());

        ReleaseItemFieldUpdateReq req = new ReleaseItemFieldUpdateReq();
        req.setSourceType(ReleaseSourceType.RFQ);
        req.setRfqId(101);
        req.setDescription(" updated description ");
        req.setSurfaceTreatment(" anodized ");
        req.setRemark(" updated remark ");
        req.setCustomerRequirement(" pack separately ");
        Map<String, Object> result = service.updateReleaseItemFields(201L, req);

        assertThat(result).containsEntry("success", true);
        Map<String, Object> row = (Map<String, Object>) result.get("data");
        assertThat(row)
                .containsEntry("id", 201)
                .containsEntry("sourceType", ReleaseSourceType.RFQ.name())
                .containsEntry("description", "updated description")
                .containsEntry("surfaceTreatment", "anodized")
                .containsEntry("remark", "updated remark")
                .containsEntry("customerRequirement", "pack separately");
        assertThat(item.getDescription()).isEqualTo("updated description");
        assertThat(item.getSurfaceTreatment()).isEqualTo("anodized");
        assertThat(item.getRemark()).isEqualTo("updated remark");
        assertThat(item.getCustomerRequirement()).isEqualTo("pack separately");
        verify(itemMapper).updateById(item);
        verify(auditService).record(eq("RELEASE_ITEM_FIELD_UPDATE"), eq("item_quote_version"), eq(201), any(Map.class), any(Map.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateReleaseItemFieldsUpdatesSalesOrderItemEditablePartFields() {
        OrderRfqMapper orderMapper = mock(OrderRfqMapper.class);
        ItemQuoteVersionMapper itemMapper = mock(ItemQuoteVersionMapper.class);
        OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
        AuditService auditService = mock(AuditService.class);
        InternalTargetLeadTimeService service = releaseService(orderMapper, itemMapper, ticketMapper, auditService, null);
        SalesOrderHeaderMapper salesOrderHeaderMapper = mock(SalesOrderHeaderMapper.class);
        SalesOrderItemMapper salesOrderItemMapper = mock(SalesOrderItemMapper.class);
        ReflectionTestUtils.setField(service, "salesOrderHeaderMapper", salesOrderHeaderMapper);
        ReflectionTestUtils.setField(service, "salesOrderItemMapper", salesOrderItemMapper);
        SalesOrderHeader order = salesOrder(2001L, "SO-2001");
        SalesOrderItem item = salesOrderItem(3001L, 2001L, "P-SO-001", "B");
        item.setDescription("old sales description");
        item.setSurfaceTreatment("old sales surface");
        item.setRemark("old sales remark");
        item.setCustomerRequirement("old sales requirement");
        when(salesOrderHeaderMapper.selectByIdForUpdate(2001L)).thenReturn(order);
        when(salesOrderItemMapper.selectByIdForUpdate(3001L)).thenReturn(item);
        when(salesOrderItemMapper.selectById(3001L)).thenReturn(item);

        ReleaseItemFieldUpdateReq req = new ReleaseItemFieldUpdateReq();
        req.setSourceType(ReleaseSourceType.SALES_ORDER);
        req.setSourceOrderId(2001L);
        req.setDescription(" sales description ");
        req.setSurfaceTreatment(" passivation ");
        req.setRemark(" sales remark ");
        req.setCustomerRequirement(" customer needs clean packing ");
        Map<String, Object> result = service.updateReleaseItemFields(3001L, req);

        assertThat(result).containsEntry("success", true);
        Map<String, Object> row = (Map<String, Object>) result.get("data");
        assertThat(row)
                .containsEntry("id", 3001L)
                .containsEntry("sourceType", ReleaseSourceType.SALES_ORDER.name())
                .containsEntry("description", "sales description")
                .containsEntry("surfaceTreatment", "passivation")
                .containsEntry("remark", "sales remark")
                .containsEntry("customerRequirement", "customer needs clean packing");
        assertThat(item.getDescription()).isEqualTo("sales description");
        assertThat(item.getSurfaceTreatment()).isEqualTo("passivation");
        assertThat(item.getRemark()).isEqualTo("sales remark");
        assertThat(item.getCustomerRequirement()).isEqualTo("customer needs clean packing");
        verify(salesOrderItemMapper).updateById(item);
        verify(auditService).record(eq("RELEASE_ITEM_FIELD_UPDATE"), eq("sales_order_item"), eq(3001L), any(Map.class), any(Map.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void replaceReleaseDrawingsOverwritesMatchedItemsAcrossSelectedOrdersAndWritesAudit() throws Exception {
        OrderRfqMapper orderMapper = mock(OrderRfqMapper.class);
        ItemQuoteVersionMapper itemMapper = mock(ItemQuoteVersionMapper.class);
        OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
        AuditService auditService = mock(AuditService.class);
        InternalTargetLeadTimeService service = releaseService(orderMapper, itemMapper, ticketMapper, auditService, null);
        BusinessFileService businessFileService = mock(BusinessFileService.class);
        ReflectionTestUtils.setField(service, "businessFileService", businessFileService);

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
        when(businessFileService.saveDrawing(any(OrderRfq.class), eq(drawing), eq("RELEASE_DRAWING_REPLACE"), any()))
                .thenAnswer(invocation -> {
                    OrderRfq order = invocation.getArgument(0);
                    String publicPath = "/uploads/ACME/" + order.getRfqNo() + "/replacement-" + order.getId() + ".pdf";
                    return new BusinessFileService.StoredBusinessFile(
                            publicPath,
                            StorageType.LOCAL,
                            1L,
                            publicPath.substring("/uploads/".length()),
                            "DRW-001.pdf",
                            "application/pdf",
                            drawing.getSize(),
                            null);
                });

        Map<String, Object> result = service.replaceReleaseDrawings(
                List.of(ReleaseOrderSource.rfq(101), ReleaseOrderSource.rfq(102)),
                List.of(drawing));
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
        verify(businessFileService).saveDrawing(eq(firstOrder), eq(drawing), eq("RELEASE_DRAWING_REPLACE"), eq("101"));
        verify(businessFileService).saveDrawing(eq(secondOrder), eq(drawing), eq("RELEASE_DRAWING_REPLACE"), eq("102"));
        verify(itemMapper).updateById(firstItem);
        verify(itemMapper).updateById(secondItem);
        verify(auditService).record(eq("RELEASE_DRAWING_REPLACE"), eq("release_drawing_replace"), eq("RFQ:101,RFQ:102"),
                isNull(), any(Map.class), eq("MARKET"), eq("SUCCESS"), contains("replacedCount=2"), isNull());
    }

    @Test
    @SuppressWarnings("unchecked")
    void replaceReleaseDrawingsReplacesSamePartAcrossRfqAndSalesOrderSources() throws Exception {
        OrderRfqMapper orderMapper = mock(OrderRfqMapper.class);
        ItemQuoteVersionMapper itemMapper = mock(ItemQuoteVersionMapper.class);
        OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
        AuditService auditService = mock(AuditService.class);
        InternalTargetLeadTimeService service = releaseService(orderMapper, itemMapper, ticketMapper, auditService, null);
        SalesOrderHeaderMapper salesOrderHeaderMapper = mock(SalesOrderHeaderMapper.class);
        SalesOrderItemMapper salesOrderItemMapper = mock(SalesOrderItemMapper.class);
        BusinessFileService businessFileService = mock(BusinessFileService.class);
        ReflectionTestUtils.setField(service, "salesOrderHeaderMapper", salesOrderHeaderMapper);
        ReflectionTestUtils.setField(service, "salesOrderItemMapper", salesOrderItemMapper);
        ReflectionTestUtils.setField(service, "businessFileService", businessFileService);

        OrderRfq rfqOrder = releaseOrder(101, "RFQ-MIX-001");
        ItemQuoteVersion rfqItem = releaseItem(201, 101, "P-MIX-001", "A");
        rfqItem.setDrawingUrl("/uploads/old-rfq.pdf");
        SalesOrderHeader salesOrder = salesOrder(2001L, "SO-MIX-001");
        SalesOrderItem salesItem = salesOrderItem(3001L, 2001L, "P-MIX-001", "B");
        salesItem.setDrawingUrl("/uploads/old-sales.pdf");
        salesItem.setHasDrawing(true);

        when(orderMapper.selectByIdForUpdate(101)).thenReturn(rfqOrder);
        when(salesOrderHeaderMapper.selectByIdForUpdate(2001L)).thenReturn(salesOrder);
        when(itemMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(rfqItem));
        when(salesOrderItemMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(salesItem));
        when(ticketMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());

        MockMultipartFile drawing = pdf("P-MIX-001.pdf");
        when(businessFileService.saveDrawing(eq(rfqOrder), eq(drawing), eq("RELEASE_DRAWING_REPLACE"), eq("101")))
                .thenReturn(stored("/uploads/ACME/RFQ-MIX-001/P-MIX-001-new.pdf", drawing));
        when(businessFileService.saveSalesOrderImportDrawing(eq("ACME"), eq("SO-MIX-001"), eq(drawing),
                eq("RELEASE_DRAWING_REPLACE"), eq("2001")))
                .thenReturn(stored("/uploads/ACME/SO-MIX-001/P-MIX-001-new.pdf", drawing));

        Map<String, Object> result = service.replaceReleaseDrawings(
                List.of(ReleaseOrderSource.rfq(101), ReleaseOrderSource.salesOrder(2001L)),
                List.of(drawing));
        List<Map<String, Object>> replacedItems = (List<Map<String, Object>>) result.get("replacedItems");

        assertThat(result)
                .containsEntry("success", true)
                .containsEntry("matchedCount", 2)
                .containsEntry("replacedCount", 2)
                .containsEntry("unmatchedCount", 0)
                .containsEntry("skippedCount", 0);
        assertThat(rfqItem.getDrawingUrl()).isEqualTo("/uploads/ACME/RFQ-MIX-001/P-MIX-001-new.pdf");
        assertThat(salesItem.getDrawingUrl()).isEqualTo("/uploads/ACME/SO-MIX-001/P-MIX-001-new.pdf");
        assertThat(salesItem.getHasDrawing()).isTrue();
        assertThat(replacedItems)
                .extracting(row -> row.get("sourceType"))
                .containsExactlyInAnyOrder(ReleaseSourceType.RFQ.name(), ReleaseSourceType.SALES_ORDER.name());
        assertThat(replacedItems)
                .extracting(row -> row.get("sourceItemId"))
                .containsExactlyInAnyOrder(201L, 3001L);
        verify(itemMapper).updateById(rfqItem);
        verify(salesOrderItemMapper).updateById(salesItem);
        verify(auditService).record(eq("RELEASE_DRAWING_REPLACE"), eq("release_drawing_replace"), any(),
                isNull(), any(Map.class), eq("MARKET"), eq("SUCCESS"), contains("replacedCount=2"), isNull());
    }

    @Test
    @SuppressWarnings("unchecked")
    void replaceReleaseVersionDrawingsReplacesOnlyMatchingPartVersionAcrossMixedSources() {
        OrderRfqMapper orderMapper = mock(OrderRfqMapper.class);
        ItemQuoteVersionMapper itemMapper = mock(ItemQuoteVersionMapper.class);
        OrderDeliveryTicketMapper ticketMapper = mock(OrderDeliveryTicketMapper.class);
        AuditService auditService = mock(AuditService.class);
        InternalTargetLeadTimeService service = releaseService(orderMapper, itemMapper, ticketMapper, auditService, null);
        SalesOrderHeaderMapper salesOrderHeaderMapper = mock(SalesOrderHeaderMapper.class);
        SalesOrderItemMapper salesOrderItemMapper = mock(SalesOrderItemMapper.class);
        BusinessFileService businessFileService = mock(BusinessFileService.class);
        ReflectionTestUtils.setField(service, "salesOrderHeaderMapper", salesOrderHeaderMapper);
        ReflectionTestUtils.setField(service, "salesOrderItemMapper", salesOrderItemMapper);
        ReflectionTestUtils.setField(service, "businessFileService", businessFileService);

        OrderRfq rfqOrder = releaseOrder(301, "RFQ-VERSION-001");
        ItemQuoteVersion rfqVersionA = releaseItem(401, 301, "P-300", "A");
        rfqVersionA.setDrawingUrl("/uploads/old-rfq-a.pdf");
        ItemQuoteVersion rfqVersionB = releaseItem(402, 301, "P-300", "B");
        rfqVersionB.setDrawingUrl("/uploads/old-rfq-b.pdf");
        ItemQuoteVersion releasedRfqVersionA = releaseItem(403, 301, "P-300", "A");
        releasedRfqVersionA.setDrawingUrl("/uploads/old-rfq-a-released.pdf");
        releasedRfqVersionA.setStatus(QuoteStatusWorkflow.ITEM_RELEASED);
        SalesOrderHeader salesOrder = salesOrder(3001L, "SO-VERSION-001");
        SalesOrderItem salesVersionA = salesOrderItem(5001L, 3001L, "P-300", "A");
        salesVersionA.setDrawingUrl("/uploads/old-sales-a.pdf");
        salesVersionA.setHasDrawing(true);
        SalesOrderItem salesVersionB = salesOrderItem(5002L, 3001L, "P-300", "B");
        salesVersionB.setDrawingUrl("/uploads/old-sales-b.pdf");
        salesVersionB.setHasDrawing(true);

        when(orderMapper.selectByIdForUpdate(301)).thenReturn(rfqOrder);
        when(salesOrderHeaderMapper.selectByIdForUpdate(3001L)).thenReturn(salesOrder);
        when(itemMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(rfqVersionA, rfqVersionB, releasedRfqVersionA));
        when(salesOrderItemMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(salesVersionA, salesVersionB));
        when(ticketMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of());

        MockMultipartFile versionDrawing = pdf("versionDrawings", "P-300_A.pdf");
        when(businessFileService.saveDrawing(eq(rfqOrder), eq(versionDrawing), eq("RELEASE_DRAWING_REPLACE"), eq("301")))
                .thenReturn(stored("/uploads/ACME/RFQ-VERSION-001/P-300_A-new.pdf", versionDrawing));
        when(businessFileService.saveSalesOrderImportDrawing(eq("ACME"), eq("SO-VERSION-001"), eq(versionDrawing),
                eq("RELEASE_DRAWING_REPLACE"), eq("3001")))
                .thenReturn(stored("/uploads/ACME/SO-VERSION-001/P-300_A-new.pdf", versionDrawing));

        Map<String, Object> result = service.replaceReleaseDrawings(
                List.of(ReleaseOrderSource.rfq(301), ReleaseOrderSource.salesOrder(3001L)),
                List.<MultipartFile>of(),
                List.<MultipartFile>of(versionDrawing));
        List<Map<String, Object>> replacedItems = (List<Map<String, Object>>) result.get("replacedItems");
        List<Map<String, Object>> skippedItems = (List<Map<String, Object>>) result.get("skippedItems");

        assertThat(result)
                .containsEntry("success", true)
                .containsEntry("uploadCount", 1)
                .containsEntry("matchedCount", 3)
                .containsEntry("replacedCount", 2)
                .containsEntry("unmatchedCount", 0)
                .containsEntry("skippedCount", 1);
        assertThat(rfqVersionA.getDrawingUrl()).isEqualTo("/uploads/ACME/RFQ-VERSION-001/P-300_A-new.pdf");
        assertThat(salesVersionA.getDrawingUrl()).isEqualTo("/uploads/ACME/SO-VERSION-001/P-300_A-new.pdf");
        assertThat(salesVersionA.getHasDrawing()).isTrue();
        assertThat(rfqVersionB.getDrawingUrl()).isEqualTo("/uploads/old-rfq-b.pdf");
        assertThat(salesVersionB.getDrawingUrl()).isEqualTo("/uploads/old-sales-b.pdf");
        assertThat(releasedRfqVersionA.getDrawingUrl()).isEqualTo("/uploads/old-rfq-a-released.pdf");
        assertThat(replacedItems).extracting(row -> row.get("sourceItemId")).containsExactlyInAnyOrder(401L, 5001L);
        assertThat(skippedItems).extracting(row -> row.get("sourceItemId")).containsExactly(403L);
        assertThat(skippedItems).extracting(row -> row.get("reason")).containsExactly("Item is released");
        verify(itemMapper).updateById(rfqVersionA);
        verify(salesOrderItemMapper).updateById(salesVersionA);
        ArgumentCaptor<Object> afterDataCaptor = ArgumentCaptor.forClass(Object.class);
        verify(auditService).record(eq("RELEASE_DRAWING_REPLACE"), eq("release_drawing_replace"), any(),
                isNull(), afterDataCaptor.capture(), eq("MARKET"), eq("SUCCESS"), contains("replacedCount=2"), isNull());
        Map<String, Object> afterData = (Map<String, Object>) afterDataCaptor.getValue();
        assertThat(afterData)
                .containsEntry("uploadCount", 1)
                .containsEntry("versionUploadedDrawingCount", 1)
                .containsEntry("matchedCount", 3)
                .containsEntry("replacedCount", 2)
                .containsEntry("unmatchedCount", 0)
                .containsEntry("skippedCount", 1);
        assertThat((Map<String, Object>) afterData.get("drawingMatchModes"))
                .containsEntry("partNo", 0)
                .containsEntry("partNoVersion", 1);
        assertThat(String.valueOf(afterData))
                .doesNotContain("%PDF")
                .doesNotContain("MockMultipartFile")
                .doesNotContain("multipart");
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
        assertThatThrownBy(() -> service.replaceReleaseDrawings(List.of(ReleaseOrderSource.rfq(101)), List.of(pdf("DRW-001.pdf"))))
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
        ReflectionTestUtils.setField(service, "businessFileService", mock(BusinessFileService.class));
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

    private SalesOrderHeader salesOrder(Long id, String salesOrderNo) {
        SalesOrderHeader order = new SalesOrderHeader();
        order.setId(id);
        order.setSalesOrderNo(salesOrderNo);
        order.setCustomerName("ACME");
        order.setStatus(QuoteStatusWorkflow.QUOTED);
        return order;
    }

    private SalesOrderItem salesOrderItem(Long id, Long salesOrderId, String partNo, String version) {
        SalesOrderItem item = new SalesOrderItem();
        item.setId(id);
        item.setSalesOrderId(salesOrderId);
        item.setPartNo(partNo);
        item.setVersion(version);
        item.setDescription("sales order item");
        item.setQty(2);
        item.setReleasedQty(0);
        item.setRemainingQty(2);
        item.setFinalUnitPrice(java.math.BigDecimal.TEN);
        item.setStatus(QuoteStatusWorkflow.QUOTED);
        return item;
    }

    private BusinessFileService.StoredBusinessFile stored(String publicPath, MockMultipartFile drawing) {
        return new BusinessFileService.StoredBusinessFile(
                publicPath,
                StorageType.LOCAL,
                1L,
                publicPath.substring("/uploads/".length()),
                drawing.getOriginalFilename(),
                drawing.getContentType(),
                drawing.getSize(),
                null);
    }

    private MockMultipartFile pdf(String fileName) {
        return pdf("drawings", fileName);
    }

    private MockMultipartFile pdf(String fieldName, String fileName) {
        return new MockMultipartFile(
                fieldName,
                fileName,
                "application/pdf",
                "%PDF-1.4".getBytes(StandardCharsets.UTF_8)
        );
    }

    private String source(String packageName, String fileName) throws Exception {
        return Files.readString(MAIN_JAVA.resolve(packageName).resolve(fileName), StandardCharsets.UTF_8);
    }
}
