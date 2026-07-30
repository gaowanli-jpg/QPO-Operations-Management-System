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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    @Transactional
    public SalesOrderQpoImportResp importQpo(MultipartFile excelFile, Long customerId, String documentTypeCode) {
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

        SalesOrderImportBatch batch = createBatch(batchNo, sourceFileName, parseResult);
        batchMapper.insert(batch);

        SalesOrderQpoImportResp resp = baseResponse(batchNo, parseResult);
        int sequence = 1;
        for (SalesOrderQpoOrderGroupDTO group : parseResult.getOrders()) {
            CreatedSalesOrder created = createSalesOrderGroup(
                    batch,
                    customer,
                    effectiveDocumentType,
                    group,
                    batchNo,
                    sequence++
            );
            resp.getRows().add(successRow(created, group));
        }
        resp.setSuccess(true);
        resp.setStatus(STATUS_SUCCESS);
        resp.setSuccessOrderCount(resp.getRows().size());
        resp.setFailedOrderCount(0);
        audit(batch, resp, customer, effectiveDocumentType, startedAt);
        log.info("Sales order independent import completed batchNo={} orderCount={} lineCount={} durationMs={}",
                batchNo, resp.getTotalOrderCount(), resp.getTotalLineCount(), System.currentTimeMillis() - startedAt);
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

    private CreatedSalesOrder createSalesOrderGroup(
            SalesOrderImportBatch batch,
            CustomerMaster customer,
            String documentTypeCode,
            SalesOrderQpoOrderGroupDTO group,
            String batchNo,
            int sequence) {
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
            itemMapper.insert(toItem(header, line, now));
        }
        return new CreatedSalesOrder(header.getId(), header.getSalesOrderNo());
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

    private SalesOrderQpoImportResp baseResponse(String batchNo, SalesOrderQpoExcelParseResult parseResult) {
        SalesOrderQpoImportResp resp = new SalesOrderQpoImportResp();
        resp.setBatchNo(batchNo);
        resp.setTotalOrderCount(parseResult.getOrderCount());
        resp.setTotalLineCount(parseResult.getLineCount());
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
        row.setMatchedItemCount(0);
        row.setUnmatchedItemCount(group.getLines().size());
        row.setWarningMessage(WAITING_SNAPSHOT_MESSAGE);
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

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String safeError(String value) {
        return isBlank(value) ? "Sales order independent import failed" : value.replaceAll("\\s+", " ").trim();
    }

    private record CreatedSalesOrder(Long salesOrderId, String salesOrderNo) {
    }
}
