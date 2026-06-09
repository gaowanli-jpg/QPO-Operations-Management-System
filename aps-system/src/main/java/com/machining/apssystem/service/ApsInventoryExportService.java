package com.machining.apssystem.service;

import com.alibaba.excel.EasyExcel;
import com.machining.apssystem.dto.ApsPartInventoryLockDTO;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ApsInventoryExportService {
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    @Autowired
    private ApsInventoryService inventoryService;

    public void exportPartLocks(String customerName, Integer requestedLimit, HttpServletResponse response) throws IOException {
        List<ApsPartInventoryLockDTO> records = inventoryService.listPartLocks(customerName, requestedLimit);
        String customer = text(customerName);
        String fileName = "零件锁定记录-" + (customer.isEmpty() ? "全部客户" : sanitizeFileName(customer))
                + "-" + LocalDate.now().format(FILE_DATE);
        write(response, fileName, partLockHead(), partLockRows(records));
    }

    private void write(
            HttpServletResponse response,
            String fileName,
            List<List<String>> head,
            List<List<Object>> rows) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String encodedName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encodedName + ".xlsx");
        EasyExcel.write(response.getOutputStream()).head(head).sheet("零件锁定记录").doWrite(rows);
    }

    private List<List<String>> partLockHead() {
        return List.of(
                List.of("任务"),
                List.of("内部项目号"),
                List.of("客户订单号"),
                List.of("客户项目号"),
                List.of("客户"),
                List.of("图号"),
                List.of("版本"),
                List.of("锁定库存"),
                List.of("在产单号"),
                List.of("锁定数量")
        );
    }

    private List<List<Object>> partLockRows(List<ApsPartInventoryLockDTO> records) {
        return records.stream()
                .map(row -> List.of(
                        object(row.getWorkItemId()),
                        value(row.getInternalProjectNo()),
                        value(row.getCustomerOrderNo()),
                        customerProjectText(row.getCustomerProjectNo()),
                        value(row.getCustomerName()),
                        value(row.getPartNo()),
                        value(row.getVersion()),
                        value(row.getLockTypeText()),
                        value(row.getPendingRefNo()),
                        object(row.getLockedQty())
                ))
                .toList();
    }

    private Object object(Object value) {
        return value == null ? "" : value;
    }

    private String value(String value) {
        String text = text(value);
        return text.isEmpty() ? "" : text;
    }

    private String customerProjectText(String value) {
        String text = text(value);
        return text.isEmpty() ? "空" : text;
    }

    private String sanitizeFileName(String value) {
        String text = text(value).replaceAll("[\\\\/:*?\"<>|]", "_");
        return text.isEmpty() ? "全部客户" : text;
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
