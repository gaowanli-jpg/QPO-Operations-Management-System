package com.machining.quote.controller;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.config.RequireRole;
import com.machining.quote.dto.DeliveryGenerateReq;
import com.machining.quote.dto.RfqExcelDTO;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderProcessSheet;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.service.DeliveryService;
import com.machining.quote.service.ExcelExportService;
import com.machining.quote.service.OrderFilePathService;
import com.machining.quote.service.RfqNumberService;
import com.machining.quote.util.DrawingPartNoResolver;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequireRole({"MARKET", "ADMIN"})
@RequestMapping("/api/market")
public class MarketController {

    @Autowired
    private DeliveryService deliveryService;

    @Autowired
    private ExcelExportService excelExportService;

    @Autowired
    private OrderRfqMapper orderRfqMapper;

    @Autowired
    private ItemQuoteVersionMapper itemQuoteVersionMapper;

    @Autowired
    private OrderFilePathService orderFilePathService;

    @Autowired
    private RfqNumberService rfqNumberService;

    @GetMapping("/orders")
    @RequireRole({"MARKET", "ENGINEER", "ADMIN"})
    public List<OrderRfq> listOrders() {
        QueryWrapper<OrderRfq> qw = new QueryWrapper<>();
        qw.orderByDesc("id");
        return orderRfqMapper.selectList(qw);
    }

    @GetMapping("/tickets/{rfqId}")
    public List<OrderDeliveryTicket> listDeliveryTickets(@PathVariable Integer rfqId) {
        return deliveryService.listTickets(rfqId);
    }

    @GetMapping("/process-sheets/{rfqId}")
    public List<OrderProcessSheet> listProcessSheets(@PathVariable Integer rfqId) {
        return deliveryService.listProcessSheets(rfqId);
    }

    @DeleteMapping("/tickets/{ticketId}")
    public Map<String, Object> deleteDeliveryTicket(@PathVariable Integer ticketId) {
        deliveryService.deleteTicket(ticketId);
        return Map.of("success", true, "message", "提单行已删除");
    }

    @DeleteMapping("/process-sheets/{sheetId}")
    public Map<String, Object> deleteProcessSheet(@PathVariable Integer sheetId) {
        deliveryService.deleteProcessSheet(sheetId);
        return Map.of("success", true, "message", "工艺表行已删除");
    }

    @GetMapping("/export/tickets/{rfqId}")
    public void exportDeliveryTickets(@PathVariable Integer rfqId, HttpServletResponse response) throws IOException {
        excelExportService.exportDeliveryTickets(rfqId, response);
    }

    @GetMapping("/export/process-sheets/{rfqId}")
    public void exportProcessSheets(@PathVariable Integer rfqId, HttpServletResponse response) throws IOException {
        excelExportService.exportProcessSheets(rfqId, response);
    }

    @GetMapping("/export/quote/{rfqId}")
    @RequireRole({"MARKET", "ENGINEER", "ADMIN"})
    public void exportQuoteSheet(@PathVariable Integer rfqId, HttpServletResponse response) throws IOException {
        excelExportService.exportQuoteSheet(rfqId, response);
    }

    @GetMapping("/export/quote/customer-lead-time/{rfqId}")
    @RequireRole({"MARKET", "ENGINEER", "ADMIN"})
    public void exportCustomerLeadTimeQuoteSheet(@PathVariable Integer rfqId, HttpServletResponse response) throws IOException {
        excelExportService.exportCustomerLeadTimeQuoteSheet(rfqId, response);
    }

    @PostMapping("/generate-ticket")
    public Map<String, Object> generateDeliveryTicket(@RequestBody DeliveryGenerateReq req) {
        Map<String, Object> result = new HashMap<>();
        List<OrderDeliveryTicket> tickets = deliveryService.generateTickets(req);
        result.put("success", true);
        result.put("message", "提单已生成，共拆分 " + tickets.size() + " 行。");
        result.put("data", tickets);
        return result;
    }

    @GetMapping("/summary/{rfqId}")
    @RequireRole({"MARKET", "ENGINEER", "ADMIN"})
    public Map<String, Object> getQuoteSummary(@PathVariable Integer rfqId) {
        OrderRfq order = orderRfqMapper.selectById(rfqId);
        QueryWrapper<ItemQuoteVersion> qw = new QueryWrapper<>();
        qw.eq("rfq_id", rfqId).orderByAsc("id");
        List<ItemQuoteVersion> items = itemQuoteVersionMapper.selectList(qw);

        Map<String, Object> result = new HashMap<>();
        result.put("orderInfo", order);
        result.put("itemList", items);
        return result;
    }

