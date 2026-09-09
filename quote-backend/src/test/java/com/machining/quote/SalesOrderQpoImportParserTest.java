package com.machining.quote;

import com.machining.quote.dto.SalesOrderQpoExcelLineDTO;
import com.machining.quote.dto.SalesOrderQpoExcelParseResult;
import com.machining.quote.dto.SalesOrderQpoImportDefaultsDTO;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SalesOrderQpoImportParserTest {
    private final SalesOrderQpoExcelParser parser = new SalesOrderQpoExcelParser();

    @Test
    void parsesRequiredHeadersOnlyAndBuildsSingleOrderFromPageDefaults() throws Exception {
        SalesOrderQpoImportDefaultsDTO defaults = defaults();
        SalesOrderQpoExcelParseResult result = parser.parse(
                new ByteArrayInputStream(workbookBytes(minimalHeaders(), List.of(
                        row("P-001", 10, "A")
                ))),
                defaults);

        assertThat(result.getSourceRowCount()).isEqualTo(1);
        assertThat(result.getOrderCount()).isEqualTo(1);
        assertThat(result.getLineCount()).isEqualTo(1);
        assertThat(result.getOrders()).hasSize(1);

        SalesOrderQpoOrderGroupDTO group = result.getOrders().get(0);
        assertThat(group.getGroupKey()).isEqualTo("PO-001|IP-001");
        assertThat(group.getCustomerOrderNo()).isEqualTo("PO-001");
        assertThat(group.getInternalProjectNo()).isEqualTo("IP-001");
        assertThat(group.getSalesOrderDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(group.getCustomerProjectNo()).isEqualTo("CP-001");
        assertThat(group.getCustomerOrderTitle()).isEqualTo("默认抬头");
        assertThat(group.getOrderType()).isEqualTo("正式订单");
        assertThat(group.getLines()).hasSize(1);

        SalesOrderQpoExcelLineDTO line = group.getLines().get(0);
        assertThat(line.getPartNo()).isEqualTo("P-001");
        assertThat(line.getQty()).isEqualTo(10);
        assertThat(line.getVersion()).isEqualTo("A");
        assertThat(line.getSalesOrderDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(line.getCustomerOrderNo()).isEqualTo("PO-001");
        assertThat(line.getCustomerProjectNo()).isEqualTo("CP-001");
        assertThat(line.getCustomerOrderTitle()).isEqualTo("默认抬头");
        assertThat(line.getOrderType()).isEqualTo("正式订单");
        assertThat(line.getInternalProjectNo()).isEqualTo("IP-001");
        assertThat(line.getTaxIncludedUnitPrice()).isNull();
        assertThat(line.getTaxIncludedTotalPrice()).isNull();
        assertThat(line.getUntaxedUnitPrice()).isNull();
        assertThat(line.getUntaxedTotalPrice()).isNull();
    }

    @Test
    void lineValuesOverrideDefaultsAndBlankCellsFallbackToPageDefaults() throws Exception {
        SalesOrderQpoImportDefaultsDTO defaults = defaults();
        SalesOrderQpoExcelParseResult result = parser.parse(
                new ByteArrayInputStream(workbookBytes(fullHeaders(), List.of(
                        row(1, LocalDate.of(2026, 7, 2), "ROW-PO-001", "ROW-CP-001", "ROW-TITLE",
                                "样品订单", "ROW-IP-001", null, null, "P-001", 10, null, null, null,
                                "A", null, null, null, null),
                        row(2, null, null, null, null, null, null, null, null, "P-002", 5, null,
                                null, null, "B", null, null, null, null)
                ))),
                defaults);

        assertThat(result.getOrderCount()).isEqualTo(1);
        assertThat(result.getLineCount()).isEqualTo(2);
        assertThat(result.getOrders()).hasSize(1);

        SalesOrderQpoOrderGroupDTO group = result.getOrders().get(0);
        assertThat(group.getCustomerOrderNo()).isEqualTo("PO-001");
        assertThat(group.getInternalProjectNo()).isEqualTo("IP-001");
        assertThat(group.getSalesOrderDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(group.getCustomerProjectNo()).isEqualTo("CP-001");
        assertThat(group.getCustomerOrderTitle()).isEqualTo("默认抬头");
        assertThat(group.getOrderType()).isEqualTo("正式订单");

        SalesOrderQpoExcelLineDTO firstLine = group.getLines().get(0);
        assertThat(firstLine.getSalesOrderDate()).isEqualTo(LocalDate.of(2026, 7, 2));
        assertThat(firstLine.getCustomerOrderNo()).isEqualTo("ROW-PO-001");
        assertThat(firstLine.getCustomerProjectNo()).isEqualTo("ROW-CP-001");
        assertThat(firstLine.getCustomerOrderTitle()).isEqualTo("ROW-TITLE");
        assertThat(firstLine.getOrderType()).isEqualTo("样品订单");
        assertThat(firstLine.getInternalProjectNo()).isEqualTo("ROW-IP-001");

        SalesOrderQpoExcelLineDTO secondLine = group.getLines().get(1);
        assertThat(secondLine.getSalesOrderDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(secondLine.getCustomerOrderNo()).isEqualTo("PO-001");
        assertThat(secondLine.getCustomerProjectNo()).isEqualTo("CP-001");
        assertThat(secondLine.getCustomerOrderTitle()).isEqualTo("默认抬头");
        assertThat(secondLine.getOrderType()).isEqualTo("正式订单");
        assertThat(secondLine.getInternalProjectNo()).isEqualTo("IP-001");
    }

    @Test
    void ignoresCustomerExcelSequenceWhenParsingSalesOrderRows() throws Exception {
        SalesOrderQpoExcelParseResult result = parser.parse(
                new ByteArrayInputStream(workbookBytes(fullHeaders(), List.of(
                        row(10, LocalDate.of(2026, 7, 2), "ROW-PO-001", "ROW-CP-001", "ROW-TITLE",
                                "样品订单", "ROW-IP-001", null, null, "P-001", 10, null, null, null,
                                "A", null, null, null, null),
                        row(10, null, null, null, null, null, null, null, null, "P-002", 5, null,
                                null, null, "B", null, null, null, null),
                        row(99, null, null, null, null, null, null, null, null, "P-003", 2, null,
                                null, null, "C", null, null, null, null)
                ))),
                defaults());

        assertThat(result.getOrders().get(0).getLines())
                .extracting(SalesOrderQpoExcelLineDTO::getSalesOrderLineNo)
                .containsExactly(null, null, null);
        assertThat(result.getOrders().get(0).getLines())
                .extracting(SalesOrderQpoExcelLineDTO::getSourceRowNumber)
                .containsExactly(2, 3, 4);
    }

    @Test
    void parsesDocumentedSalesOrderDateFormats() throws Exception {
        List<Object> dateValues = List.of(
                "2026-03-08",
                "2026-3-8",
                "2026.03.08",
                "2026.3.8",
                "2026/3/8",
                "2026年3月8日",
                46089,
                "46089"
        );
        List<List<Object>> rows = new java.util.ArrayList<>();
        for (int i = 0; i < dateValues.size(); i++) {
            Object dateValue = dateValues.get(i);
            rows.add(row(i + 1, dateValue, null, null, null, null, null, dateValue, dateValue,
                    "P-DATE-" + i, 1, null, null, null, "A", null, null, null, null));
        }

        SalesOrderQpoExcelParseResult result = parser.parse(
                new ByteArrayInputStream(workbookBytes(fullHeaders(), rows)),
                defaults());

        assertThat(result.getLineCount()).isEqualTo(dateValues.size());
        for (SalesOrderQpoExcelLineDTO line : result.getOrders().get(0).getLines()) {
            assertThat(line.getSalesOrderDate()).isEqualTo(LocalDate.of(2026, 3, 8));
            assertThat(line.getCustomerDeliveryDate()).isEqualTo(LocalDate.of(2026, 3, 8));
            assertThat(line.getInternalDeliveryDate()).isEqualTo(LocalDate.of(2026, 3, 8));
        }
    }

    @Test
    void computesDocumentedPriceTotalsFromProvidedUnitPrices() throws Exception {
        SalesOrderQpoExcelParseResult result = parser.parse(
                new ByteArrayInputStream(workbookBytes(fullHeaders(), List.of(
                        row(1, LocalDate.of(2026, 7, 2), "ROW-PO-001", "ROW-CP-001", "ROW-TITLE",
                                "样品订单", "ROW-IP-001", null, null, "P-PRICE-001", 3, null, null, null,
                                "A", "12.34", "10.50", null, null)
                ))),
                defaults());

        SalesOrderQpoExcelLineDTO line = result.getOrders().get(0).getLines().get(0);
        assertThat(line.getTaxIncludedUnitPrice()).isEqualByComparingTo("12.34");
        assertThat(line.getTaxIncludedTotalPrice()).isEqualByComparingTo("37.02");
        assertThat(line.getUntaxedUnitPrice()).isEqualByComparingTo("10.50");
        assertThat(line.getUntaxedTotalPrice()).isEqualByComparingTo("31.50");
    }

    @Test
    void rejectsMissingRequiredHeadersWithClearMessage() {
        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(workbookBytes(
                List.of("订单数量", "版本"),
                List.of(row(10, "A"))
        ))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("缺失表头")
                .hasMessageContaining("图号");
    }

    private static SalesOrderQpoImportDefaultsDTO defaults() {
        SalesOrderQpoImportDefaultsDTO defaults = new SalesOrderQpoImportDefaultsDTO();
        defaults.setSalesOrderDate(LocalDate.of(2026, 7, 1));
        defaults.setCustomerOrderNo("PO-001");
        defaults.setCustomerProjectNo("CP-001");
        defaults.setCustomerOrderTitle("默认抬头");
        defaults.setOrderType("正式订单");
        defaults.setInternalProjectNo("IP-001");
        return defaults;
    }

    private static List<Object> row(Object... values) {
        return Arrays.asList(values);
    }

    private static byte[] workbookBytes(List<List<Object>> rows) throws Exception {
        return workbookBytes(fullHeaders(), rows);
    }

    private static List<String> minimalHeaders() {
        return List.of("图号", "订单数量", "版本");
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
                "序号",
                "下单日期",
                "客户订单号",
                "客户项目号",
                "客户下单抬头",
                "订单类型",
                "内部项目号",
                "客户交期",
                "内部交期(给生产的交期)",
                "图号",
                "订单数量",
                "描述",
                "材料",
                "表面处理",
                "版本",
                "VAT单价-含税",
                "未税单价(CNY)",
                "（未税）金额",
                "备注"
        );
    }
}
