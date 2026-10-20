package com.machining.quote.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CellValue;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Locale;

public final class ExcelDateValueParser {
    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            strictFormatter("uuuu-M-d"),
            strictFormatter("uuuu/M/d"),
            strictFormatter("uuuu.M.d"),
            strictFormatter("uuuu年M月d日"));

    private ExcelDateValueParser() {
    }

    public static LocalDate parseOptional(
            Cell cell,
            DataFormatter formatter,
            FormulaEvaluator evaluator) {
        if (cell == null) {
            return null;
        }

        NumericValue numericValue = numericValue(cell, evaluator);
        if (numericValue.numeric()) {
            return excelDate(numericValue.value());
        }

        String value = clean(formatter.formatCellValue(cell, evaluator));
        if (value.isEmpty()) {
            return null;
        }
        if (value.matches("\\d+(\\.0+)?")) {
            return excelDate(new BigDecimal(value).doubleValue());
        }
        for (DateTimeFormatter dateFormatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(value, dateFormatter);
            } catch (DateTimeParseException ignored) {
                // Try the next explicitly supported date format.
            }
        }
        throw new IllegalArgumentException("不是有效日期");
    }

    private static NumericValue numericValue(Cell cell, FormulaEvaluator evaluator) {
        if (cell.getCellType() == CellType.NUMERIC) {
            return new NumericValue(true, cell.getNumericCellValue());
        }
        if (cell.getCellType() != CellType.FORMULA) {
            return NumericValue.NOT_NUMERIC;
        }
        CellValue evaluated = evaluator.evaluate(cell);
        if (evaluated != null && evaluated.getCellType() == CellType.NUMERIC) {
            return new NumericValue(true, evaluated.getNumberValue());
        }
        return NumericValue.NOT_NUMERIC;
    }

    private static LocalDate excelDate(double value) {
        if (!DateUtil.isValidExcelDate(value)) {
            throw new IllegalArgumentException("不是有效日期");
        }
        return DateUtil.getLocalDateTime(value).toLocalDate();
    }

    private static DateTimeFormatter strictFormatter(String pattern) {
        return new DateTimeFormatterBuilder()
                .appendPattern(pattern)
                .toFormatter(Locale.CHINA)
                .withResolverStyle(ResolverStyle.STRICT);
    }

    private static String clean(String value) {
        return value == null ? "" : value.replace('\u00A0', ' ').trim();
    }

    private record NumericValue(boolean numeric, double value) {
        private static final NumericValue NOT_NUMERIC = new NumericValue(false, 0D);
    }
}
