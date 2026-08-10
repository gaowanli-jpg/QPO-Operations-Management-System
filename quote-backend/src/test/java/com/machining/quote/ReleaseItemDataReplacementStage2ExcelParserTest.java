package com.machining.quote;

import com.machining.quote.dto.ReleaseItemDataReplacementExcelParseResult;
import com.machining.quote.dto.ReleaseItemDataReplacementExcelRowDTO;
import com.machining.quote.service.ReleaseItemDataReplacementExcelParser;
import com.machining.quote.service.SalesOrderQpoExcelParser;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReleaseItemDataReplacementStage2ExcelParserTest {
    private final ReleaseItemDataReplacementExcelParser parser = new ReleaseItemDataReplacementExcelParser();

    @Test
    void parsesMinimalReplacementSubsetAfterPreambleWithHeadersInAnyOrder() throws Exception {
        byte[] workbook = workbookBytes(
                2,
                List.of("内部　目标交期", "版本", "忽略列", "图号"),
                List.of(row("2026/8/15", " a ", "ignored", " p-001 ")));

        ReleaseItemDataReplacementExcelParseResult result = parse(workbook);

        assertThat(result.getHeaderRowNumber()).isEqualTo(3);
        assertThat(result.getRecognizedFields())
                .containsExactly("internalTargetLeadTime");
        assertThat(result.getRows()).hasSize(1);
        ReleaseItemDataReplacementExcelRowDTO parsed = result.getRows().get(0);
        assertThat(parsed.getExcelRowNumber()).isEqualTo(4);
        assertThat(parsed.getPartNo()).isEqualTo("p-001");
        assertThat(parsed.getVersion()).isEqualTo("a");
        assertThat(parsed.getNormalizedPartNo()).isEqualTo("P-001");
        assertThat(parsed.getNormalizedVersion()).isEqualTo("A");
        assertThat(parsed.getValues())
                .containsExactly(Map.entry("internalTargetLeadTime", LocalDate.of(2026, 8, 15)));
        assertThat(parsed.getErrors()).isEmpty();
    }

    @Test
    void blankOptionalCellsDoNotCreateClearOperationsAndUnknownOnlyRowsAreIgnored() throws Exception {
        byte[] workbook = workbookBytes(
                0,
                List.of("图号", "版本", "物料描述", "备注", "未知列"),
                List.of(
                        row("P-001", "A", "  ", null, "ignored"),
                        row(null, null, null, null, "unknown-only"),
                        row("P-002", "B", "新描述", "", null)));

        ReleaseItemDataReplacementExcelParseResult result = parse(workbook);

        assertThat(result.getRows()).hasSize(2);
        assertThat(result.getRows().get(0).getValues()).isEmpty();
        assertThat(result.getRows().get(1).getValues())
                .containsExactly(Map.entry("description", "新描述"));
    }

    @Test
    void evaluatesFormulaCellsForMatchDateTextAndAcceptedQuantity() throws Exception {
        byte[] workbook;
        try (XSSFWorkbook source = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            CreationHelper creationHelper = source.getCreationHelper();
            CellStyle dateStyle = source.createCellStyle();
            dateStyle.setDataFormat(creationHelper.createDataFormat().getFormat("yyyy-mm-dd"));
            Sheet sheet = source.createSheet("replacement");
            writeHeaders(sheet.createRow(0), List.of("图号", "版本", "内部目标交期", "物料描述", "通过数量"));
            Row data = sheet.createRow(1);
            data.createCell(0).setCellFormula("\"P-\"&\"003\"");
            data.createCell(1).setCellFormula("\"c\"");
            data.createCell(2).setCellFormula("DATE(2026,8,16)");
            data.getCell(2).setCellStyle(dateStyle);
            data.createCell(3).setCellFormula("\"公式描述\"");
            data.createCell(4).setCellFormula("1+2");
            source.getCreationHelper().createFormulaEvaluator().evaluateAll();
            source.write(output);
            workbook = output.toByteArray();
        }

        ReleaseItemDataReplacementExcelRowDTO parsed = parse(workbook).getRows().get(0);

        assertThat(parsed.getNormalizedPartNo()).isEqualTo("P-003");
        assertThat(parsed.getNormalizedVersion()).isEqualTo("C");
        assertThat(parsed.getValues())
                .containsEntry("internalTargetLeadTime", LocalDate.of(2026, 8, 16))
                .containsEntry("description", "公式描述")
                .containsEntry("acceptedQty", 3);
        assertThat(parsed.getErrors()).isEmpty();
    }

    @Test
    void acceptsTheSalesOrderDateFormatsAndKeepsSalesOrderParserOnTheSameRules() throws Exception {
        byte[] replacementWorkbook = workbookBytes(
                0,
                List.of("图号", "版本", "内部目标交期", "下单日期"),
                List.of(
                        row("P-001", "A", "2026-8-1", "2026/8/2"),
                        row("P-002", "A", "2026.8.3", "2026年8月4日"),
                        row("P-003", "A", LocalDate.of(2026, 8, 5), 46240)));

        ReleaseItemDataReplacementExcelParseResult result = parse(replacementWorkbook);

        assertThat(result.getRows()).extracting(row -> row.getValues().get("internalTargetLeadTime"))
                .containsExactly(
                        LocalDate.of(2026, 8, 1),
                        LocalDate.of(2026, 8, 3),
                        LocalDate.of(2026, 8, 5));
        assertThat(result.getRows()).extracting(row -> row.getValues().get("salesOrderDate"))
                .containsExactly(
                        LocalDate.of(2026, 8, 2),
                        LocalDate.of(2026, 8, 4),
                        LocalDate.of(2026, 8, 6));

        byte[] salesOrderWorkbook = workbookBytes(
                0,
                List.of("图号", "订单数量", "版本", "下单日期"),
                List.of(row("SO-001", 1, "A", "2026/8/2")));
        assertThat(new SalesOrderQpoExcelParser()
                .parse(new ByteArrayInputStream(salesOrderWorkbook))
                .getOrders().get(0).getLines().get(0).getSalesOrderDate())
                .isEqualTo(LocalDate.of(2026, 8, 2));
    }

    @Test
    void reportsInvalidDateAndQuantityWithExactRowColumnAndField() throws Exception {
        byte[] workbook = workbookBytes(
                0,
                List.of("版本", "图号", "通过数量", "下单日期"),
                List.of(
                        row("A", "P-001", 1.5, "2026-02-30"),
                        row("B", "P-002", 0, "not-a-date"),
                        row("C", "P-003", "abc", null)));

        ReleaseItemDataReplacementExcelParseResult result = parse(workbook);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getRows()).hasSize(3);
        assertError(result.getRows().get(0), "acceptedQty", 3, "正整数");
        assertError(result.getRows().get(0), "salesOrderDate", 4, "有效日期");
        assertError(result.getRows().get(1), "acceptedQty", 3, "大于 0");
        assertError(result.getRows().get(1), "salesOrderDate", 4, "有效日期");
        assertError(result.getRows().get(2), "acceptedQty", 3, "有效数字");
    }

    @Test
    void reportsMissingMatchKeysAndInvalidTextOrOrderTypeWithoutDiscardingOtherRows() throws Exception {
        byte[] workbook = workbookBytes(
                0,
                List.of("图号", "版本", "订单类型", "客户订单号"),
                List.of(
                        row(null, "A", "正式订单", "CO-1"),
                        row("P-002", null, "普通订单", "CO-2"),
                        row("P-003", "C", "预测订单", "X".repeat(121))));

        ReleaseItemDataReplacementExcelParseResult result = parse(workbook);

        assertThat(result.getRows()).hasSize(3);
        assertError(result.getRows().get(0), "partNo", 1, "不能为空");
        assertError(result.getRows().get(1), "version", 2, "不能为空");
        assertError(result.getRows().get(1), "orderType", 3, "预测订单 / 备货订单 / 正式订单");
        assertError(result.getRows().get(2), "customerOrderNo", 4, "120");
    }

    @Test
    void marksEveryDuplicateNormalizedPartVersionWithAllExcelRowNumbers() throws Exception {
        byte[] workbook = workbookBytes(
                0,
                List.of("图号", "版本", "备注"),
                List.of(
                        row(" p-001 ", "a", "first"),
                        row("P-002", "A", "other"),
                        row("P-001", " A ", "duplicate")));

        ReleaseItemDataReplacementExcelParseResult result = parse(workbook);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getRows().get(0).getErrors()).singleElement()
                .satisfies(error -> assertThat(error.getMessage()).contains("重复行：2、4"));
        assertThat(result.getRows().get(2).getErrors()).singleElement()
                .satisfies(error -> assertThat(error.getMessage()).contains("重复行：2、4"));
        assertThat(result.getRows().get(1).getErrors()).isEmpty();
    }

    @Test
    void rejectsDuplicateStandardHeadersWithOneBasedColumnNumbers() throws Exception {
        byte[] workbook = workbookBytes(
                0,
                List.of("图号", "版本", "备注", " 图\n号 "),
                List.of(row("P-001", "A", "remark", "P-001")));

        assertThatThrownBy(() -> parse(workbook))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("重复表头")
                .hasMessageContaining("图号")
                .hasMessageContaining("第1、4列");
    }

    @Test
    void rejectsMissingReplacementHeadersAndHeadersOutsideFirstTwentyRows() throws Exception {
        byte[] noReplacement = workbookBytes(
                0,
                List.of("图号", "版本", "未知列"),
                List.of(row("P-001", "A", "ignored")));
        byte[] lateHeader = workbookBytes(
                20,
                List.of("图号", "版本", "备注"),
                List.of(row("P-001", "A", "remark")));

        assertThatThrownBy(() -> parse(noReplacement))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("至少需要一个可替换业务表头");
        assertThatThrownBy(() -> parse(lateHeader))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("前20行")
                .hasMessageContaining("图号")
                .hasMessageContaining("版本");
    }

    @Test
    void rejectsMoreThanTenThousandEffectiveRows() throws Exception {
        List<List<Object>> rows = java.util.stream.IntStream.rangeClosed(1, 10_001)
                .mapToObj(index -> row("P-" + index, "A", "remark"))
                .toList();
        byte[] workbook = workbookBytes(0, List.of("图号", "版本", "备注"), rows);

        assertThatThrownBy(() -> parse(workbook))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10,000");
    }

    private ReleaseItemDataReplacementExcelParseResult parse(byte[] workbook) {
        return parser.parse(new ByteArrayInputStream(workbook));
    }

    private static void assertError(
            ReleaseItemDataReplacementExcelRowDTO row,
            String field,
            int columnNumber,
            String messagePart) {
        assertThat(row.getErrors())
                .filteredOn(error -> field.equals(error.getField()))
                .singleElement()
                .satisfies(error -> {
                    assertThat(error.getExcelRowNumber()).isEqualTo(row.getExcelRowNumber());
                    assertThat(error.getColumnNumber()).isEqualTo(columnNumber);
                    assertThat(error.getMessage()).contains(messagePart);
                });
    }

    private static byte[] workbookBytes(
            int headerRowIndex,
            List<String> headers,
            List<List<Object>> rows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            CreationHelper creationHelper = workbook.getCreationHelper();
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(creationHelper.createDataFormat().getFormat("yyyy-mm-dd"));
            Sheet sheet = workbook.createSheet("replacement");
            for (int rowIndex = 0; rowIndex < headerRowIndex; rowIndex++) {
                sheet.createRow(rowIndex).createCell(0).setCellValue("说明 " + rowIndex);
            }
            writeHeaders(sheet.createRow(headerRowIndex), headers);
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                Row dataRow = sheet.createRow(headerRowIndex + rowIndex + 1);
                List<Object> values = rows.get(rowIndex);
                for (int columnIndex = 0; columnIndex < values.size(); columnIndex++) {
                    writeCell(dataRow.createCell(columnIndex), values.get(columnIndex), dateStyle);
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

    private static void writeCell(Cell cell, Object value, CellStyle dateStyle) {
        if (value == null) {
            return;
        }
        if (value instanceof LocalDate date) {
            cell.setCellValue(Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant()));
            cell.setCellStyle(dateStyle);
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
