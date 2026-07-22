package com.machining.quote.controller;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.config.RequirePermission;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.DeliveryGenerateReq;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.ProcessSheetDTO;
import com.machining.quote.dto.RfqExcelDTO;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.service.AuditService;
import com.machining.quote.service.DataScopeService;
import com.machining.quote.service.DeliveryService;
import com.machining.quote.service.ExcelExportService;
import com.machining.quote.service.InternalTargetLeadTimeService;
import com.machining.quote.service.OrderFilePathService;
import com.machining.quote.service.OrderManagementService;
import com.machining.quote.service.QuoteTemplateExportService;
import com.machining.quote.service.RfqNumberService;
import com.machining.quote.util.DrawingPartNoResolver;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
@RequestMapping("/api/market")
public class MarketController {
    private static final Logger log = LoggerFactory.getLogger(MarketController.class);

    @Autowired
    private DeliveryService deliveryService;

    @Autowired
    private InternalTargetLeadTimeService internalTargetLeadTimeService;

    @Autowired
    private ExcelExportService excelExportService;

    @Autowired
    private QuoteTemplateExportService quoteTemplateExportService;

    @Autowired
    private OrderRfqMapper orderRfqMapper;

    @Autowired
    private OrderManagementService orderManagementService;

    @Autowired
    private ItemQuoteVersionMapper itemQuoteVersionMapper;

    @Autowired
    private OrderFilePathService orderFilePathService;

    @Autowired
    private RfqNumberService rfqNumberService;

    @Autowired
    private AuditService auditService;
    @Autowired(required = false)
    private DataScopeService dataScopeService;

    @GetMapping("/rfq/customers")
    @RequirePermission("market:rfq:customer-select")
    public List<String> listRfqCustomers() {
        return orderManagementService.listCustomers();
    }

    @GetMapping("/quote-process/customers")
    @RequirePermission("market:quote-process:customer-select")
    public List<String> listQuoteProcessCustomers() {
        return orderManagementService.listCustomers();
    }

    @GetMapping("/quote-process/orders")
    @RequirePermission("market:quote-process:order-select")
    public PageResult<OrderRfq> listQuoteProcessOrders(
            @RequestParam(value = "customerName", required = false) String customerName,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return orderManagementService.listQuoteProcessCandidateOrders(customerName, keyword, status, page, pageSize);
    }

    @GetMapping("/release/customers")
    @RequirePermission("market:release:customer-select")
    public List<String> listReleaseCustomers() {
        return orderManagementService.listCustomers();
    }

    @GetMapping("/release/orders")
    @RequirePermission("market:release:order-select")
    public PageResult<OrderRfq> listReleaseOrders(
            @RequestParam(value = "customerName", required = false) String customerName,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return orderManagementService.listOrders(customerName, keyword, status, page, pageSize);
    }

    @GetMapping("/tickets/{rfqId}")
    @RequirePermission("market:release:view")
    public PageResult<OrderDeliveryTicket> listDeliveryTickets(
            @PathVariable Integer rfqId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return deliveryService.pageTickets(rfqId, page, pageSize);
    }

    @GetMapping("/process-sheets/{rfqId}")
    @RequirePermission("market:release:view")
    public PageResult<ProcessSheetDTO> listProcessSheets(
            @PathVariable Integer rfqId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return deliveryService.pageProcessSheets(rfqId, page, pageSize);
    }

    @GetMapping("/export/tickets/{rfqId}")
    @RequirePermission("market:release:view")
    public void exportDeliveryTickets(@PathVariable Integer rfqId, HttpServletResponse response) throws IOException {
        excelExportService.exportDeliveryTickets(rfqId, response);
    }

    @GetMapping("/export/process-sheets/{rfqId}")
    @RequirePermission("market:release:view")
    public void exportProcessSheets(@PathVariable Integer rfqId, HttpServletResponse response) throws IOException {
        excelExportService.exportProcessSheets(rfqId, response);
    }

    @GetMapping("/export/quote/{rfqId}")
    @RequirePermission("market:quote-process:export")
    public void exportQuoteSheet(@PathVariable Integer rfqId, HttpServletResponse response) throws IOException {
        excelExportService.exportQuoteSheet(rfqId, response);
    }

    @GetMapping("/export/quote/simple/{rfqId}")
    @RequirePermission("market:quote-process:export")
    public void exportSimpleQuoteSheet(@PathVariable Integer rfqId, HttpServletResponse response) throws IOException {
        excelExportService.exportSimpleQuoteSheet(rfqId, response);
    }

