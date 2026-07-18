package com.machining.quote.service;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderProcessSheet;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderProcessSheetMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ExcelExportService {
    private static final int MAX_PROCESS_STEPS = 30;

    @Autowired
    private OrderRfqMapper orderRfqMapper;
    @Autowired
    private ItemQuoteVersionMapper itemMapper;
    @Autowired
    private OrderDeliveryTicketMapper ticketMapper;
    @Autowired
    private OrderProcessSheetMapper processSheetMapper;
    @Autowired
    private LeadTimeService leadTimeService;
    @Autowired(required = false)
    private DataScopeService dataScopeService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public void exportDeliveryTickets(Integer rfqId, HttpServletResponse response) throws IOException {
        assertOrderDataScope(rfqId);
        QueryWrapper<OrderDeliveryTicket> qw = new QueryWrapper<>();
        qw.eq("rfq_id", rfqId).orderByAsc("id");
        List<OrderDeliveryTicket> tickets = ticketMapper.selectList(qw);

        List<List<String>> head = List.of(
                List.of("序号"),
                List.of("订单类型"),
                List.of("内部项目号"),
                List.of("RFQ号"),
                List.of("类型"),
                List.of("图号"),
                List.of("数量"),
                List.of("材料名称"),
                List.of("表面处理"),
                List.of("版本"),
                List.of("开工日期"),
                List.of("交货周期"),
                List.of("备注"),
                List.of("安排类型")
        );

        List<List<Object>> rows = new ArrayList<>();
        for (int i = 0; i < tickets.size(); i++) {
            OrderDeliveryTicket ticket = tickets.get(i);
            rows.add(List.of(
                    i + 1,
                    safe(ticket.getOrderType()),
                    safe(ticket.getInternalProjectNo()),
                    safe(ticket.getProjectNo()),
                    safe(ticket.getItemType()),
                    safe(ticket.getPartNo()),
                    safeInt(ticket.getQty()),
                    safe(ticket.getEngineeringMaterialName()),
                    safe(ticket.getSurfaceTreatment()),
                    safe(ticket.getVersion()),
                    formatDate(ticket.getStartDate()),
                    formatDate(ticket.getDeliveryDate()),
                    displayTicketText(ticket.getRemark()),
                    displayTicketText(ticket.getArrangeType())
            ));
        }

        write(response, buildFileName(rfqId, "提单表"), "提单表", head, rows);
    }

    public void exportProcessSheets(Integer rfqId, HttpServletResponse response) throws IOException {
        assertOrderDataScope(rfqId);
        QueryWrapper<OrderProcessSheet> qw = new QueryWrapper<>();
        qw.eq("rfq_id", rfqId).orderByAsc("id");
        List<OrderProcessSheet> sheets = processSheetMapper.selectList(qw);
        String rfqNo = resolveRfqNo(rfqId);

        List<List<String>> head = new ArrayList<>();
        for (String title : List.of("序号", "内部项目号", "RFQ号", "类型", "图号", "版本", "材料描述", "数量", "材料", "代替材料", "表面处理", "热处理", "交期", "备注")) {
            head.add(List.of(title));
        }
        head.add(9, List.of("毛重(kg)"));
        head.add(10, List.of("净重(kg)"));
        for (int i = 1; i <= MAX_PROCESS_STEPS; i++) {
            head.add(List.of(String.valueOf(i * 10)));
            head.add(List.of("工时"));
        }

        List<List<Object>> rows = new ArrayList<>();
        for (int i = 0; i < sheets.size(); i++) {
            OrderProcessSheet sheet = sheets.get(i);
            List<Object> row = new ArrayList<>();
            row.add(i + 1);
            row.add(safe(sheet.getInternalProjectNo()));
            row.add(rfqNo);
            row.add(safe(sheet.getItemType()));
            row.add(safe(sheet.getPartNo()));
            row.add(safe(sheet.getVersion()));
            row.add(safe(sheet.getDescription()));
            row.add(safeInt(sheet.getQty()));
            row.add(safe(sheet.getEngineeringMaterialName()));
            row.add(safeNumber(sheet.getMaterialGrossWeight()));
            row.add(safeNumber(sheet.getPartNetWeight()));
            row.add(safe(sheet.getSubstituteMaterial()));
            row.add(safe(sheet.getSurfaceTreatment()));
            row.add(safe(sheet.getHeatTreatment()));
            row.add(formatDate(sheet.getDeliveryDate()));
            row.add(safe(sheet.getRemark()));

            List<Map<String, Object>> routes = normalizeProcessRoute(sheet.getProcessRoute());
            for (int j = 0; j < MAX_PROCESS_STEPS; j++) {
                if (j < routes.size()) {
                    Map<String, Object> route = routes.get(j);
                    row.add(safe(route.get("processName")));
                    row.add(processSheetWorkHour(route));
                } else {
                    row.add("");
                    row.add("");
                }
            }
            rows.add(row);
        }

        write(response, buildFileName(rfqId, "工艺表"), "工艺表", head, rows);
    }

    public void exportQuoteSheet(Integer rfqId, HttpServletResponse response) throws IOException {
        assertOrderDataScope(rfqId);
        exportQuoteSheet(rfqId, response, true);
    }

    public void exportCustomerLeadTimeQuoteSheet(Integer rfqId, HttpServletResponse response) throws IOException {
        assertOrderDataScope(rfqId);
        exportQuoteSheet(rfqId, response, false);
    }

    public void exportSimpleQuoteSheet(Integer rfqId, HttpServletResponse response) throws IOException {
        assertOrderDataScope(rfqId);
        QueryWrapper<ItemQuoteVersion> qw = new QueryWrapper<>();
        qw.eq("rfq_id", rfqId).orderByAsc("id");
        List<ItemQuoteVersion> items = itemMapper.selectList(qw);

        List<List<String>> head = buildSimpleQuoteHead();
        List<List<Object>> rows = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            rows.add(buildSimpleQuoteRow(items.get(i), i + 1));
        }
        write(response, buildFileName(rfqId, "简化报价单"), "简化报价单", head, rows);
    }

    public Path exportQuoteSheetFile(Integer rfqId, Path outputDir, String exportType) throws IOException {
        assertOrderDataScope(rfqId);
        Files.createDirectories(outputDir);
        String safeType = exportType == null ? "" : exportType.trim().toUpperCase();
        boolean simple = "SIMPLE".equals(safeType);
        boolean useQuoteLeadTime = !"CUSTOMER_LEAD_TIME".equals(safeType);

        QueryWrapper<ItemQuoteVersion> qw = new QueryWrapper<>();
        qw.eq("rfq_id", rfqId).orderByAsc("id");
        List<ItemQuoteVersion> items = itemMapper.selectList(qw);

        String suffix = simple ? "简化报价单" : "报价单";
        String sheetName = simple ? "简化报价单" : "报价单";
        Path file = uniqueExportFile(outputDir, buildFileName(rfqId, suffix));
        if (simple) {
            List<List<Object>> rows = new ArrayList<>();
            for (int i = 0; i < items.size(); i++) {
                rows.add(buildSimpleQuoteRow(items.get(i), i + 1));
            }
            EasyExcel.write(file.toFile()).head(buildSimpleQuoteHead()).sheet(sheetName).doWrite(rows);
            return file;
        }

        int maxRouteSize = resolveMaxRouteSize(items);
        List<String> feeNames = collectQuoteFeeNames(items);
        List<List<String>> head = buildQuoteHead(maxRouteSize, feeNames);
        List<List<Object>> rows = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            rows.add(buildQuoteRow(items.get(i), i + 1, maxRouteSize, feeNames, useQuoteLeadTime));
        }
        EasyExcel.write(file.toFile()).head(head).sheet(sheetName).doWrite(rows);
        return file;
    }

    private void exportQuoteSheet(Integer rfqId, HttpServletResponse response, boolean useQuoteLeadTime) throws IOException {
        assertOrderDataScope(rfqId);
        QueryWrapper<ItemQuoteVersion> qw = new QueryWrapper<>();
        qw.eq("rfq_id", rfqId).orderByAsc("id");
        List<ItemQuoteVersion> items = itemMapper.selectList(qw);

        int maxRouteSize = resolveMaxRouteSize(items);
        List<String> feeNames = collectQuoteFeeNames(items);
        List<List<String>> head = buildQuoteHead(maxRouteSize, feeNames);
        List<List<Object>> rows = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            ItemQuoteVersion item = items.get(i);
            rows.add(buildQuoteRow(item, i + 1, maxRouteSize, feeNames, useQuoteLeadTime));
        }
        write(response, buildFileName(rfqId, "报价单"), "报价单", head, rows);
    }

    private void assertOrderDataScope(Integer rfqId) {
        if (dataScopeService != null) {
            dataScopeService.assertCanAccessOrder(rfqId, orderRfqMapper);
        }
    }
    private List<List<String>> buildSimpleQuoteHead() {
        List<List<String>> head = new ArrayList<>();
        for (String title : List.of("序号", "图号", "物料描述", "图纸状态", "材料名称", "表面处理", "版本", "数量", "单位")) {
            head.add(List.of("基础信息", title));
        }
        for (String title : List.of("含税单价", "含税总价", "交货周期", "备注")) {
            head.add(List.of("合计", title));
        }
        return head;
    }

    private List<Object> buildSimpleQuoteRow(ItemQuoteVersion item, int index) {
        List<Object> row = new ArrayList<>();
        row.add(index);
        row.add(safe(item.getPartNo()));
        row.add(safe(item.getDescription()));
        row.add(resolveDrawingStatus(item));
        row.add(safe(item.getMaterialName()));
        row.add(safe(item.getSurfaceTreatment()));
        row.add(safe(item.getVersion()));
        row.add(safeInt(item.getQty()));
        row.add(safe(item.getUnit()));

        if (isMissingDrawing(item)) {
            row.add("");
            row.add("");
            row.add(safe(resolveQuoteExportLeadTime(item, true)));
            row.add(appendRemark(item.getRemark(), "无图，未核价"));
            return row;
        }

        double unitPrice = item.getFinalUnitPrice() == null ? 0.0 : item.getFinalUnitPrice();
        double totalPrice = item.getFinalTotalPrice() == null ? unitPrice * safeInt(item.getQty()) : item.getFinalTotalPrice();
        row.add(unitPrice);
        row.add(totalPrice);
        row.add(safe(resolveQuoteExportLeadTime(item, true)));
        row.add(safe(item.getRemark()));
        return row;
    }

    private List<List<String>> buildQuoteHead(int maxRouteSize, List<String> feeNames) {
        List<List<String>> head = new ArrayList<>();
        for (String title : List.of("序号", "图号", "物料描述", "图纸状态", "材料名称", "表面处理", "版本", "数量", "单位", "重量(KG)", "材料费")) {
            head.add(List.of("基础信息", title));
        }
        for (int i = 0; i < maxRouteSize; i++) {
            String stepName = "第" + (i + 1) + "道工艺";
            head.add(List.of(stepName, "工艺名称"));
            head.add(List.of(stepName, "类型"));
            head.add(List.of(stepName, "工时/金额"));
            head.add(List.of(stepName, "单价"));
            head.add(List.of(stepName, "费用"));
        }
        for (String feeName : feeNames) {
            head.add(List.of("百分比费用", feeName + "费率(%)"));
            head.add(List.of("百分比费用", feeName + "金额"));
        }
        for (String title : List.of("基础成本", "百分比费用合计", "含税单价", "含税总价", "交货周期", "备注")) {
            head.add(List.of("合计", title));
        }
        return head;
    }

    private List<Object> buildQuoteRow(ItemQuoteVersion item, int index, int maxRouteSize, List<String> feeNames, boolean useQuoteLeadTime) {
        if (isMissingDrawing(item)) {
            return buildMissingDrawingQuoteRow(item, index, maxRouteSize, feeNames, useQuoteLeadTime);
        }

        Map<String, Object> materialData = normalizeMap(item.getMaterialData());
        List<Map<String, Object>> processRoutes = normalizeProcessRoute(item.getProcessRoute());
        List<Map<String, Object>> feeRows = normalizeProcessRoute(item.getFeeData());

        double materialCost = firstNonNullDouble(materialData.get("cost"), 0.0);
        double processCost = sumProcessCost(processRoutes);
        double markupCost = sumCost(feeRows);
        double baseCost = item.getBaseCost() == null ? materialCost + processCost : item.getBaseCost();
        double unitPrice = item.getFinalUnitPrice() == null ? materialCost + processCost + markupCost : item.getFinalUnitPrice();
        double totalPrice = item.getFinalTotalPrice() == null ? unitPrice * safeInt(item.getQty()) : item.getFinalTotalPrice();

        List<Object> row = new ArrayList<>();
        row.add(index);
        row.add(safe(item.getPartNo()));
        row.add(safe(item.getDescription()));
        row.add(resolveDrawingStatus(item));
        row.add(safe(item.getMaterialName()));
        row.add(safe(item.getSurfaceTreatment()));
        row.add(safe(item.getVersion()));
        row.add(safeInt(item.getQty()));
        row.add(safe(item.getUnit()));
        row.add(firstNonNullDouble(materialData.get("weight"), 0.0));
        row.add(materialCost);

        for (int i = 0; i < maxRouteSize; i++) {
            if (i < processRoutes.size()) {
                Map<String, Object> route = processRoutes.get(i);
                row.add(safe(route.get("processName")));
                row.add(safe(route.get("calcType")));
                row.add(firstNonNullDouble(route.get("inputVal"), 0.0));
                row.add(safeNumber(route.get("unitRate")));
                row.add(processLineCost(route));
            } else {
                row.add("");
                row.add("");
                row.add("");
                row.add("");
                row.add("");
            }
        }

        Map<String, Map<String, Object>> feeMap = mapByName(feeRows, "feeName");
        for (String feeName : feeNames) {
            Map<String, Object> fee = feeMap.get(feeName);
            row.add(fee == null ? "" : firstNonNullDouble(fee.get("ratePercent"), 0.0));
            row.add(fee == null ? "" : firstNonNullDouble(fee.get("cost"), 0.0));
        }

        row.add(baseCost);
        row.add(markupCost);
        row.add(unitPrice);
        row.add(totalPrice);
        row.add(safe(resolveQuoteExportLeadTime(item, useQuoteLeadTime)));
        row.add(safe(item.getRemark()));
        return row;
    }

    private List<Object> buildMissingDrawingQuoteRow(ItemQuoteVersion item, int index, int maxRouteSize, List<String> feeNames, boolean useQuoteLeadTime) {
        List<Object> row = new ArrayList<>();
        row.add(index);
        row.add(safe(item.getPartNo()));
        row.add(safe(item.getDescription()));
        row.add("无图");
        row.add(safe(item.getMaterialName()));
        row.add(safe(item.getSurfaceTreatment()));
        row.add(safe(item.getVersion()));
        row.add(safeInt(item.getQty()));
        row.add(safe(item.getUnit()));
        row.add("");
        row.add("");

        for (int i = 0; i < maxRouteSize; i++) {
            row.add("");
            row.add("");
            row.add("");
            row.add("");
            row.add("");
        }

        for (String ignored : feeNames) {
            row.add("");
            row.add("");
        }

        row.add("");
        row.add("");
        row.add("");
        row.add("");
        row.add(safe(resolveQuoteExportLeadTime(item, useQuoteLeadTime)));
        row.add(appendRemark(item.getRemark(), "无图，未核价"));
        return row;
    }

    private String resolveQuoteExportLeadTime(ItemQuoteVersion item, boolean useQuoteLeadTime) {
        return useQuoteLeadTime ? leadTimeService.effectiveLeadTime(item) : item.getLeadTime();
    }

    private int resolveMaxRouteSize(List<ItemQuoteVersion> items) {
        int max = 0;
        for (ItemQuoteVersion item : items) {
            max = Math.max(max, normalizeProcessRoute(item.getProcessRoute()).size());
        }
        return max;
    }

    private List<String> collectQuoteFeeNames(List<ItemQuoteVersion> items) {
        List<String> names = new ArrayList<>();
        for (ItemQuoteVersion item : items) {
            for (Map<String, Object> fee : normalizeProcessRoute(item.getFeeData())) {
                String feeName = safe(fee.get("feeName"));
                if (!feeName.isEmpty() && !names.contains(feeName)) {
                    names.add(feeName);
                }
            }
        }
        return names;
    }

    private Map<String, Map<String, Object>> mapByName(List<Map<String, Object>> rows, String nameKey) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String name = safe(row.get(nameKey));
            if (!name.isEmpty()) {
                result.put(name, row);
            }
        }
        return result;
    }

    private void write(HttpServletResponse response, String fileName, String sheetName,
                       List<List<String>> head, List<List<Object>> rows) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String encodedName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encodedName + ".xlsx");
        EasyExcel.write(response.getOutputStream()).head(head).sheet(sheetName).doWrite(rows);
    }

    private String buildFileName(Integer rfqId, String suffix) {
        OrderRfq order = orderRfqMapper.selectById(rfqId);
        String rfqNo = order == null ? "" : safe(order.getRfqNo());
        String prefix = rfqNo.isEmpty() ? "RFQ-" + rfqId : rfqNo;
        return prefix + "-" + suffix;
    }

    private Path uniqueExportFile(Path outputDir, String baseName) {
        String cleanName = sanitizeFileName(baseName);
        Path file = outputDir.resolve(cleanName + ".xlsx");
        int index = 1;
        while (Files.exists(file)) {
            file = outputDir.resolve(cleanName + "-" + index + ".xlsx");
            index++;
        }
        return file;
    }

    private String sanitizeFileName(String value) {
        String text = value == null ? "export" : value.trim();
        text = text.replaceAll("[\\\\/:*?\"<>|]", "_");
        return text.isEmpty() ? "export" : text;
    }

    private String resolveRfqNo(Integer rfqId) {
        OrderRfq order = orderRfqMapper.selectById(rfqId);
        String rfqNo = order == null ? "" : safe(order.getRfqNo());
        return rfqNo.isEmpty() ? "RFQ-" + rfqId : rfqNo;
    }

    private List<Map<String, Object>> normalizeProcessRoute(Object routeObject) {
        if (routeObject == null) {
            return List.of();
        }
        if (routeObject instanceof List<?> rawList) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : rawList) {
                if (item instanceof Map<?, ?> rawMap) {
                    Map<String, Object> normalized = new LinkedHashMap<>();
                    rawMap.forEach((key, value) -> normalized.put(String.valueOf(key), value));
                    result.add(normalized);
                }
            }
            sortProcessRoute(result);
            return result;
        }
        if (routeObject instanceof String text && !text.trim().isEmpty()) {
            try {
                List<Map<String, Object>> result = objectMapper.readValue(text, new TypeReference<List<Map<String, Object>>>() {});
                sortProcessRoute(result);
                return result;
            } catch (Exception ignored) {
                return List.of();
            }
        }
        return List.of();
    }

    private void sortProcessRoute(List<Map<String, Object>> routes) {
        boolean hasSortNo = routes.stream().anyMatch(route -> intValue(route.get("sortNo")) != null);
        if (!hasSortNo) {
            return;
        }
        for (int i = 0; i < routes.size(); i++) {
            routes.get(i).putIfAbsent("__index", i);
        }
        routes.sort((left, right) -> {
            Integer leftSort = intValue(left.get("sortNo"));
            Integer rightSort = intValue(right.get("sortNo"));
            if (leftSort != null && rightSort != null && !leftSort.equals(rightSort)) {
                return leftSort.compareTo(rightSort);
            }
            if (leftSort != null && rightSort == null) {
                return -1;
            }
            if (leftSort == null && rightSort != null) {
                return 1;
            }
            return intValue(left.get("__index")).compareTo(intValue(right.get("__index")));
        });
        routes.forEach(route -> route.remove("__index"));
    }

    private Map<String, Object> normalizeMap(Object value) {
        if (value instanceof Map<?, ?> rawMap) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            rawMap.forEach((key, mapValue) -> normalized.put(String.valueOf(key), mapValue));
            return normalized;
        }
        if (value instanceof String text && !text.trim().isEmpty()) {
            try {
                return objectMapper.readValue(text, new TypeReference<Map<String, Object>>() {});
            } catch (Exception ignored) {
                return Map.of();
            }
        }
        return Map.of();
    }

    private double sumProcessCost(List<Map<String, Object>> routes) {
        double total = 0.0;
        for (Map<String, Object> route : routes) {
            total += processLineCost(route);
        }
        return total;
    }

    private double processLineCost(Map<String, Object> route) {
        Double lineCost = doubleValue(route.get("lineCost"));
        if (lineCost != null) {
            return lineCost;
        }
        double inputVal = firstNonNullDouble(route.get("inputVal"), 0.0);
        Double unitRate = doubleValue(route.get("unitRate"));
        if (unitRate != null && unitRate > 0) {
            return inputVal * unitRate;
        }
        return inputVal;
    }

    private String processSheetWorkHour(Map<String, Object> route) {
        String calcType = safe(route.get("calcType")).trim();
        String sourceType = safe(route.get("sourceType")).trim();
        if ("非工时费用".equals(calcType) || "extra".equalsIgnoreCase(sourceType)) {
            return "";
        }
        return safe(route.get("inputVal"));
    }

    private double sumCost(List<Map<String, Object>> rows) {
        double total = 0.0;
        for (Map<String, Object> row : rows) {
            total += firstNonNullDouble(row.get("cost"), 0.0);
        }
        return total;
    }

    private double firstNonNullDouble(Object primary, Object fallback) {
        Double value = doubleValue(primary);
        if (value != null) {
            return value;
        }
        value = doubleValue(fallback);
        return value == null ? 0.0 : value;
    }

    private String formatDate(LocalDate date) {
        return date == null ? "" : date.toString();
    }

    private String safe(Object value) {
        return value == null ? "" : value.toString();
    }

    private String displayTicketText(Object value) {
        return safe(value).replace("待产", "在产");
    }

    private Integer safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private Object safeNumber(Object value) {
        Double number = doubleValue(value);
        return number == null ? "" : number;
    }

    private boolean isMissingDrawing(ItemQuoteVersion item) {
        return isBlank(item.getDrawingUrl());
    }

    private String resolveDrawingStatus(ItemQuoteVersion item) {
        return isBlank(item.getDrawingUrl()) ? "无图" : "有图";
    }

    private String appendRemark(String original, String extra) {
        if (isBlank(original)) {
            return extra;
        }
        return original + "；" + extra;
    }

    private Double doubleValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer intValue(Object value) {
        Double number = doubleValue(value);
        return number == null ? null : number.intValue();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
