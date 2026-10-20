package com.machining.quote;

import com.alibaba.excel.EasyExcel;
import com.machining.quote.controller.MarketController;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MarketControllerPartSelectionTests {

    @Test
    @SuppressWarnings("unchecked")
    void parsesPartSelectionByPartNoAndVersionHeaders() {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        List<List<String>> rows = List.of(
                List.of("客户选择清单"),
                List.of("图号", "物料描述", "版本"),
                List.of(" 7.1.12334 ", "零件一", " a "),
                List.of("7.1.12334", "重复零件", "A"),
                List.of("L20100000733", "空版本零件", "")
        );
        EasyExcel.write(outputStream).sheet("selection").doWrite(rows);

        MockMultipartFile file = new MockMultipartFile(
                "excelFile",
                "selection.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                outputStream.toByteArray()
        );

        Map<String, Object> result = new MarketController().parsePartSelection(file);
        List<Map<String, String>> items = (List<Map<String, String>>) result.get("data");

        assertThat(items).hasSize(2);
        assertThat(items).extracting("normalizedPartNo")
                .containsExactly("7.1.12334", "L20100000733");
        assertThat(items).extracting("normalizedVersion")
                .containsExactly("A", "");
    }

    @Test
    @SuppressWarnings("unchecked")
    void parsesOptionalRfqNoHeaderForBatchSelection() {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        List<List<String>> rows = List.of(
                List.of("selection"),
                List.of("RFQ\u53f7", "\u56fe\u53f7", "\u7248\u672c"),
                List.of(" RFQ-2026-001 ", " CROSS-ORDER-01 ", " b ")
        );
        EasyExcel.write(outputStream).sheet("selection").doWrite(rows);

        MockMultipartFile file = new MockMultipartFile(
                "excelFile",
                "selection.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                outputStream.toByteArray()
        );

        Map<String, Object> result = new MarketController().parsePartSelection(file);
        List<Map<String, String>> items = (List<Map<String, String>>) result.get("data");

        assertThat(items).hasSize(1);
        assertThat(items.get(0))
                .containsEntry("rfqNo", "RFQ-2026-001")
                .containsEntry("normalizedRfqNo", "RFQ-2026-001")
                .containsEntry("normalizedPartNo", "CROSS-ORDER-01")
                .containsEntry("normalizedVersion", "B");
    }
}
