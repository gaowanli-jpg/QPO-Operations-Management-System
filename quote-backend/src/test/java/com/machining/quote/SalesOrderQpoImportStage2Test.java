package com.machining.quote;

import com.machining.quote.dto.SalesOrderQpoExcelLineDTO;
import com.machining.quote.dto.SalesOrderQpoExcelParseResult;
import com.machining.quote.dto.SalesOrderQpoOrderGroupDTO;
import com.machining.quote.service.SalesOrderQpoExcelParser;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SalesOrderQpoImportStage2Test {
    private final SalesOrderQpoExcelParser parser = new SalesOrderQpoExcelParser();

    @Test
    void parsesMarketTemplateAndGroupsRowsByCustomerOrderNoAndInternalProjectNo() throws Exception {
        SalesOrderQpoExcelParseResult result = parser.parse(new ByteArrayInputStream(workbookBytes(List.of(
                row(1, LocalDate.of(2026, 7, 1), "PO-001", "CP-001", "GSQ1", "\u6837\u54c1\u8ba2\u5355", "IP-001",
                        LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 5), "P-001", 10, "\u652f\u6491\u8154", "6061", "\u9633\u6781", "A",
                        "", "", "", LocalDate.of(2026, 8, 9), "\u9996\u6279"),
                row(2, "2026/07/01", "PO-001", "CP-001", "GSQ1", "\u6837\u54c1\u8ba2\u5355", "IP-001",
                        "2026-08-02", "2026-08-06", "P-002", 4, "\u8fde\u63a5\u677f", "7075", "/", "B",
                        "12.00", "10.50", "42.00", "", ""),
                row(3, LocalDate.of(2026, 7, 2), "PO-001", "", "GSQ2", "\u6279\u91cf\u8ba2\u5355", "IP-002",
                        "", "", "P-003", 5, "", "", "", "C",
                        "20", "18", "", "", "")
        ))));

        assertThat(result.getSourceRowCount()).isEqualTo(3);
        assertThat(result.getOrderCount()).isEqualTo(2);
        assertThat(result.getLineCount()).isEqualTo(3);

        SalesOrderQpoOrderGroupDTO firstGroup = result.getOrders().get(0);
        assertThat(firstGroup.getGroupKey()).isEqualTo("PO-001|IP-001");
        assertThat(firstGroup.getCustomerOrderNo()).isEqualTo("PO-001");
        assertThat(firstGroup.getInternalProjectNo()).isEqualTo("IP-001");
        assertThat(firstGroup.getSalesOrderDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(firstGroup.getCustomerProjectNo()).isEqualTo("CP-001");
        assertThat(firstGroup.getCustomerOrderTitle()).isEqualTo("GSQ1");
        assertThat(firstGroup.getOrderType()).isEqualTo("\u6837\u54c1\u8ba2\u5355");
        assertThat(firstGroup.getLines()).hasSize(2);

        SalesOrderQpoExcelLineDTO firstLine = firstGroup.getLines().get(0);
        assertThat(firstLine.getSourceRowNumber()).isEqualTo(2);
        assertThat(firstLine.getSalesOrderLineNo()).isEqualTo(1);
        assertThat(firstLine.getSalesMaterialNo()).isNull();
        assertThat(firstLine.getPartNo()).isEqualTo("P-001");
        assertThat(firstLine.getVersion()).isEqualTo("A");
        assertThat(firstLine.getQty()).isEqualTo(10);
        assertThat(firstLine.getShippedQty()).isZero();
        assertThat(firstLine.getUnshippedQty()).isEqualTo(10);
        assertThat(firstLine.getCustomerDeliveryDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(firstLine.getInternalDeliveryDate()).isEqualTo(LocalDate.of(2026, 8, 5));
        assertThat(firstLine.getActualShipDate()).isEqualTo(LocalDate.of(2026, 8, 9));
        assertThat(firstLine.getTaxIncludedUnitPrice()).isNull();
        assertThat(firstLine.getUntaxedUnitPrice()).isNull();
        assertThat(firstLine.getTaxIncludedTotalPrice()).isNull();
        assertThat(firstLine.getUntaxedTotalPrice()).isNull();

        SalesOrderQpoExcelLineDTO secondLine = firstGroup.getLines().get(1);
        assertThat(secondLine.getUntaxedTotalPrice()).isEqualByComparingTo(new BigDecimal("42.00"));
        assertThat(secondLine.getUnshippedQty()).isEqualTo(4);

        SalesOrderQpoOrderGroupDTO secondGroup = result.getOrders().get(1);
        assertThat(secondGroup.getGroupKey()).isEqualTo("PO-001|IP-002");
        assertThat(secondGroup.getLines()).hasSize(1);
    }

    @Test
    void rejectsMissingRequiredHeadersWithClearMessage() {
        List<String> headers = fullHeaders().stream()
                .filter(header -> !header.equals("\u56fe\u53f7"))
                .toList();

        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(workbookBytes(headers, List.of()))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("\u7f3a\u5931\u8868\u5934")
                .hasMessageContaining("\u56fe\u53f7");
    }

    @Test
    void ignoresLegacyMaterialAndShippingColumnsWhenOldTemplateIsUploaded() throws Exception {
        List<String> legacyHeaders = new java.util.ArrayList<>(fullHeaders());
        legacyHeaders.add(6, "\u6599\u53f7");
        legacyHeaders.add(12, "\u5df2\u51fa\u6570\u91cf");
        legacyHeaders.add(13, "\u672a\u51fa\u6570\u91cf");

        SalesOrderQpoExcelParseResult result = parser.parse(new ByteArrayInputStream(workbookBytes(legacyHeaders, List.of(
                row(1, LocalDate.of(2026, 7, 1), "PO-001", "", "", "\u6837\u54c1\u8ba2\u5355", "LEGACY-MAT", "IP-001",
                        "", "", "P-001", 10, 2, 8, "", "", "", "A", "", "", "", "", "")
        ))));

        SalesOrderQpoExcelLineDTO line = result.getOrders().get(0).getLines().get(0);
        assertThat(line.getSalesMaterialNo()).isNull();
        assertThat(line.getShippedQty()).isZero();
        assertThat(line.getUnshippedQty()).isEqualTo(10);
    }

    @Test
    void rejectsInconsistentHeaderFieldsInsideSameGroup() {
        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(workbookBytes(List.of(
                row(1, LocalDate.of(2026, 7, 1), "PO-001", "", "", "\u6837\u54c1\u8ba2\u5355", "IP-001",
                        "", "", "P-001", 10, "", "", "", "A", "11", "10", "", "", ""),
                row(2, LocalDate.of(2026, 7, 1), "PO-001", "", "", "\u6279\u91cf\u8ba2\u5355", "IP-001",
                        "", "", "P-002", 10, "", "", "", "A", "11", "10", "", "", "")
        )))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("\u7b2c3\u884c")
                .hasMessageContaining("\u8ba2\u5355\u7c7b\u578b")
                .hasMessageContaining("\u540c\u4e00\u8ba2\u5355\u5206\u7ec4");
    }

    private static List<Object> row(Object... values) {
        return List.of(values);
    }

    private static byte[] workbookBytes(List<List<Object>> rows) throws Exception {
        return workbookBytes(fullHeaders(), rows);
    }

    private static byte[] workbookBytes(List<String> headers, List<List<Object>> rows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            CreationHelper creationHelper = workbook.getCreationHelper();
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(creationHelper.createDataFormat().getFormat("yyyy-mm-dd"));
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("sales-order");
            Row headerRow = sheet.createRow(0);
            for (int col = 0; col < headers.size(); col++) {
                headerRow.createCell(col).setCellValue(headers.get(col));
            }
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                Row sheetRow = sheet.createRow(rowIndex + 1);
                List<Object> values = rows.get(rowIndex);
                for (int col = 0; col < values.size(); col++) {
                    writeCell(sheetRow.createCell(col), values.get(col), dateStyle);
                }
            }
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private static void writeCell(Cell cell, Object value, CellStyle dateStyle) {
        if (value == null) {
            return;
        }
        if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
            return;
        }
        if (value instanceof LocalDate date) {
            cell.setCellValue(Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant()));
            cell.setCellStyle(dateStyle);
            return;
        }
        cell.setCellValue(String.valueOf(value));
    }

    private static List<String> fullHeaders() {
        return List.of(
                "\u5e8f\u53f7",
                "\u4e0b\u5355\u65e5\u671f",
                "\u5ba2\u6237\u8ba2\u5355\u53f7",
                "\u5ba2\u6237\n\u9879\u76ee\u53f7",
                "\u5ba2\u6237\u4e0b\u5355\u62ac\u5934",
                "\u8ba2\u5355\n\u7c7b\u578b",
                "\u5185\u90e8\u9879\u76ee\u53f7",
                "\u5ba2\u6237\u4ea4\u671f",
                "\u5185\u90e8\u4ea4\u671f\n(\u7ed9\u751f\u4ea7\u7684\u4ea4\u671f)",
                "\u56fe\u53f7",
                "\u8ba2\u5355\u6570\u91cf",
                "\u63cf\u8ff0",
                "\u6750\u6599",
                "\u8868\u9762\u5904\u7406",
                "\u7248\u672c",
                "VAT\u5355\u4ef7-\n\u542b\u7a0e",
                "\u672a\u7a0e\u5355\u4ef7(CNY)",
                "\uff08\u672a\u7a0e\uff09\u91d1\u989d",
                "\u5b9e\u9645\n\u51fa\u8d27\u65e5\u671f",
                "\u5907\u6ce8"
        );
    }
}
