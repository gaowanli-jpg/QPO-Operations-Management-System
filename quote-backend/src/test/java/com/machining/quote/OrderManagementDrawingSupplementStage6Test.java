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
                .containsEntry("skippedCount", 0);
        assertThat((Map<String, Object>) afterData.get("drawingMatchModes"))
                .containsEntry("partNo", 0)
                .containsEntry("partNoVersion", 1);
        assertThat(String.valueOf(afterData))
                .doesNotContain("rfq-secret-content")
                .doesNotContain("MockMultipartFile")
                .doesNotContain("multipart");
    }

    private ItemQuoteVersion item(Integer id, Integer rfqId, String partNo, String version) {
        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setId(id);
        item.setRfqId(rfqId);
        item.setPartNo(partNo);
        item.setVersion(version);
        item.setDescription("stage6 item");
        item.setQty(1);
        item.setStatus(QuoteStatusWorkflow.PRICED);
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
                null
        );
    }
}