    @PostMapping("/parse-part-selection")
    public Map<String, Object> parsePartSelection(@RequestParam("excelFile") MultipartFile excelFile) {
        try {
            List<Map<Integer, String>> rows = EasyExcel.read(excelFile.getInputStream())
                    .headRowNumber(0)
                    .sheet()
                    .doReadSync();

            int partNoColumn = -1;
            int versionColumn = -1;
            int headerRowIndex = -1;
            int maxScanRows = Math.min(rows.size(), 20);
            for (int rowIndex = 0; rowIndex < maxScanRows; rowIndex++) {
                Map<Integer, String> row = rows.get(rowIndex);
                int currentPartNoColumn = -1;
                int currentVersionColumn = -1;
                for (Map.Entry<Integer, String> cell : row.entrySet()) {
                    String header = normalizeHeader(cell.getValue());
                    if ("图号".equals(header)) {
                        currentPartNoColumn = cell.getKey();
                    } else if ("版本".equals(header)) {
                        currentVersionColumn = cell.getKey();
                    }
                }
                if (currentPartNoColumn >= 0 && currentVersionColumn >= 0) {
                    partNoColumn = currentPartNoColumn;
                    versionColumn = currentVersionColumn;
                    headerRowIndex = rowIndex;
                    break;
                }
            }

            if (partNoColumn < 0 || versionColumn < 0) {
                throw new IllegalArgumentException("导入表格必须包含表头：图号、版本");
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
                String key = normalizedPartNo + "||" + normalizedVersion;
                if (!seenKeys.add(key)) {
                    continue;
                }

                Map<String, String> item = new LinkedHashMap<>();
                item.put("partNo", partNo);
                item.put("version", normalizedVersion);
                item.put("normalizedPartNo", normalizedPartNo);
                item.put("normalizedVersion", normalizedVersion);
                items.add(item);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("count", items.size());
            result.put("data", items);
            return result;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("导入表格选定解析失败：" + e.getMessage(), e);
        }
    }

    @PostMapping("/import-rfq")
    @Transactional
    public Map<String, Object> importRfq(
            @RequestParam("customerName") String customerName,
            @RequestParam("excelFile") MultipartFile excelFile,
            @RequestParam(value = "drawings", required = false) List<MultipartFile> drawings) {

        Map<String, Object> result = new HashMap<>();

        try {
            System.out.println("\n====== 🚀 市场部新单导入开始 ======");

            String autoRfqNo = rfqNumberService.nextRfqNo();
            System.out.println("客户名称: " + customerName + " | RFQ: " + autoRfqNo);

            List<RfqExcelDTO> excelDataList = EasyExcel.read(excelFile.getInputStream())
                    .head(RfqExcelDTO.class)
                    .sheet()
                    .doReadSync();
            Set<String> excelPartNos = new HashSet<>();
            for (RfqExcelDTO item : excelDataList) {
                String safePartNo = normalizePartNo(item.getPartNo());
                if (!safePartNo.isEmpty()) {
                    excelPartNos.add(safePartNo);
                }
            }
            System.out.println("✅ 成功解析 Excel，共找到 " + excelDataList.size() + " 个零件明细！");

            Map<String, DrawingFileInfo> drawingMap = new LinkedHashMap<>();
            List<DrawingFileInfo> uploadedDrawings = new ArrayList<>();
            Path projectDir = null;
            if (drawings != null && !drawings.isEmpty()) {
                OrderRfq pathOrder = new OrderRfq();
                pathOrder.setCustomerName(customerName);
                pathOrder.setRfqNo(autoRfqNo);
                projectDir = orderFilePathService.orderDrawingDirectory(pathOrder);
                Files.createDirectories(projectDir);

                for (MultipartFile drawing : drawings) {
                    String originalFilename = drawing.getOriginalFilename();
                    if (originalFilename != null && originalFilename.toLowerCase().endsWith(".pdf")) {

                        String cleanFileName = originalFilename.replaceAll(".*[\\\\/]", "");

                        String partNoFromFileName = DrawingPartNoResolver.extractPartNo(cleanFileName, excelPartNos);

                        System.out.println("📡 硬盘图纸脱水成功，拿到纯图号 -> [" + partNoFromFileName + "]");

                        String newFilename = UUID.randomUUID().toString() + ".pdf";

                        Path dest = projectDir.resolve(newFilename).normalize();
                        if (!dest.startsWith(projectDir)) {
                            throw new IOException("图纸保存路径非法：" + cleanFileName);
                        }
                        drawing.transferTo(dest);

                        String fileUrl = orderFilePathService.drawingUrl(pathOrder, newFilename);
                        DrawingFileInfo drawingFileInfo = new DrawingFileInfo(partNoFromFileName, cleanFileName, fileUrl);
                        uploadedDrawings.add(drawingFileInfo);
                        drawingMap.put(partNoFromFileName, drawingFileInfo);
                    }
                }
            }

            System.out.println("====== ⚙️ 开始智能匹配与数据落库 ======");

            OrderRfq newOrder = new OrderRfq();
            newOrder.setRfqNo(autoRfqNo);
            newOrder.setCustomerName(customerName);
            newOrder.setStatus("待工程部评估");
            orderRfqMapper.insert(newOrder);
            Integer generatedRfqId = newOrder.getId();
            System.out.println("📄 已生成主单，单号 ID: " + generatedRfqId);

            Set<String> matchedDrawingPartNos = new HashSet<>();
            List<Map<String, Object>> missingDrawingItems = new ArrayList<>();
            int matchedItemCount = 0;

            for (RfqExcelDTO item : excelDataList) {
                String originalPartNo = item.getPartNo();
                String safePartNo = normalizePartNo(originalPartNo);
                if (!safePartNo.isEmpty()) {
                    excelPartNos.add(safePartNo);
                }

                DrawingFileInfo matchedDrawing = drawingMap.get(safePartNo);
                String matchedDrawingUrl = matchedDrawing == null ? null : matchedDrawing.url();

                ItemQuoteVersion dbItem = new ItemQuoteVersion();
                dbItem.setRfqId(generatedRfqId);
                dbItem.setPartNo(originalPartNo);
                dbItem.setDescription(item.getDescription());
                dbItem.setQty(item.getQty());
                dbItem.setMaterialName(item.getMaterialName());
                dbItem.setSurfaceTreatment(item.getSurfaceTreatment());
                dbItem.setVersion(normalizeVersion(item.getVersion()));
                dbItem.setUnit(item.getUnit());
                dbItem.setLeadTime(item.getLeadTime());
                dbItem.setRemark(item.getRemark());
                dbItem.setStatus("待核价");

                if (matchedDrawingUrl != null) {
                    dbItem.setDrawingUrl(matchedDrawingUrl);
                    matchedItemCount++;
                    matchedDrawingPartNos.add(safePartNo);
                    System.out.println("🔗 [" + safePartNo + "] 绑定图纸: " + matchedDrawingUrl);
                } else {
                    missingDrawingItems.add(buildMissingDrawingItem(item, safePartNo));
                    System.out.println("⚠️ [" + safePartNo + "] 无图纸导入");
                }

                itemQuoteVersionMapper.insert(dbItem);
            }

            List<Map<String, String>> unmatchedDrawings = new ArrayList<>();
            for (DrawingFileInfo drawing : uploadedDrawings) {
                if (!excelPartNos.contains(drawing.partNo())) {
                    Map<String, String> unmatchedDrawing = new LinkedHashMap<>();
                    unmatchedDrawing.put("partNo", drawing.partNo());
                    unmatchedDrawing.put("fileName", drawing.originalFilename());
                    unmatchedDrawings.add(unmatchedDrawing);
                }
            }

            Map<String, Object> drawingReport = new LinkedHashMap<>();
            drawingReport.put("totalItemCount", excelDataList.size());
            drawingReport.put("uploadedDrawingCount", uploadedDrawings.size());
            drawingReport.put("matchedItemCount", matchedItemCount);
            drawingReport.put("matchedDrawingCount", matchedDrawingPartNos.size());
            drawingReport.put("missingDrawingCount", missingDrawingItems.size());
            drawingReport.put("unmatchedDrawingCount", unmatchedDrawings.size());
            drawingReport.put("missingItems", missingDrawingItems);
            drawingReport.put("unmatchedDrawings", unmatchedDrawings);
            System.out.println("========================================\n");

            result.put("success", true);
            result.put("message", "已成功为您生成询价单！<br><b>单号：</b><span style='color:#409EFF'>" + autoRfqNo + "</span>");
            result.put("drawingReport", drawingReport);
            return result;

        } catch (Exception e) {
            throw new RuntimeException("服务器处理失败：" + e.getMessage(), e);
        }
    }

    private String normalizePartNo(String partNo) {
        return DrawingPartNoResolver.normalizePartNo(partNo);
    }

    private String normalizeHeader(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").trim();
    }

    private String cellText(String value) {
        return value == null ? "" : value.trim();
    }

    private Map<String, Object> buildMissingDrawingItem(RfqExcelDTO item, String normalizedPartNo) {
        Map<String, Object> missingItem = new LinkedHashMap<>();
        String partNo = item.getPartNo();
        missingItem.put("partNo", partNo == null || partNo.isBlank() ? normalizedPartNo : partNo.trim());
        missingItem.put("version", normalizeVersion(item.getVersion()));
        missingItem.put("description", item.getDescription());
        missingItem.put("qty", item.getQty());
        return missingItem;
    }

    private record DrawingFileInfo(String partNo, String originalFilename, String url) {
    }

    private String normalizeVersion(String version) {
        String normalized = version == null ? "" : version.trim().toUpperCase();
        return normalized;
    }
}
