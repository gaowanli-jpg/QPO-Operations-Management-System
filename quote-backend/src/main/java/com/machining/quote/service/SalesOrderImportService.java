package com.machining.quote.service;

import com.machining.quote.config.AuthContext;
import com.machining.quote.dto.SalesOrderDryRunResp;
import com.machining.quote.dto.SalesOrderExcelParseResult;
import com.machining.quote.dto.SalesOrderImportResp;
import com.machining.quote.dto.SalesOrderImportResultRow;
import com.machining.quote.dto.u9.U9ApiResult;
import com.machining.quote.dto.u9.U9ResultDTO;
import com.machining.quote.dto.u9.U9SalesOrderHeadDTO;
import com.machining.quote.dto.u9.U9SalesOrderLineDTO;
import com.machining.quote.entity.SalesOrderImportBatch;
import com.machining.quote.entity.SalesOrderImportRecord;
import com.machining.quote.mapper.SalesOrderImportBatchMapper;
import com.machining.quote.mapper.SalesOrderImportRecordMapper;
import com.machining.quote.service.u9.U9SalesOrderClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SalesOrderImportService {
    private static final Logger log = LoggerFactory.getLogger(SalesOrderImportService.class);
    private static final String ACTION = "SALES_ORDER_IMPORT_U9";
    private static final String TARGET_TYPE = "sales_order_import_batch";

    private final SalesOrderExcelParser excelParser;

    @Autowired
    private U9SalesOrderClient u9SalesOrderClient;
    @Autowired
    private SalesOrderImportBatchMapper batchMapper;
    @Autowired
    private SalesOrderImportRecordMapper recordMapper;
    @Autowired
    private AuditService auditService;

    @Autowired
    public SalesOrderImportService(SalesOrderExcelParser excelParser) {
        this.excelParser = excelParser;
    }

    public SalesOrderDryRunResp dryRun(MultipartFile excelFile, String batchNo) {
        validateFile(excelFile);
        try {
            String effectiveBatchNo = effectiveBatchNo(batchNo);
            SalesOrderExcelParseResult parseResult = excelParser.parse(excelFile.getInputStream(), effectiveBatchNo);
            SalesOrderDryRunResp resp = new SalesOrderDryRunResp();
            resp.setSuccess(true);
            resp.setBatchNo(effectiveBatchNo);
            resp.setTotalOrderCount(parseResult.getOrderCount());
            resp.setTotalLineCount(parseResult.getLineCount());
            resp.setOrders(parseResult.getOrders());
            return resp;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("销售订单导入校验失败：" + safeError(e.getMessage()), e);
        }
    }

    @Transactional
    public SalesOrderImportResp importAndSync(MultipartFile excelFile, String batchNo) {
        validateFile(excelFile);
        long startedAt = System.currentTimeMillis();
        String effectiveBatchNo = effectiveBatchNo(batchNo);
        String sourceFileName = safeFileName(excelFile.getOriginalFilename());
        log.info("Sales order import started batchNo={} sourceFileName={}", effectiveBatchNo, sourceFileName);

        SalesOrderExcelParseResult parseResult = parseForSync(excelFile, effectiveBatchNo);
        SalesOrderImportBatch batch = createBatch(effectiveBatchNo, sourceFileName, parseResult);
        batchMapper.insert(batch);

        try {
            log.info("Sales order U9 sync started batchNo={} orderCount={}", effectiveBatchNo, parseResult.getOrderCount());
            U9ApiResult<U9ResultDTO> u9Result = u9SalesOrderClient.create(parseResult.getOrders());
            SalesOrderImportResp resp = buildResponse(effectiveBatchNo, parseResult, u9Result);
            finishBatch(batch, resp);
            saveRecords(batch, parseResult, resp, u9Result == null ? null : u9Result.getResCode());
            audit(batch, resp, startedAt);
            log.info("Sales order U9 sync completed batchNo={} successOrderCount={} failedOrderCount={} durationMs={}",
                    effectiveBatchNo, resp.getSuccessOrderCount(), resp.getFailedOrderCount(), durationMs(startedAt));
            return resp;
        } catch (RuntimeException e) {
            String error = safeError(e.getMessage());
            SalesOrderImportResp resp = failedResponse(effectiveBatchNo, parseResult, error);
            finishBatch(batch, resp);
            saveRecords(batch, parseResult, resp, null);
            audit(batch, resp, startedAt);
            log.error("Sales order U9 sync failed batchNo={} durationMs={} reason={}",
                    effectiveBatchNo, durationMs(startedAt), error, sanitizedException(e, error));
            return resp;
        }
    }

    private SalesOrderExcelParseResult parseForSync(MultipartFile excelFile, String batchNo) {
        try {
            SalesOrderExcelParseResult parseResult = excelParser.parse(excelFile.getInputStream(), batchNo);
            if (parseResult.getOrderCount() <= 0 || parseResult.getOrders().isEmpty()) {
                throw new IllegalArgumentException("销售订单导入表格没有可同步的订单");
            }
            log.info("Sales order import parsed batchNo={} orderCount={} lineCount={}",
                    batchNo, parseResult.getOrderCount(), parseResult.getLineCount());
            return parseResult;
        } catch (IllegalArgumentException e) {
            log.warn("Sales order import rejected batchNo={} reason={}", batchNo, safeError(e.getMessage()));
            throw e;
        } catch (Exception e) {
            String message = "销售订单导入解析失败：" + safeError(e.getMessage());
            log.warn("Sales order import rejected batchNo={} reason={}", batchNo, message);
            throw new RuntimeException(message, e);
        }
    }

    private SalesOrderImportBatch createBatch(String batchNo, String sourceFileName, SalesOrderExcelParseResult parseResult) {
        LocalDateTime now = LocalDateTime.now();
        SalesOrderImportBatch batch = new SalesOrderImportBatch();
        batch.setBatchNo(batchNo);
        batch.setSourceFileName(sourceFileName);
        batch.setStatus("SYNCING");
        batch.setTotalOrderCount(parseResult.getOrderCount());
        batch.setSuccessOrderCount(0);
        batch.setFailedOrderCount(0);
        AuthContext.SessionUser user = AuthContext.get();
        batch.setCreatedBy(user == null || user.id() == null ? null : user.id().longValue());
        batch.setCreatedAt(now);
        batch.setUpdatedAt(now);
        return batch;
    }

    private SalesOrderImportResp buildResponse(
            String batchNo,
            SalesOrderExcelParseResult parseResult,
            U9ApiResult<U9ResultDTO> u9Result) {
        Integer resCode = u9Result == null ? null : u9Result.getResCode();
        if (resCode == null || resCode != 0) {
            String error = safeError(firstNonBlank(
                    u9Result == null ? null : u9Result.getResMsg(),
                    u9Result == null ? null : u9Result.getException(),
                    "U9 sales order sync failed"
            ));
            return failedResponse(batchNo, parseResult, error);
        }

        Map<String, U9ResultDTO> resultByOtherId = (u9Result.getData() == null ? List.<U9ResultDTO>of() : u9Result.getData())
                .stream()
                .filter(result -> !isBlank(result.getMOtherID()))
                .collect(Collectors.toMap(U9ResultDTO::getMOtherID, Function.identity(), (left, right) -> left));

        SalesOrderImportResp resp = baseResponse(batchNo, parseResult);
        for (U9SalesOrderHeadDTO order : parseResult.getOrders()) {
            U9ResultDTO u9Row = resultByOtherId.get(order.getOtherID());
            boolean success = u9Row != null && Boolean.TRUE.equals(u9Row.getMIsSucess());
            SalesOrderImportResultRow row = new SalesOrderImportResultRow();
            row.setOtherId(order.getOtherID());
            row.setCustomerOrderNo(order.getCustomerOrderNo());
            row.setSuccess(success);
            row.setStatus(success ? "SUCCESS" : "FAILED");
            if (u9Row != null) {
                row.setU9Id(u9Row.getMID());
                row.setU9Code(u9Row.getMCode());
            }
            row.setErrorMessage(success ? "" : safeError(firstNonBlank(
                    u9Row == null ? null : u9Row.getMErrorMsg(),
                    "U9 result missing for otherId=" + order.getOtherID()
            )));
            resp.getRows().add(row);
        }
        populateCountsAndStatus(resp);
        return resp;
    }

    private SalesOrderImportResp failedResponse(String batchNo, SalesOrderExcelParseResult parseResult, String errorMessage) {
        SalesOrderImportResp resp = baseResponse(batchNo, parseResult);
        resp.setSuccess(false);
        resp.setStatus("FAILED");
        resp.setSuccessOrderCount(0);
        resp.setFailedOrderCount(parseResult.getOrderCount());
        resp.setErrorMessage(errorMessage);
        for (U9SalesOrderHeadDTO order : parseResult.getOrders()) {
            SalesOrderImportResultRow row = new SalesOrderImportResultRow();
            row.setOtherId(order.getOtherID());
            row.setCustomerOrderNo(order.getCustomerOrderNo());
            row.setSuccess(false);
            row.setStatus("FAILED");
            row.setErrorMessage(errorMessage);
            resp.getRows().add(row);
        }
        return resp;
    }

    private SalesOrderImportResp baseResponse(String batchNo, SalesOrderExcelParseResult parseResult) {
        SalesOrderImportResp resp = new SalesOrderImportResp();
        resp.setBatchNo(batchNo);
        resp.setTotalOrderCount(parseResult.getOrderCount());
        return resp;
    }

    private void populateCountsAndStatus(SalesOrderImportResp resp) {
        int successCount = (int) resp.getRows().stream().filter(SalesOrderImportResultRow::isSuccess).count();
        int failedCount = resp.getRows().size() - successCount;
        resp.setSuccessOrderCount(successCount);
        resp.setFailedOrderCount(failedCount);
        resp.setSuccess(failedCount == 0);
        resp.setStatus(statusFor(successCount, failedCount));
        if (failedCount > 0) {
            resp.setErrorMessage(firstNonBlank(
                    resp.getRows().stream()
                            .filter(row -> !row.isSuccess())
                            .map(SalesOrderImportResultRow::getErrorMessage)
                            .filter(message -> !isBlank(message))
                            .findFirst()
                            .orElse(null),
                    "U9 sales order sync partially failed"
            ));
        }
    }

    private String statusFor(int successCount, int failedCount) {
        if (failedCount == 0) {
            return "SUCCESS";
        }
        if (successCount > 0) {
            return "PARTIAL_SUCCESS";
        }
        return "FAILED";
    }

    private void finishBatch(SalesOrderImportBatch batch, SalesOrderImportResp resp) {
        SalesOrderImportBatch update = new SalesOrderImportBatch();
        update.setId(batch.getId());
        update.setStatus(resp.getStatus());
        update.setTotalOrderCount(resp.getTotalOrderCount());
        update.setSuccessOrderCount(resp.getSuccessOrderCount());
        update.setFailedOrderCount(resp.getFailedOrderCount());
        update.setUpdatedAt(LocalDateTime.now());
        batchMapper.updateById(update);
    }

    private void saveRecords(
            SalesOrderImportBatch batch,
            SalesOrderExcelParseResult parseResult,
            SalesOrderImportResp resp,
            Integer u9ResCode) {
        Map<String, SalesOrderImportResultRow> rowsByOtherId = resp.getRows().stream()
                .collect(Collectors.toMap(SalesOrderImportResultRow::getOtherId, Function.identity(), (left, right) -> left));
        LocalDateTime now = LocalDateTime.now();
        for (U9SalesOrderHeadDTO order : parseResult.getOrders()) {
            SalesOrderImportResultRow row = rowsByOtherId.get(order.getOtherID());
            SalesOrderImportRecord record = new SalesOrderImportRecord();
            record.setBatchId(batch.getId());
            record.setOtherId(order.getOtherID());
            record.setCustomerOrderNo(order.getCustomerOrderNo());
            record.setOrderBy(order.getOrderBy());
            record.setDocumentType(order.getDocumentType());
            record.setLineCount(lineCount(order));
            record.setRequestSummaryJson(requestSummary(order));
            record.setU9ResCode(u9ResCode);
            record.setU9Success(row != null && row.isSuccess());
            record.setU9Id(row == null ? null : row.getU9Id());
            record.setU9Code(row == null ? null : row.getU9Code());
            record.setU9ErrorMsg(row == null ? null : emptyToNull(row.getErrorMessage()));
            record.setStatus(row != null && row.isSuccess() ? "SUCCESS" : "FAILED");
            record.setCreatedAt(now);
            record.setUpdatedAt(now);
            recordMapper.insert(record);
        }
    }

    private Map<String, Object> requestSummary(U9SalesOrderHeadDTO order) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("otherId", order.getOtherID());
        summary.put("customerOrderNo", order.getCustomerOrderNo());
        summary.put("orderBy", order.getOrderBy());
        summary.put("documentType", order.getDocumentType());
        summary.put("businessDate", order.getBusinessDate());
        summary.put("lineCount", lineCount(order));
        summary.put("itemCodeSample", order.getSOLineDTOList().stream()
                .map(U9SalesOrderLineDTO::getItemCode)
                .filter(code -> !isBlank(code))
                .limit(10)
                .toList());
        return summary;
    }

    private void audit(SalesOrderImportBatch batch, SalesOrderImportResp resp, long startedAt) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("batchNo", batch.getBatchNo());
        summary.put("sourceFileName", batch.getSourceFileName());
        summary.put("status", resp.getStatus());
        summary.put("totalOrderCount", resp.getTotalOrderCount());
        summary.put("successOrderCount", resp.getSuccessOrderCount());
        summary.put("failedOrderCount", resp.getFailedOrderCount());
        summary.put("durationMs", durationMs(startedAt));
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

    private int lineCount(U9SalesOrderHeadDTO order) {
        return order.getSOLineDTOList() == null ? 0 : order.getSOLineDTOList().size();
    }

    private long durationMs(long startedAt) {
        return System.currentTimeMillis() - startedAt;
    }

    private String effectiveBatchNo(String batchNo) {
        return isBlank(batchNo) ? "SOI" + System.currentTimeMillis() : batchNo.trim();
    }

    private void validateFile(MultipartFile excelFile) {
        if (excelFile == null || excelFile.isEmpty()) {
            throw new IllegalArgumentException("请上传销售订单 Excel 文件");
        }
    }

    private String safeFileName(String fileName) {
        if (isBlank(fileName)) {
            return "";
        }
        return fileName.replace('\\', '_').replace('/', '_').trim();
    }

    private String safeError(String value) {
        if (isBlank(value)) {
            return "U9 sales order sync failed";
        }
        String text = value.replaceAll("\\s+", " ").trim();
        text = text.replaceAll("(?i)\\b(token|clientsecret|client[-_]?secret|authorization|cookie)\\s*[:=]\\s*[^\\s,;]+", "[REDACTED]");
        text = text.replaceAll("(?i)SO(LineDTOList|ShiplineDTOList)\\s*=\\s*\\[[^]]*]", "request=[REDACTED]");
        return abbreviate(text, 500);
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String emptyToNull(String value) {
        return isBlank(value) ? null : value;
    }

    private RuntimeException sanitizedException(Throwable source, String message) {
        RuntimeException sanitized = new RuntimeException(message);
        if (source != null) {
            sanitized.setStackTrace(source.getStackTrace());
        }
        return sanitized;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
