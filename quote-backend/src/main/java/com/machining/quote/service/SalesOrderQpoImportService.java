package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.config.AuthContext;
import com.machining.quote.dto.SalesOrderQpoExcelLineDTO;
import com.machining.quote.dto.SalesOrderQpoExcelParseResult;
import com.machining.quote.dto.SalesOrderQpoImportResp;
import com.machining.quote.dto.SalesOrderQpoImportResultRow;
import com.machining.quote.dto.SalesOrderQpoOrderGroupDTO;
import com.machining.quote.entity.CustomerMaster;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderImportBatch;
import com.machining.quote.entity.SalesOrderImportRecord;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.CustomerMasterMapper;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderImportBatchMapper;
import com.machining.quote.mapper.SalesOrderImportRecordMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.service.BusinessFileService.StoredBusinessFile;
import com.machining.quote.util.DrawingPartNoResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class SalesOrderQpoImportService {
    private static final Logger log = LoggerFactory.getLogger(SalesOrderQpoImportService.class);
    private static final String ACTION = "SALES_ORDER_IMPORT_QPO";
    private static final String TARGET_TYPE = "sales_order_import_batch";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String IMPORT_MODE_QPO_INDEPENDENT = "QPO_INDEPENDENT";
    private static final String DEFAULT_DOCUMENT_TYPE_CODE = "SO01";
    private static final String QUOTED = "已报价";
    private static final String UNMATCHED = "未匹配";
    private static final String WAITING_SNAPSHOT_MESSAGE = "已导入价格，未补齐历史生产快照";

    @Autowired
    private SalesOrderQpoExcelParser excelParser;
    @Autowired
    private CustomerMasterMapper customerMapper;
    @Autowired
    private SalesOrderImportBatchMapper batchMapper;
    @Autowired
    private SalesOrderImportRecordMapper recordMapper;
    @Autowired
    private SalesOrderHeaderMapper headerMapper;
    @Autowired
    private SalesOrderItemMapper itemMapper;
    @Autowired(required = false)
    private DataScopeService dataScopeService;
    @Autowired
    private AuditService auditService;
    @Autowired(required = false)
    private BusinessFileService businessFileService;

    @Transactional
    public SalesOrderQpoImportResp importQpo(MultipartFile excelFile, Long customerId, String documentTypeCode) {
        return importQpo(excelFile, customerId, documentTypeCode, null, null);
    }

    @Transactional
    public SalesOrderQpoImportResp importQpo(
            MultipartFile excelFile,
            Long customerId,
            String documentTypeCode,
            List<MultipartFile> drawings) {
        return importQpo(excelFile, customerId, documentTypeCode, drawings, null);
    }

    @Transactional
    public SalesOrderQpoImportResp importQpo(
            MultipartFile excelFile,
            Long customerId,
            String documentTypeCode,
            List<MultipartFile> drawings,
            List<MultipartFile> versionDrawings) {
        validateFile(excelFile);
        CustomerMaster customer = requireCustomer(customerId);
        if (dataScopeService != null) {
            dataScopeService.assertCanAccessCustomer(customer);
        }
        String effectiveDocumentType = effectiveDocumentType(documentTypeCode);
        String batchNo = nextBatchNo();
        String sourceFileName = safeFileName(excelFile.getOriginalFilename());
        long startedAt = System.currentTimeMillis();

        SalesOrderQpoExcelParseResult parseResult = parse(excelFile);
        if (parseResult.getOrderCount() <= 0 || parseResult.getOrders().isEmpty()) {
            throw new IllegalArgumentException("销售订单表格没有可导入的订单");
        }
        validateDuplicateImports(customer, parseResult.getOrders());
        DrawingBindingResult drawingBinding = bindDrawings(customer, batchNo, parseResult, drawings, versionDrawings);

        SalesOrderImportBatch batch = createBatch(batchNo, sourceFileName, parseResult);
        batchMapper.insert(batch);

        SalesOrderQpoImportResp resp = baseResponse(batchNo, parseResult, drawingBinding);
        int sequence = 1;
        for (SalesOrderQpoOrderGroupDTO group : parseResult.getOrders()) {
            CreatedSalesOrder created = createSalesOrderGroup(
                    batch,
                    customer,
                    effectiveDocumentType,
                    group,
                    batchNo,
                    sequence++,
                    drawingBinding
            );
            resp.getRows().add(successRow(created, group));
        }
        resp.setSuccess(true);
        resp.setStatus(STATUS_SUCCESS);
        resp.setSuccessOrderCount(resp.getRows().size());
        resp.setFailedOrderCount(0);
        audit(batch, resp, customer, effectiveDocumentType, startedAt);
        log.info("Sales order independent import completed batchNo={} orderCount={} lineCount={} uploadedDrawingCount={} matchedDrawingCount={} missingDrawingCount={} unmatchedDrawingCount={} durationMs={}",
                batchNo,
                resp.getTotalOrderCount(),
                resp.getTotalLineCount(),
                drawingBinding.uploadedDrawingCount(),
                drawingBinding.matchedItemCount(),
                drawingBinding.missingDrawingCount(),
                drawingBinding.unmatchedDrawingCount(),
                System.currentTimeMillis() - startedAt);
        return resp;
    }

    private SalesOrderQpoExcelParseResult parse(MultipartFile excelFile) {
        try {
            return excelParser.parse(excelFile.getInputStream());
        } catch (IOException e) {
            throw new RuntimeException("销售订单导入表格读取失败：" + safeError(e.getMessage()), e);
        }
    }

    private void validateDuplicateImports(CustomerMaster customer, List<SalesOrderQpoOrderGroupDTO> groups) {
        for (SalesOrderQpoOrderGroupDTO group : groups) {
            QueryWrapper<SalesOrderHeader> query = new QueryWrapper<>();
            query.eq("customer_id", customer.getId())
                    .eq("customer_order_no", group.getCustomerOrderNo())
                    .eq("internal_project_no", group.getInternalProjectNo());
            Long count = headerMapper.selectCount(query);
            if (count != null && count > 0) {
                throw new IllegalArgumentException("销售订单重复导入：客户订单号 "
                        + group.getCustomerOrderNo() + " / 内部项目号 " + group.getInternalProjectNo());
            }
        }
    }

    private DrawingBindingResult bindDrawings(
            CustomerMaster customer,
            String batchNo,
            SalesOrderQpoExcelParseResult parseResult,
            List<MultipartFile> drawings,
            List<MultipartFile> versionDrawings) {
        Set<String> knownPartNos = collectKnownPartNos(parseResult);
        Set<DrawingPartNoResolver.DrawingIdentity> knownPartNoVersions = collectKnownPartNoVersions(parseResult);
        Map<String, DrawingFileInfo> drawingByPartNo = new LinkedHashMap<>();
        Map<String, DrawingFileInfo> drawingByPartNoVersion = new LinkedHashMap<>();
        List<DrawingFileInfo> uploadedDrawings = new ArrayList<>();
        int versionUploadedDrawingCount = 0;

        if (drawings != null && !drawings.isEmpty()) {
            if (businessFileService == null) {
                throw new IllegalStateException("销售订单图纸保存服务不可用");
            }
            for (MultipartFile drawing : drawings) {
                if (!isPdfDrawing(drawing)) {
                    continue;
                }
                String cleanFileName = cleanDrawingFileName(drawing.getOriginalFilename());
                String partNo = DrawingPartNoResolver.extractPartNo(cleanFileName, knownPartNos);
                StoredBusinessFile storedFile = businessFileService.saveSalesOrderImportDrawing(
                        customer.getCustomerName(),
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
            if (businessFileService == null) {
                throw new IllegalStateException("销售订单图纸保存服务不可用");
            }
            for (MultipartFile drawing : versionDrawings) {
                if (!isPdfDrawing(drawing)) {
                    continue;
                }
                String cleanFileName = cleanDrawingFileName(drawing.getOriginalFilename());
                DrawingPartNoResolver.DrawingIdentity identity =
                        DrawingPartNoResolver.extractPartNoVersion(cleanFileName, knownPartNoVersions);
                StoredBusinessFile storedFile = businessFileService.saveSalesOrderImportDrawing(
                        customer.getCustomerName(),
                        batchNo,
                        drawing,
                        "SALES_ORDER_IMPORT_DRAWING",
                        batchNo);
                String partNo = identity == null
                        ? DrawingPartNoResolver.extractPartNo(cleanFileName, knownPartNos)
                        : identity.partNo();
                String version = identity == null ? "" : identity.version();
                DrawingFileInfo drawingInfo = new DrawingFileInfo(
                        partNo, version, cleanFileName, storedFile.publicPath(), true);
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
                versionUploadedDrawingCount);
    }

    private Set<String> collectKnownPartNos(SalesOrderQpoExcelParseResult parseResult) {
        Set<String> partNos = new HashSet<>();
        if (parseResult == null || parseResult.getOrders() == null) {
            return partNos;
        }
        for (SalesOrderQpoOrderGroupDTO group : parseResult.getOrders()) {
            if (group == null || group.getLines() == null) {
                continue;
            }
            for (SalesOrderQpoExcelLineDTO line : group.getLines()) {
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
        if (parseResult == null || parseResult.getOrders() == null) {
            return identities;
        }
        for (SalesOrderQpoOrderGroupDTO group : parseResult.getOrders()) {
            if (group == null || group.getLines() == null) {
                continue;
            }
            for (SalesOrderQpoExcelLineDTO line : group.getLines()) {
                String normalizedPartNo = normalizePartNo(line == null ? null : line.getPartNo());
                String normalizedVersion = normalizeVersion(line == null ? null : line.getVersion());
                if (!normalizedPartNo.isEmpty() && !normalizedVersion.isEmpty()) {
                    identities.add(new DrawingPartNoResolver.DrawingIdentity(normalizedPartNo, normalizedVersion));
                }
            }
        }
        return identities;
    }

    private void applyDrawingBinding(
            SalesOrderItem item,
            SalesOrderQpoExcelLineDTO line,
            DrawingBindingResult drawingBinding) {
        DrawingFileInfo drawing = drawingBinding == null ? null : drawingBinding.find(line.getPartNo(), line.getVersion());
        if (drawing != null) {
            item.setDrawingUrl(drawing.url());
            item.setHasDrawing(true);
        }
    }

    private CreatedSalesOrder createSalesOrderGroup(
            SalesOrderImportBatch batch,
            CustomerMaster customer,
            String documentTypeCode,
            SalesOrderQpoOrderGroupDTO group,
            String batchNo,
            int sequence,
            DrawingBindingResult drawingBinding) {
        String salesOrderNo = batchNo + "-" + String.format("%03d", sequence);
        LocalDateTime now = LocalDateTime.now();

        SalesOrderHeader header = new SalesOrderHeader();
        header.setSalesOrderNo(salesOrderNo);
        header.setDocumentTypeCode(documentTypeCode);
        header.setCustomerId(customer.getId());
        header.setCustomerCode(customer.getCustomerCode());
        header.setCustomerShortName(customer.getCustomerShortName());
        header.setCustomerName(customer.getCustomerName());
        header.setSalesOrderDate(group.getSalesOrderDate());
        header.setCustomerOrderNo(group.getCustomerOrderNo());
        header.setCustomerProjectNo(group.getCustomerProjectNo());
        header.setCustomerOrderTitle(group.getCustomerOrderTitle());
        header.setOrderType(group.getOrderType());
        header.setInternalProjectNo(group.getInternalProjectNo());
        header.setStatus(QUOTED);
        header.setImportBatchId(batch.getId());
        header.setTotalItemCount(group.getLines().size());
        header.setMatchedItemCount(0);
        header.setUnmatchedItemCount(group.getLines().size());
        header.setWarningMessage(WAITING_SNAPSHOT_MESSAGE);
        AuthContext.SessionUser user = AuthContext.get();
        if (user != null && user.id() != null) {
            header.setCreatedBy(user.id().longValue());
            header.setOwnerUserId(user.id());
        }
        header.setCreatedAt(now);
        header.setUpdatedAt(now);
        headerMapper.insert(header);

        SalesOrderImportRecord record = createRecord(batch, customer, documentTypeCode, group, header, sequence, now);
        recordMapper.insert(record);

        SalesOrderHeader update = new SalesOrderHeader();
        update.setId(header.getId());
        update.setImportRecordId(record.getId());
        update.setUpdatedAt(LocalDateTime.now());
        headerMapper.updateById(update);
        header.setImportRecordId(record.getId());

        for (SalesOrderQpoExcelLineDTO line : group.getLines()) {
            SalesOrderItem item = toItem(header, line, now);
            applyDrawingBinding(item, line, drawingBinding);
            itemMapper.insert(item);
        }

        return new CreatedSalesOrder(
                header.getId(),
                header.getSalesOrderNo(),
                group.getLines().size(),
                drawingBinding.groupSummary(group));
    }

    private SalesOrderImportRecord createRecord(
            SalesOrderImportBatch batch,
            CustomerMaster customer,
            String documentTypeCode,
            SalesOrderQpoOrderGroupDTO group,
            SalesOrderHeader header,
            int sequence,
            LocalDateTime now) {
        SalesOrderImportRecord record = new SalesOrderImportRecord();
        record.setBatchId(batch.getId());
        record.setOtherId(batch.getBatchNo() + "-" + sequence);
        record.setCustomerOrderNo(group.getCustomerOrderNo());
        record.setOrderBy(customer.getCustomerCode());
        record.setDocumentType(documentTypeCode);
        record.setLineCount(group.getLines().size());
        record.setRequestSummaryJson(requestSummary(group, header.getSalesOrderNo()));
        record.setSalesOrderId(header.getId());
        record.setSalesOrderNo(header.getSalesOrderNo());
        record.setItemCount(group.getLines().size());
        record.setMatchedItemCount(0);
        record.setUnmatchedItemCount(group.getLines().size());
        record.setWarningMessage(WAITING_SNAPSHOT_MESSAGE);
        record.setStatus(STATUS_SUCCESS);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        return record;
    }

    private SalesOrderItem toItem(SalesOrderHeader header, SalesOrderQpoExcelLineDTO line, LocalDateTime now) {
        SalesOrderItem item = new SalesOrderItem();
        item.setSalesOrderId(header.getId());
        item.setLineNo(line.getSalesOrderLineNo());
        item.setPartNo(line.getPartNo());
        item.setDescription(line.getDescription());
        item.setQty(line.getQty());
        item.setVersion(line.getVersion());
        item.setOrderType(line.getOrderType());
        item.setCustomerOrderNo(line.getCustomerOrderNo());
        item.setCustomerProjectNo(line.getCustomerProjectNo());
        item.setInternalProjectNo(line.getInternalProjectNo());
        item.setSalesMaterialNo(line.getSalesMaterialNo());
        item.setMaterialNo(isBlank(line.getSalesMaterialNo()) ? null : line.getSalesMaterialNo());
        item.setCustomerDeliveryDate(line.getCustomerDeliveryDate());
        item.setInternalDeliveryDate(line.getInternalDeliveryDate());
        item.setMaterialName(line.getMaterialName());
        item.setSurfaceTreatment(line.getSurfaceTreatment());
        item.setTaxIncludedUnitPrice(line.getTaxIncludedUnitPrice());
        item.setUntaxedUnitPrice(line.getUntaxedUnitPrice());
        item.setTaxIncludedTotalPrice(line.getTaxIncludedTotalPrice());
        item.setUntaxedTotalPrice(line.getUntaxedTotalPrice());
        item.setFinalUnitPrice(line.getTaxIncludedUnitPrice());
        item.setFinalTotalPrice(line.getTaxIncludedTotalPrice());
        item.setShippedQty(line.getShippedQty());
        item.setUnshippedQty(line.getUnshippedQty());
        item.setReleasedQty(0);
        item.setRemainingQty(line.getQty());
        item.setActualShipDate(line.getActualShipDate());
        item.setHasDrawing(false);
        item.setQuoteMode("NORMAL");
        item.setMatchStatus(UNMATCHED);
        item.setMatchMessage(WAITING_SNAPSHOT_MESSAGE);
        item.setRemark(line.getRemark());
        item.setStatus(QUOTED);
        item.setCreatedAt(now);
        item.setUpdatedAt(now);
        return item;
    }

    private SalesOrderImportBatch createBatch(
            String batchNo,
            String sourceFileName,
            SalesOrderQpoExcelParseResult parseResult) {
        LocalDateTime now = LocalDateTime.now();
        SalesOrderImportBatch batch = new SalesOrderImportBatch();
        batch.setBatchNo(batchNo);
        batch.setSourceFileName(sourceFileName);
        batch.setImportMode(IMPORT_MODE_QPO_INDEPENDENT);
        batch.setStatus(STATUS_SUCCESS);
        batch.setTotalOrderCount(parseResult.getOrderCount());
        batch.setSuccessOrderCount(parseResult.getOrderCount());
        batch.setFailedOrderCount(0);
        AuthContext.SessionUser user = AuthContext.get();
        batch.setCreatedBy(user == null || user.id() == null ? null : user.id().longValue());
        batch.setCreatedAt(now);
        batch.setUpdatedAt(now);
        return batch;
    }

    private SalesOrderQpoImportResp baseResponse(
            String batchNo,
            SalesOrderQpoExcelParseResult parseResult,
            DrawingBindingResult drawingBinding) {
        SalesOrderQpoImportResp resp = new SalesOrderQpoImportResp();
        resp.setBatchNo(batchNo);
        resp.setTotalOrderCount(parseResult.getOrderCount());
        resp.setTotalLineCount(parseResult.getLineCount());
        resp.setDrawingReport(drawingBinding.report());
        return resp;
    }

    private SalesOrderQpoImportResultRow successRow(CreatedSalesOrder created, SalesOrderQpoOrderGroupDTO group) {
        SalesOrderQpoImportResultRow row = new SalesOrderQpoImportResultRow();
        row.setSuccess(true);
        row.setStatus(STATUS_SUCCESS);
        row.setSalesOrderId(created.salesOrderId());
        row.setSalesOrderNo(created.salesOrderNo());
        row.setCustomerOrderNo(group.getCustomerOrderNo());
        row.setInternalProjectNo(group.getInternalProjectNo());
        row.setLineCount(group.getLines().size());
        row.setMatchedItemCount(created.matchedItemCount());
        row.setUnmatchedItemCount(created.unmatchedItemCount());
        row.setUploadedDrawingCount(created.drawingSummary().uploadedDrawingCount());
        row.setMatchedDrawingCount(created.drawingSummary().matchedItemCount());
        row.setMissingDrawingCount(created.drawingSummary().missingDrawingCount());
        row.setUnmatchedDrawingCount(created.drawingSummary().unmatchedDrawingCount());
        row.setWarningMessage(created.warningMessage());
        return row;
    }

    private Map<String, Object> requestSummary(SalesOrderQpoOrderGroupDTO group, String salesOrderNo) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("salesOrderNo", salesOrderNo);
        summary.put("customerOrderNo", group.getCustomerOrderNo());
        summary.put("internalProjectNo", group.getInternalProjectNo());
        summary.put("salesOrderDate", group.getSalesOrderDate());
        summary.put("orderType", group.getOrderType());
        summary.put("lineCount", group.getLines().size());
        summary.put("partNoSample", group.getLines().stream()
                .map(SalesOrderQpoExcelLineDTO::getPartNo)
                .filter(partNo -> !isBlank(partNo))
                .limit(10)
                .toList());
        return summary;
    }

    private void audit(
            SalesOrderImportBatch batch,
            SalesOrderQpoImportResp resp,
            CustomerMaster customer,
            String documentTypeCode,
            long startedAt) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("batchNo", batch.getBatchNo());
        summary.put("sourceFileName", batch.getSourceFileName());
        summary.put("customerCode", customer.getCustomerCode());
        summary.put("customerName", customer.getCustomerName());
        summary.put("documentTypeCode", documentTypeCode);
        summary.put("status", resp.getStatus());
        summary.put("totalOrderCount", resp.getTotalOrderCount());
        summary.put("successOrderCount", resp.getSuccessOrderCount());
        summary.put("failedOrderCount", resp.getFailedOrderCount());
        summary.put("totalLineCount", resp.getTotalLineCount());
        summary.put("successItemCount", resp.getTotalLineCount());
        summary.put("salesOrderNos", resp.getRows().stream()
                .map(SalesOrderQpoImportResultRow::getSalesOrderNo)
                .filter(value -> !isBlank(value))
                .limit(10)
                .toList());
        summary.put("matchedItemCount", resp.getRows().stream()
                .mapToInt(row -> row.getMatchedItemCount() == null ? 0 : row.getMatchedItemCount())
                .sum());
        summary.put("unmatchedItemCount", resp.getRows().stream()
                .mapToInt(row -> row.getUnmatchedItemCount() == null ? 0 : row.getUnmatchedItemCount())
                .sum());
        Map<String, Object> drawingReport = resp.getDrawingReport();
        if (drawingReport != null) {
            summary.put("uploadedDrawingCount", drawingReport.get("uploadedDrawingCount"));
            summary.put("matchedDrawingItemCount", drawingReport.get("matchedItemCount"));
            summary.put("missingDrawingCount", drawingReport.get("missingDrawingCount"));
            summary.put("unmatchedDrawingCount", drawingReport.get("unmatchedDrawingCount"));
        }
        summary.put("durationMs", System.currentTimeMillis() - startedAt);
        auditService.record(
                ACTION,
                TARGET_TYPE,
                batch.getId() == null ? batch.getBatchNo() : batch.getId(),
                null,
                summary,
                "MARKET",
                resp.isSuccess() ? "SUCCESS" : "FAILED",
                AuditService.labelForAction(ACTION),
                resp.isSuccess() ? null : resp.getErrorMessage()
        );
    }

    private CustomerMaster requireCustomer(Long customerId) {
        if (customerId == null) {
            throw new RuntimeException("请选择客户");
        }
        CustomerMaster customer = customerMapper.selectById(customerId);
        if (customer == null || Boolean.FALSE.equals(customer.getEnabled())) {
            throw new RuntimeException("客户不存在或已禁用");
        }
        if (isBlank(customer.getCustomerCode()) || isBlank(customer.getCustomerName())) {
            throw new RuntimeException("客户资料缺少客户编码或客户名称");
        }
        return customer;
    }

    private void validateFile(MultipartFile excelFile) {
        if (excelFile == null || excelFile.isEmpty()) {
            throw new IllegalArgumentException("请上传销售订单 Excel 文件");
        }
    }

    private String effectiveDocumentType(String documentTypeCode) {
        return isBlank(documentTypeCode) ? DEFAULT_DOCUMENT_TYPE_CODE : documentTypeCode.trim();
    }

    private String nextBatchNo() {
        return "SOQPO" + System.currentTimeMillis();
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

    private String normalizePartNo(String partNo) {
        return DrawingPartNoResolver.normalizePartNo(partNo);
    }

    private String normalizeVersion(String version) {
        return DrawingPartNoResolver.normalizeVersion(version);
    }

    private String itemKey(String partNo, String version) {
        return normalizePartNo(partNo) + "||" + normalizeVersion(version);
    }

    private boolean isPdfDrawing(MultipartFile drawing) {
        String originalFilename = drawing == null ? null : drawing.getOriginalFilename();
        return drawing != null
                && !drawing.isEmpty()
                && originalFilename != null
                && originalFilename.toLowerCase().endsWith(".pdf");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String safeError(String value) {
        return isBlank(value) ? "Sales order independent import failed" : value.replaceAll("\\s+", " ").trim();
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

    private class DrawingBindingResult {
        private final Set<String> knownPartNos;
        private final Set<DrawingPartNoResolver.DrawingIdentity> knownPartNoVersions;
        private final Map<String, DrawingFileInfo> drawingByPartNo;
        private final Map<String, DrawingFileInfo> drawingByPartNoVersion;
        private final List<DrawingFileInfo> uploadedDrawings;
        private final int versionUploadedDrawingCount;
        private final Map<String, Object> report;

        private DrawingBindingResult(
                SalesOrderQpoExcelParseResult parseResult,
                Set<String> knownPartNos,
                Set<DrawingPartNoResolver.DrawingIdentity> knownPartNoVersions,
                Map<String, DrawingFileInfo> drawingByPartNo,
                Map<String, DrawingFileInfo> drawingByPartNoVersion,
                List<DrawingFileInfo> uploadedDrawings,
                int versionUploadedDrawingCount) {
            this.knownPartNos = knownPartNos;
            this.knownPartNoVersions = knownPartNoVersions;
            this.drawingByPartNo = drawingByPartNo;
            this.drawingByPartNoVersion = drawingByPartNoVersion;
            this.uploadedDrawings = uploadedDrawings;
            this.versionUploadedDrawingCount = versionUploadedDrawingCount;
            this.report = buildReport(parseResult);
        }

        private DrawingFileInfo find(String partNo, String version) {
            DrawingFileInfo versionDrawing = drawingByPartNoVersion.get(itemKey(partNo, version));
            return versionDrawing == null ? drawingByPartNo.get(normalizePartNo(partNo)) : versionDrawing;
        }

        private Map<String, Object> report() {
            return report;
        }

        private int uploadedDrawingCount() {
            return uploadedDrawings.size();
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

        private DrawingSummary groupSummary(SalesOrderQpoOrderGroupDTO group) {
            Set<String> groupPartNos = new HashSet<>();
            Set<DrawingPartNoResolver.DrawingIdentity> groupPartNoVersions = new HashSet<>();
            int matchedItems = 0;
            int missingItems = 0;
            if (group != null && group.getLines() != null) {
                for (SalesOrderQpoExcelLineDTO line : group.getLines()) {
                    String normalizedPartNo = normalizePartNo(line == null ? null : line.getPartNo());
                    String normalizedVersion = normalizeVersion(line == null ? null : line.getVersion());
                    if (!normalizedPartNo.isEmpty()) {
                        groupPartNos.add(normalizedPartNo);
                    }
                    if (!normalizedPartNo.isEmpty() && !normalizedVersion.isEmpty()) {
                        groupPartNoVersions.add(new DrawingPartNoResolver.DrawingIdentity(normalizedPartNo, normalizedVersion));
                    }
                    if (find(normalizedPartNo, normalizedVersion) != null) {
                        matchedItems++;
                    } else {
                        missingItems++;
                    }
                }
            }
            int uploadedForGroup = 0;
            int unmatchedForGroup = 0;
            for (DrawingFileInfo drawing : uploadedDrawings) {
                if (drawing.versionSpecific()
                        ? groupPartNoVersions.contains(new DrawingPartNoResolver.DrawingIdentity(drawing.partNo(), drawing.version()))
                        : groupPartNos.contains(drawing.partNo())) {
                    uploadedForGroup++;
                } else if (isUnmatchedDrawing(drawing)) {
                    unmatchedForGroup++;
                }
            }
            return new DrawingSummary(uploadedForGroup, matchedItems, missingItems, unmatchedForGroup);
        }

        private Map<String, Object> buildReport(SalesOrderQpoExcelParseResult parseResult) {
            List<Map<String, Object>> missingItems = new ArrayList<>();
            Set<String> matchedDrawingPartNos = new HashSet<>();
            Set<String> matchedVersionDrawingKeys = new HashSet<>();
            int matchedItemCount = 0;
            if (parseResult != null && parseResult.getOrders() != null) {
                for (SalesOrderQpoOrderGroupDTO group : parseResult.getOrders()) {
                    if (group == null || group.getLines() == null) {
                        continue;
                    }
                    for (SalesOrderQpoExcelLineDTO line : group.getLines()) {
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

    private record DrawingFileInfo(
            String partNo,
            String version,
            String originalFilename,
            String url,
            boolean versionSpecific) {
    }

    private record DrawingSummary(
            int uploadedDrawingCount,
            int matchedItemCount,
            int missingDrawingCount,
            int unmatchedDrawingCount) {
    }

    private record CreatedSalesOrder(
            Long salesOrderId,
            String salesOrderNo,
            int itemCount,
            DrawingSummary drawingSummary) {
        private int matchedItemCount() {
            return 0;
        }

        private int unmatchedItemCount() {
            return itemCount;
        }

        private String warningMessage() {
            return itemCount > 0 ? WAITING_SNAPSHOT_MESSAGE : "";
        }

        public DrawingSummary drawingSummary() {
            return drawingSummary == null ? new DrawingSummary(0, 0, 0, 0) : drawingSummary;
        }
    }
}
