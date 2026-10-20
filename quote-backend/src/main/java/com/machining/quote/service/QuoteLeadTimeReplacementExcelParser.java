package com.machining.quote.service;

import com.machining.quote.dto.QuoteLeadTimeReplacementRowDTO;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class QuoteLeadTimeReplacementExcelParser {
    private static final int HEADER_SCAN_ROW_LIMIT = 20;
    private static final int EFFECTIVE_ROW_LIMIT = 10_000;
    private static final String PART_NO = "partNo";
    private static final String VERSION = "version";
    private static final String QUOTE_LEAD_TIME = "quoteLeadTime";

    public ParseResult parse(InputStream inputStream) {
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
            List<QuoteLeadTimeReplacementRowDTO> rows = parseRows(sheet, header, formatter, evaluator);
            markDuplicateKeys(rows);
            return new ParseResult(header.rowIndex() + 1, List.of(PART_NO, VERSION, QUOTE_LEAD_TIME), rows);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("报价处理表格数据替换解析失败：" + safeMessage(e), e);
        }
    }

    private HeaderContext findHeader(Sheet sheet, DataFormatter formatter, FormulaEvaluator evaluator) {
        int lastScannedRow = Math.min(sheet.getLastRowNum(), HEADER_SCAN_ROW_LIMIT - 1);
        for (int rowIndex = 0; rowIndex <= lastScannedRow; rowIndex++) {
            HeaderContext header = readHeader(sheet.getRow(rowIndex), rowIndex, formatter, evaluator);
            if (header.columns().containsKey(PART_NO)
                    && header.columns().containsKey(VERSION)
                    && header.columns().containsKey(QUOTE_LEAD_TIME)) {
                return header;
            }
        }
        throw new IllegalArgumentException("表格数据替换前20行未找到同时包含图号、版本、报价交期的表头");
    }

    private HeaderContext readHeader(Row row, int rowIndex, DataFormatter formatter, FormulaEvaluator evaluator) {
        Map<String, List<Integer>> allColumns = new LinkedHashMap<>();
        if (row != null && row.getLastCellNum() > 0) {
            for (int columnIndex = 0; columnIndex < row.getLastCellNum(); columnIndex++) {
                String field = fieldName(cellText(row.getCell(columnIndex), formatter, evaluator));
                if (field == null) {
                    continue;
                }
                allColumns.computeIfAbsent(field, ignored -> new ArrayList<>()).add(columnIndex);
            }
        }
        Map<String, Integer> columns = new LinkedHashMap<>();
        allColumns.forEach((field, indexes) -> columns.put(field, indexes.get(0)));
        return new HeaderContext(rowIndex, columns, allColumns);
    }

    private void validateHeader(HeaderContext header) {
        List<String> duplicateHeaders = header.allColumns().entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(entry -> label(entry.getKey()) + "位于第"
                        + entry.getValue().stream()
                        .map(index -> String.valueOf(index + 1))
                        .collect(Collectors.joining("、"))
                        + "列")
                .toList();
        if (!duplicateHeaders.isEmpty()) {
            throw new IllegalArgumentException("表格数据替换存在重复表头：" + String.join("；", duplicateHeaders));
        }
    }

    private List<QuoteLeadTimeReplacementRowDTO> parseRows(
            Sheet sheet,
            HeaderContext header,
            DataFormatter formatter,
            FormulaEvaluator evaluator) {
        List<QuoteLeadTimeReplacementRowDTO> rows = new ArrayList<>();
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

    private QuoteLeadTimeReplacementRowDTO parseRow(
            Row row,
            int excelRowNumber,
            HeaderContext header,
            DataFormatter formatter,
            FormulaEvaluator evaluator) {
        QuoteLeadTimeReplacementRowDTO parsed = new QuoteLeadTimeReplacementRowDTO();
        parsed.setExcelRowNumber(excelRowNumber);
        String partNo = textValue(row, header, PART_NO, formatter, evaluator);
        String version = textValue(row, header, VERSION, formatter, evaluator);
        parsed.setPartNo(partNo);
        parsed.setVersion(version);
        parsed.setNormalizedPartNo(DrawingPartNoResolver.normalizePartNo(partNo));
        parsed.setNormalizedVersion(DrawingPartNoResolver.normalizeVersion(version));

        requireMatchKey(parsed, PART_NO, partNo);
        requireMatchKey(parsed, VERSION, version);

        Cell quoteLeadTimeCell = cell(row, header, QUOTE_LEAD_TIME);
        if (!cellText(quoteLeadTimeCell, formatter, evaluator).isEmpty()) {
            try {
                LocalDate quoteLeadTime = ExcelDateValueParser.parseOptional(quoteLeadTimeCell, formatter, evaluator);
                if (quoteLeadTime != null) {
                    parsed.setImportedQuoteLeadTime(quoteLeadTime.toString());
                }
            } catch (IllegalArgumentException e) {
                parsed.getErrors().add("第" + excelRowNumber + "行字段[报价交期]" + safeMessage(e));
            }
        }
        return parsed;
    }

    private void requireMatchKey(QuoteLeadTimeReplacementRowDTO row, String field, String value) {
        if (value == null || value.isBlank()) {
            row.getErrors().add("第" + row.getExcelRowNumber() + "行字段[" + label(field) + "]不能为空");
        }
    }

    private void markDuplicateKeys(List<QuoteLeadTimeReplacementRowDTO> rows) {
        Map<String, List<QuoteLeadTimeReplacementRowDTO>> grouped = new LinkedHashMap<>();
        for (QuoteLeadTimeReplacementRowDTO row : rows) {
            if (row.getNormalizedPartNo() == null || row.getNormalizedPartNo().isBlank()
                    || row.getNormalizedVersion() == null || row.getNormalizedVersion().isBlank()) {
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
                    for (QuoteLeadTimeReplacementRowDTO row : group) {
                        row.getErrors().add("图号和版本重复，重复行：" + duplicateRows);
                    }
                });
    }

    private boolean hasRecognizedContent(Row row, HeaderContext header, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (row == null) {
            return false;
        }
        return Set.of(PART_NO, VERSION, QUOTE_LEAD_TIME).stream()
                .map(field -> cell(row, header, field))
                .map(cell -> cellText(cell, formatter, evaluator))
                .anyMatch(value -> !value.isEmpty());
    }

    private String textValue(Row row, HeaderContext header, String field, DataFormatter formatter, FormulaEvaluator evaluator) {
        return cellText(cell(row, header, field), formatter, evaluator);
    }

    private Cell cell(Row row, HeaderContext header, String field) {
        Integer columnIndex = header.columns().get(field);
        return row == null || columnIndex == null ? null : row.getCell(columnIndex);
    }

    private String cellText(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (cell == null) {
            return "";
        }
        return clean(formatter.formatCellValue(cell, evaluator));
    }

    private String fieldName(String header) {
        return switch (normalizeHeader(header)) {
            case "图号" -> PART_NO;
            case "版本" -> VERSION;
            case "报价交期" -> QUOTE_LEAD_TIME;
            default -> null;
        };
    }

    private String label(String field) {
        return switch (field) {
            case PART_NO -> "图号";
            case VERSION -> "版本";
            case QUOTE_LEAD_TIME -> "报价交期";
            default -> field;
        };
    }

    private String normalizeHeader(String header) {
        return header == null ? "" : header.replaceAll("[\\s\\u00A0\\u3000]+", "").trim();
    }

    private String clean(String value) {
        return value == null ? "" : value.replace('\u00A0', ' ').trim();
    }

    private String safeMessage(Exception exception) {
        String message = exception == null ? null : exception.getMessage();
        return message == null || message.isBlank() ? "未知错误" : message.trim();
    }

    private record HeaderContext(int rowIndex, Map<String, Integer> columns, Map<String, List<Integer>> allColumns) {
    }

    public record ParseResult(int headerRowNumber, List<String> recognizedFields, List<QuoteLeadTimeReplacementRowDTO> rows) {
    }
}
