package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderProcessSheet;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderProcessSheetMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.util.DrawingPartNoResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class OrderManagementService {
    @Autowired
    private OrderRfqMapper orderMapper;
    @Autowired
    private ItemQuoteVersionMapper itemMapper;
    @Autowired
    private OrderDeliveryTicketMapper ticketMapper;
    @Autowired
    private OrderProcessSheetMapper processSheetMapper;
    @Autowired
    private DeliveryService deliveryService;
    @Autowired
    private OrderFilePathService orderFilePathService;
    @Autowired
    private AuditService auditService;

    public List<Map<String, Object>> listOrderSummaries(String customerName) {
        QueryWrapper<OrderRfq> qw = new QueryWrapper<>();
        if (!isBlank(customerName)) {
            qw.eq("customer_name", customerName.trim());
        }
        qw.orderByDesc("id");
        return orderMapper.selectList(qw).stream()
                .map(this::buildSummary)
                .toList();
    }

    public List<String> listCustomers() {
        QueryWrapper<OrderRfq> qw = new QueryWrapper<>();
        qw.select("DISTINCT customer_name");
        qw.isNotNull("customer_name");
        qw.ne("customer_name", "");
        qw.orderByAsc("customer_name");
        return orderMapper.selectList(qw).stream()
                .map(OrderRfq::getCustomerName)
                .filter(customerName -> !isBlank(customerName))
                .toList();
    }

    public Map<String, Object> getOrderDetail(Integer rfqId) {
        OrderRfq order = requireOrder(rfqId);
        Map<String, Object> detail = new HashMap<>();
        detail.put("summary", buildSummary(order));
        detail.put("orderInfo", order);
        detail.put("itemList", listItems(rfqId));
        detail.put("ticketList", listTickets(rfqId));
        detail.put("processSheetList", listProcessSheets(rfqId));
        detail.put("fileRoot", buildOrderFileRoot(order));
        detail.put("missingDrawingFiles", listMissingDrawingFiles(listItems(rfqId)));
        return detail;
    }

    @Transactional
    public void resetProductionData(Integer rfqId) {
        OrderRfq order = requireOrder(rfqId);

        for (OrderDeliveryTicket ticket : listTickets(rfqId)) {
            deliveryService.deleteTicket(ticket.getId());
        }
        for (OrderProcessSheet sheet : listProcessSheets(rfqId)) {
            processSheetMapper.deleteById(sheet.getId());
        }

        order.setStatus(resolveOrderStatusAfterReset(rfqId));
        orderMapper.updateById(order);
    }

    @Transactional
    public Map<String, Object> supplementDrawings(Integer rfqId, List<MultipartFile> drawings) {
        OrderRfq order = requireOrder(rfqId);
        if (drawings == null || drawings.isEmpty()) {
            throw new RuntimeException("请至少上传一份 PDF 图纸");
        }

        List<ItemQuoteVersion> items = listItems(rfqId);
        Map<String, List<ItemQuoteVersion>> itemMap = new HashMap<>();
        for (ItemQuoteVersion item : items) {
            String normalizedPartNo = normalizePartNo(item.getPartNo());
            if (!normalizedPartNo.isEmpty()) {
                itemMap.computeIfAbsent(normalizedPartNo, key -> new ArrayList<>()).add(item);
            }
        }

        File projectDir = orderFilePathService.orderDrawingDirectory(order).toFile();
        if (!projectDir.exists() && !projectDir.mkdirs()) {
            throw new RuntimeException("图纸目录创建失败：" + projectDir.getAbsolutePath());
        }

        List<Map<String, Object>> boundItems = new ArrayList<>();
        List<Map<String, Object>> alreadyBoundItems = new ArrayList<>();
        List<Map<String, Object>> unmatchedDrawings = new ArrayList<>();
        List<Map<String, Object>> skippedFiles = new ArrayList<>();

        for (MultipartFile drawing : drawings) {
            String originalFilename = drawing.getOriginalFilename();
            String cleanFileName = cleanFilename(originalFilename);
            if (isBlank(cleanFileName) || !cleanFileName.toLowerCase().endsWith(".pdf")) {
                skippedFiles.add(buildFileResult(cleanFileName, "", "非 PDF 文件"));
                continue;
            }

            String partNoFromFileName = DrawingPartNoResolver.extractPartNo(cleanFileName, itemMap.keySet());
            List<ItemQuoteVersion> matchedItems = itemMap.get(partNoFromFileName);
            if (matchedItems == null || matchedItems.isEmpty()) {
                unmatchedDrawings.add(buildFileResult(cleanFileName, partNoFromFileName, "未找到该订单对应零件"));
                continue;
            }
            if (matchedItems.stream().allMatch(item -> !isBlank(item.getDrawingUrl()))) {
                for (ItemQuoteVersion item : matchedItems) {
                    alreadyBoundItems.add(buildItemResult(item, cleanFileName, "该零件已有图纸，未覆盖"));
                }
                continue;
            }

            String newFilename = UUID.randomUUID() + ".pdf";
            File dest = new File(projectDir, newFilename);
            try {
                drawing.transferTo(dest);
            } catch (IOException e) {
                throw new RuntimeException("图纸保存失败：" + cleanFileName, e);
            }

            String fileUrl = orderFilePathService.drawingUrl(order, newFilename);
            for (ItemQuoteVersion item : matchedItems) {
                if (!isBlank(item.getDrawingUrl())) {
                    alreadyBoundItems.add(buildItemResult(item, cleanFileName, "该零件已有图纸，未覆盖"));
                    continue;
                }
                item.setDrawingUrl(fileUrl);
                item.setStatus("待核价");
                itemMapper.updateById(item);
                boundItems.add(buildItemResult(item, cleanFileName, "已绑定"));
            }
        }

        if (!boundItems.isEmpty()) {
            order.setStatus(resolveOrderStatusAfterReset(rfqId));
            orderMapper.updateById(order);
            auditService.record("ORDER_SUPPLEMENT_DRAWINGS", "order_rfq", rfqId, null, boundItems);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "补传图纸处理完成，成功绑定 " + boundItems.size() + " 份。");
        result.put("boundCount", boundItems.size());
        result.put("alreadyBoundCount", alreadyBoundItems.size());
        result.put("unmatchedCount", unmatchedDrawings.size());
        result.put("skippedCount", skippedFiles.size());
        result.put("boundItems", boundItems);
        result.put("alreadyBoundItems", alreadyBoundItems);
        result.put("unmatchedDrawings", unmatchedDrawings);
        result.put("skippedFiles", skippedFiles);
        return result;
    }

    @Transactional
    public void deleteOrderCompletely(Integer rfqId, boolean deleteFiles) {
        OrderRfq order = requireOrder(rfqId);

        List<ItemQuoteVersion> items = listItems(rfqId);

        QueryWrapper<OrderProcessSheet> sheetQw = new QueryWrapper<>();
        sheetQw.eq("rfq_id", rfqId);
        processSheetMapper.delete(sheetQw);

        QueryWrapper<OrderDeliveryTicket> ticketQw = new QueryWrapper<>();
        ticketQw.eq("rfq_id", rfqId);
        ticketMapper.delete(ticketQw);

        QueryWrapper<ItemQuoteVersion> itemQw = new QueryWrapper<>();
        itemQw.eq("rfq_id", rfqId);
        itemMapper.delete(itemQw);
        orderMapper.deleteById(rfqId);

        if (deleteFiles) {
            deleteOrderFiles(order, items);
        }
        auditService.record("ORDER_DELETE_COMPLETELY", "order_rfq", rfqId, order, Map.of(
                "itemCount", items.size(),
                "deleteFiles", deleteFiles
        ));
    }

    private Map<String, Object> buildSummary(OrderRfq order) {
        Integer rfqId = order.getId();
        List<ItemQuoteVersion> items = listItems(rfqId);
        List<OrderDeliveryTicket> tickets = listTickets(rfqId);
        List<OrderProcessSheet> sheets = listProcessSheets(rfqId);

        long quotedCount = items.stream().filter(item -> item.getFinalUnitPrice() != null).count();
        long missingDrawingCount = items.stream().filter(item -> isBlank(item.getDrawingUrl())).count();
        int ticketQty = tickets.stream().mapToInt(ticket -> safeInt(ticket.getQty())).sum();

        Map<String, Object> summary = new HashMap<>();
        summary.put("id", order.getId());
        summary.put("rfqNo", order.getRfqNo());
        summary.put("customerName", order.getCustomerName());
        summary.put("projectName", order.getProjectName());
        summary.put("status", order.getStatus());
        summary.put("createdAt", order.getCreatedAt());
        summary.put("itemCount", items.size());
        summary.put("quotedCount", quotedCount);
        summary.put("missingDrawingCount", missingDrawingCount);
        summary.put("ticketCount", tickets.size());
        summary.put("ticketQty", ticketQty);
        summary.put("processSheetCount", sheets.size());
        summary.put("fileRoot", buildOrderFileRoot(order));
        return summary;
    }

    private String resolveOrderStatusAfterReset(Integer rfqId) {
        List<ItemQuoteVersion> items = listItems(rfqId);
        if (items.isEmpty()) {
            return "待工程部评估";
        }
        List<ItemQuoteVersion> drawableItems = items.stream()
                .filter(item -> !isBlank(item.getDrawingUrl()))
                .toList();
        if (drawableItems.isEmpty()) {
            return "待工程部评估";
        }
        boolean allQuoted = drawableItems.stream().allMatch(item -> item.getFinalUnitPrice() != null);
        if (allQuoted) {
            return "已报价";
        }
        return "待工程部评估";
    }

    private OrderRfq requireOrder(Integer rfqId) {
        if (rfqId == null) {
            throw new RuntimeException("订单 ID 不能为空");
        }
        OrderRfq order = orderMapper.selectById(rfqId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        return order;
    }

    private List<ItemQuoteVersion> listItems(Integer rfqId) {
        QueryWrapper<ItemQuoteVersion> qw = new QueryWrapper<>();
        qw.eq("rfq_id", rfqId).orderByAsc("id");
        return itemMapper.selectList(qw);
    }

    private List<OrderDeliveryTicket> listTickets(Integer rfqId) {
        QueryWrapper<OrderDeliveryTicket> qw = new QueryWrapper<>();
        qw.eq("rfq_id", rfqId).orderByAsc("id");
        return ticketMapper.selectList(qw);
    }

    private List<OrderProcessSheet> listProcessSheets(Integer rfqId) {
        QueryWrapper<OrderProcessSheet> qw = new QueryWrapper<>();
        qw.eq("rfq_id", rfqId).orderByAsc("id");
        return processSheetMapper.selectList(qw);
    }

    private void deleteOrderFiles(OrderRfq order, List<ItemQuoteVersion> items) {
        for (ItemQuoteVersion item : items) {
            deleteDrawingByUrl(item.getDrawingUrl());
        }
        deleteDirectoryIfInsideUploadRoot(orderFilePathService.uploadRoot().resolve("orders").resolve(String.valueOf(order.getId())));
        if (!isBlank(order.getCustomerName()) && !isBlank(order.getProjectName())) {
            deleteDirectoryIfInsideUploadRoot(orderFilePathService.orderDrawingDirectory(order));
        }
    }

    private List<Map<String, Object>> listMissingDrawingFiles(List<ItemQuoteVersion> items) {
        List<Map<String, Object>> missingFiles = new ArrayList<>();
        for (ItemQuoteVersion item : items) {
            if (isBlank(item.getDrawingUrl())) {
                continue;
            }
            try {
                String pathText = URI.create(item.getDrawingUrl()).getPath();
                if (!pathText.contains("/uploads/")) {
                    continue;
                }
                String relative = pathText.substring(pathText.indexOf("/uploads/") + "/uploads/".length());
                Path file = orderFilePathService.uploadRoot().resolve(URLDecoder.decode(relative, StandardCharsets.UTF_8)).normalize();
                if (isInsideUploadRoot(file) && !Files.isRegularFile(file)) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("itemId", item.getId());
                    row.put("partNo", item.getPartNo());
                    row.put("version", item.getVersion());
                    row.put("drawingUrl", item.getDrawingUrl());
                    row.put("expectedPath", file.toString());
                    missingFiles.add(row);
                }
            } catch (Exception ignored) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("itemId", item.getId());
                row.put("partNo", item.getPartNo());
                row.put("version", item.getVersion());
                row.put("drawingUrl", item.getDrawingUrl());
                row.put("expectedPath", "");
                missingFiles.add(row);
            }
        }
        return missingFiles;
    }

    private void deleteDrawingByUrl(String drawingUrl) {
        if (isBlank(drawingUrl) || !drawingUrl.contains("/uploads/")) {
            return;
        }
        try {
            String pathText = URI.create(drawingUrl).getPath();
            String relative = pathText.substring(pathText.indexOf("/uploads/") + "/uploads/".length());
            Path file = orderFilePathService.uploadRoot().resolve(URLDecoder.decode(relative, StandardCharsets.UTF_8)).normalize();
            if (isInsideUploadRoot(file) && Files.isRegularFile(file)) {
                Files.deleteIfExists(file);
            }
        } catch (Exception ignored) {
            // 文件删除失败不能影响数据库清理事务。
        }
    }

    private void deleteDirectoryIfInsideUploadRoot(Path directory) {
        Path normalized = directory.normalize();
        if (!isInsideUploadRoot(normalized) || !Files.exists(normalized)) {
            return;
        }
        try {
            Files.walk(normalized)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                            // 保留无法删除的文件，避免管理操作被单个占用文件阻断。
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    private boolean isInsideUploadRoot(Path path) {
        return path.toAbsolutePath().normalize().startsWith(orderFilePathService.uploadRoot());
    }

    private String buildOrderFileRoot(OrderRfq order) {
        return orderFilePathService.orderDrawingDirectory(order).toString();
    }

    private String cleanFilename(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        return originalFilename.replaceAll(".*[\\\\/]", "").trim();
    }

    private String normalizePartNo(String partNo) {
        return DrawingPartNoResolver.normalizePartNo(partNo);
    }

    private Map<String, Object> buildItemResult(ItemQuoteVersion item, String fileName, String reason) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("partNo", item.getPartNo());
        result.put("version", item.getVersion());
        result.put("description", item.getDescription());
        result.put("fileName", fileName);
        result.put("reason", reason);
        return result;
    }

    private Map<String, Object> buildFileResult(String fileName, String partNo, String reason) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fileName", fileName);
        result.put("partNo", partNo);
        result.put("reason", reason);
        return result;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
