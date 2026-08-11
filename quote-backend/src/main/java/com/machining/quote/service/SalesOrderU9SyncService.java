package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.SalesOrderU9SyncOrderRow;
import com.machining.quote.dto.SalesOrderU9SyncPreviewLine;
import com.machining.quote.dto.SalesOrderU9SyncPreviewResp;
import com.machining.quote.dto.SalesOrderU9SyncResp;
import com.machining.quote.dto.SalesOrderU9SyncResultRow;
import com.machining.quote.dto.u9.U9ApiResult;
import com.machining.quote.dto.u9.U9ResultDTO;
import com.machining.quote.dto.u9.U9SalesOrderHeadDTO;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import com.machining.quote.service.u9.U9SalesOrderClient;
import com.machining.quote.service.u9.U9SalesOrderContractValidator;
import com.machining.quote.service.u9.U9SalesOrderContractValidator.ValidationResult;
import com.machining.quote.service.u9.U9SalesOrderRequestAssembler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SalesOrderU9SyncService {
    private static final Logger log = LoggerFactory.getLogger(SalesOrderU9SyncService.class);
    private static final String ACTION = "SALES_ORDER_U9_SYNC";
    private static final String TARGET_TYPE = "sales_order_header";
    private static final String STATUS_SYNCING = "同步中";
    private static final String STATUS_SYNCED = "已同步";
    private static final String STATUS_FAILED = "同步失败";
    private static final String MATERIAL_CONFIRMED = "已确认";
    private static final String RESP_SUCCESS = "SUCCESS";
    private static final String RESP_FAILED = "FAILED";
    private static final String RESP_PARTIAL = "PARTIAL_SUCCESS";
    private static final String SOURCE_RFQ_RELEASE = "RFQ_RELEASE";
    private static final int MAX_PAGE_SIZE = 100;

    @Autowired
    private SalesOrderHeaderMapper headerMapper;
    @Autowired
    private SalesOrderItemMapper itemMapper;
    @Autowired
    private U9SalesOrderClient u9SalesOrderClient;
    @Autowired
    private AuditService auditService;
    @Autowired(required = false)
    private DataScopeService dataScopeService;
    @Autowired
    private U9SalesOrderRequestAssembler requestAssembler = new U9SalesOrderRequestAssembler();
    @Autowired
    private U9SalesOrderContractValidator contractValidator = new U9SalesOrderContractValidator();

    public PageResult<SalesOrderU9SyncOrderRow> listOrders(
            String customerName,
            String keyword,
            String status,
            String u9SyncStatus,
            String materialReadyStatus,
            Integer page,
            Integer pageSize) {
        int safePage = normalizePage(page);
        int safePageSize = normalizePageSize(pageSize);
        QueryWrapper<SalesOrderHeader> query = new QueryWrapper<>();
        applySalesOrderScope(query);
        applyListFilters(query, customerName, keyword, status, u9SyncStatus, materialReadyStatus);
        Long total = headerMapper.selectCount(query);
        if (total == null || total == 0) {
            return PageResult.of(List.of(), 0, safePage, safePageSize);
        }
        query.orderByDesc("id");
        query.last("LIMIT " + safePageSize + " OFFSET " + ((safePage - 1L) * safePageSize));
        List<SalesOrderU9SyncOrderRow> records = safeList(headerMapper.selectList(query)).stream()
                .map(this::toOrderRow)
                .toList();
        return PageResult.of(records, total, safePage, safePageSize);
    }

    public SalesOrderU9SyncPreviewResp previewSalesOrder(Long salesOrderId) {
        SalesOrderHeader header = requireSalesOrder(salesOrderId);
        List<SalesOrderItem> items = loadItems(header.getId());
        MaterialStats stats = materialStats(items);
        ValidationResult validation = contractValidator.validate(header, items);
        SalesOrderU9SyncPreviewResp resp = new SalesOrderU9SyncPreviewResp();
        resp.setSalesOrderId(header.getId());
        resp.setSalesOrderNo(header.getSalesOrderNo());
        resp.setSourceType(header.getSourceType());
        resp.setSourceLabel(sourceLabel(header.getSourceType()));
        resp.setDocumentNo(header.getDocumentNo());
        resp.setCustomerName(header.getCustomerName());
        resp.setCustomerShortName(header.getCustomerShortName());
        resp.setCustomerCode(header.getCustomerCode());
        resp.setOrderBy(header.getCustomerCode());
        resp.setSalesOrderDate(header.getSalesOrderDate());
        resp.setCustomerOrderNo(header.getCustomerOrderNo());
        resp.setInternalProjectNo(header.getInternalProjectNo());
        resp.setCustomerProjectNo(header.getCustomerProjectNo());
        resp.setCustomerOrderTitle(header.getCustomerOrderTitle());
        resp.setOrderType(header.getOrderType());
        resp.setDocumentTypeCode(header.getDocumentTypeCode());
        resp.setStatus(header.getStatus());
        resp.setU9SyncStatus(header.getU9SyncStatus());
        resp.setU9OrderCode(header.getU9OrderCode());
        resp.setU9SyncMessage(header.getU9SyncMessage());
        resp.setItemCount(stats.total());
        resp.setMaterialConfirmedItemCount(stats.confirmed());
        resp.setMissingMaterialItemCount(stats.missingOrUnconfirmed());
        resp.setBlockingErrors(validation.blockingErrors());
        resp.setWarnings(validation.warnings());
        resp.setBlockedReason(validation.firstBlockingError());
        resp.setSyncReady(!validation.hasBlockingErrors());
        if (!validation.hasBlockingErrors()) {
            resp.setRequestFingerprint(requestAssembler.fingerprint(requestAssembler.assemble(header, items)));
        }
        resp.setLines(items.stream().map(item -> toPreviewLine(header, item)).toList());
        return resp;
    }

    @Transactional
    public SalesOrderU9SyncResp syncSalesOrders(List<Long> salesOrderIds) {
        long startedAt = System.currentTimeMillis();
        List<Long> ids = normalizeIds(salesOrderIds);
        SalesOrderU9SyncResp resp = new SalesOrderU9SyncResp();
        if (ids.isEmpty()) {
            resp.setSuccess(false);
            resp.setStatus(RESP_FAILED);
            resp.setMessage("请选择需要同步 U9 的销售订单");
            resp.setTotalOrderCount(0);
            resp.setSuccessOrderCount(0);
            resp.setFailedOrderCount(0);
            return resp;
        }

        log.info("Sales order U9 sync started orderCount={}", ids.size());
        List<PreparedOrder> preparedOrders = new ArrayList<>();
        for (Long id : ids) {
            SalesOrderHeader header = headerMapper.selectByIdForUpdate(id);
            if (header == null) {
                resp.getRows().add(failedRow(id, null, "销售订单不存在"));
                continue;
            }
            assertDataScope(header);
            if (alreadySynced(header)) {
                resp.getRows().add(failedRow(header, "销售订单已同步，不能重复创建 U9 订单"));
                continue;
            }
            try {
                preparedOrders.add(prepareOrder(header));
            } catch (RuntimeException error) {
                String message = safeError(error.getMessage());
                markFailed(header, message);
                resp.getRows().add(failedRow(header, message));
            }
        }

        if (!preparedOrders.isEmpty()) {
            syncPreparedOrders(preparedOrders, resp);
        }
        finalizeResponse(resp);
        audit(resp, startedAt);
        log.info("Sales order U9 sync completed orderCount={} successOrderCount={} failedOrderCount={} durationMs={}",
                resp.getTotalOrderCount(), resp.getSuccessOrderCount(), resp.getFailedOrderCount(),
                System.currentTimeMillis() - startedAt);
        return resp;
    }

    private void syncPreparedOrders(List<PreparedOrder> preparedOrders, SalesOrderU9SyncResp resp) {
        for (PreparedOrder prepared : preparedOrders) {
            markSyncing(prepared.header(), prepared.requestFingerprint());
        }
        Map<String, PreparedOrder> preparedByOtherId = preparedOrders.stream()
                .collect(Collectors.toMap(order -> order.request().getOtherID(), Function.identity(), (left, right) -> left, LinkedHashMap::new));
        try {
            U9ApiResult<U9ResultDTO> u9Result = u9SalesOrderClient.create(preparedOrders.stream()
                    .map(PreparedOrder::request)
                    .toList());
            if (u9Result == null || u9Result.getResCode() == null || u9Result.getResCode() != 0) {
                String message = safeError(firstNonBlank(
                        u9Result == null ? null : u9Result.getResMsg(),
                        u9Result == null ? null : u9Result.getException(),
                        "U9 sales order sync failed"
                ));
                failPreparedOrders(preparedOrders, resp, message);
                return;
            }
            Map<String, U9ResultDTO> resultByOtherId = (u9Result.getData() == null ? List.<U9ResultDTO>of() : u9Result.getData())
                    .stream()
                    .filter(row -> !isBlank(row.getMOtherID()))
                    .collect(Collectors.toMap(U9ResultDTO::getMOtherID, Function.identity(), (left, right) -> left));
            for (Map.Entry<String, PreparedOrder> entry : preparedByOtherId.entrySet()) {
                PreparedOrder prepared = entry.getValue();
                U9ResultDTO resultRow = resultByOtherId.get(entry.getKey());
                if (resultRow != null && Boolean.TRUE.equals(resultRow.getMIsSucess())) {
                    markSuccess(prepared.header(), resultRow);
                    resp.getRows().add(successRow(prepared.header(), resultRow));
                } else {
                    String message = safeError(firstNonBlank(
                            resultRow == null ? null : resultRow.getMErrorMsg(),
                            "U9 result missing for otherId=" + entry.getKey()
                    ));
                    markFailed(prepared.header(), message);
                    resp.getRows().add(failedRow(prepared.header(), message));
                }
            }
        } catch (RuntimeException error) {
            String message = safeError(error.getMessage());
            failPreparedOrders(preparedOrders, resp, message);
            log.error("Sales order U9 sync failed orderCount={} reason={}", preparedOrders.size(), message, sanitizedException(error, message));
        }
    }

    private PreparedOrder prepareOrder(SalesOrderHeader header) {
        List<SalesOrderItem> items = loadItems(header.getId());
        ValidationResult validation = contractValidator.validate(header, items);
        if (validation.hasBlockingErrors()) {
            throw new RuntimeException(validation.firstBlockingError());
        }
        U9SalesOrderHeadDTO request = requestAssembler.assemble(header, items);
        return new PreparedOrder(header, request, requestAssembler.fingerprint(request));
    }

    private List<SalesOrderItem> loadItems(Long salesOrderId) {
        QueryWrapper<SalesOrderItem> query = new QueryWrapper<>();
        query.eq("sales_order_id", salesOrderId).orderByAsc("id");
        List<SalesOrderItem> items = itemMapper.selectList(query);
        return items == null ? List.of() : items;
    }

    private SalesOrderHeader requireSalesOrder(Long salesOrderId) {
        if (salesOrderId == null) {
            throw new RuntimeException("销售订单 ID 不能为空");
        }
        SalesOrderHeader header = headerMapper.selectById(salesOrderId);
        if (header == null) {
            throw new RuntimeException("销售订单不存在");
        }
        assertDataScope(header);
        return header;
    }

    private SalesOrderU9SyncOrderRow toOrderRow(SalesOrderHeader header) {
        List<SalesOrderItem> items = loadItems(header.getId());
        MaterialStats stats = materialStats(items);
        SalesOrderU9SyncOrderRow row = new SalesOrderU9SyncOrderRow();
        row.setSalesOrderId(header.getId());
        row.setSalesOrderNo(header.getSalesOrderNo());
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
        row.setU9SyncStatus(header.getU9SyncStatus());
        row.setU9OrderCode(header.getU9OrderCode());
        row.setU9SyncMessage(header.getU9SyncMessage());
        row.setItemCount(stats.total());
        row.setMaterialConfirmedItemCount(stats.confirmed());
        row.setMissingMaterialItemCount(stats.missingOrUnconfirmed());
        String blockedReason = syncBlockedReason(header, items, stats);
        row.setBlockedReason(blockedReason);
        row.setSyncReady(isBlank(blockedReason));
        row.setUpdatedAt(header.getUpdatedAt());
        return row;
    }

    private SalesOrderU9SyncPreviewLine toPreviewLine(SalesOrderHeader header, SalesOrderItem item) {
        SalesOrderU9SyncPreviewLine line = new SalesOrderU9SyncPreviewLine();
        line.setSalesOrderItemId(item.getId());
        line.setLineNo(item.getLineNo());
        line.setPartNo(item.getPartNo());
        line.setVersion(item.getVersion());
        line.setSalesOrderDate(header.getSalesOrderDate());
        line.setCustomerOrderTitle(header.getCustomerOrderTitle());
        line.setSalesMaterialNo(item.getSalesMaterialNo());
        line.setCustomerDeliveryDate(item.getCustomerDeliveryDate());
        line.setInternalDeliveryDate(item.getInternalDeliveryDate());
        line.setDescription(item.getDescription());
        line.setMaterialName(item.getMaterialName());
        line.setEngineeringMaterialCode(item.getEngineeringMaterialCode());
        line.setEngineeringMaterialTypeCode(item.getEngineeringMaterialTypeCode());
        line.setEngineeringMaterialTypeName(item.getEngineeringMaterialTypeName());
        line.setEngineeringMaterialCategory(item.getEngineeringMaterialCategory());
        line.setSurfaceTreatment(item.getSurfaceTreatment());
        line.setMaterialNo(item.getMaterialNo());
        line.setMaterialNoStatus(item.getMaterialNoStatus());
        line.setQty(item.getQty());
        line.setShippedQty(item.getShippedQty());
        line.setUnshippedQty(item.getUnshippedQty());
        line.setTaxIncludedUnitPrice(item.getTaxIncludedUnitPrice());
        line.setTaxIncludedTotalPrice(item.getTaxIncludedTotalPrice());
        line.setUntaxedUnitPrice(item.getUntaxedUnitPrice());
        line.setUntaxedTotalPrice(item.getUntaxedTotalPrice());
        line.setDeliveryDate(preferredDeliveryDate(item));
        line.setRemark(item.getRemark());
        ValidationResult validation = contractValidator.validateItem(item);
        line.setBlockingErrors(validation.blockingErrors());
        line.setWarnings(validation.warnings());
        line.setReady(!validation.hasBlockingErrors());
        line.setBlockedReason(validation.firstBlockingError());
        return line;
    }

    private void applyListFilters(
            QueryWrapper<SalesOrderHeader> query,
            String customerName,
            String keyword,
            String status,
            String u9SyncStatus,
            String materialReadyStatus) {
        if (!isBlank(customerName)) {
            query.eq("customer_name", customerName.trim());
        }
        if (!isBlank(status)) {
            query.eq("status", status.trim());
        } else {
            query.and(wrapper -> wrapper.ne("status", QuoteStatusWorkflow.DISCARDED).or().isNull("status"));
        }
        if (!isBlank(u9SyncStatus)) {
            query.eq("u9_sync_status", u9SyncStatus.trim());
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
        applyMaterialReadyFilter(query, materialReadyStatus);
    }

    private void applyMaterialReadyFilter(QueryWrapper<SalesOrderHeader> query, String materialReadyStatus) {
        if (isBlank(materialReadyStatus)) {
            return;
        }
        String normalized = materialReadyStatus.trim().toUpperCase(Locale.ROOT);
        if ("READY".equals(normalized) || "已就绪".equals(materialReadyStatus.trim())) {
            query.apply("""
                    EXISTS (SELECT 1 FROM sales_order_item soi WHERE soi.sales_order_id = sales_order_header.id)
                    AND NOT EXISTS (
                        SELECT 1 FROM sales_order_item soi
                        WHERE soi.sales_order_id = sales_order_header.id
                        AND (soi.material_no IS NULL OR TRIM(soi.material_no) = '' OR COALESCE(soi.material_no_status, '') <> '已确认')
                    )
                    """);
        } else if ("BLOCKED".equals(normalized) || "MISSING".equals(normalized)
                || "未就绪".equals(materialReadyStatus.trim()) || "缺物料号".equals(materialReadyStatus.trim())) {
            query.apply("""
                    EXISTS (
                        SELECT 1 FROM sales_order_item soi
                        WHERE soi.sales_order_id = sales_order_header.id
                        AND (soi.material_no IS NULL OR TRIM(soi.material_no) = '' OR COALESCE(soi.material_no_status, '') <> '已确认')
                    )
                    """);
        }
    }

    private void applySalesOrderScope(QueryWrapper<SalesOrderHeader> query) {
        if (dataScopeService != null) {
            dataScopeService.applySalesOrderScope(query);
        }
    }

    private String sourceLabel(String sourceType) {
        return SOURCE_RFQ_RELEASE.equalsIgnoreCase(sourceType)
                ? "RFQ 议价提单派生"
                : "销售订单导入";
    }

    private MaterialStats materialStats(List<SalesOrderItem> items) {
        int total = safeList(items).size();
        int confirmed = (int) safeList(items).stream()
                .filter(this::isConfirmedMaterialNo)
                .count();
        return new MaterialStats(total, confirmed, total - confirmed);
    }

    private String syncBlockedReason(SalesOrderHeader header, List<SalesOrderItem> items, MaterialStats stats) {
        return contractValidator.validate(header, items).firstBlockingError();
    }

    private boolean isConfirmedMaterialNo(SalesOrderItem item) {
        return item != null
                && !isBlank(item.getMaterialNo())
                && MATERIAL_CONFIRMED.equals(item.getMaterialNoStatus());
    }

    private LocalDate preferredDeliveryDate(SalesOrderItem item) {
        return item.getInternalDeliveryDate() == null ? item.getCustomerDeliveryDate() : item.getInternalDeliveryDate();
    }

    private void failPreparedOrders(List<PreparedOrder> preparedOrders, SalesOrderU9SyncResp resp, String message) {
        for (PreparedOrder prepared : preparedOrders) {
            markFailed(prepared.header(), message);
            resp.getRows().add(failedRow(prepared.header(), message));
        }
    }

    private void markSyncing(SalesOrderHeader header, String requestFingerprint) {
        SalesOrderHeader update = new SalesOrderHeader();
        update.setId(header.getId());
        update.setU9SyncStatus(STATUS_SYNCING);
        update.setU9SyncRequestHash(requestFingerprint);
        update.setU9SyncMessage("U9 同步处理中");
        update.setUpdatedAt(LocalDateTime.now());
        headerMapper.updateById(update);
    }

    private void markSuccess(SalesOrderHeader header, U9ResultDTO row) {
        SalesOrderHeader update = new SalesOrderHeader();
        update.setId(header.getId());
        update.setU9SyncStatus(STATUS_SYNCED);
        update.setU9OrderId(row.getMID());
        update.setU9OrderCode(row.getMCode());
        update.setU9SyncMessage("U9 同步成功");
        update.setUpdatedAt(LocalDateTime.now());
        headerMapper.updateById(update);
    }

    private void markFailed(SalesOrderHeader header, String message) {
        SalesOrderHeader update = new SalesOrderHeader();
        update.setId(header.getId());
        update.setU9SyncStatus(STATUS_FAILED);
        update.setU9SyncMessage(safeError(message));
        update.setUpdatedAt(LocalDateTime.now());
        headerMapper.updateById(update);
    }

    private void finalizeResponse(SalesOrderU9SyncResp resp) {
        int successCount = (int) resp.getRows().stream().filter(SalesOrderU9SyncResultRow::isSuccess).count();
        int failedCount = resp.getRows().size() - successCount;
        resp.setTotalOrderCount(resp.getRows().size());
        resp.setSuccessOrderCount(successCount);
        resp.setFailedOrderCount(failedCount);
        resp.setSuccess(failedCount == 0 && resp.getTotalOrderCount() > 0);
        resp.setStatus(statusFor(successCount, failedCount));
        resp.setMessage(resp.isSuccess()
                ? "U9 同步成功"
                : firstNonBlank(resp.getRows().stream()
                        .filter(row -> !row.isSuccess())
                        .map(SalesOrderU9SyncResultRow::getErrorMessage)
                        .filter(message -> !isBlank(message))
                        .findFirst()
                        .orElse(null), "U9 同步失败"));
    }

    private String statusFor(int successCount, int failedCount) {
        if (failedCount == 0) {
            return RESP_SUCCESS;
        }
        if (successCount > 0) {
            return RESP_PARTIAL;
        }
        return RESP_FAILED;
    }

    private SalesOrderU9SyncResultRow successRow(SalesOrderHeader header, U9ResultDTO u9Row) {
        SalesOrderU9SyncResultRow row = new SalesOrderU9SyncResultRow();
        row.setSuccess(true);
        row.setStatus(RESP_SUCCESS);
        row.setSalesOrderId(header.getId());
        row.setSalesOrderNo(header.getSalesOrderNo());
        row.setU9OrderId(u9Row.getMID());
        row.setU9OrderCode(u9Row.getMCode());
        return row;
    }

    private SalesOrderU9SyncResultRow failedRow(SalesOrderHeader header, String message) {
        return failedRow(header.getId(), header.getSalesOrderNo(), message);
    }

    private SalesOrderU9SyncResultRow failedRow(Long salesOrderId, String salesOrderNo, String message) {
        SalesOrderU9SyncResultRow row = new SalesOrderU9SyncResultRow();
        row.setSuccess(false);
        row.setStatus(RESP_FAILED);
        row.setSalesOrderId(salesOrderId);
        row.setSalesOrderNo(salesOrderNo);
        row.setErrorMessage(safeError(message));
        return row;
    }

    private void audit(SalesOrderU9SyncResp resp, long startedAt) {
        if (auditService == null) {
            return;
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("status", resp.getStatus());
        summary.put("totalOrderCount", resp.getTotalOrderCount());
        summary.put("successOrderCount", resp.getSuccessOrderCount());
        summary.put("failedOrderCount", resp.getFailedOrderCount());
        summary.put("salesOrderNoSample", resp.getRows().stream()
                .map(SalesOrderU9SyncResultRow::getSalesOrderNo)
                .filter(value -> !isBlank(value))
                .limit(10)
                .toList());
        summary.put("u9OrderCodeSample", resp.getRows().stream()
                .map(SalesOrderU9SyncResultRow::getU9OrderCode)
                .filter(value -> !isBlank(value))
                .limit(10)
                .toList());
        summary.put("durationMs", System.currentTimeMillis() - startedAt);
        auditService.record(
                ACTION,
                TARGET_TYPE,
                resp.getRows().size() == 1 ? resp.getRows().get(0).getSalesOrderId() : null,
                null,
                summary,
                "MARKET",
                resp.isSuccess() ? "SUCCESS" : "FAILED",
                AuditService.labelForAction(ACTION),
                resp.isSuccess() ? null : resp.getMessage()
        );
    }

    private List<Long> normalizeIds(List<Long> rawIds) {
        if (rawIds == null) {
            return List.of();
        }
        return rawIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
    }

    private int normalizePage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 20;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private boolean alreadySynced(SalesOrderHeader header) {
        return STATUS_SYNCED.equals(header.getU9SyncStatus())
                && (!isBlank(header.getU9OrderId()) || !isBlank(header.getU9OrderCode()));
    }

    private void assertDataScope(SalesOrderHeader header) {
        if (dataScopeService != null) {
            dataScopeService.assertCanAccessSalesOrder(header);
        }
    }

    private String safeError(String value) {
        if (isBlank(value)) {
            return "U9 同步失败";
        }
        String text = value.replaceAll("\\s+", " ").trim();
        text = text.replaceAll("(?i)\\b(token|clientsecret|client[-_]?secret|authorization|cookie|password|secret)\\s*[:=]\\s*[^\\s,;]+", "[REDACTED]");
        text = text.replaceAll("(?i)SO(LineDTOList|ShiplineDTOList)\\s*=\\s*\\[[^]]*]", "request=[REDACTED]");
        text = text.replaceAll("(?i)\\[(\\s*\\{.*}\\s*)]", "request=[REDACTED]");
        return text.length() <= 500 ? text : text.substring(0, 500) + "...";
    }

    private RuntimeException sanitizedException(Throwable source, String message) {
        RuntimeException sanitized = new RuntimeException(message);
        if (source != null) {
            sanitized.setStackTrace(source.getStackTrace());
        }
        return sanitized;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private record PreparedOrder(SalesOrderHeader header, U9SalesOrderHeadDTO request, String requestFingerprint) {
    }

    private record MaterialStats(int total, int confirmed, int missingOrUnconfirmed) {
    }
}
