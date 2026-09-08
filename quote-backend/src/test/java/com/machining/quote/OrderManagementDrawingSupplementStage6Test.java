package com.machining.quote;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.BusinessFileService;
import com.machining.quote.service.OrderManagementService;
import com.machining.quote.storage.StorageType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderManagementDrawingSupplementStage6Test {
    @Test
    @SuppressWarnings("unchecked")
    void supplementVersionDrawingAuditUsesCountsWithoutFileContent() {
        OrderRfqMapper orderMapper = mock(OrderRfqMapper.class);
        ItemQuoteVersionMapper itemMapper = mock(ItemQuoteVersionMapper.class);
        BusinessFileService businessFileService = mock(BusinessFileService.class);
        AuditService auditService = mock(AuditService.class);
        OrderManagementService service = new OrderManagementService();
        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "itemMapper", itemMapper);
        ReflectionTestUtils.setField(service, "businessFileService", businessFileService);
        ReflectionTestUtils.setField(service, "auditService", auditService);

        OrderRfq order = new OrderRfq();
        order.setId(510);
        order.setRfqNo("RFQ-510");
        order.setCustomerName("ACME");
        order.setStatus(QuoteStatusWorkflow.PRICED);
        ItemQuoteVersion versionA = item(810, 510, "P-200", "A");
        ItemQuoteVersion versionB = item(811, 510, "P-200", "B");
        MockMultipartFile versionDrawing = new MockMultipartFile(
                "versionDrawings",
                "P-200_B.pdf",
                "application/pdf",
                "%PDF-1.4 rfq-secret-content".getBytes(StandardCharsets.UTF_8)
        );

        when(orderMapper.selectByIdForUpdate(510)).thenReturn(order);
        when(itemMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(versionA, versionB));
        when(businessFileService.saveDrawing(eq(order), eq(versionDrawing), eq("ORDER_SUPPLEMENT_DRAWING"), eq("510")))
                .thenReturn(stored("/uploads/rfq/P-200_B.pdf", versionDrawing));

        Map<String, Object> result = service.supplementDrawings(
                510,
                List.<MultipartFile>of(),
                List.<MultipartFile>of(versionDrawing)
        );

        assertThat(result)
                .containsEntry("success", true)
                .containsEntry("boundCount", 1)
                .containsEntry("alreadyBoundCount", 0)
                .containsEntry("unmatchedCount", 0)
                .containsEntry("skippedCount", 0);
        verify(itemMapper).updateById(versionB);
        assertThat(versionA.getDrawingUrl()).isNull();
        assertThat(versionB.getDrawingUrl()).isEqualTo("/uploads/rfq/P-200_B.pdf");
        assertThat(versionB.getStatus()).isEqualTo(QuoteStatusWorkflow.PRICED);

        ArgumentCaptor<Object> afterDataCaptor = ArgumentCaptor.forClass(Object.class);
        verify(auditService).record(
                eq("ORDER_SUPPLEMENT_DRAWINGS"),
                eq("order_rfq"),
                eq(510),
                isNull(),
                afterDataCaptor.capture()
        );
        Map<String, Object> afterData = (Map<String, Object>) afterDataCaptor.getValue();
        assertThat(afterData)
                .containsEntry("uploadCount", 1)
                .containsEntry("uploadedDrawingCount", 0)
                .containsEntry("versionUploadedDrawingCount", 1)
                .containsEntry("boundCount", 1)
                .containsEntry("alreadyBoundCount", 0)
                .containsEntry("unmatchedCount", 0)
                .containsEntry("skippedCount", 0)
                .containsEntry("restoredUnpricedCount", 0);
        assertThat((Map<String, Object>) afterData.get("drawingMatchModes"))
                .containsEntry("partNo", 0)
                .containsEntry("partNoVersion", 1);
        assertThat(String.valueOf(afterData))
                .doesNotContain("rfq-secret-content")
                .doesNotContain("MockMultipartFile")
                .doesNotContain("multipart");
    }

    @Test
    @SuppressWarnings("unchecked")
    void supplementDrawingRestoresOnlyNoDrawingItemsAndKeepsConfirmedStatuses() {
        OrderRfqMapper orderMapper = mock(OrderRfqMapper.class);
        ItemQuoteVersionMapper itemMapper = mock(ItemQuoteVersionMapper.class);
        BusinessFileService businessFileService = mock(BusinessFileService.class);
        AuditService auditService = mock(AuditService.class);
        OrderManagementService service = new OrderManagementService();
        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "itemMapper", itemMapper);
        ReflectionTestUtils.setField(service, "businessFileService", businessFileService);
        ReflectionTestUtils.setField(service, "auditService", auditService);

        OrderRfq order = new OrderRfq();
        order.setId(511);
        order.setRfqNo("RFQ-511");
        order.setCustomerName("ACME");
        order.setStatus(QuoteStatusWorkflow.RELEASED);
        ItemQuoteVersion missingNoPrice = item(820, 511, "P-300", "A", QuoteStatusWorkflow.NO_DRAWING, null);
        ItemQuoteVersion pricedNoPrice = item(821, 511, "P-301", "A", QuoteStatusWorkflow.PRICED, null);
        ItemQuoteVersion quotedWithPrice = item(822, 511, "P-302", "A", QuoteStatusWorkflow.QUOTED, 12.5);
        MockMultipartFile drawing300 = drawing("P-300.pdf");
        MockMultipartFile drawing301 = drawing("P-301.pdf");
        MockMultipartFile drawing302 = drawing("P-302.pdf");

        when(orderMapper.selectByIdForUpdate(511)).thenReturn(order);
        when(itemMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(missingNoPrice, pricedNoPrice, quotedWithPrice));
        when(businessFileService.saveDrawing(eq(order), eq(drawing300), eq("ORDER_SUPPLEMENT_DRAWING"), eq("511")))
                .thenReturn(stored("/uploads/rfq/P-300.pdf", drawing300));
        when(businessFileService.saveDrawing(eq(order), eq(drawing301), eq("ORDER_SUPPLEMENT_DRAWING"), eq("511")))
                .thenReturn(stored("/uploads/rfq/P-301.pdf", drawing301));
        when(businessFileService.saveDrawing(eq(order), eq(drawing302), eq("ORDER_SUPPLEMENT_DRAWING"), eq("511")))
                .thenReturn(stored("/uploads/rfq/P-302.pdf", drawing302));

        Map<String, Object> result = service.supplementDrawings(
                511,
                List.of(drawing300, drawing301, drawing302),
                List.of()
        );

        assertThat(result)
                .containsEntry("success", true)
                .containsEntry("boundCount", 3)
                .containsEntry("restoredUnpricedCount", 1);
        assertThat(missingNoPrice.getStatus()).isEqualTo(QuoteStatusWorkflow.UNPRICED);
        assertThat(pricedNoPrice.getStatus()).isEqualTo(QuoteStatusWorkflow.PRICED);
        assertThat(quotedWithPrice.getStatus()).isEqualTo(QuoteStatusWorkflow.QUOTED);

        ArgumentCaptor<Object> afterDataCaptor = ArgumentCaptor.forClass(Object.class);
        verify(auditService).record(
                eq("ORDER_SUPPLEMENT_DRAWINGS"),
                eq("order_rfq"),
                eq(511),
                isNull(),
                afterDataCaptor.capture()
        );
        Map<String, Object> afterData = (Map<String, Object>) afterDataCaptor.getValue();
        assertThat(afterData)
                .containsEntry("boundCount", 3)
                .containsEntry("restoredUnpricedCount", 1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void supplementDrawingFolderBindsAllItemsAndAuditsOnlyFolderCounts() {
        OrderRfqMapper orderMapper = mock(OrderRfqMapper.class);
        ItemQuoteVersionMapper itemMapper = mock(ItemQuoteVersionMapper.class);
        BusinessFileService businessFileService = mock(BusinessFileService.class);
        AuditService auditService = mock(AuditService.class);
        OrderManagementService service = new OrderManagementService();
        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "itemMapper", itemMapper);
        ReflectionTestUtils.setField(service, "businessFileService", businessFileService);
        ReflectionTestUtils.setField(service, "auditService", auditService);

        OrderRfq order = new OrderRfq();
        order.setId(512);
        order.setRfqNo("RFQ-512");
        order.setCustomerName("ACME");
        order.setStatus(QuoteStatusWorkflow.RELEASED);
        ItemQuoteVersion missingNoPrice = item(830, 512, "P-400", "A", QuoteStatusWorkflow.NO_DRAWING, null);
        ItemQuoteVersion quotedWithPrice = item(831, 512, "P-401", "A", QuoteStatusWorkflow.QUOTED, 12.5);
        MockMultipartFile folderDrawing = drawing("folder/order-pack.pdf");
        String folderUrl = "/uploads/drawing-folders/RFQ/512/folder-512/";
        BusinessFileService.DrawingFolderSaveResult folderResult = drawingFolderResult(folderUrl, "整单图纸", folderDrawing);

        when(orderMapper.selectByIdForUpdate(512)).thenReturn(order);
        when(itemMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(missingNoPrice, quotedWithPrice));
        when(businessFileService.saveDrawingFolderFiles(
                eq("RFQ"), eq("512"), eq("整单图纸"), eq(List.of(folderDrawing)), eq("ORDER_SUPPLEMENT_DRAWING_FOLDER"), eq("512")))
                .thenReturn(folderResult);
        when(orderMapper.updateById(order)).thenReturn(1);
        when(itemMapper.updateById(any(ItemQuoteVersion.class))).thenReturn(1);

        Map<String, Object> result = service.supplementDrawings(
                512,
                List.<MultipartFile>of(),
                List.<MultipartFile>of(),
                List.<MultipartFile>of(folderDrawing),
                "整单图纸"
        );

        assertThat(result)
                .containsEntry("success", true)
                .containsEntry("boundCount", 2)
                .containsEntry("restoredUnpricedCount", 1)
                .containsEntry("drawingFolderFileCount", 1);
        assertThat(order.getDrawingFolderUrl()).isEqualTo(folderUrl);
        assertThat(order.getDrawingFolderName()).isEqualTo("整单图纸");
        assertThat(order.getDrawingFolderFileCount()).isEqualTo(1);
        assertThat(missingNoPrice.getDrawingUrl()).isEqualTo(folderUrl);
        assertThat(missingNoPrice.getStatus()).isEqualTo(QuoteStatusWorkflow.UNPRICED);
        assertThat(quotedWithPrice.getDrawingUrl()).isEqualTo(folderUrl);
        assertThat(quotedWithPrice.getStatus()).isEqualTo(QuoteStatusWorkflow.QUOTED);

        ArgumentCaptor<Object> afterDataCaptor = ArgumentCaptor.forClass(Object.class);
        verify(auditService).record(
                eq("ORDER_SUPPLEMENT_DRAWINGS"),
                eq("order_rfq"),
                eq(512),
                isNull(),
                afterDataCaptor.capture()
        );
        Map<String, Object> afterData = (Map<String, Object>) afterDataCaptor.getValue();
        assertThat(afterData)
                .containsEntry("drawingFolderMode", true)
                .containsEntry("drawingFolderFileCount", 1)
                .containsEntry("boundCount", 2)
                .containsEntry("restoredUnpricedCount", 1);
        assertThat(String.valueOf(afterData)).doesNotContain(folderUrl);
    }

    private ItemQuoteVersion item(Integer id, Integer rfqId, String partNo, String version) {
        return item(id, rfqId, partNo, version, QuoteStatusWorkflow.PRICED, null);
    }

    private ItemQuoteVersion item(Integer id, Integer rfqId, String partNo, String version, String status, Double taxIncludedUnitPrice) {
        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setId(id);
        item.setRfqId(rfqId);
        item.setPartNo(partNo);
        item.setVersion(version);
        item.setDescription("stage6 item");
        item.setQty(1);
        item.setStatus(status);
        item.setTaxIncludedUnitPrice(taxIncludedUnitPrice);
        return item;
    }

    private MockMultipartFile drawing(String fileName) {
        return new MockMultipartFile(
                "drawings",
                fileName,
                "application/pdf",
                ("%PDF-1.4 " + fileName).getBytes(StandardCharsets.UTF_8)
        );
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
                null
        );
    }

    private BusinessFileService.DrawingFolderSaveResult drawingFolderResult(
            String folderUrl,
            String folderName,
            MockMultipartFile drawing) {
        BusinessFileService.DrawingFolderFile file = new BusinessFileService.DrawingFolderFile(
                drawing.getOriginalFilename(),
                folderUrl + "file-1.pdf",
                drawing.getContentType(),
                drawing.getSize(),
                java.time.LocalDateTime.now());
        return new BusinessFileService.DrawingFolderSaveResult(folderUrl, folderName, 1, List.of(file));
    }
}
