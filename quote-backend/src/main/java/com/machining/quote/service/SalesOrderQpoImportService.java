package com.machining.quote.service;

import com.machining.quote.config.AuthContext;
import com.machining.quote.dto.SalesOrderQpoExcelLineDTO;
import com.machining.quote.dto.SalesOrderQpoExcelParseResult;
import com.machining.quote.dto.SalesOrderQpoImportResp;
import com.machining.quote.dto.SalesOrderQpoImportResultRow;
import com.machining.quote.dto.SalesOrderQpoOrderGroupDTO;
import com.machining.quote.entity.CustomerMaster;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.entity.SalesOrderImportBatch;
import com.machining.quote.entity.SalesOrderImportRecord;
import com.machining.quote.mapper.CustomerMasterMapper;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.mapper.SalesOrderImportBatchMapper;
import com.machining.quote.mapper.SalesOrderImportRecordMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
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
    private static final String IMPORT_MODE_QPO = "QPO";
    private static final String SOURCE_SALES_ORDER = "SALES_ORDER";
    private static final String DEFAULT_DOCUMENT_TYPE_CODE = "SO01";
    private static final String QUOTED = "\u5df2\u62a5\u4ef7";

    @Autowired
    private SalesOrderQpoExcelParser excelParser;
    @Autowired
    private CustomerMasterMapper customerMapper;
    @Autowired
    private OrderRfqMapper orderMapper;
    @Autowired
    private ItemQuoteVersionMapper itemMapper;
    @Autowired
    private SalesOrderImportBatchMapper batchMapper;
    @Autowired
    private SalesOrderImportRecordMapper recordMapper;
    @Autowired
    private RfqNumberService rfqNumberService;
    @Autowired
    private AuditService auditService;

    @Transactional
    public SalesOrderQpoImportResp importQpo(MultipartFile excelFile, Long customerId, String documentTypeCode) {
        validateFile(excelFile);
        CustomerMaster customer = requireCustomer(customerId);
        String effectiveDocumentType = effectiveDocumentType(documentTypeCode);
        String batchNo = nextBatchNo();
        String sourceFileName = safeFileName(excelFile.getOriginalFilename());
        long startedAt = System.currentTimeMillis();

        try {
            SalesOrderQpoExcelParseResult parseResult = excelParser.parse(excelFile.getInputStream());
            if (parseResult.getOrderCount() <= 0 || parseResult.getOrders().isEmpty()) {
                throw new IllegalArgumentException("\u9500\u552e\u8ba2\u5355\u8868\u683c\u6ca1\u6709\u53ef\u5bfc\u5165\u7684\u8ba2\u5355");
            }
            log.info("Sales order QPO import parsed batchNo={} orderCount={} lineCount={} sourceFileName={}",
                    batchNo, parseResult.getOrderCount(), parseResult.getLineCount(), sourceFileName);

            SalesOrderImportBatch batch = createBatch(batchNo, sourceFileName, parseResult);
            batchMapper.insert(batch);

            SalesOrderQpoImportResp resp = baseResponse(batchNo, parseResult);
            int sequence = 1;
            for (SalesOrderQpoOrderGroupDTO group : parseResult.getOrders()) {
                CreatedOrder created = createOrderGroup(batch, customer, effectiveDocumentType, group, sequence++);
                resp.getRows().add(successRow(created, group));
            }
            resp.setSuccess(true);
            resp.setStatus(STATUS_SUCCESS);
            resp.setSuccessOrderCount(resp.getRows().size());
            resp.setFailedOrderCount(0);
            audit(batch, resp, startedAt);
            log.info("Sales order QPO import completed batchNo={} orderCount={} lineCount={} durationMs={}",
                    batchNo, resp.getTotalOrderCount(), resp.getTotalLineCount(), System.currentTimeMillis() - startedAt);
            return resp;
        } catch (IllegalArgumentException e) {
            log.warn("Sales order QPO import rejected batchNo={} reason={}", batchNo, safeError(e.getMessage()));
            throw e;
        } catch (Exception e) {
            String message = "\u9500\u552e\u8ba2\u5355\u5bfc\u5165QPO\u4e3b\u6d41\u7a0b\u5931\u8d25\uff1a" + safeError(e.getMessage());
            log.warn("Sales order QPO import rejected batchNo={} reason={}", batchNo, message);
            throw new RuntimeException(message, e);
        }
    }

    private CreatedOrder createOrderGroup(
            SalesOrderImportBatch batch,
            CustomerMaster customer,
            String documentTypeCode,
            SalesOrderQpoOrderGroupDTO group,
            int sequence) {
        String rfqNo = rfqNumberService.nextRfqNo();
        OrderRfq order = new OrderRfq();
        order.setRfqNo(rfqNo);
        order.setCustomerName(customer.getCustomerName());
        order.setStatus(QUOTED);
        order.setOrderType(group.getOrderType());
        order.setSourceType(SOURCE_SALES_ORDER);
        order.setCustomerCode(customer.getCustomerCode());
        order.setCustomerShortName(customer.getCustomerShortName());
        order.setSalesDocumentTypeCode(documentTypeCode);
        order.setSalesOrderDate(group.getSalesOrderDate());
        order.setCustomerOrderNo(group.getCustomerOrderNo());
        order.setCustomerProjectNo(group.getCustomerProjectNo());
        order.setCustomerOrderTitle(group.getCustomerOrderTitle());
        order.setInternalProjectNo(group.getInternalProjectNo());
        order.setSalesOrderImportBatchId(batch.getId());
        order.setCreatedAt(LocalDateTime.now());
        AuthContext.SessionUser user = AuthContext.get();
        if (user != null && user.id() != null) {
            order.setCreatedBy(user.id());
            order.setOwnerUserId(user.id());
        }
        orderMapper.insert(order);

        SalesOrderImportRecord record = createRecord(batch, customer, documentTypeCode, group, order, sequence);
        recordMapper.insert(record);

        OrderRfq update = new OrderRfq();
        update.setId(order.getId());
        update.setSalesOrderImportRecordId(record.getId());
        orderMapper.updateById(update);

        for (SalesOrderQpoExcelLineDTO line : group.getLines()) {
            itemMapper.insert(toItem(order, record, line));
        }
        return new CreatedOrder(order.getId(), order.getRfqNo(), record.getId());
    }

    private SalesOrderImportBatch createBatch(
            String batchNo,
            String sourceFileName,
            SalesOrderQpoExcelParseResult parseResult) {
        LocalDateTime now = LocalDateTime.now();
        SalesOrderImportBatch batch = new SalesOrderImportBatch();
        batch.setBatchNo(batchNo);
        batch.setSourceFileName(sourceFileName);
        batch.setImportMode(IMPORT_MODE_QPO);
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

    private SalesOrderImportRecord createRecord(
            SalesOrderImportBatch batch,
            CustomerMaster customer,
            String documentTypeCode,
            SalesOrderQpoOrderGroupDTO group,
            OrderRfq order,
            int sequence) {
        LocalDateTime now = LocalDateTime.now();
        SalesOrderImportRecord record = new SalesOrderImportRecord();
        record.setBatchId(batch.getId());
        record.setOtherId(batch.getBatchNo() + "-" + sequence);
        record.setCustomerOrderNo(group.getCustomerOrderNo());
        record.setOrderBy(customer.getCustomerCode());
        record.setDocumentType(documentTypeCode);
        record.setLineCount(group.getLines().size());
        record.setRequestSummaryJson(requestSummary(group, order.getRfqNo()));
        record.setRfqId(order.getId());
        record.setRfqNo(order.getRfqNo());
        record.setQpoStatus(STATUS_SUCCESS);
        record.setMatchedItemCount(0);
        record.setUnmatchedItemCount(0);
        record.setStatus(STATUS_SUCCESS);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        return record;
    }

    private ItemQuoteVersion toItem(OrderRfq order, SalesOrderImportRecord record, SalesOrderQpoExcelLineDTO line) {
        ItemQuoteVersion item = new ItemQuoteVersion();
        item.setRfqId(order.getId());
        item.setPartNo(line.getPartNo());
        item.setDescription(line.getDescription());
        item.setQty(line.getQty());
        item.setVersion(line.getVersion());
        item.setOrderType(line.getOrderType());
        item.setSalesOrderLineNo(line.getSalesOrderLineNo());
        item.setCustomerOrderNo(line.getCustomerOrderNo());
        item.setCustomerProjectNo(line.getCustomerProjectNo());
        item.setInternalProjectNo(line.getInternalProjectNo());
        item.setSalesMaterialNo(line.getSalesMaterialNo());
        item.setCustomerDeliveryDate(line.getCustomerDeliveryDate());
        item.setInternalDeliveryDate(line.getInternalDeliveryDate());
        item.setMaterialName(line.getMaterialName());
        item.setSurfaceTreatment(line.getSurfaceTreatment());
        item.setLeadTime(toDateText(line.getCustomerDeliveryDate()));
        item.setQuoteLeadTime(toDateText(line.getCustomerDeliveryDate()));
        item.setInternalTargetLeadTime(toDateText(line.getInternalDeliveryDate()));
        item.setRemark(line.getRemark());
        item.setFinalUnitPrice(toDouble(line.getTaxIncludedUnitPrice()));
        item.setFinalTotalPrice(toDouble(line.getTaxIncludedTotalPrice()));
        item.setTaxIncludedUnitPrice(toDouble(line.getTaxIncludedUnitPrice()));
        item.setUntaxedUnitPrice(toDouble(line.getUntaxedUnitPrice()));
        item.setTaxIncludedTotalPrice(toDouble(line.getTaxIncludedTotalPrice()));
        item.setUntaxedTotalPrice(toDouble(line.getUntaxedTotalPrice()));
        item.setShippedQty(line.getShippedQty());
        item.setUnshippedQty(line.getUnshippedQty());
        item.setActualShipDate(line.getActualShipDate());
        item.setSalesOrderImportRecordId(record.getId());
        item.setQuoteMode("NORMAL");
        item.setStatus(QUOTED);
        return item;
    }

    private SalesOrderQpoImportResp baseResponse(String batchNo, SalesOrderQpoExcelParseResult parseResult) {
        SalesOrderQpoImportResp resp = new SalesOrderQpoImportResp();
        resp.setBatchNo(batchNo);
        resp.setTotalOrderCount(parseResult.getOrderCount());
        resp.setTotalLineCount(parseResult.getLineCount());
        return resp;
    }

    private SalesOrderQpoImportResultRow successRow(CreatedOrder created, SalesOrderQpoOrderGroupDTO group) {
        SalesOrderQpoImportResultRow row = new SalesOrderQpoImportResultRow();
        row.setSuccess(true);
        row.setStatus(STATUS_SUCCESS);
        row.setRfqId(created.rfqId());
        row.setRfqNo(created.rfqNo());
        row.setCustomerOrderNo(group.getCustomerOrderNo());
        row.setInternalProjectNo(group.getInternalProjectNo());
        row.setLineCount(group.getLines().size());
        return row;
    }

    private Map<String, Object> requestSummary(SalesOrderQpoOrderGroupDTO group, String rfqNo) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("rfqNo", rfqNo);
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

    private void audit(SalesOrderImportBatch batch, SalesOrderQpoImportResp resp, long startedAt) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("batchNo", batch.getBatchNo());
        summary.put("sourceFileName", batch.getSourceFileName());
        summary.put("status", resp.getStatus());
        summary.put("totalOrderCount", resp.getTotalOrderCount());
        summary.put("successOrderCount", resp.getSuccessOrderCount());
        summary.put("failedOrderCount", resp.getFailedOrderCount());
        summary.put("totalLineCount", resp.getTotalLineCount());
        summary.put("rfqNoSample", resp.getRows().stream()
                .map(SalesOrderQpoImportResultRow::getRfqNo)
                .filter(rfqNo -> !isBlank(rfqNo))
                .limit(10)
                .toList());
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
            throw new RuntimeException("\u8bf7\u9009\u62e9\u5ba2\u6237");
        }
        CustomerMaster customer = customerMapper.selectById(customerId);
        if (customer == null || Boolean.FALSE.equals(customer.getEnabled())) {
            throw new RuntimeException("\u5ba2\u6237\u4e0d\u5b58\u5728\u6216\u5df2\u7981\u7528");
        }
        if (isBlank(customer.getCustomerCode()) || isBlank(customer.getCustomerName())) {
            throw new RuntimeException("\u5ba2\u6237\u8d44\u6599\u7f3a\u5c11\u5ba2\u6237\u7f16\u7801\u6216\u5ba2\u6237\u540d\u79f0");
        }
        return customer;
    }

    private void validateFile(MultipartFile excelFile) {
        if (excelFile == null || excelFile.isEmpty()) {
            throw new IllegalArgumentException("\u8bf7\u4e0a\u4f20\u9500\u552e\u8ba2\u5355 Excel \u6587\u4ef6");
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

    private String safeError(String value) {
        return isBlank(value) ? "Sales order QPO import failed" : value.replaceAll("\\s+", " ").trim();
    }

    private Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    private String toDateText(LocalDate value) {
        return value == null ? null : value.toString();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private record CreatedOrder(Integer rfqId, String rfqNo, Long recordId) {
    }
}