    @GetMapping("/export/quote/template/detail/{rfqId}")
    @RequirePermission("market:quote-process:export")
    public void exportTemplateQuoteDetail(@PathVariable Integer rfqId, HttpServletResponse response) throws IOException {
        quoteTemplateExportService.exportTemplateQuoteDetail(rfqId, response);
    }

    @GetMapping("/export/quote/template/simple/{rfqId}")
    @RequirePermission("market:quote-process:export")
    public void exportTemplateQuoteSimple(@PathVariable Integer rfqId, HttpServletResponse response) throws IOException {
        quoteTemplateExportService.exportTemplateQuoteSimple(rfqId, response);
    }

    @PostMapping("/generate-ticket")
    @RequirePermission("market:release:create")
    public Map<String, Object> generateDeliveryTicket(@RequestBody DeliveryGenerateReq req) {
        Map<String, Object> result = new HashMap<>();
        List<OrderDeliveryTicket> tickets = deliveryService.generateTickets(req);
        result.put("success", true);
        result.put("message", "提单已生成，共拆分 " + tickets.size() + " 行。");
        result.put("data", tickets);
        return result;
    }

    @GetMapping("/release/summary/{rfqId}")
    @RequirePermission("market:release:view")
    public Map<String, Object> getReleaseSummary(@PathVariable Integer rfqId) {
        return internalTargetLeadTimeService.releaseSummary(rfqId);
    }

