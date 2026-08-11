package com.machining.quote.service;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class SalesOrderNumberService {
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private final AtomicLong lastTimestamp = new AtomicLong();

    public String nextImportBatchNo() {
        long value = lastTimestamp.updateAndGet(previous -> Math.max(System.currentTimeMillis(), previous + 1));
        return "SOQPO" + value;
    }

    public SalesOrderNumbers forDirectImport(String salesOrderNo) {
        if (salesOrderNo == null || salesOrderNo.isBlank()) {
            throw new IllegalArgumentException("销售订单编号不能为空");
        }
        String normalized = salesOrderNo.trim();
        return new SalesOrderNumbers(normalized, normalized);
    }

    public SalesOrderNumbers forRfqRelease(LocalDate salesOrderDate, String releaseBatchKey) {
        if (salesOrderDate == null || releaseBatchKey == null || releaseBatchKey.isBlank()) {
            throw new IllegalArgumentException("生成销售订单编号所需参数不完整");
        }
        String date = DATE.format(salesOrderDate);
        String suffix = sha256(releaseBatchKey.trim()).substring(0, 16).toUpperCase();
        return new SalesOrderNumbers(
                "SORFQ-" + date + "-" + suffix,
                "DOC-RFQ-" + date + "-" + suffix);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    public record SalesOrderNumbers(String salesOrderNo, String documentNo) {
    }
}
