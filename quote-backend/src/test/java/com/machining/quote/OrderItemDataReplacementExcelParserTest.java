package com.machining.quote;

import com.machining.quote.dto.OrderItemDataReplacementExcelParseResult;
import com.machining.quote.dto.OrderItemDataReplacementExcelRowDTO;
import com.machining.quote.dto.OrderItemDataReplacementField;
import com.machining.quote.service.OrderItemDataReplacementExcelParser;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OrderItemDataReplacementExcelParserTest {
    private final OrderItemDataReplacementExcelParser parser = new OrderItemDataReplacementExcelParser();

    @Test
    void parsesRequestedEditableOrderFieldsAndIgnoresReleaseOnlyHeaders() throws Exception {
        byte[] workbook = workbookBytes(
                List.of("零件ID", "图号", "版本", "新图号", "新版本", "询价数量", "客户询价交期", "报价承诺交期", "来源单号", "通过数量", "可选状态", "提单交期"),
                List.of(row(1001, "P-OLD", "A", "P-NEW", "B", 12, "2026/10/01", "2026-10-05", "SO-1", 9, "可选", "2026-11-01")));

        OrderItemDataReplacementExcelParseResult result = parse(workbook);

        assertThat(result.getRecognizedFields())
                .containsExactly("partNo", "version", "qty", "leadTime", "quoteLeadTime");
        assertThat(result.getRows()).hasSize(1);
        OrderItemDataReplacementExcelRowDTO parsed = result.getRows().get(0);
        assertThat(parsed.getItemId()).isEqualTo(1001);
        assertThat(parsed.getPartNo()).isEqualTo("P-OLD");
        assertThat(parsed.getVersion()).isEqualTo("A");
        assertThat(parsed.getValues()).containsExactly(
                Map.entry("partNo", "P-NEW"),
                Map.entry("version", "B"),
                Map.entry("qty", 12),
                Map.entry("leadTime", "2026/10/01"),
                Map.entry("quoteLeadTime", "2026-10-05")
        );
        assertThat(parsed.getErrors()).isEmpty();
    }

    @Test
    void doesNotAcceptReleaseOnlyColumnsAsOrderReplacementFields() {
        assertThat(OrderItemDataReplacementField.fromHeader("来源单号")).isEmpty();
        assertThat(OrderItemDataReplacementField.fromHeader("通过数量")).isEmpty();
        assertThat(OrderItemDataReplacementField.fromHeader("可选状态")).isEmpty();
        assertThat(OrderItemDataReplacementField.fromHeader("提单交期")).isEmpty();
    }

    @Test
    void marksDuplicatePartVersionMatchRows() throws Exception {
        byte[] workbook = workbookBytes(
                List.of("图号", "版本", "询价数量"),
                List.of(
                        row(" p-001 ", "a", 1),
                        row("P-001", " A ", 2)));

        OrderItemDataReplacementExcelParseResult result = parse(workbook);

        assertThat(result.getRows()).hasSize(2);
        assertThat(result.getRows().get(0).getErrors()).singleElement()
                .satisfies(error -> assertThat(error.getMessage()).contains("重复行：2、3"));
        assertThat(result.getRows().get(1).getErrors()).singleElement()
                .satisfies(error -> assertThat(error.getMessage()).contains("重复行：2、3"));
    }

    private OrderItemDataReplacementExcelParseResult parse(byte[] workbook) {
        return parser.parse(new ByteArrayInputStream(workbook));
    }

    private static byte[] workbookBytes(List<String> headers, List<List<Object>> rows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("replacement");
            writeHeaders(sheet.createRow(0), headers);
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                Row dataRow = sheet.createRow(rowIndex + 1);
                List<Object> values = rows.get(rowIndex);
                for (int columnIndex = 0; columnIndex < values.size(); columnIndex++) {
                    writeCell(dataRow.createCell(columnIndex), values.get(columnIndex));
                }
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private static void writeHeaders(Row row, List<String> headers) {
        for (int columnIndex = 0; columnIndex < headers.size(); columnIndex++) {
            row.createCell(columnIndex).setCellValue(headers.get(columnIndex));
        }
    }

    private static void writeCell(Cell cell, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
            return;
        }
        cell.setCellValue(String.valueOf(value));
    }

    private static List<Object> row(Object... values) {
        return Arrays.asList(values);
    }
}
