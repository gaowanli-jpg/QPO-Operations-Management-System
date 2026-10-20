package com.machining.quote.service;

import com.alibaba.excel.EasyExcel;
import com.machining.quote.util.DrawingPartNoResolver;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PartSelectionImportService {

    public List<Map<String, String>> readPartSelectionRows(MultipartFile excelFile) {
        try {
            List<Map<Integer, String>> rows = EasyExcel.read(excelFile.getInputStream())
                    .headRowNumber(0)
                    .sheet()
                    .doReadSync();

            int partNoColumn = -1;
            int versionColumn = -1;
            int rfqNoColumn = -1;
            int headerRowIndex = -1;
            int maxScanRows = Math.min(rows.size(), 20);
            for (int rowIndex = 0; rowIndex < maxScanRows; rowIndex++) {
                Map<Integer, String> row = rows.get(rowIndex);
                int currentPartNoColumn = -1;
                int currentVersionColumn = -1;
                int currentRfqNoColumn = -1;
                for (Map.Entry<Integer, String> cell : row.entrySet()) {
                    String header = normalizeHeader(cell.getValue());
                    if (isRfqNoHeader(header)) {
                        currentRfqNoColumn = cell.getKey();
                    }
                    if ("\u56fe\u53f7".equals(header)) {
                        currentPartNoColumn = cell.getKey();
                    } else if ("\u7248\u672c".equals(header)) {
                        currentVersionColumn = cell.getKey();
                    }
                }
                if (currentPartNoColumn >= 0 && currentVersionColumn >= 0) {
                    partNoColumn = currentPartNoColumn;
                    versionColumn = currentVersionColumn;
                    rfqNoColumn = currentRfqNoColumn;
                    headerRowIndex = rowIndex;
                    break;
                }
            }

            if (partNoColumn < 0 || versionColumn < 0) {
                throw new IllegalArgumentException("\u5bfc\u5165\u8868\u683c\u5fc5\u987b\u5305\u542b\u8868\u5934\uff1a\u56fe\u53f7\u3001\u7248\u672c");
            }

            List<Map<String, String>> items = new ArrayList<>();
            Set<String> seenKeys = new HashSet<>();
            for (int rowIndex = headerRowIndex + 1; rowIndex < rows.size(); rowIndex++) {
                Map<Integer, String> row = rows.get(rowIndex);
                String partNo = cellText(row.get(partNoColumn));
                String normalizedPartNo = normalizePartNo(partNo);
                if (normalizedPartNo.isEmpty()) {
                    continue;
                }

                String normalizedVersion = normalizeVersion(row.get(versionColumn));
                String rfqNo = rfqNoColumn >= 0 ? cellText(row.get(rfqNoColumn)) : "";
                String normalizedRfqNo = normalizeRfqNo(rfqNo);
                String key = normalizedRfqNo + "||" + normalizedPartNo + "||" + normalizedVersion;
                if (!seenKeys.add(key)) {
                    continue;
                }

                Map<String, String> item = new LinkedHashMap<>();
                item.put("rfqNo", rfqNo);
                item.put("partNo", partNo);
                item.put("version", normalizedVersion);
                item.put("normalizedRfqNo", normalizedRfqNo);
                item.put("normalizedPartNo", normalizedPartNo);
                item.put("normalizedVersion", normalizedVersion);
                items.add(item);
            }

            return items;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("\u5bfc\u5165\u8868\u683c\u9009\u5b9a\u89e3\u6790\u5931\u8d25\uff1a" + e.getMessage(), e);
        }
    }

    private String normalizeHeader(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").trim();
    }

    private boolean isRfqNoHeader(String header) {
        String normalized = header == null ? "" : header.trim().toUpperCase();
        return "RFQ\u53f7".equals(normalized) || "RFQNO".equals(normalized) || "RFQ".equals(normalized);
    }

    private String cellText(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizePartNo(String partNo) {
        return DrawingPartNoResolver.normalizePartNo(partNo);
    }

    private String normalizeVersion(String version) {
        return version == null ? "" : version.trim().toUpperCase();
    }

    private String normalizeRfqNo(String rfqNo) {
        return rfqNo == null ? "" : rfqNo.trim().toUpperCase();
    }
}
