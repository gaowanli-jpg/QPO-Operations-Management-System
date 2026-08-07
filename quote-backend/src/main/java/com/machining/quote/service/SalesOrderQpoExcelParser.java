package com.machining.quote.service;

import com.machining.quote.dto.SalesOrderQpoExcelLineDTO;
import com.machining.quote.dto.SalesOrderQpoExcelParseResult;
import com.machining.quote.dto.SalesOrderQpoImportDefaultsDTO;
import com.machining.quote.dto.SalesOrderQpoOrderGroupDTO;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class SalesOrderQpoExcelParser {
    private static final String LINE_NO = "\u5e8f\u53f7";
    private static final String SALES_ORDER_DATE = "\u4e0b\u5355\u65e5\u671f";
    private static final String CUSTOMER_ORDER_NO = "\u5ba2\u6237\u8ba2\u5355\u53f7";
    private static final String CUSTOMER_PROJECT_NO = "\u5ba2\u6237\u9879\u76ee\u53f7";
    private static final String CUSTOMER_ORDER_TITLE = "\u5ba2\u6237\u4e0b\u5355\u62ac\u5934";
    private static final String ORDER_TYPE = "\u8ba2\u5355\u7c7b\u578b";
    private static final String INTERNAL_PROJECT_NO = "\u5185\u90e8\u9879\u76ee\u53f7";
    private static final String CUSTOMER_DELIVERY_DATE = "\u5ba2\u6237\u4ea4\u671f";
    private static final String INTERNAL_DELIVERY_DATE = "\u5185\u90e8\u4ea4\u671f(\u7ed9\u751f\u4ea7\u7684\u4ea4\u671f)";
    private static final String PART_NO = "\u56fe\u53f7";
    private static final String QTY = "\u8ba2\u5355\u6570\u91cf";
    private static final String DESCRIPTION = "\u63cf\u8ff0";
    private static final String MATERIAL = "\u6750\u6599";
    private static final String SURFACE_TREATMENT = "\u8868\u9762\u5904\u7406";
    private static final String VERSION = "\u7248\u672c";
    private static final String TAX_INCLUDED_UNIT_PRICE = "VAT\u5355\u4ef7-\u542b\u7a0e";
    private static final String UNTAXED_UNIT_PRICE = "\u672a\u7a0e\u5355\u4ef7(CNY)";
    private static final String UNTAXED_TOTAL_PRICE = "\uff08\u672a\u7a0e\uff09\u91d1\u989d";
    private static final String REMARK = "\u5907\u6ce8";

    private static final List<String> REQUIRED_HEADERS = List.of(
            PART_NO,
            QTY,
            VERSION
    );

    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("yyyy/M/d"),
            DateTimeFormatter.ofPattern("yyyy.MM.dd"),
            DateTimeFormatter.ofPattern("yyyy\u5e74M\u6708d\u65e5")
    );

    public SalesOrderQpoExcelParseResult parse(InputStream inputStream) {
        return parse(inputStream, null);
    }

    public SalesOrderQpoExcelParseResult parse(InputStream inputStream, SalesOrderQpoImportDefaultsDTO defaults) {
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new IllegalArgumentException("\u9500\u552e\u8ba2\u5355\u5bfc\u5165\u8868\u683c\u4e3a\u7a7a");
            }
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            DataFormatter formatter = new DataFormatter(Locale.CHINA, true);
            Sheet sheet = workbook.getSheetAt(0);
            HeaderContext header = findHeader(sheet, formatter, evaluator);
            validateHeaders(header.indexes());

            Map<String, SalesOrderQpoOrderGroupDTO> groups = new LinkedHashMap<>();
            SalesOrderQpoOrderGroupDTO group = null;
            int sourceRowCount = 0;
            int lineCount = 0;
            for (int rowIndex = header.rowIndex() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (isBlankRow(row, formatter, evaluator)) {
                    continue;
                }
                int excelRowNumber = rowIndex + 1;
                SalesOrderQpoExcelLineDTO line = parseLine(row, header.indexes(), formatter, evaluator, excelRowNumber, defaults);
                sourceRowCount++;
                lineCount++;

                if (group == null) {
                    group = new SalesOrderQpoOrderGroupDTO();
                    group.setGroupKey(resolveGroupKey(defaults, line));
                    group.setCustomerOrderNo(resolveGroupValue(
                            defaults == null ? null : defaults.getCustomerOrderNo(),
                            line.getCustomerOrderNo()));
                    group.setInternalProjectNo(resolveGroupValue(
                            defaults == null ? null : defaults.getInternalProjectNo(),
                            line.getInternalProjectNo()));
                    group.setSalesOrderDate(resolveGroupValue(
                            defaults == null ? null : defaults.getSalesOrderDate(),
                            line.getSalesOrderDate()));
                    group.setCustomerProjectNo(resolveGroupValue(
                            defaults == null ? null : defaults.getCustomerProjectNo(),
                            line.getCustomerProjectNo()));
                    group.setCustomerOrderTitle(resolveGroupValue(
                            defaults == null ? null : defaults.getCustomerOrderTitle(),
                            line.getCustomerOrderTitle()));
                    group.setOrderType(resolveGroupValue(
                            defaults == null ? null : defaults.getOrderType(),
                            line.getOrderType()));
                    groups.put(group.getGroupKey(), group);
                }
                group.getLines().add(line);
            }

            SalesOrderQpoExcelParseResult result = new SalesOrderQpoExcelParseResult();
            result.setSourceRowCount(sourceRowCount);
            result.setLineCount(lineCount);
            result.setOrders(new ArrayList<>(groups.values()));
            result.setOrderCount(result.getOrders().size());
            return result;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("\u9500\u552e\u8ba2\u5355\u5bfc\u5165\u8868\u683c\u89e3\u6790\u5931\u8d25\uff1a" + safeMessage(e), e);
        }
    }

    private SalesOrderQpoExcelLineDTO parseLine(
            Row row,
            Map<String, Integer> headerIndexes,
            DataFormatter formatter,
            FormulaEvaluator evaluator,
            int excelRowNumber,
            SalesOrderQpoImportDefaultsDTO defaults) {
        Integer qty = requiredInteger(row, headerIndexes, QTY, formatter, evaluator, excelRowNumber);
        if (qty <= 0) {
            throw rowError(excelRowNumber, QTY, "\u5fc5\u987b\u5927\u4e8e 0");
        }
        Integer shippedQty = 0;
        Integer unshippedQty = qty;

        BigDecimal taxIncludedUnitPrice = optionalDecimal(row, headerIndexes, TAX_INCLUDED_UNIT_PRICE, formatter, evaluator, excelRowNumber);
        BigDecimal untaxedUnitPrice = optionalDecimal(row, headerIndexes, UNTAXED_UNIT_PRICE, formatter, evaluator, excelRowNumber);
        BigDecimal untaxedTotalPrice = optionalDecimal(row, headerIndexes, UNTAXED_TOTAL_PRICE, formatter, evaluator, excelRowNumber);
        if (untaxedTotalPrice == null && untaxedUnitPrice != null) {
            untaxedTotalPrice = untaxedUnitPrice.multiply(BigDecimal.valueOf(qty));
        }

        SalesOrderQpoExcelLineDTO line = new SalesOrderQpoExcelLineDTO();
        line.setSourceRowNumber(excelRowNumber);
        line.setSalesOrderLineNo(optionalInteger(row, headerIndexes, LINE_NO, formatter, evaluator, excelRowNumber, null));
        line.setSalesOrderDate(fallbackDate(row, headerIndexes, SALES_ORDER_DATE, formatter, evaluator, excelRowNumber,
                defaults == null ? null : defaults.getSalesOrderDate()));
        line.setCustomerOrderNo(fallbackText(row, headerIndexes, CUSTOMER_ORDER_NO, formatter, evaluator,
                defaults == null ? null : defaults.getCustomerOrderNo()));
        line.setCustomerProjectNo(fallbackText(row, headerIndexes, CUSTOMER_PROJECT_NO, formatter, evaluator,
                defaults == null ? null : defaults.getCustomerProjectNo()));
        line.setCustomerOrderTitle(fallbackText(row, headerIndexes, CUSTOMER_ORDER_TITLE, formatter, evaluator,
                defaults == null ? null : defaults.getCustomerOrderTitle()));
        line.setOrderType(fallbackText(row, headerIndexes, ORDER_TYPE, formatter, evaluator,
                defaults == null ? null : defaults.getOrderType()));
        line.setSalesMaterialNo(null);
        line.setInternalProjectNo(fallbackText(row, headerIndexes, INTERNAL_PROJECT_NO, formatter, evaluator,
                defaults == null ? null : defaults.getInternalProjectNo()));
        line.setCustomerDeliveryDate(optionalDate(row, headerIndexes, CUSTOMER_DELIVERY_DATE, formatter, evaluator, excelRowNumber));
        line.setInternalDeliveryDate(optionalDate(row, headerIndexes, INTERNAL_DELIVERY_DATE, formatter, evaluator, excelRowNumber));
        line.setPartNo(requiredText(row, headerIndexes, PART_NO, formatter, evaluator, excelRowNumber));
        line.setQty(qty);
        line.setShippedQty(shippedQty);
        line.setUnshippedQty(unshippedQty);
        line.setDescription(optionalText(row, headerIndexes, DESCRIPTION, formatter, evaluator));
        line.setMaterialName(optionalText(row, headerIndexes, MATERIAL, formatter, evaluator));
        line.setSurfaceTreatment(optionalText(row, headerIndexes, SURFACE_TREATMENT, formatter, evaluator));
        line.setVersion(requiredText(row, headerIndexes, VERSION, formatter, evaluator, excelRowNumber));
        line.setTaxIncludedUnitPrice(taxIncludedUnitPrice);
        line.setUntaxedUnitPrice(untaxedUnitPrice);
        line.setTaxIncludedTotalPrice(taxIncludedUnitPrice == null ? null : taxIncludedUnitPrice.multiply(BigDecimal.valueOf(qty)));
        line.setUntaxedTotalPrice(untaxedTotalPrice);
        line.setRemark(optionalText(row, headerIndexes, REMARK, formatter, evaluator));
        return line;
    }

    private HeaderContext findHeader(Sheet sheet, DataFormatter formatter, FormulaEvaluator evaluator) {
        for (int rowIndex = sheet.getFirstRowNum(); rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            Map<String, Integer> indexes = buildHeaderIndexes(row, formatter, evaluator);
            if (indexes.containsKey(normalizeHeader(PART_NO))
                    && indexes.containsKey(normalizeHeader(QTY))
                    && indexes.containsKey(normalizeHeader(VERSION))) {
                return new HeaderContext(rowIndex, indexes);
            }
        }
        throw new IllegalArgumentException("\u9500\u552e\u8ba2\u5355\u5bfc\u5165\u8868\u683c\u7f3a\u5931\u8868\u5934\uff1a" + String.join("\u3001", REQUIRED_HEADERS));
    }

    private Map<String, Integer> buildHeaderIndexes(Row headerRow, DataFormatter formatter, FormulaEvaluator evaluator) {
        Map<String, Integer> indexes = new LinkedHashMap<>();
        if (headerRow == null) {
            return indexes;
        }
        for (int col = 0; col < headerRow.getLastCellNum(); col++) {
            String header = normalizeHeader(cellText(headerRow.getCell(col), formatter, evaluator));
            if (!header.isEmpty()) {
                indexes.put(header, col);
            }
        }
        return indexes;
    }

    private void validateHeaders(Map<String, Integer> headerIndexes) {
        List<String> missing = REQUIRED_HEADERS.stream()
                .filter(header -> !headerIndexes.containsKey(normalizeHeader(header)))
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("\u9500\u552e\u8ba2\u5355\u5bfc\u5165\u8868\u683c\u7f3a\u5931\u8868\u5934\uff1a" + String.join("\u3001", missing));
        }
    }

    private String requiredText(
            Row row,
            Map<String, Integer> headerIndexes,
            String header,
            DataFormatter formatter,
            FormulaEvaluator evaluator,
            int excelRowNumber) {
        String value = optionalText(row, headerIndexes, header, formatter, evaluator);
        if (value.isEmpty()) {
            throw rowError(excelRowNumber, header, "\u4e0d\u80fd\u4e3a\u7a7a");
        }
        return value;
    }

    private String optionalText(Row row, Map<String, Integer> headerIndexes, String header, DataFormatter formatter, FormulaEvaluator evaluator) {
        Cell cell = cell(row, headerIndexes, header);
        return clean(cellText(cell, formatter, evaluator));
    }

    private Integer requiredInteger(
            Row row,
            Map<String, Integer> headerIndexes,
            String header,
            DataFormatter formatter,
            FormulaEvaluator evaluator,
            int excelRowNumber) {
        Integer value = optionalInteger(row, headerIndexes, header, formatter, evaluator, excelRowNumber, null);
        if (value == null) {
            throw rowError(excelRowNumber, header, "\u4e0d\u80fd\u4e3a\u7a7a");
        }
        return value;
    }

    private Integer optionalInteger(
            Row row,
            Map<String, Integer> headerIndexes,
            String header,
            DataFormatter formatter,
            FormulaEvaluator evaluator,
            int excelRowNumber,
            Integer defaultValue) {
        BigDecimal decimal = optionalDecimal(row, headerIndexes, header, formatter, evaluator, excelRowNumber);
        if (decimal == null) {
            return defaultValue;
        }
        try {
            return decimal.stripTrailingZeros().intValueExact();
        } catch (ArithmeticException e) {
            throw rowError(excelRowNumber, header, "\u5fc5\u987b\u4e3a\u6574\u6570");
        }
    }

    private BigDecimal requiredDecimal(
            Row row,
            Map<String, Integer> headerIndexes,
            String header,
            DataFormatter formatter,
            FormulaEvaluator evaluator,
            int excelRowNumber) {
        BigDecimal value = optionalDecimal(row, headerIndexes, header, formatter, evaluator, excelRowNumber);
        if (value == null) {
            throw rowError(excelRowNumber, header, "\u4e0d\u80fd\u4e3a\u7a7a");
        }
        return value;
    }

    private BigDecimal optionalDecimal(
            Row row,
            Map<String, Integer> headerIndexes,
            String header,
            DataFormatter formatter,
            FormulaEvaluator evaluator,
            int excelRowNumber) {
        Cell cell = cell(row, headerIndexes, header);
        if (cell == null) {
            return null;
        }
        CellType type = effectiveCellType(cell);
        if (type == CellType.NUMERIC && !DateUtil.isCellDateFormatted(cell)) {
            return BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros();
        }
        String value = clean(cellText(cell, formatter, evaluator));
        if (value.isEmpty()) {
            return null;
        }
        String normalized = value.replace(",", "")
                .replace("\uffe5", "")
                .replace("\u00a5", "")
                .trim();
        try {
            return new BigDecimal(normalized).stripTrailingZeros();
        } catch (NumberFormatException e) {
            throw rowError(excelRowNumber, header, "\u4e0d\u662f\u6709\u6548\u6570\u5b57");
        }
    }

    private LocalDate requiredDate(
            Row row,
            Map<String, Integer> headerIndexes,
            String header,
            DataFormatter formatter,
            FormulaEvaluator evaluator,
            int excelRowNumber) {
        LocalDate value = optionalDate(row, headerIndexes, header, formatter, evaluator, excelRowNumber);
        if (value == null) {
            throw rowError(excelRowNumber, header, "\u4e0d\u80fd\u4e3a\u7a7a");
        }
        return value;
    }

    private LocalDate optionalDate(
            Row row,
            Map<String, Integer> headerIndexes,
            String header,
            DataFormatter formatter,
            FormulaEvaluator evaluator,
            int excelRowNumber) {
        Cell cell = cell(row, headerIndexes, header);
        if (cell == null) {
            return null;
        }
        if (effectiveCellType(cell) == CellType.NUMERIC && DateUtil.isValidExcelDate(cell.getNumericCellValue())) {
            return DateUtil.getJavaDate(cell.getNumericCellValue())
                    .toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate();
        }
        String value = clean(cellText(cell, formatter, evaluator));
        if (value.isEmpty()) {
            return null;
        }
        if (value.matches("\\d+(\\.0+)?")) {
            return DateUtil.getJavaDate(new BigDecimal(value).doubleValue())
                    .toInstant()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate();
        }
        String normalized = value.replace('/', '-').replace('.', '-');
        for (DateTimeFormatter dateFormatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(normalized, dateFormatter);
            } catch (DateTimeParseException ignored) {
                // Try the next accepted market date format.
            }
        }
        throw rowError(excelRowNumber, header, "\u4e0d\u662f\u6709\u6548\u65e5\u671f");
    }

    private String fallbackText(
            Row row,
            Map<String, Integer> headerIndexes,
            String header,
            DataFormatter formatter,
            FormulaEvaluator evaluator,
            String defaultValue) {
        String value = optionalText(row, headerIndexes, header, formatter, evaluator);
        return value.isEmpty() ? clean(defaultValue) : value;
    }

    private LocalDate fallbackDate(
            Row row,
            Map<String, Integer> headerIndexes,
            String header,
            DataFormatter formatter,
            FormulaEvaluator evaluator,
            int excelRowNumber,
            LocalDate defaultValue) {
        LocalDate value = optionalDate(row, headerIndexes, header, formatter, evaluator, excelRowNumber);
        return value == null ? defaultValue : value;
    }

    private String resolveGroupKey(SalesOrderQpoImportDefaultsDTO defaults, SalesOrderQpoExcelLineDTO line) {
        return resolveGroupValue(defaults == null ? null : defaults.getCustomerOrderNo(),
                line == null ? null : line.getCustomerOrderNo())
                + "|" + resolveGroupValue(defaults == null ? null : defaults.getInternalProjectNo(),
                line == null ? null : line.getInternalProjectNo());
    }

    private String resolveGroupValue(String defaultValue, String lineValue) {
        String cleanedDefault = clean(defaultValue);
        return cleanedDefault.isEmpty() ? clean(lineValue) : cleanedDefault;
    }

    private LocalDate resolveGroupValue(LocalDate defaultValue, LocalDate lineValue) {
        return defaultValue != null ? defaultValue : lineValue;
    }

    private boolean isBlankRow(Row row, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (row == null) {
            return true;
        }
        for (int col = row.getFirstCellNum(); col < row.getLastCellNum(); col++) {
            if (!cellText(row.getCell(col), formatter, evaluator).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private Cell cell(Row row, Map<String, Integer> headerIndexes, String header) {
        Integer col = headerIndexes.get(normalizeHeader(header));
        return col == null || row == null ? null : row.getCell(col);
    }

    private String cellText(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (cell == null) {
            return "";
        }
        return clean(formatter.formatCellValue(cell, evaluator));
    }

    private CellType effectiveCellType(Cell cell) {
        return cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
    }

    private String normalizeHeader(String value) {
        return clean(value).replaceAll("[\\s\\u00A0\\u3000]+", "");
    }

    private String clean(String value) {
        return value == null ? "" : value.replace('\u00A0', ' ').trim();
    }

    private IllegalArgumentException rowError(int excelRowNumber, String header, String reason) {
        return new IllegalArgumentException("\u9500\u552e\u8ba2\u5355\u5bfc\u5165\u8868\u683c\u7b2c" + excelRowNumber + "\u884c\u5b57\u6bb5[" + header + "]" + reason);
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

    private record HeaderContext(int rowIndex, Map<String, Integer> indexes) {
    }
}
