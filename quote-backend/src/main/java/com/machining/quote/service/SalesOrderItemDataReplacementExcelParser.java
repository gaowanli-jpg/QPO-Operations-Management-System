package com.machining.quote.service;

import com.machining.quote.dto.SalesOrderItemDataReplacementField;
import com.machining.quote.dto.SalesOrderU9ItemDataReplacementExcelErrorDTO;
import com.machining.quote.dto.SalesOrderU9ItemDataReplacementExcelParseResult;
import com.machining.quote.dto.SalesOrderU9ItemDataReplacementExcelRowDTO;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class SalesOrderItemDataReplacementExcelParser {
    private static final int HEADER_SCAN_ROW_LIMIT = 20;
    private static final int EFFECTIVE_ROW_LIMIT = 10_000;

    public SalesOrderU9ItemDataReplacementExcelParseResult parse(InputStream inputStream) {
        if (inputStream == null) {
            throw new IllegalArgumentException("销售订单表格数据替换文件不能为空");
        }
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new IllegalArgumentException("销售订单表格数据替换文件没有工作表");
            }
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            DataFormatter formatter = new DataFormatter(Locale.CHINA, true);
            Sheet sheet = workbook.getSheetAt(0);
            HeaderContext header = findHeader(sheet, formatter, evaluator);
            validateHeader(header);

            List<SalesOrderU9ItemDataReplacementExcelRowDTO> rows = parseRows(
                    sheet,
                    header,
                    formatter,
                    evaluator);
            markDuplicateKeys(rows);

            SalesOrderU9ItemDataReplacementExcelParseResult result = new SalesOrderU9ItemDataReplacementExcelParseResult();
            result.setHeaderRowNumber(header.rowIndex() + 1);
            result.setRecognizedFields(SalesOrderItemDataReplacementField.replaceableFields().stream()
                    .filter(header.columns()::containsKey)
                    .map(SalesOrderItemDataReplacementField::getPropertyName)
                    .toList());
            result.setRows(rows);
            return result;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("销售订单表格数据替换解析失败：" + safeMessage(e), e);
        }
    }

    private HeaderContext findHeader(
            Sheet sheet,
            DataFormatter formatter,
            FormulaEvaluator evaluator) {
        int lastScannedRow = Math.min(sheet.getLastRowNum(), HEADER_SCAN_ROW_LIMIT - 1);
        for (int rowIndex = 0; rowIndex <= lastScannedRow; rowIndex++) {
            HeaderContext header = readHeader(sheet.getRow(rowIndex), rowIndex, formatter, evaluator);
            if (header.columns().containsKey(SalesOrderItemDataReplacementField.PART_NO)
                    && header.columns().containsKey(SalesOrderItemDataReplacementField.VERSION)) {
                return header;
            }
        }
        throw new IllegalArgumentException("销售订单表格数据替换前20行未找到同时包含图号和版本的表头");
    }

    private HeaderContext readHeader(
            Row row,
            int rowIndex,
            DataFormatter formatter,
            FormulaEvaluator evaluator) {
        Map<SalesOrderItemDataReplacementField, List<Integer>> allColumns = new LinkedHashMap<>();
        if (row != null && row.getLastCellNum() > 0) {
            for (int columnIndex = 0; columnIndex < row.getLastCellNum(); columnIndex++) {
                int currentColumnIndex = columnIndex;
                SalesOrderItemDataReplacementField.fromHeader(cellText(row.getCell(columnIndex), formatter, evaluator))
                        .ifPresent(field -> allColumns
                                .computeIfAbsent(field, ignored -> new ArrayList<>())
                                .add(currentColumnIndex));
            }
        }
        Map<SalesOrderItemDataReplacementField, Integer> columns = new LinkedHashMap<>();
        allColumns.forEach((field, indexes) -> columns.put(field, indexes.get(0)));
        List<SalesOrderItemDataReplacementField> orderedFields = columns.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .toList();
        return new HeaderContext(rowIndex, columns, allColumns, orderedFields);
    }

    private void validateHeader(HeaderContext header) {
        List<String> duplicateHeaders = header.allColumns().entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(entry -> entry.getKey().getHeader() + "位于第"
                        + entry.getValue().stream()
                        .map(index -> String.valueOf(index + 1))
                        .collect(Collectors.joining("、"))
                        + "列")
                .toList();
        if (!duplicateHeaders.isEmpty()) {
            throw new IllegalArgumentException("销售订单表格数据替换存在重复表头：" + String.join("；", duplicateHeaders));
        }
        if (header.orderedFields().stream().noneMatch(SalesOrderItemDataReplacementField::isReplaceable)) {
            throw new IllegalArgumentException("销售订单表格数据替换至少需要一个可替换业务表头");
        }
    }

    private List<SalesOrderU9ItemDataReplacementExcelRowDTO> parseRows(
            Sheet sheet,
            HeaderContext header,
            DataFormatter formatter,
            FormulaEvaluator evaluator) {
        List<SalesOrderU9ItemDataReplacementExcelRowDTO> rows = new ArrayList<>();
        for (int rowIndex = header.rowIndex() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (!hasRecognizedContent(row, header, formatter, evaluator)) {
                continue;
            }
            if (rows.size() >= EFFECTIVE_ROW_LIMIT) {
                throw new IllegalArgumentException("销售订单表格数据替换有效数据不能超过 10,000 行");
            }
            rows.add(parseRow(row, rowIndex + 1, header, formatter, evaluator));
        }
        return rows;
    }

    private SalesOrderU9ItemDataReplacementExcelRowDTO parseRow(
            Row row,
            int excelRowNumber,
            HeaderContext header,
            DataFormatter formatter,
            FormulaEvaluator evaluator) {
        SalesOrderU9ItemDataReplacementExcelRowDTO parsed = new SalesOrderU9ItemDataReplacementExcelRowDTO();
        parsed.setExcelRowNumber(excelRowNumber);
        String partNo = textValue(row, header, SalesOrderItemDataReplacementField.PART_NO, formatter, evaluator);
        String version = textValue(row, header, SalesOrderItemDataReplacementField.VERSION, formatter, evaluator);
        parsed.setPartNo(partNo);
        parsed.setVersion(version);
        parsed.setNormalizedPartNo(normalizeIdentity(partNo));
        parsed.setNormalizedVersion(normalizeIdentity(version));

        requireMatchKey(parsed, header, SalesOrderItemDataReplacementField.PART_NO, partNo);
        parseLineNo(parsed, row, header, formatter, evaluator);

        for (SalesOrderItemDataReplacementField field : header.orderedFields()) {
            if (!field.isReplaceable()) {
                continue;
            }
            Cell cell = cell(row, header, field);
            if (cellText(cell, formatter, evaluator).isEmpty()) {
                continue;
            }
            parseReplacementValue(parsed, field, cell, header, formatter, evaluator);
        }
        return parsed;
    }

    private void parseLineNo(
            SalesOrderU9ItemDataReplacementExcelRowDTO parsed,
            Row row,
            HeaderContext header,
            DataFormatter formatter,
            FormulaEvaluator evaluator) {
        if (!header.columns().containsKey(SalesOrderItemDataReplacementField.LINE_NO)) {
            return;
        }
        Cell cell = cell(row, header, SalesOrderItemDataReplacementField.LINE_NO);
        if (cellText(cell, formatter, evaluator).isEmpty()) {
            return;
        }
        try {
            parsed.setLineNo(parsePositiveInteger(cell, formatter, evaluator));
        } catch (IllegalArgumentException e) {
            addError(parsed, header, SalesOrderItemDataReplacementField.LINE_NO, safeMessage(e));
        }
    }

    private void parseReplacementValue(
            SalesOrderU9ItemDataReplacementExcelRowDTO row,
            SalesOrderItemDataReplacementField field,
            Cell cell,
            HeaderContext header,
            DataFormatter formatter,
            FormulaEvaluator evaluator) {
        try {
            Object value = switch (field.getValueType()) {
                case DATE -> parseDate(cell, formatter, evaluator);
                case POSITIVE_INTEGER -> parsePositiveInteger(cell, formatter, evaluator);
                case NON_NEGATIVE_DECIMAL -> parseNonNegativeDecimal(cell, formatter, evaluator);
                case TEXT -> parseText(field, cellText(cell, formatter, evaluator));
            };
            if (value != null) {
                row.getValues().put(field.getPropertyName(), value);
            }
        } catch (IllegalArgumentException e) {
            addError(row, header, field, safeMessage(e));
        }
    }

    private LocalDate parseDate(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        return ExcelDateValueParser.parseOptional(cell, formatter, evaluator);
    }

    private Integer parsePositiveInteger(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        BigDecimal decimal = parseDecimal(cellText(cell, formatter, evaluator));
        int integer;
        try {
            integer = decimal.stripTrailingZeros().intValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("必须为正整数");
        }
        if (integer <= 0) {
            throw new IllegalArgumentException("必须大于 0");
        }
        return integer;
    }

    private BigDecimal parseNonNegativeDecimal(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        BigDecimal decimal = parseDecimal(cellText(cell, formatter, evaluator));
        if (decimal.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("必须大于等于 0");
        }
        return decimal;
    }

    private BigDecimal parseDecimal(String raw) {
        try {
            return new BigDecimal(raw.replace(",", ""));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("不是有效数字");
        }
    }

    private String parseText(SalesOrderItemDataReplacementField field, String value) {
        Integer maxLength = field.getMaxLength();
        if (maxLength != null && value.length() > maxLength) {
            throw new IllegalArgumentException("长度不能超过 " + maxLength + " 个字符");
        }
        return value;
    }

    private void requireMatchKey(
            SalesOrderU9ItemDataReplacementExcelRowDTO row,
            HeaderContext header,
            SalesOrderItemDataReplacementField field,
            String value) {
        if (value.isEmpty()) {
            addError(row, header, field, "不能为空");
        }
    }

    private void markDuplicateKeys(List<SalesOrderU9ItemDataReplacementExcelRowDTO> rows) {
        Map<String, List<SalesOrderU9ItemDataReplacementExcelRowDTO>> grouped = new LinkedHashMap<>();
        for (SalesOrderU9ItemDataReplacementExcelRowDTO row : rows) {
            if (row.getNormalizedPartNo().isEmpty() || row.hasErrors()) {
                continue;
            }
            String key = row.getNormalizedPartNo() + "||" + row.getNormalizedVersion()
                    + "||" + Objects.toString(row.getLineNo(), "");
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(row);
        }
        grouped.values().stream()
                .filter(group -> group.size() > 1)
                .forEach(group -> {
                    String duplicateRows = group.stream()
                            .map(row -> String.valueOf(row.getExcelRowNumber()))
                            .collect(Collectors.joining("、"));
                    for (SalesOrderU9ItemDataReplacementExcelRowDTO row : group) {
                        SalesOrderU9ItemDataReplacementExcelErrorDTO error = new SalesOrderU9ItemDataReplacementExcelErrorDTO();
                        error.setExcelRowNumber(row.getExcelRowNumber());
                        error.setField("partNoVersionLineNo");
                        error.setLabel("图号+版本+行号");
                        error.setMessage("图号、版本和行号重复，重复行：" + duplicateRows);
                        row.getErrors().add(error);
                    }
                });
    }

    private void addError(
            SalesOrderU9ItemDataReplacementExcelRowDTO row,
            HeaderContext header,
            SalesOrderItemDataReplacementField field,
            String reason) {
        int columnNumber = header.columns().get(field) + 1;
        SalesOrderU9ItemDataReplacementExcelErrorDTO error = new SalesOrderU9ItemDataReplacementExcelErrorDTO();
        error.setExcelRowNumber(row.getExcelRowNumber());
        error.setColumnNumber(columnNumber);
        error.setField(field.getPropertyName());
        error.setLabel(field.getHeader());
        error.setMessage("第" + row.getExcelRowNumber() + "行第" + columnNumber
                + "列字段[" + field.getHeader() + "]" + reason);
        row.getErrors().add(error);
    }

    private boolean hasRecognizedContent(
            Row row,
            HeaderContext header,
            DataFormatter formatter,
            FormulaEvaluator evaluator) {
        if (row == null) {
            return false;
        }
        return header.orderedFields().stream()
                .map(field -> cell(row, header, field))
                .map(cell -> cellText(cell, formatter, evaluator))
                .anyMatch(value -> !value.isEmpty());
    }

    private String textValue(
            Row row,
            HeaderContext header,
            SalesOrderItemDataReplacementField field,
            DataFormatter formatter,
            FormulaEvaluator evaluator) {
        return cellText(cell(row, header, field), formatter, evaluator);
    }

    private Cell cell(Row row, HeaderContext header, SalesOrderItemDataReplacementField field) {
        Integer columnIndex = header.columns().get(field);
        return row == null || columnIndex == null ? null : row.getCell(columnIndex);
    }

    private String cellText(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (cell == null) {
            return "";
        }
        return clean(formatter.formatCellValue(cell, evaluator));
    }

    private String clean(String value) {
        return value == null ? "" : value.replace('\u00A0', ' ').trim();
    }

    private String normalizeIdentity(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String safeMessage(Exception exception) {
        String message = exception == null ? null : exception.getMessage();
        return message == null || message.isBlank() ? "未知错误" : message.trim();
    }

    private record HeaderContext(
            int rowIndex,
            Map<SalesOrderItemDataReplacementField, Integer> columns,
            Map<SalesOrderItemDataReplacementField, List<Integer>> allColumns,
            List<SalesOrderItemDataReplacementField> orderedFields) {
    }
}
