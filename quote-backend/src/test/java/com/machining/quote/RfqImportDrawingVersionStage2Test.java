package com.machining.quote;

import com.alibaba.excel.EasyExcel;
import com.machining.quote.controller.MarketController;
import com.machining.quote.dto.RfqExcelDTO;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.BusinessFileService;
import com.machining.quote.service.RfqNumberService;
import com.machining.quote.storage.StorageType;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RfqImportDrawingVersionStage2Test {
    @Test
    void versionDrawingsBindOnlyMatchingPartVersion() {
        MarketController controller = controller();

        Map<String, Object> result = controller.importRfq(
                "ACME",
                rfqFileWithSamePartDifferentVersions(),
                List.of(),
                List.of(pdf("versionDrawings", "P-100_A.pdf"))
        );

        ArgumentCaptor<ItemQuoteVersion> itemCaptor = ArgumentCaptor.forClass(ItemQuoteVersion.class);
        ItemQuoteVersionMapper itemMapper = itemMapper(controller);
        verify(itemMapper, times(2)).insert(itemCaptor.capture());
        ItemQuoteVersion versionA = itemCaptor.getAllValues().get(0);
        ItemQuoteVersion versionB = itemCaptor.getAllValues().get(1);

        assertThat(versionA.getPartNo()).isEqualTo("P-100");
        assertThat(versionA.getVersion()).isEqualTo("A");
        assertThat(versionA.getDrawingUrl()).isEqualTo("/uploads/rfq/P-100_A.pdf");
        assertThat(versionB.getPartNo()).isEqualTo("P-100");
        assertThat(versionB.getVersion()).isEqualTo("B");
        assertThat(versionB.getDrawingUrl()).isNull();
        assertThat((Map<String, Object>) result.get("drawingReport"))
                .containsEntry("uploadedDrawingCount", 1)
                .containsEntry("matchedItemCount", 1)
                .containsEntry("missingDrawingCount", 1)
                .containsEntry("versionUploadedDrawingCount", 1)
                .containsEntry("versionMatchedDrawingCount", 1);
    }

    @Test
    void legacyDrawingsStillBindByPartNoAcrossVersions() {
        MarketController controller = controller();

        controller.importRfq(
                "ACME",
                rfqFileWithSamePartDifferentVersions(),
                List.of(pdf("drawings", "P-100.pdf")),
                List.of()
        );

        ArgumentCaptor<ItemQuoteVersion> itemCaptor = ArgumentCaptor.forClass(ItemQuoteVersion.class);
        ItemQuoteVersionMapper itemMapper = itemMapper(controller);
        verify(itemMapper, times(2)).insert(itemCaptor.capture());

        assertThat(itemCaptor.getAllValues())
                .extracting(ItemQuoteVersion::getDrawingUrl)
                .containsExactly("/uploads/rfq/P-100.pdf", "/uploads/rfq/P-100.pdf");
    }

    private MarketController controller() {
        AuditService auditService = mock(AuditService.class);
        OrderRfqMapper orderMapper = mock(OrderRfqMapper.class);
        ItemQuoteVersionMapper itemMapper = mock(ItemQuoteVersionMapper.class);
        RfqNumberService rfqNumberService = mock(RfqNumberService.class);
        BusinessFileService businessFileService = mock(BusinessFileService.class, invocation -> {
            if ("saveDrawing".equals(invocation.getMethod().getName())) {
                MultipartFile drawing = invocation.getArgument(1);
                String fileName = cleanFileName(drawing);
                return new BusinessFileService.StoredBusinessFile(
                        "/uploads/rfq/" + fileName,
                        StorageType.LOCAL,
                        1L,
                        "rfq/" + fileName,
                        fileName,
                        "application/pdf",
                        drawing.getSize(),
                        null
                );
            }
            return Answers.RETURNS_DEFAULTS.answer(invocation);
        });
        MarketController controller = new MarketController();
        ReflectionTestUtils.setField(controller, "auditService", auditService);
        ReflectionTestUtils.setField(controller, "orderRfqMapper", orderMapper);
        ReflectionTestUtils.setField(controller, "itemQuoteVersionMapper", itemMapper);
        ReflectionTestUtils.setField(controller, "rfqNumberService", rfqNumberService);
        ReflectionTestUtils.setField(controller, "businessFileService", businessFileService);
        when(rfqNumberService.nextRfqNo()).thenReturn("RFQ-20260803-001");
        doAnswer(invocation -> {
            OrderRfq order = invocation.getArgument(0);
            order.setId(88);
            return 1;
        }).when(orderMapper).insert(any(OrderRfq.class));
        return controller;
    }

    private ItemQuoteVersionMapper itemMapper(MarketController controller) {
        return (ItemQuoteVersionMapper) ReflectionTestUtils.getField(controller, "itemQuoteVersionMapper");
    }

    private MockMultipartFile rfqFileWithSamePartDifferentVersions() {
        RfqExcelDTO versionA = item("P-100", "A", 1);
        RfqExcelDTO versionB = item("P-100", "B", 2);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        EasyExcel.write(outputStream, RfqExcelDTO.class).sheet("rfq").doWrite(List.of(versionA, versionB));
        return new MockMultipartFile(
                "excelFile",
                "rfq.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                outputStream.toByteArray()
        );
    }

    private RfqExcelDTO item(String partNo, String version, int qty) {
        RfqExcelDTO item = new RfqExcelDTO();
        item.setPartNo(partNo);
        item.setVersion(version);
        item.setDescription("desc-" + version);
        item.setQty(qty);
        item.setUnit("pcs");
        return item;
    }

    private MockMultipartFile pdf(String fieldName, String fileName) {
        return new MockMultipartFile(
                fieldName,
                fileName,
                "application/pdf",
                fileName.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String cleanFileName(MultipartFile drawing) {
        return drawing.getOriginalFilename() == null
                ? "drawing.pdf"
                : drawing.getOriginalFilename().replaceAll(".*[\\\\/]", "");
    }
}
