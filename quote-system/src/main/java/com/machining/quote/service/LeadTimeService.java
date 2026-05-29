package com.machining.quote.service;

import com.machining.quote.entity.ItemQuoteVersion;
import org.springframework.stereotype.Service;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LeadTimeService {
    private static final Pattern FIRST_NUMBER = Pattern.compile("(\\d+)");
    private static final Pattern DELIVERY_DATE = Pattern.compile("(\\d{4})\\s*[.\\-/年]\\s*(\\d{1,2})\\s*[.\\-/月]\\s*(\\d{1,2})\\s*日?");
    private static final Pattern EXCEL_SERIAL_DATE = Pattern.compile("\\d{5}(?:\\.0+)?");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    public String effectiveLeadTime(ItemQuoteVersion item) {
        if (item == null) {
            return "";
        }
        return effectiveLeadTime(item.getQuoteLeadTime(), item.getLeadTime());
    }

    public String effectiveDeliveryLeadTime(ItemQuoteVersion item) {
        if (item == null) {
            return "";
        }
        String quoteOrCustomerLeadTime = effectiveLeadTime(item);
        return isBlank(item.getInternalTargetLeadTime()) ? quoteOrCustomerLeadTime : safe(item.getInternalTargetLeadTime());
    }

    public String effectiveLeadTime(String quoteLeadTime, String leadTime) {
        return isBlank(quoteLeadTime) ? safe(leadTime) : safe(quoteLeadTime);
    }

    public String normalizeForQuote(String leadTime) {
        if (isBlank(leadTime)) {
            return null;
        }
        LocalDate date = parseDeliveryDate(leadTime);
        if (date == null) {
            date = parseExcelSerialDate(leadTime);
        }
        return date == null ? leadTime.trim() : DATE_FORMAT.format(date);
    }

    public String normalizeRequiredDate(String leadTime) {
        if (isBlank(leadTime)) {
            throw new RuntimeException("请先选择日期");
        }
        LocalDate date = parseDeliveryDate(leadTime);
        if (date == null) {
            date = parseExcelSerialDate(leadTime);
        }
        if (date == null) {
            throw new RuntimeException("日期格式不正确，请输入类似 2026-03-20 的日期");
        }
        return DATE_FORMAT.format(date);
    }

    public String shiftLeadTime(String leadTime, int days) {
        if (isBlank(leadTime)) {
            throw new RuntimeException("交货周期为空，无法自动加减");
        }

        LocalDate exactDate = parseDeliveryDate(leadTime);
        if (exactDate != null) {
            return DATE_FORMAT.format(exactDate.plusDays(days));
        }

        LocalDate excelSerialDate = parseExcelSerialDate(leadTime);
        if (excelSerialDate != null) {
            return DATE_FORMAT.format(excelSerialDate.plusDays(days));
        }

        Matcher matcher = FIRST_NUMBER.matcher(leadTime);
        if (!matcher.find()) {
            throw new RuntimeException("交货周期没有可识别的日期或天数");
        }
        int currentDays = Integer.parseInt(matcher.group(1));
        int nextDays = currentDays + days;
        if (nextDays < 0) {
            throw new RuntimeException("交货周期天数不能小于 0");
        }
        return nextDays + "天";
    }

    public LocalDate resolveDeliveryDate(LocalDate startDate, String leadTime) {
        LocalDate safeStartDate = startDate == null ? LocalDate.now() : startDate;
        if (isBlank(leadTime)) {
            return safeStartDate;
        }
        LocalDate exactDate = parseDeliveryDate(leadTime);
        if (exactDate != null) {
            return exactDate;
        }
        LocalDate excelSerialDate = parseExcelSerialDate(leadTime);
        if (excelSerialDate != null) {
            return excelSerialDate;
        }
        Matcher matcher = FIRST_NUMBER.matcher(leadTime);
        if (!matcher.find()) {
            return safeStartDate;
        }
        return safeStartDate.plusDays(Long.parseLong(matcher.group(1)));
    }

    public String parsedDateText(String leadTime) {
        LocalDate exactDate = parseDeliveryDate(leadTime);
        if (exactDate != null) {
            return DATE_FORMAT.format(exactDate);
        }
        LocalDate excelSerialDate = parseExcelSerialDate(leadTime);
        return excelSerialDate == null ? "" : DATE_FORMAT.format(excelSerialDate);
    }

    public boolean canAutoShift(String leadTime) {
        if (isBlank(leadTime)) {
            return false;
        }
        return parseDeliveryDate(leadTime) != null
                || parseExcelSerialDate(leadTime) != null
                || FIRST_NUMBER.matcher(leadTime).find();
    }

    private LocalDate parseDeliveryDate(String leadTime) {
        if (isBlank(leadTime)) {
            return null;
        }
        Matcher matcher = DELIVERY_DATE.matcher(leadTime.trim());
        if (!matcher.find()) {
            return null;
        }
        try {
            int year = Integer.parseInt(matcher.group(1));
            int month = Integer.parseInt(matcher.group(2));
            int day = Integer.parseInt(matcher.group(3));
            return LocalDate.of(year, month, day);
        } catch (DateTimeException | NumberFormatException e) {
            return null;
        }
    }

    private LocalDate parseExcelSerialDate(String leadTime) {
        if (isBlank(leadTime)) {
            return null;
        }
        String normalized = leadTime.trim();
        if (!EXCEL_SERIAL_DATE.matcher(normalized).matches()) {
            return null;
        }
        long serial = (long) Double.parseDouble(normalized);
        LocalDate date = LocalDate.of(1899, 12, 31).plusDays(serial);
        return serial >= 60 ? date.minusDays(1) : date;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
