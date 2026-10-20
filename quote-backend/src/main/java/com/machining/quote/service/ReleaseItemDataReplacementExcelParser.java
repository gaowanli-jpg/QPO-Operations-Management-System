package com.machining.quote.service;

import com.machining.quote.dto.ReleaseItemDataReplacementExcelErrorDTO;
import com.machining.quote.dto.ReleaseItemDataReplacementExcelParseResult;
import com.machining.quote.dto.ReleaseItemDataReplacementExcelRowDTO;
import com.machining.quote.dto.ReleaseItemDataReplacementField;
import com.machining.quote.util.DrawingPartNoResolver;
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
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ReleaseItemDataReplacementExcelParser {
    private static final int HEADER_SCAN_ROW_LIMIT = 20;
    private static final int EFFECTIVE_ROW_LIMIT = 10_000;
    private static final Set<String> ORDER_TYPES = Set.of("预测订单", "备货订单", "正式订单");

    public ReleaseItemDataReplacementExcelParseResult parse(InputStream inputStream) {
        if (inputStream == null) {
            throw new IllegalArgumentException("表格数据替换文件不能为空");
        }
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new IllegalArgumentException("表格数据替换文件没有工作表");
            }
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            DataFormatter formatter = new DataFormatter(Locale.CHINA, true);
            Sheet sheet = workbook.getSheetAt(0);
            HeaderContext header = findHeader(sheet, formatter, evaluator);
            validateHeader(header);

            List<ReleaseItemDataReplacementExcelRowDTO> rows = parseRows(
                    sheet,
                    header,
                    formatter,
                    evaluator);
            markDuplicateKeys(rows);

            ReleaseItemDataReplacementExcelParseResult result = new ReleaseItemDataReplacementExcelParseResult();
            result.setHeaderRowNumber(header.rowIndex() + 1);
            result.setRecognizedFields(ReleaseItemDataReplacementField.replaceableFields().stream()
                    .filter(header.columns()::containsKey)
                    .map(ReleaseItemDataReplacementField::getPropertyName)
                    .toList());
            result.setRows(rows);
            return result;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("议价提单表格数据替换解析失败：" + safeMessage(e), e);
        }
    }

    private HeaderContext findHeader(
            Sheet sheet,
            DataFormatter formatter,
            FormulaEvaluator evaluator) {
        int lastScannedRow = Math.min(sheet.getLastRowNum(), HEADER_SCAN_ROW_LIMIT - 1);
        for (int rowIndex = 0; rowIndex <= lastScannedRow; rowIndex++) {
            HeaderContext header = readHeader(sheet.getRow(rowIndex), rowIndex, formatter, evaluator);
            if (header.columns().containsKey(ReleaseItemDataReplacementField.PART_NO)
                    && header.columns().containsKey(ReleaseItemDataReplacementField.VERSION)) {
                return header;
            }
        }
        throw new IllegalArgumentException("表格数据替换前20行未找到同时包含图号和版本的表头");
    }

    private HeaderContext readHeader(
            Row row,
            int rowIndex,
            DataFormatter formatter,
            FormulaEvaluator evaluator) {
        Map<ReleaseItemDataReplacementField, List<Integer>> allColumns = new LinkedHashMap<>();
        if (row != null && row.getLastCellNum() > 0) {
            for (int columnIndex = 0; columnIndex < row.getLastCellNum(); columnIndex++) {
                int currentColumnIndex = columnIndex;
                ReleaseItemDataReplacementField.fromHeader(cellText(row.getCell(columnIndex), formatter, evaluator))
                        .ifPresent(field -> allColumns
                                .computeIfAbsent(field, ignored -> new ArrayList<>())
                                .add(currentColumnIndex));
            }
        }
        Map<ReleaseItemDataReplacementField, Integer> columns = new LinkedHashMap<>();
        allColumns.forEach((field, indexes) -> columns.put(field, indexes.get(0)));
        List<ReleaseItemDataReplacementField> orderedFields = columns.entrySet().stream()
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
            throw new IllegalArgumentException("表格数据替换存在重复表头：" + String.join("；", duplicateHeaders));
        }
        if (header.orderedFields().stream().noneMatch(ReleaseItemDataReplacementField::isReplaceable)) {
            throw new IllegalArgumentException("表格数据替换至少需要一个可替换业务表头");
        }
    }

    private List<ReleaseItemDataReplacementExcelRowDTO> parseRows(
            Sheet sheet,
            HeaderContext header,
            DataFormatter formatter,
            FormulaEvaluator evaluator) {
        List<ReleaseItemDataReplacementExcelRowDTO> rows = new ArrayList<>();
        for (int rowIndex = header.rowIndex() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (!hasRecognizedContent(row, header, formatter, evaluator)) {
                continue;
            }
            if (rows.size() >= EFFECTIVE_ROW_LIMIT) {
                throw new IllegalArgumentException("表格数据替换有效数据不能超过 10,000 行");
            }
            rows.add(parseRow(row, rowIndex + 1, header, formatter, evaluator));
        }
        return rows;
    }

    private ReleaseItemDataReplacementExcelRowDTO parseRow(
            Row row,
            int excelRowNumber,
            HeaderContext header,
            DataFormatter formatter,
            FormulaEvaluator evaluator) {
        ReleaseItemDataReplacementExcelRowDTO parsed = new ReleaseItemDataReplacementExcelRowDTO();
        parsed.setExcelRowNumber(excelRowNumber);
        String partNo = textValue(row, header, ReleaseItemDataReplacementField.PART_NO, formatter, evaluator);
        String version = textValue(row, header, ReleaseItemDataReplacementField.VERSION, formatter, evaluator);
        parsed.setPartNo(partNo);
        parsed.setVersion(version);
        parsed.setNormalizedPartNo(DrawingPartNoResolver.normalizePartNo(partNo));
        parsed.setNormalizedVersion(DrawingPartNoResolver.normalizeVersion(version));

        requireMatchKey(parsed, header, ReleaseItemDataReplacementField.PART_NO, partNo);
        requireMatchKey(parsed, header, ReleaseItemDataReplacementField.VERSION, version);

        for (ReleaseItemDataReplacementField field : header.orderedFields()) {
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

    private void parseReplacementValue(
            ReleaseItemDataReplacementExcelRowDTO row,
            ReleaseItemDataReplacementField field,
            Cell cell,
            HeaderContext header,
            DataFormatter formatter,
            FormulaEvaluator evaluator) {
        try {
            Object value = switch (field.getValueType()) {
                case DATE -> parseDate(cell, formatter, evaluator);
                case POSITIVE_INTEGER -> parsePositiveInteger(cell, formatter, evaluator);
                case ORDER_TYPE -> parseOrderType(cellText(cell, formatter, evaluator));
                case TEXT -> parseText(field, cellText(cell, formatter, evaluator));
                case MATCH_KEY -> null;
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
        String value = cellText(cell, formatter, evaluator).replace(",", "");
        BigDecimal decimal;
        try {
            decimal = new BigDecimal(value).stripTrailingZeros();
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("不是有效数字");
        }
        int integer;
        try {
            integer = decimal.intValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("必须为正整数");
        }
        if (integer <= 0) {
            throw new IllegalArgumentException("必须大于 0");
        }
        return integer;
    }

    private String parseOrderType(String value) {
        if (!ORDER_TYPES.contains(value)) {
            throw new IllegalArgumentException("只能是 预测订单 / 备货订单 / 正式订单");
        }
        return value;
    }

    private String parseText(ReleaseItemDataReplacementField field, String value) {
        Integer maxLength = field.getMaxLength();
        if (maxLength != null && value.length() > maxLength) {
            throw new IllegalArgumentException("长度不能超过 " + maxLength + " 个字符");
        }
        return value;
    }

    private void requireMatchKey(
            ReleaseItemDataReplacementExcelRowDTO row,
            HeaderContext header,
            ReleaseItemDataReplacementField field,
            String value) {
        if (value.isEmpty()) {
            addError(row, header, field, "不能为空");
        }
    }

    private void markDuplicateKeys(List<ReleaseItemDataReplacementExcelRowDTO> rows) {
        Map<String, List<ReleaseItemDataReplacementExcelRowDTO>> grouped = new LinkedHashMap<>();
        for (ReleaseItemDataReplacementExcelRowDTO row : rows) {
            if (row.getNormalizedPartNo().isEmpty() || row.getNormalizedVersion().isEmpty()) {
                continue;
            }
            String key = row.getNormalizedPartNo() + "||" + row.getNormalizedVersion();
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(row);
        }
        grouped.values().stream()
                .filter(group -> group.size() > 1)
                .forEach(group -> {
                    String duplicateRows = group.stream()
                            .map(row -> String.valueOf(row.getExcelRowNumber()))
                            .collect(Collectors.joining("、"));
                    for (ReleaseItemDataReplacementExcelRowDTO row : group) {
                        ReleaseItemDataReplacementExcelErrorDTO error = new ReleaseItemDataReplacementExcelErrorDTO();
                        error.setExcelRowNumber(row.getExcelRowNumber());
                        error.setField("partNoVersion");
                        error.setLabel("图号+版本");
                        error.setMessage("图号和版本重复，重复行：" + duplicateRows);
                        row.getErrors().add(error);
                    }
                });
    }

    private void addError(
            ReleaseItemDataReplacementExcelRowDTO row,
            HeaderContext header,
            ReleaseItemDataReplacementField field,
            String reason) {
        int columnNumber = header.columns().get(field) + 1;
        ReleaseItemDataReplacementExcelErrorDTO error = new ReleaseItemDataReplacementExcelErrorDTO();
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
            ReleaseItemDataReplacementField field,
            DataFormatter formatter,
            FormulaEvaluator evaluator) {
        return cellText(cell(row, header, field), formatter, evaluator);
    }

    private Cell cell(Row row, HeaderContext header, ReleaseItemDataReplacementField field) {
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

    private String safeMessage(Exception exception) {
        String message = exception == null ? null : exception.getMessage();
        return message == null || message.isBlank() ? "未知错误" : message.trim();
    }

    private record HeaderContext(
            int rowIndex,
            Map<ReleaseItemDataReplacementField, Integer> columns,
            Map<ReleaseItemDataReplacementField, List<Integer>> allColumns,
            List<ReleaseItemDataReplacementField> orderedFields) {
    }
}
