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

    @PostMapping("/import-rfq")
    @Transactional
    public Map<String, Object> importRfq(
            @RequestParam("customerName") String customerName,
            @RequestParam(value = "projectName", required = false) String projectName,
            @RequestParam("excelFile") MultipartFile excelFile,
            @RequestParam(value = "drawings", required = false) List<MultipartFile> drawings) {

        Map<String, Object> result = new HashMap<>();

        try {
            System.out.println("\n====== 🚀 市场部新单导入开始 ======");

            String safeProjectName = (projectName != null && !projectName.trim().isEmpty()) ? projectName : "默认未命名项目";
            System.out.println("客户名称: " + customerName + " | 项目: " + safeProjectName);

            QueryWrapper<OrderRfq> dupCheck = new QueryWrapper<>();
            dupCheck.eq("customer_name", customerName).eq("project_name", safeProjectName);
            if (orderRfqMapper.selectCount(dupCheck) > 0) {
                System.out.println("❌ 拦截：项目重复提交！");
                result.put("success", false);
                result.put("message", "拦截提示：客户【" + customerName + "】下已存在名为【" + safeProjectName + "】的项目，请勿重复导入！");
                return result;
            }

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
                pathOrder.setProjectName(safeProjectName);
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
            String autoRfqNo = "RFQ-" + System.currentTimeMillis();
            newOrder.setRfqNo(autoRfqNo);
            newOrder.setCustomerName(customerName);
            newOrder.setProjectName(safeProjectName);
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
                dbItem.setVersion(item.getVersion());
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

    private Map<String, Object> buildMissingDrawingItem(RfqExcelDTO item, String normalizedPartNo) {
        Map<String, Object> missingItem = new LinkedHashMap<>();
        String partNo = item.getPartNo();
        missingItem.put("partNo", partNo == null || partNo.isBlank() ? normalizedPartNo : partNo.trim());
        missingItem.put("description", item.getDescription());
        missingItem.put("qty", item.getQty());
        return missingItem;
    }

    private record DrawingFileInfo(String partNo, String originalFilename, String url) {
    }
}
