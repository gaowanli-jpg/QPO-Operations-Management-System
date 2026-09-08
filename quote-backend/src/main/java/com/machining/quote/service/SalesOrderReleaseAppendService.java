package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.machining.quote.config.AuthContext;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.CustomerOptionDTO;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.ReleaseSourceType;
import com.machining.quote.dto.SalesOrderAutoSnapshotCompletionResult;
import com.machining.quote.dto.SalesOrderQpoExcelLineDTO;
import com.machining.quote.dto.SalesOrderQpoExcelParseResult;
import com.machining.quote.dto.SalesOrderQpoImportDefaultsDTO;
import com.machining.quote.dto.SalesOrderQpoOrderGroupDTO;
import com.machining.quote.dto.SalesOrderReleaseAppendResp;
import com.machining.quote.dto.SalesOrderReleaseAppendRow;
import com.machining.quote.dto.SalesOrderU9SyncOrderRow;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.util.DrawingPartNoResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class SalesOrderReleaseAppendService {
    private static final Logger log = LoggerFactory.getLogger(SalesOrderReleaseAppendService.class);
    private static final String U9_UNSYNCED = "未同步";
    private static final String U9_FAILED = "同步失败";
    private static final String U9_SYNCED = "已同步";
    private static final String U9_SYNCING = "同步中";
    private static final String U9_PENDING_CONFIRMATION = "结果待确认";
    private static final String MATERIAL_UNCONFIRMED = "未确认";
    private static final String QUOTED = QuoteStatusWorkflow.QUOTED;
    private static final String SOURCE_TYPE_APPEND = "DIRECT_IMPORT_APPEND";
    private static final String AUDIT_ACTION = "SALES_ORDER_APPEND_RELEASE";
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    @Autowired
    private SalesOrderQpoExcelParser excelParser;
    @Autowired
    private SalesOrderHeaderMapper headerMapper;
    @Autowired
    private SalesOrderItemMapper itemMapper;
    @Autowired
    private SalesOrderAutoSnapshotCompletionService snapshotCompletionService;
    @Autowired
    private DeliveryService deliveryService;
    @Autowired(required = false)
    private BusinessFileService businessFileService;
    @Autowired(required = false)
    private AuditService auditService;
    @Autowired(required = false)
    private DataScopeService dataScopeService;
    @Autowired(required = false)
    private OrderManagementService orderManagementService;
    @Autowired(required = false)
    private SalesOrderNumberService salesOrderNumberService = new SalesOrderNumberService();
    @Autowired(required = false)
    private DeliveryAutoMesMaterialSyncService autoMesMaterialSyncService;

    public PageResult<CustomerOptionDTO> pageCustomerOptions(String field, String keyword, Integer page, Integer pageSize) {
        if (orderManagementService == null) {
            return PageResult.of(List.of(), 0, normalizePage(page), normalizePageSize(pageSize));
        }
        return orderManagementService.pageCustomerOptions(field, keyword, page, pageSize);
    }

    public PageResult<SalesOrderU9SyncOrderRow> listAppendTargetSalesOrders(
            String customerCode,
            String customerName,
            String keyword,
            Integer page,
            Integer pageSize) {
        int safePage = normalizePage(page);
        int safePageSize = normalizePageSize(pageSize);
        QueryWrapper<SalesOrderHeader> query = new QueryWrapper<>();
        applySalesOrderScope(query);
        query.and(wrapper -> wrapper.isNull("u9_sync_status")
                .or()
                .eq("u9_sync_status", "")
                .or()
                .in("u9_sync_status", List.of(U9_UNSYNCED, U9_FAILED)));
        if (!isBlank(customerCode)) {
            query.eq("customer_code", customerCode.trim());
        } else if (!isBlank(customerName)) {
            query.eq("customer_name", customerName.trim());
        }
        if (!isBlank(keyword)) {
            String text = keyword.trim();
            query.and(wrapper -> wrapper
                    .like("sales_order_no", text)
                    .or()
                    .like("document_no", text)
                    .or()
                    .like("customer_order_no", text)
                    .or()
                    .like("internal_project_no", text)
                    .or()
                    .like("customer_project_no", text));
        }
        Long total = headerMapper.selectCount(query);
        if (total == null || total == 0) {
            return PageResult.of(List.of(), 0, safePage, safePageSize);
        }
        query.orderByDesc("id");
        query.last("LIMIT " + safePageSize + " OFFSET " + ((safePage - 1L) * safePageSize));
        return PageResult.of(headerMapper.selectList(query).stream().map(this::toOrderRow).toList(), total, safePage, safePageSize);
    }

    @Transactional
    public SalesOrderReleaseAppendResp appendItems(
            Long salesOrderId,
            MultipartFile excelFile,
            List<MultipartFile> drawings,
            List<MultipartFile> versionDrawings) {
        return appendItems(salesOrderId, excelFile, drawings, versionDrawings, null, null);
    }

    @Transactional
    public SalesOrderReleaseAppendResp appendItems(
            Long salesOrderId,
            MultipartFile excelFile,
            List<MultipartFile> drawings,
            List<MultipartFile> versionDrawings,
            List<MultipartFile> drawingFolderFiles,
            String drawingFolderName) {
        validateFile(excelFile);
        long startedAt = System.currentTimeMillis();
        SalesOrderHeader target = lockAndValidateTarget(salesOrderId);
        SalesOrderQpoImportDefaultsDTO defaults = defaultsFromTarget(target);
        SalesOrderQpoExcelParseResult parseResult = parse(excelFile, defaults);
        if (parseResult.getLineCount() <= 0 || parseResult.getOrders().isEmpty()) {
            throw new IllegalArgumentException("销售订单表格没有可追加的零件行");
        }
        String sourceFileName = safeFileName(excelFile.getOriginalFilename());
        String batchNo = nextBatchNo();
        DrawingBindingResult drawingBinding = bindDrawings(target, batchNo, parseResult, drawings, versionDrawings, drawingFolderFiles, drawingFolderName);

        int nextLineNo = nextLineNo(target.getId());
        LocalDateTime now = LocalDateTime.now();
        List<SalesOrderItem> insertedItems = new ArrayList<>();
        List<SalesOrderReleaseAppendRow> rows = new ArrayList<>();
        for (SalesOrderQpoOrderGroupDTO group : safeGroups(parseResult)) {
            for (SalesOrderQpoExcelLineDTO line : safeLines(group)) {
                SalesOrderItem item = toItem(target, applyHeaderFallback(line, target), nextLineNo++, now);
                applyDrawingBinding(item, line, drawingBinding);
                itemMapper.insert(item);
                insertedItems.add(item);
                rows.add(toRow(item, line));
            }
        }
        List<Long> insertedItemIds = insertedItems.stream()
                .map(SalesOrderItem::getId)
                .filter(Objects::nonNull)
                .toList();

        SalesOrderAutoSnapshotCompletionResult matchResult = snapshotCompletionService == null || insertedItemIds.isEmpty()
                ? null
                : snapshotCompletionService.completeImportedOrderItems(target.getId(), insertedItemIds);
        List<OrderDeliveryTicket> tickets = deliveryService == null || insertedItemIds.isEmpty()
                ? List.of()
                : deliveryService.generateImportedSalesOrderTicketsForItems(target.getId(), insertedItemIds, AUDIT_ACTION);
        refreshTargetAfterAppend(target, insertedItems.size(), now);
        submitAutoMesMaterial(target, tickets);

        SalesOrderReleaseAppendResp resp = new SalesOrderReleaseAppendResp();
        resp.setSuccess(true);
        resp.setStatus("SUCCESS");
        resp.setTargetSalesOrderId(target.getId());
        resp.setTargetSalesOrderNo(target.getSalesOrderNo());
        resp.setAppendedItemCount(insertedItems.size());
        resp.setTicketCount(tickets.size());
        resp.setTicketQty(ticketQty(tickets));
        resp.setDrawingReport(drawingBinding.report());
        resp.setRows(rows);
        resp.setMessage("已追加到销售订单 " + target.getSalesOrderNo() + "，新增 " + insertedItems.size()
                + " 条零件并生成 " + tickets.size() + " 条提单行。");
        recordAudit(target, sourceFileName, resp, drawingBinding, matchResult, startedAt);
        log.info("Sales order release append completed targetSalesOrderId={} appendedItemCount={} ticketCount={} uploadedDrawingCount={} matchedDrawingCount={} durationMs={}",
                target.getId(), insertedItems.size(), tickets.size(), drawingBinding.uploadedDrawingCount(),
                drawingBinding.matchedItemCount(), System.currentTimeMillis() - startedAt);
        return resp;
    }

    private SalesOrderHeader lockAndValidateTarget(Long salesOrderId) {
        if (salesOrderId == null) {
            throw new RuntimeException("目标销售订单不能为空");
        }
        SalesOrderHeader target = headerMapper.selectByIdForUpdate(salesOrderId);
        if (target == null) {
            throw new RuntimeException("目标销售订单不存在");
        }
        if (dataScopeService != null) {
            dataScopeService.assertCanAccessSalesOrder(target);
        }
        String u9Status = trimToNull(target.getU9SyncStatus());
        if (U9_SYNCED.equals(u9Status) || U9_SYNCING.equals(u9Status) || U9_PENDING_CONFIRMATION.equals(u9Status)) {
            throw new RuntimeException("不能追加到已同步或同步中的销售订单");
        }
        if (u9Status != null && !U9_UNSYNCED.equals(u9Status) && !U9_FAILED.equals(u9Status)) {
            throw new RuntimeException("不能追加到当前 U9 状态的销售订单：" + u9Status);
        }
        return target;
    }

    private SalesOrderQpoImportDefaultsDTO defaultsFromTarget(SalesOrderHeader target) {
        SalesOrderQpoImportDefaultsDTO defaults = new SalesOrderQpoImportDefaultsDTO();
        defaults.setSalesOrderDate(target.getSalesOrderDate());
        defaults.setCustomerOrderNo(requiredText(target.getCustomerOrderNo(), "目标销售订单客户订单号不能为空"));
        defaults.setCustomerProjectNo(trimToNull(target.getCustomerProjectNo()));
        defaults.setCustomerOrderTitle(trimToNull(target.getCustomerOrderTitle()));
        defaults.setOrderType(requiredText(target.getOrderType(), "目标销售订单订单类型不能为空"));
        defaults.setInternalProjectNo(requiredText(target.getInternalProjectNo(), "目标销售订单内部项目号不能为空"));
        return defaults;
    }

    private SalesOrderQpoExcelParseResult parse(MultipartFile excelFile, SalesOrderQpoImportDefaultsDTO defaults) {
        try (InputStream inputStream = excelFile.getInputStream()) {
            return excelParser.parse(inputStream, defaults);
        } catch (IOException e) {
            throw new RuntimeException("销售订单追加表格读取失败：" + safeError(e.getMessage()), e);
        }
    }

    private int nextLineNo(Long salesOrderId) {
        List<SalesOrderItem> latest = itemMapper.selectList(new QueryWrapper<SalesOrderItem>()
                .eq("sales_order_id", salesOrderId)
                .orderByDesc("line_no")
                .orderByDesc("id")
                .last("LIMIT 1"));
        if (latest == null || latest.isEmpty() || latest.get(0).getLineNo() == null) {
            return 1;
        }
        return latest.get(0).getLineNo() + 1;
    }

    private SalesOrderItem toItem(SalesOrderHeader target, SalesOrderQpoExcelLineDTO line, int lineNo, LocalDateTime now) {
        SalesOrderItem item = new SalesOrderItem();
        item.setSalesOrderId(target.getId());
        item.setSourceType(SOURCE_TYPE_APPEND);
        item.setSourceOrderId(target.getId());
        item.setSalesOrderDate(line.getSalesOrderDate());
        item.setLineNo(lineNo);
        item.setPartNo(line.getPartNo());
        item.setDescription(line.getDescription());
        item.setQty(line.getQty());
        item.setVersion(line.getVersion());
        item.setOrderType(line.getOrderType());
        item.setCustomerOrderNo(line.getCustomerOrderNo());
        item.setCustomerProjectNo(line.getCustomerProjectNo());
        item.setCustomerOrderTitle(line.getCustomerOrderTitle());
        item.setInternalProjectNo(line.getInternalProjectNo());
        item.setSalesMaterialNo(null);
        item.setMaterialNo(null);
        item.setMaterialNoStatus(MATERIAL_UNCONFIRMED);
        item.setCustomerDeliveryDate(line.getCustomerDeliveryDate());
        item.setInternalDeliveryDate(line.getInternalDeliveryDate());
        item.setMaterialName(line.getMaterialName());
        item.setSurfaceTreatment(line.getSurfaceTreatment());
        item.setTaxIncludedUnitPrice(line.getTaxIncludedUnitPrice());
        item.setUntaxedUnitPrice(line.getUntaxedUnitPrice());
        item.setTaxIncludedTotalPrice(line.getTaxIncludedTotalPrice());
        item.setUntaxedTotalPrice(line.getUntaxedTotalPrice());
        item.setShippedQty(line.getShippedQty());
        item.setUnshippedQty(line.getUnshippedQty());
        item.setReleasedQty(0);
        item.setRemainingQty(line.getQty());
        item.setHasDrawing(false);
        item.setQuoteMode("NORMAL");
        item.setMatchStatus("未匹配");
        item.setMatchMessage("未补齐历史生产快照，需在销售订单详情或 U9 同步前补齐");
        item.setRemark(line.getRemark());
        item.setStatus(QUOTED);
        item.setCreatedAt(now);
        item.setUpdatedAt(now);
        return item;
    }

    private SalesOrderQpoExcelLineDTO applyHeaderFallback(SalesOrderQpoExcelLineDTO source, SalesOrderHeader target) {
        if (source == null) {
            return null;
        }
        if (source.getSalesOrderDate() == null) {
            source.setSalesOrderDate(target.getSalesOrderDate());
        }
        if (isBlank(source.getCustomerOrderNo())) {
            source.setCustomerOrderNo(target.getCustomerOrderNo());
        }
        if (isBlank(source.getCustomerProjectNo())) {
            source.setCustomerProjectNo(target.getCustomerProjectNo());
        }
        if (isBlank(source.getCustomerOrderTitle())) {
            source.setCustomerOrderTitle(target.getCustomerOrderTitle());
        }
        if (isBlank(source.getOrderType())) {
            source.setOrderType(target.getOrderType());
        }
        if (isBlank(source.getInternalProjectNo())) {
            source.setInternalProjectNo(target.getInternalProjectNo());
        }
        return source;
    }

    private void refreshTargetAfterAppend(SalesOrderHeader target, int appendedItemCount, LocalDateTime now) {
        Long count = itemMapper.selectCount(new QueryWrapper<SalesOrderItem>().eq("sales_order_id", target.getId()));
        int total = count == null ? safeInt(target.getTotalItemCount()) + appendedItemCount : Math.toIntExact(count);
        UpdateWrapper<SalesOrderHeader> update = new UpdateWrapper<>();
        update.eq("id", target.getId())
                .set("total_item_count", total)
                .set("updated_at", now);
        String u9Status = trimToNull(target.getU9SyncStatus());
        if (u9Status == null || U9_FAILED.equals(u9Status)) {
            update.set("u9_sync_status", U9_UNSYNCED)
                    .set("u9_sync_message", null)
                    .set("u9_sync_attempt_id", null)
                    .set("u9_sync_request_hash", null)
                    .set("u9_sync_started_at", null)
                    .set("u9_sync_finished_at", null);
            target.setU9SyncStatus(U9_UNSYNCED);
        }
        headerMapper.update(null, update);
        target.setTotalItemCount(total);
        target.setUpdatedAt(now);
    }

    private SalesOrderReleaseAppendRow toRow(SalesOrderItem item, SalesOrderQpoExcelLineDTO line) {
        SalesOrderReleaseAppendRow row = new SalesOrderReleaseAppendRow();
        row.setSalesOrderItemId(item.getId());
        row.setSourceRowNumber(line.getSourceRowNumber());
        row.setLineNo(item.getLineNo());
        row.setPartNo(item.getPartNo());
        row.setVersion(item.getVersion());
        row.setQty(item.getQty());
        row.setStatus(item.getStatus());
        row.setWarningMessage(item.getMatchMessage());
        return row;
    }

    private void recordAudit(
            SalesOrderHeader target,
            String sourceFileName,
            SalesOrderReleaseAppendResp resp,
            DrawingBindingResult drawingBinding,
            SalesOrderAutoSnapshotCompletionResult matchResult,
            long startedAt) {
        if (auditService == null) {
            return;
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("targetSalesOrderId", target.getId());
        summary.put("targetSalesOrderNo", target.getSalesOrderNo());
        summary.put("customerCode", target.getCustomerCode());
        summary.put("customerName", target.getCustomerName());
        summary.put("sourceFileName", sourceFileName);
        summary.put("appendedItemCount", resp.getAppendedItemCount());
        summary.put("ticketCount", resp.getTicketCount());
        summary.put("ticketQty", resp.getTicketQty());
        summary.put("matchedItemCount", matchResult == null ? 0 : matchResult.getMatchedItemCount());
        summary.put("unmatchedItemCount", matchResult == null ? 0 : matchResult.getUnmatchedItemCount());
        summary.put("uploadedDrawingCount", drawingBinding.uploadedDrawingCount());
        summary.put("matchedDrawingCount", drawingBinding.matchedItemCount());
        summary.put("missingDrawingCount", drawingBinding.missingDrawingCount());
        summary.put("unmatchedDrawingCount", drawingBinding.unmatchedDrawingCount());
        Map<String, Object> drawingReport = drawingBinding.report();
        summary.put("drawingFolderMode", drawingReport.get("drawingFolderMode"));
        summary.put("drawingFolderFileCount", drawingReport.get("drawingFolderFileCount"));
        summary.put("durationMs", System.currentTimeMillis() - startedAt);
        auditService.record(AUDIT_ACTION, "sales_order_header", target.getId(), null, summary,
                "MARKET", "SUCCESS", "追加销售提单", null);
    }

    private void submitAutoMesMaterial(SalesOrderHeader target, List<OrderDeliveryTicket> tickets) {
        if (autoMesMaterialSyncService == null || target == null || target.getId() == null || tickets == null || tickets.isEmpty()) {
            return;
        }
        List<Integer> ticketIds = tickets.stream()
                .filter(ticket -> ticket != null && ticket.getId() != null)
                .map(OrderDeliveryTicket::getId)
                .toList();
        if (ticketIds.isEmpty()) {
            return;
        }
        try {
            autoMesMaterialSyncService.submitAfterCommit(
                    ReleaseSourceType.SALES_ORDER,
                    target.getId(),
                    ticketIds,
                    AUDIT_ACTION);
        } catch (RuntimeException ex) {
            log.warn("Sales order release append auto MES material sync submit failed salesOrderId={} ticketCount={} reason={}",
                    target.getId(), ticketIds.size(), ex.getMessage());
        }
    }

    private int ticketQty(List<OrderDeliveryTicket> tickets) {
        return tickets == null ? 0 : tickets.stream().mapToInt(ticket -> ticket == null || ticket.getQty() == null ? 0 : ticket.getQty()).sum();
    }

    private void validateFile(MultipartFile excelFile) {
        if (excelFile == null || excelFile.isEmpty()) {
            throw new IllegalArgumentException("请上传销售订单 Excel");
        }
    }

    private DrawingBindingResult bindDrawings(
            SalesOrderHeader target,
            String batchNo,
            SalesOrderQpoExcelParseResult parseResult,
            List<MultipartFile> drawings,
            List<MultipartFile> versionDrawings,
            List<MultipartFile> drawingFolderFiles,
            String drawingFolderName) {
        Set<String> knownPartNos = collectKnownPartNos(parseResult);
        Set<DrawingPartNoResolver.DrawingIdentity> knownPartNoVersions = collectKnownPartNoVersions(parseResult);
        Map<String, DrawingFileInfo> drawingByPartNo = new LinkedHashMap<>();
        Map<String, DrawingFileInfo> drawingByPartNoVersion = new LinkedHashMap<>();
        List<DrawingFileInfo> uploadedDrawings = new ArrayList<>();
        int versionUploadedDrawingCount = 0;

        if (drawingFolderFiles != null && !drawingFolderFiles.isEmpty()) {
            requireBusinessFileService();
            BusinessFileService.DrawingFolderSaveResult drawingFolder = businessFileService.saveDrawingFolderFiles(
                    "SALES_ORDER",
                    String.valueOf(target.getId()),
                    drawingFolderName,
                    drawingFolderFiles,
                    "SALES_ORDER_APPEND_DRAWING_FOLDER",
                    String.valueOf(target.getId()));
            target.setDrawingFolderUrl(drawingFolder.folderUrl());
            target.setDrawingFolderName(drawingFolder.folderName());
            target.setDrawingFolderFileCount(drawingFolder.fileCount());
            SalesOrderHeader update = new SalesOrderHeader();
            update.setId(target.getId());
            update.setDrawingFolderUrl(drawingFolder.folderUrl());
            update.setDrawingFolderName(drawingFolder.folderName());
            update.setDrawingFolderFileCount(drawingFolder.fileCount());
            update.setUpdatedAt(LocalDateTime.now());
            if (headerMapper.updateById(update) <= 0) {
                throw new RuntimeException("销售订单图纸文件夹保存失败，请刷新后重试");
            }
            return new DrawingBindingResult(
                    parseResult,
                    knownPartNos,
                    knownPartNoVersions,
                    drawingByPartNo,
                    drawingByPartNoVersion,
                    uploadedDrawings,
                    versionUploadedDrawingCount,
                    drawingFolder);
        }

        if (drawings != null && !drawings.isEmpty()) {
            requireBusinessFileService();
            for (MultipartFile drawing : drawings) {
                if (!isPdfDrawing(drawing)) {
                    continue;
                }
                String cleanFileName = cleanDrawingFileName(drawing.getOriginalFilename());
                String partNo = DrawingPartNoResolver.extractPartNo(cleanFileName, knownPartNos);
                BusinessFileService.StoredBusinessFile storedFile = businessFileService.saveSalesOrderImportDrawing(
                        target.getCustomerName(),
                        batchNo,
                        drawing,
                        "SALES_ORDER_IMPORT_DRAWING",
                        batchNo);
                DrawingFileInfo drawingInfo = new DrawingFileInfo(partNo, "", cleanFileName, storedFile.publicPath(), false);
                uploadedDrawings.add(drawingInfo);
                if (!isBlank(partNo)) {
                    drawingByPartNo.put(partNo, drawingInfo);
                }
            }
        }
        if (versionDrawings != null && !versionDrawings.isEmpty()) {
            requireBusinessFileService();
            for (MultipartFile drawing : versionDrawings) {
                if (!isPdfDrawing(drawing)) {
                    continue;
                }
                String cleanFileName = cleanDrawingFileName(drawing.getOriginalFilename());
                DrawingPartNoResolver.DrawingIdentity identity =
                        DrawingPartNoResolver.extractPartNoVersion(cleanFileName, knownPartNoVersions);
                BusinessFileService.StoredBusinessFile storedFile = businessFileService.saveSalesOrderImportDrawing(
                        target.getCustomerName(),
                        batchNo,
                        drawing,
                        "SALES_ORDER_IMPORT_DRAWING",
                        batchNo);
                String partNo = identity == null
                        ? DrawingPartNoResolver.extractPartNo(cleanFileName, knownPartNos)
                        : identity.partNo();
                String version = identity == null ? "" : identity.version();
                DrawingFileInfo drawingInfo = new DrawingFileInfo(partNo, version, cleanFileName, storedFile.publicPath(), true);
                uploadedDrawings.add(drawingInfo);
                versionUploadedDrawingCount++;
                if (identity != null) {
                    drawingByPartNoVersion.put(itemKey(identity.partNo(), identity.version()), drawingInfo);
                }
            }
        }
        return new DrawingBindingResult(
                parseResult,
                knownPartNos,
                knownPartNoVersions,
                drawingByPartNo,
                drawingByPartNoVersion,
                uploadedDrawings,
                versionUploadedDrawingCount,
                null);
    }

    private void applyDrawingBinding(SalesOrderItem item, SalesOrderQpoExcelLineDTO line, DrawingBindingResult drawingBinding) {
        if (drawingBinding != null && drawingBinding.hasDrawingFolder()) {
            item.setDrawingUrl(drawingBinding.drawingFolderUrl());
            item.setHasDrawing(true);
            return;
        }
        DrawingFileInfo drawing = drawingBinding == null ? null : drawingBinding.find(line.getPartNo(), line.getVersion());
        if (drawing != null) {
            item.setDrawingUrl(drawing.url());
            item.setHasDrawing(true);
        }
    }

    private Set<String> collectKnownPartNos(SalesOrderQpoExcelParseResult parseResult) {
        Set<String> partNos = new HashSet<>();
        for (SalesOrderQpoOrderGroupDTO group : safeGroups(parseResult)) {
            for (SalesOrderQpoExcelLineDTO line : safeLines(group)) {
                String normalized = normalizePartNo(line == null ? null : line.getPartNo());
                if (!normalized.isEmpty()) {
                    partNos.add(normalized);
                }
            }
        }
        return partNos;
    }

    private Set<DrawingPartNoResolver.DrawingIdentity> collectKnownPartNoVersions(SalesOrderQpoExcelParseResult parseResult) {
        Set<DrawingPartNoResolver.DrawingIdentity> identities = new HashSet<>();
        for (SalesOrderQpoOrderGroupDTO group : safeGroups(parseResult)) {
            for (SalesOrderQpoExcelLineDTO line : safeLines(group)) {
                String normalizedPartNo = normalizePartNo(line == null ? null : line.getPartNo());
                String normalizedVersion = normalizeVersion(line == null ? null : line.getVersion());
                if (!normalizedPartNo.isEmpty() && !normalizedVersion.isEmpty()) {
                    identities.add(new DrawingPartNoResolver.DrawingIdentity(normalizedPartNo, normalizedVersion));
                }
            }
        }
        return identities;
    }

    private List<SalesOrderQpoOrderGroupDTO> safeGroups(SalesOrderQpoExcelParseResult parseResult) {
        return parseResult == null || parseResult.getOrders() == null ? List.of() : parseResult.getOrders();
    }

    private List<SalesOrderQpoExcelLineDTO> safeLines(SalesOrderQpoOrderGroupDTO group) {
        return group == null || group.getLines() == null ? List.of() : group.getLines();
    }

    private SalesOrderU9SyncOrderRow toOrderRow(SalesOrderHeader header) {
        List<SalesOrderItem> items = itemMapper.selectList(new QueryWrapper<SalesOrderItem>()
                .eq("sales_order_id", header.getId())
                .orderByAsc("line_no")
                .orderByAsc("id"));
        MaterialStats stats = materialStats(items);
        SalesOrderU9SyncOrderRow row = new SalesOrderU9SyncOrderRow();
        row.setSalesOrderId(header.getId());
        row.setSalesOrderNo(header.getSalesOrderNo());
        row.setDocumentNo(header.getDocumentNo());
        row.setSourceType(header.getSourceType());
        row.setSourceLabel(sourceLabel(header.getSourceType()));
        row.setCustomerName(header.getCustomerName());
        row.setCustomerShortName(header.getCustomerShortName());
        row.setCustomerCode(header.getCustomerCode());
        row.setSalesOrderDate(header.getSalesOrderDate());
        row.setCustomerOrderNo(header.getCustomerOrderNo());
        row.setInternalProjectNo(header.getInternalProjectNo());
        row.setCustomerProjectNo(header.getCustomerProjectNo());
        row.setCustomerOrderTitle(header.getCustomerOrderTitle());
        row.setOrderType(header.getOrderType());
        row.setDocumentTypeCode(header.getDocumentTypeCode());
        row.setStatus(header.getStatus());
        row.setSellerCode(header.getU9SellerCode());
        row.setSellerName(header.getU9SellerName());
        row.setSaleDepartmentCode(header.getU9SaleDepartmentCode());
        row.setSaleDepartmentName(header.getU9SaleDepartmentName());
        row.setU9SyncStatus(header.getU9SyncStatus());
        row.setU9OrderCode(header.getU9OrderCode());
        row.setU9SyncMessage(header.getU9SyncMessage());
        row.setItemCount(stats.total());
        row.setMaterialConfirmedItemCount(stats.confirmed());
        row.setMissingMaterialItemCount(stats.missing());
        row.setSyncReady(false);
        row.setBlockedReason("追加后需重新在 U9 同步页预览并同步");
        row.setUpdatedAt(header.getUpdatedAt());
        return row;
    }

    private MaterialStats materialStats(List<SalesOrderItem> items) {
        int total = items == null ? 0 : items.size();
        int confirmed = items == null ? 0 : (int) items.stream().filter(this::isConfirmedMaterialNo).count();
        return new MaterialStats(total, confirmed, total - confirmed);
    }

    private boolean isConfirmedMaterialNo(SalesOrderItem item) {
        return item != null && !isBlank(item.getMaterialNo()) && "已确认".equals(item.getMaterialNoStatus());
    }

    private String sourceLabel(String sourceType) {
        return "RFQ_RELEASE".equalsIgnoreCase(sourceType) ? "RFQ 议价提单派生" : "销售订单导入";
    }

    private void applySalesOrderScope(QueryWrapper<SalesOrderHeader> query) {
        if (dataScopeService != null) {
            dataScopeService.applySalesOrderScope(query);
        }
    }

    private String nextBatchNo() {
        return salesOrderNumberService == null ? "SOAPP" + System.currentTimeMillis() : salesOrderNumberService.nextImportBatchNo();
    }

    private void requireBusinessFileService() {
        if (businessFileService == null) {
            throw new IllegalStateException("销售订单图纸保存服务不可用");
        }
    }

    private String safeFileName(String fileName) {
        if (isBlank(fileName)) {
            return "";
        }
        return fileName.replace('\\', '_').replace('/', '_').trim();
    }

    private String cleanDrawingFileName(String fileName) {
        if (isBlank(fileName)) {
            return "drawing.pdf";
        }
        String clean = fileName.replaceAll(".*[\\\\/]", "").trim();
        return clean.isEmpty() ? "drawing.pdf" : clean;
    }

    private boolean isPdfDrawing(MultipartFile drawing) {
        String originalFilename = drawing == null ? null : drawing.getOriginalFilename();
        return drawing != null
                && !drawing.isEmpty()
                && originalFilename != null
                && originalFilename.toLowerCase().endsWith(".pdf");
    }

    private String itemKey(String partNo, String version) {
        return normalizePartNo(partNo) + "||" + normalizeVersion(version);
    }

    private String normalizePartNo(String partNo) {
        return DrawingPartNoResolver.normalizePartNo(partNo);
    }

    private String normalizeVersion(String version) {
        return DrawingPartNoResolver.normalizeVersion(version);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String requiredText(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new RuntimeException(message);
        }
        return normalized;
    }

    private int normalizePage(Integer page) {
        return page == null || page < 1 ? DEFAULT_PAGE : page;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String safeError(String value) {
        return isBlank(value) ? "Sales order release append failed" : value.replaceAll("\\s+", " ").trim();
    }

    private Map<String, Integer> drawingMatchModes(int uploadedDrawingCount, int versionUploadedDrawingCount) {
        return Map.of(
                "partNo", Math.max(0, uploadedDrawingCount - versionUploadedDrawingCount),
                "partNoVersion", Math.max(0, versionUploadedDrawingCount)
        );
    }

    private Map<String, Object> buildMissingDrawingItem(SalesOrderQpoExcelLineDTO line, String normalizedPartNo) {
        Map<String, Object> missingItem = new LinkedHashMap<>();
        String partNo = line == null ? null : line.getPartNo();
        missingItem.put("partNo", isBlank(partNo) ? normalizedPartNo : partNo.trim());
        missingItem.put("version", line == null ? null : line.getVersion());
        missingItem.put("description", line == null ? null : line.getDescription());
        missingItem.put("qty", line == null ? null : line.getQty());
        return missingItem;
    }

    private Map<String, String> drawingFileResult(DrawingFileInfo drawing) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("partNo", drawing.partNo());
        if (!isBlank(drawing.version())) {
            result.put("version", drawing.version());
        }
        result.put("fileName", drawing.originalFilename());
        return result;
    }

    private record MaterialStats(int total, int confirmed, int missing) {
    }

    private record DrawingFileInfo(String partNo, String version, String originalFilename, String url, boolean versionSpecific) {
    }

    private class DrawingBindingResult {
        private final Set<String> knownPartNos;
        private final Set<DrawingPartNoResolver.DrawingIdentity> knownPartNoVersions;
        private final Map<String, DrawingFileInfo> drawingByPartNo;
        private final Map<String, DrawingFileInfo> drawingByPartNoVersion;
        private final List<DrawingFileInfo> uploadedDrawings;
        private final int versionUploadedDrawingCount;
        private final BusinessFileService.DrawingFolderSaveResult drawingFolder;
        private final Map<String, Object> report;

        private DrawingBindingResult(
                SalesOrderQpoExcelParseResult parseResult,
                Set<String> knownPartNos,
                Set<DrawingPartNoResolver.DrawingIdentity> knownPartNoVersions,
                Map<String, DrawingFileInfo> drawingByPartNo,
                Map<String, DrawingFileInfo> drawingByPartNoVersion,
                List<DrawingFileInfo> uploadedDrawings,
                int versionUploadedDrawingCount,
                BusinessFileService.DrawingFolderSaveResult drawingFolder) {
            this.knownPartNos = knownPartNos;
            this.knownPartNoVersions = knownPartNoVersions;
            this.drawingByPartNo = drawingByPartNo;
            this.drawingByPartNoVersion = drawingByPartNoVersion;
            this.uploadedDrawings = uploadedDrawings;
            this.versionUploadedDrawingCount = versionUploadedDrawingCount;
            this.drawingFolder = drawingFolder;
            this.report = buildReport(parseResult);
        }

        private DrawingFileInfo find(String partNo, String version) {
            if (hasDrawingFolder()) {
                return new DrawingFileInfo(normalizePartNo(partNo), normalizeVersion(version), drawingFolder.folderName(), drawingFolder.folderUrl(), true);
            }
            DrawingFileInfo versionDrawing = drawingByPartNoVersion.get(itemKey(partNo, version));
            return versionDrawing == null ? drawingByPartNo.get(normalizePartNo(partNo)) : versionDrawing;
        }

        private boolean hasDrawingFolder() {
            return drawingFolder != null;
        }

        private String drawingFolderUrl() {
            return drawingFolder == null ? null : drawingFolder.folderUrl();
        }

        private Map<String, Object> report() {
            return report;
        }

        private int uploadedDrawingCount() {
            return hasDrawingFolder() ? drawingFolder.fileCount() : uploadedDrawings.size();
        }

        private int matchedItemCount() {
            return numberValue("matchedItemCount");
        }

        private int missingDrawingCount() {
            return numberValue("missingDrawingCount");
        }

        private int unmatchedDrawingCount() {
            return numberValue("unmatchedDrawingCount");
        }

        private Map<String, Object> buildReport(SalesOrderQpoExcelParseResult parseResult) {
            if (hasDrawingFolder()) {
                int totalItemCount = parseResult == null ? 0 : parseResult.getLineCount();
                Map<String, Object> drawingReport = new LinkedHashMap<>();
                drawingReport.put("totalItemCount", totalItemCount);
                drawingReport.put("uploadedDrawingCount", drawingFolder.fileCount());
                drawingReport.put("matchedItemCount", totalItemCount);
                drawingReport.put("matchedDrawingCount", totalItemCount);
                drawingReport.put("versionUploadedDrawingCount", 0);
                drawingReport.put("versionMatchedDrawingCount", 0);
                drawingReport.put("drawingMatchModes", Map.of("folder", drawingFolder.fileCount(), "partNo", 0, "partNoVersion", 0));
                drawingReport.put("missingDrawingCount", 0);
                drawingReport.put("unmatchedDrawingCount", 0);
                drawingReport.put("missingItems", List.of());
                drawingReport.put("unmatchedDrawings", List.of());
                drawingReport.put("drawingFolderMode", true);
                drawingReport.put("drawingFolderFileCount", drawingFolder.fileCount());
                return drawingReport;
            }
            List<Map<String, Object>> missingItems = new ArrayList<>();
            Set<String> matchedDrawingPartNos = new HashSet<>();
            Set<String> matchedVersionDrawingKeys = new HashSet<>();
            int matchedItemCount = 0;
            for (SalesOrderQpoOrderGroupDTO group : safeGroups(parseResult)) {
                for (SalesOrderQpoExcelLineDTO line : safeLines(group)) {
                    String normalizedPartNo = normalizePartNo(line == null ? null : line.getPartNo());
                    String normalizedVersion = normalizeVersion(line == null ? null : line.getVersion());
                    DrawingFileInfo drawing = find(normalizedPartNo, normalizedVersion);
                    if (drawing != null) {
                        matchedItemCount++;
                        matchedDrawingPartNos.add(normalizedPartNo);
                        if (drawing.versionSpecific()) {
                            matchedVersionDrawingKeys.add(itemKey(normalizedPartNo, normalizedVersion));
                        }
                    } else {
                        missingItems.add(buildMissingDrawingItem(line, normalizedPartNo));
                    }
                }
            }

            List<Map<String, String>> unmatchedDrawings = new ArrayList<>();
            for (DrawingFileInfo drawing : uploadedDrawings) {
                if (isUnmatchedDrawing(drawing)) {
                    unmatchedDrawings.add(drawingFileResult(drawing));
                }
            }

            Map<String, Object> drawingReport = new LinkedHashMap<>();
            drawingReport.put("totalItemCount", parseResult == null ? 0 : parseResult.getLineCount());
            drawingReport.put("uploadedDrawingCount", uploadedDrawings.size());
            drawingReport.put("matchedItemCount", matchedItemCount);
            drawingReport.put("matchedDrawingCount", matchedDrawingPartNos.size());
            drawingReport.put("versionUploadedDrawingCount", versionUploadedDrawingCount);
            drawingReport.put("versionMatchedDrawingCount", matchedVersionDrawingKeys.size());
            drawingReport.put("drawingMatchModes", drawingMatchModes(uploadedDrawings.size(), versionUploadedDrawingCount));
            drawingReport.put("missingDrawingCount", missingItems.size());
            drawingReport.put("unmatchedDrawingCount", unmatchedDrawings.size());
            drawingReport.put("missingItems", missingItems);
            drawingReport.put("unmatchedDrawings", unmatchedDrawings);
            return drawingReport;
        }

        private int numberValue(String key) {
            Object value = report.get(key);
            return value instanceof Number number ? number.intValue() : 0;
        }

        private boolean isUnmatchedDrawing(DrawingFileInfo drawing) {
            return drawing.versionSpecific()
                    ? !knownPartNoVersions.contains(new DrawingPartNoResolver.DrawingIdentity(drawing.partNo(), drawing.version()))
                    : !knownPartNos.contains(drawing.partNo());
        }
    }
}