    @GetMapping("/release/items/{rfqId}")
    @RequirePermission("market:release:view")
    public PageResult<Map<String, Object>> listReleaseItems(
            @PathVariable Integer rfqId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "remainingOnly", required = false) Boolean remainingOnly) {
        return internalTargetLeadTimeService.pageItemRows(rfqId, page, pageSize, keyword, status, remainingOnly);
    }

    @GetMapping("/release/tickets/{rfqId}")
    @RequirePermission("market:release:view")
    public PageResult<OrderDeliveryTicket> listReleaseTickets(
            @PathVariable Integer rfqId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return deliveryService.pageTickets(rfqId, page, pageSize);
    }

    @GetMapping("/release/process-sheets/{rfqId}")
    @RequirePermission("market:release:view")
    public PageResult<ProcessSheetDTO> listReleaseProcessSheets(
            @PathVariable Integer rfqId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return deliveryService.pageProcessSheets(rfqId, page, pageSize);
    }

    @PostMapping("/release/parse-part-selection/{rfqId}")
    @RequirePermission("market:release:create")
    public Map<String, Object> parsePartSelectionForOrder(
            @PathVariable Integer rfqId,
            @RequestParam("excelFile") MultipartFile excelFile) {
        return internalTargetLeadTimeService.matchPartSelection(rfqId, readPartSelectionRows(excelFile));
    }

    @PostMapping("/parse-part-selection")
    @RequirePermission("market:release:create")
    public Map<String, Object> parsePartSelection(@RequestParam("excelFile") MultipartFile excelFile) {
        List<Map<String, String>> items = readPartSelectionRows(excelFile);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("count", items.size());
        result.put("data", items);
        return result;
    }

    private List<Map<String, String>> readPartSelectionRows(MultipartFile excelFile) {
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

            return items;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("导入表格选定解析失败：" + e.getMessage(), e);
        }
    }

    @PostMapping("/import-rfq")
    @RequirePermission("market:rfq:import")
    @Transactional
    public Map<String, Object> importRfq(
            @RequestParam("customerName") String customerName,
            @RequestParam("excelFile") MultipartFile excelFile,
            @RequestParam(value = "drawings", required = false) List<MultipartFile> drawings) {
        Map<String, Object> result = new HashMap<>();
        long startedAt = System.currentTimeMillis();
        String autoRfqNo = null;
        Integer generatedRfqId = null;

        try {
            autoRfqNo = rfqNumberService.nextRfqNo();
            log.info("RFQ import started customerName={} rfqNo={} excelFileName={} drawingFileCount={}",
                    customerName,
                    autoRfqNo,
                    excelFile == null ? "" : excelFile.getOriginalFilename(),
                    drawings == null ? 0 : drawings.size());

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
            log.info("RFQ import parsed excel rfqNo={} itemCount={}", autoRfqNo, excelDataList.size());

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

                        log.debug("RFQ import drawing parsed rfqNo={} fileName={} partNo={}",
                                autoRfqNo, cleanFileName, partNoFromFileName);

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

            log.info("RFQ import matching started rfqNo={} itemCount={} uploadedDrawingCount={}",
                    autoRfqNo, excelDataList.size(), uploadedDrawings.size());

            OrderRfq newOrder = new OrderRfq();
            newOrder.setRfqNo(autoRfqNo);
            newOrder.setCustomerName(customerName);
            newOrder.setStatus(QuoteStatusWorkflow.UNPRICED);
            if (dataScopeService != null) {
                dataScopeService.assignCurrentOwnership(newOrder);
            }
            orderRfqMapper.insert(newOrder);
            generatedRfqId = newOrder.getId();
            log.info("RFQ import order saved rfqId={} rfqNo={} customerName={}",
                    generatedRfqId, autoRfqNo, customerName);

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
                dbItem.setStatus(QuoteStatusWorkflow.UNPRICED);

                if (matchedDrawingUrl != null) {
                    dbItem.setDrawingUrl(matchedDrawingUrl);
                    matchedItemCount++;
                    matchedDrawingPartNos.add(safePartNo);
                    log.debug("RFQ import item drawing matched rfqId={} partNo={} drawingUrl={}",
                            generatedRfqId, safePartNo, matchedDrawingUrl);
                } else {
                    missingDrawingItems.add(buildMissingDrawingItem(item, safePartNo));
                    log.debug("RFQ import item drawing missing rfqId={} partNo={}",
                            generatedRfqId, safePartNo);
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
            log.info("RFQ import matching completed rfqId={} rfqNo={} matchedDrawingCount={} missingDrawingCount={} unmatchedDrawingCount={}",
                    generatedRfqId,
                    autoRfqNo,
                    matchedDrawingPartNos.size(),
                    missingDrawingItems.size(),
                    unmatchedDrawings.size());
            if (!missingDrawingItems.isEmpty() || !unmatchedDrawings.isEmpty()) {
                log.warn("RFQ import completed with drawing warnings rfqId={} rfqNo={} missingDrawingCount={} unmatchedDrawingCount={}",
                        generatedRfqId, autoRfqNo, missingDrawingItems.size(), unmatchedDrawings.size());
            }

            result.put("success", true);
            result.put("message", "已成功为您生成询价单！<br><b>单号：</b><span style='color:#409EFF'>" + autoRfqNo + "</span>");
            result.put("drawingReport", drawingReport);
            recordRfqImportAudit(customerName, autoRfqNo, generatedRfqId, excelDataList.size(), drawingReport);
            log.info("RFQ import completed rfqId={} rfqNo={} customerName={} itemCount={} matchedDrawingCount={} missingDrawingCount={} unmatchedDrawingCount={} durationMs={}",
                    generatedRfqId,
                    autoRfqNo,
                    customerName,
                    excelDataList.size(),
                    matchedDrawingPartNos.size(),
                    missingDrawingItems.size(),
                    unmatchedDrawings.size(),
                    System.currentTimeMillis() - startedAt);
            return result;

        } catch (Exception e) {
            log.error("RFQ import failed customerName={} rfqNo={} rfqId={} durationMs={}",
                    customerName, autoRfqNo, generatedRfqId, System.currentTimeMillis() - startedAt, e);
            throw new RuntimeException("服务器处理失败：" + e.getMessage(), e);
        }
    }

    private String normalizePartNo(String partNo) {
        return DrawingPartNoResolver.normalizePartNo(partNo);
    }

    private void recordRfqImportAudit(
            String customerName,
            String rfqNo,
            Integer rfqId,
            int itemCount,
            Map<String, Object> drawingReport) {
        if (auditService == null) {
            return;
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("customerName", customerName);
        summary.put("rfqNo", rfqNo);
        summary.put("rfqId", rfqId);
        summary.put("itemCount", itemCount);
        summary.put("uploadedDrawingCount", drawingReport.get("uploadedDrawingCount"));
        summary.put("matchedItemCount", drawingReport.get("matchedItemCount"));
        summary.put("missingDrawingCount", drawingReport.get("missingDrawingCount"));
        summary.put("unmatchedDrawingCount", drawingReport.get("unmatchedDrawingCount"));
        auditService.record(
                "RFQ_IMPORT",
                "order_rfq",
                rfqId,
                null,
                summary,
                "MARKET",
                "SUCCESS",
                "RFQ_IMPORT " + customerName + " " + rfqNo + " itemCount=" + itemCount,
                null
        );
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

    private static <T> PageResult<T> page(List<T> allRows, Integer page, Integer pageSize) {
        List<T> rows = allRows == null ? List.of() : allRows;
        int safePage = page == null || page < 1 ? 1 : page;
        int safePageSize = pageSize == null || pageSize < 1 ? 50 : Math.min(pageSize, 100);
        int fromIndex = (int) Math.min((long) (safePage - 1) * safePageSize, rows.size());
        int toIndex = Math.min(fromIndex + safePageSize, rows.size());
        return PageResult.of(rows.subList(fromIndex, toIndex), rows.size(), safePage, safePageSize);
    }
}
