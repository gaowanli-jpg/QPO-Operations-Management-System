package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.SalesOrderU9AssignmentReq;
import com.machining.quote.dto.SalesOrderU9AssignmentResp;
import com.machining.quote.dto.SalesOrderU9DepartmentOptionDTO;
import com.machining.quote.dto.SalesOrderU9OrderDateUpdateReq;
import com.machining.quote.dto.SalesOrderU9OrderDateUpdateResp;
import com.machining.quote.dto.SalesOrderU9PriceMode;
import com.machining.quote.dto.SalesOrderU9ReconcileResp;
import com.machining.quote.dto.SalesOrderU9SellerOptionDTO;
import com.machining.quote.dto.SalesOrderU9SyncOrderRow;
import com.machining.quote.dto.SalesOrderU9SyncOptionsResp;
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
import com.machining.quote.service.SalesOrderU9SyncStateService.PreparedOrder;
import com.machining.quote.service.u9.U9CommonEntityClient;
import com.machining.quote.service.u9.U9SalesOrderClient;
import com.machining.quote.service.u9.U9SalesOrderContractValidator;
import com.machining.quote.service.u9.U9SalesOrderContractValidator.ValidationResult;
import com.machining.quote.service.u9.U9SalesOrderReconciliationService;
import com.machining.quote.service.u9.U9SalesOrderReconciliationService.Result;
import com.machining.quote.service.u9.U9SalesOrderReconciliationService.ResultCode;
import com.machining.quote.service.u9.U9SalesOrderRequestAssembler;
import com.machining.quote.service.u9.U9MasterDataService;
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
    private static final String ACTION_ASSIGNMENT = "SALES_ORDER_U9_ASSIGNMENT_UPDATE";
    private static final String ACTION_ORDER_DATE = "SALES_ORDER_U9_ORDER_DATE_UPDATE";
    private static final String ACTION_RECONCILE = "SALES_ORDER_U9_RECONCILE";
    private static final String TARGET_TYPE = "sales_order_header";
    private static final String STATUS_SYNCING = "同步中";
    private static final String STATUS_PENDING_CONFIRMATION = "结果待确认";
    private static final String STATUS_SYNCED = "已同步";
    private static final String STATUS_FAILED = "同步失败";
    private static final String MATERIAL_CONFIRMED = "已确认";
    private static final String RESP_SUCCESS = "SUCCESS";
    private static final String RESP_FAILED = "FAILED";
    private static final String RESP_PARTIAL = "PARTIAL_SUCCESS";
    private static final String SOURCE_RFQ_RELEASE = "RFQ_RELEASE";
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
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
    @Autowired
    private U9MasterDataService u9MasterDataService;
    @Autowired
    private SalesOrderU9SyncStateService syncStateService;
    @Autowired
    private U9SalesOrderReconciliationService reconciliationService;

    public PageResult<SalesOrderU9SyncOrderRow> listOrders(
            String customerName,
            String keyword,
            String status,
            String u9SyncStatus,
            String materialReadyStatus,
            Integer page,
            Integer pageSize) {
        return listOrders(null, customerName, keyword, status, u9SyncStatus, materialReadyStatus, page, pageSize);
    }

    public PageResult<SalesOrderU9SyncOrderRow> listOrders(
            String customerCode,
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
        applyListFilters(query, customerCode, customerName, keyword, status, u9SyncStatus, materialReadyStatus);
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
        return previewSalesOrder(salesOrderId, DEFAULT_PAGE, DEFAULT_PAGE_SIZE);
    }

    public SalesOrderU9SyncPreviewResp previewSalesOrder(Long salesOrderId, Integer page, Integer pageSize) {
        return previewSalesOrder(salesOrderId, page, pageSize, SalesOrderU9PriceMode.defaultMode(null));
    }

    public SalesOrderU9SyncPreviewResp previewSalesOrder(Long salesOrderId, Integer page, Integer pageSize, String priceModeCode) {
        return previewSalesOrder(salesOrderId, page, pageSize, SalesOrderU9PriceMode.fromCode(priceModeCode));
    }

    public SalesOrderU9SyncPreviewResp previewSalesOrder(
            Long salesOrderId,
            Integer page,
            Integer pageSize,
            SalesOrderU9PriceMode priceMode) {
        SalesOrderU9PriceMode selectedPriceMode = SalesOrderU9PriceMode.defaultMode(priceMode);
        int safePage = normalizePage(page);
        int safePageSize = normalizePageSize(pageSize);
        SalesOrderHeader header = requireSalesOrder(salesOrderId);
        List<SalesOrderItem> items = loadItems(header.getId());
        long total = countItems(header.getId());
        List<SalesOrderItem> pageItems = loadPagedItems(header.getId(), safePage, safePageSize);
        MaterialStats stats = materialStats(items);
        ValidationResult validation = contractValidator.validate(header, items, selectedPriceMode);
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
        resp.setSellerCode(header.getU9SellerCode());
        resp.setSellerName(header.getU9SellerName());
        resp.setSaleDepartmentCode(header.getU9SaleDepartmentCode());
        resp.setSaleDepartmentName(header.getU9SaleDepartmentName());
        resp.setU9SyncStatus(header.getU9SyncStatus());
        resp.setU9OrderCode(header.getU9OrderCode());
        resp.setU9SyncMessage(header.getU9SyncMessage());
        resp.setPriceMode(selectedPriceMode.name());
        resp.setPriceModeLabel(selectedPriceMode.priceFieldLabel());
        resp.setU9PriceField(selectedPriceMode.priceField());
        resp.setItemCount(stats.total());
        resp.setMaterialConfirmedItemCount(stats.confirmed());
        resp.setMissingMaterialItemCount(stats.missingOrUnconfirmed());
        resp.setTotal(total);
        resp.setPage(safePage);
        resp.setPageSize(safePageSize);
        resp.setBlockingErrors(validation.blockingErrors());
        resp.setWarnings(validation.warnings());
        resp.setBlockedReason(validation.firstBlockingError());
        resp.setSyncReady(!validation.hasBlockingErrors());
        if (!validation.hasBlockingErrors()) {
            resp.setRequestFingerprint(requestAssembler.fingerprint(requestAssembler.assemble(header, items, selectedPriceMode)));
        }
        List<SalesOrderU9SyncPreviewLine> records = pageItems.stream().map(item -> toPreviewLine(header, item, selectedPriceMode)).toList();
        resp.setRecords(records);
        resp.setLines(records);
        return resp;
    }

    @Transactional
    public SalesOrderU9AssignmentResp saveAssignment(Long salesOrderId, SalesOrderU9AssignmentReq req) {
        if (salesOrderId == null) {
            throw new RuntimeException("销售订单 ID 不能为空");
        }
        SalesOrderHeader header = headerMapper.selectByIdForUpdate(salesOrderId);
        if (header == null) {
            throw new RuntimeException("销售订单不存在");
        }
        assertDataScope(header);
        assertAssignmentEditable(header);

        AssignmentSnapshot before = assignmentSnapshot(header);
        AssignmentSnapshot after = resolveAssignment(req);
        LocalDateTime updatedAt = LocalDateTime.now();
        UpdateWrapper<SalesOrderHeader> update = new UpdateWrapper<>();
        update.eq("id", header.getId())
                .set("u9_seller_code", after.sellerCode())
                .set("u9_seller_name", after.sellerName())
                .set("u9_sale_department_code", after.saleDepartmentCode())
                .set("u9_sale_department_name", after.saleDepartmentName())
                .set("updated_at", updatedAt);
        int updated = headerMapper.update(null, update);
        if (updated <= 0) {
            throw new RuntimeException("销售订单销售员和部门保存失败，请刷新后重试");
        }

        applyAssignment(header, after, updatedAt);
        SalesOrderHeader saved = headerMapper.selectById(header.getId());
        if (saved == null) {
            saved = header;
        }
        auditAssignment(header, before, assignmentSnapshot(saved));
        return toAssignmentResp(saved);
    }

    @Transactional
    public SalesOrderU9OrderDateUpdateResp saveOrderDate(Long salesOrderId, SalesOrderU9OrderDateUpdateReq req) {
        if (salesOrderId == null) {
            throw new RuntimeException("销售订单 ID 不能为空");
        }
        SalesOrderHeader header = headerMapper.selectByIdForUpdate(salesOrderId);
        if (header == null) {
            throw new RuntimeException("销售订单不存在");
        }
        assertDataScope(header);
        assertOrderDateEditable(header);

        LocalDate before = header.getSalesOrderDate();
        LocalDate after = req == null ? null : req.getSalesOrderDate();
        if (after == null) {
            throw new RuntimeException("销售订单下单日期不能为空");
        }

        LocalDateTime updatedAt = LocalDateTime.now();
        UpdateWrapper<SalesOrderHeader> update = new UpdateWrapper<>();
        update.eq("id", header.getId())
                .set("sales_order_date", after)
                .set("updated_at", updatedAt);
        int updated = headerMapper.update(null, update);
        if (updated <= 0) {
            throw new RuntimeException("销售订单下单日期保存失败，请刷新后重试");
        }

        header.setSalesOrderDate(after);
        header.setUpdatedAt(updatedAt);
        SalesOrderHeader saved = headerMapper.selectById(header.getId());
        if (saved == null) {
            saved = header;
        }
        auditOrderDate(saved, before, saved.getSalesOrderDate());

        SalesOrderU9OrderDateUpdateResp resp = new SalesOrderU9OrderDateUpdateResp();
        resp.setSuccess(true);
        resp.setMessage("销售订单下单日期已保存");
        resp.setSalesOrderId(saved.getId());
        resp.setSalesOrderNo(saved.getSalesOrderNo());
        resp.setSalesOrderDate(saved.getSalesOrderDate());
        resp.setUpdatedAt(saved.getUpdatedAt());
        resp.setPreview(previewSalesOrder(saved.getId()));
        return resp;
    }

    public SalesOrderU9SyncResp syncSalesOrders(List<Long> salesOrderIds) {
        return syncSalesOrders(salesOrderIds, SalesOrderU9PriceMode.defaultMode(null));
    }

    public SalesOrderU9SyncResp syncSalesOrders(List<Long> salesOrderIds, String priceModeCode) {
        return syncSalesOrders(salesOrderIds, SalesOrderU9PriceMode.fromCode(priceModeCode));
    }

    public SalesOrderU9SyncResp syncSalesOrders(List<Long> salesOrderIds, SalesOrderU9PriceMode priceMode) {
        priceMode = SalesOrderU9PriceMode.defaultMode(priceMode);
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

        log.info("Sales order U9 sync started orderCount={} priceMode={}", ids.size(), priceMode.name());
        for (Long id : ids) {
            syncOneSalesOrder(id, resp, priceMode);
        }
        finalizeResponse(resp);
        audit(resp, startedAt, priceMode);
        log.info("Sales order U9 sync completed orderCount={} successOrderCount={} failedOrderCount={} priceMode={} durationMs={}",
                resp.getTotalOrderCount(), resp.getSuccessOrderCount(), resp.getFailedOrderCount(),
                priceMode.name(), System.currentTimeMillis() - startedAt);
        return resp;
    }

    public SalesOrderU9ReconcileResp reconcileSalesOrder(Long salesOrderId) {
        long startedAt = System.currentTimeMillis();
        PreparedOrder prepared = syncStateService.prepareForReconcile(salesOrderId);
        Result result = reconciliationService.reconcile(prepared);
        applyReconcileResult(prepared, result, false);
        auditReconcile(prepared, result, startedAt);
        return reconcileResp(prepared, result);
    }

    private void syncOneSalesOrder(Long salesOrderId, SalesOrderU9SyncResp resp, SalesOrderU9PriceMode priceMode) {
        PreparedOrder prepared;
        try {
            prepared = syncStateService.reserve(salesOrderId, priceMode);
        } catch (RuntimeException error) {
            resp.getRows().add(failedRow(salesOrderId, null, safeError(error.getMessage())));
            return;
        }

        Result existing = reconciliationService.checkExisting(prepared);
        if (ResultCode.NOT_FOUND_CONFIRMED.equals(existing.code())) {
            createPreparedOrder(prepared, resp);
            return;
        }
        if (existing.matched()) {
            syncStateService.markSuccess(prepared, existing.u9OrderId(), existing.u9OrderCode(), "创建前查重命中已存在 U9 订单");
            resp.getRows().add(successRow(prepared, existing.u9OrderId(), existing.u9OrderCode()));
            return;
        }

        if (ResultCode.QUERY_FAILED.equals(existing.code())) {
            String message = "创建前查重失败：" + existing.message();
            syncStateService.markFailed(prepared, message);
            resp.getRows().add(failedRow(prepared.header(), message));
            return;
        }
        String message = "结果待确认：创建前查重无法确认：" + existing.message();
        syncStateService.markPending(prepared, message);
        resp.getRows().add(failedRow(prepared.header(), message));
    }

    private void createPreparedOrder(PreparedOrder prepared, SalesOrderU9SyncResp resp) {
        try {
            U9ApiResult<U9ResultDTO> u9Result = u9SalesOrderClient.create(List.of(prepared.request()));
            if (u9Result == null || u9Result.getResCode() == null || u9Result.getResCode() != 0) {
                String message = safeError(firstNonBlank(
                        u9Result == null ? null : u9Result.getResMsg(),
                        u9Result == null ? null : u9Result.getException(),
                        "U9 sales order sync failed"
                ));
                syncStateService.markFailed(prepared, message);
                resp.getRows().add(failedRow(prepared.header(), message));
                return;
            }
            U9ResultDTO resultRow = safeList(u9Result.getData()).stream()
                    .filter(row -> prepared.request().getOtherID().equals(row.getMOtherID()))
                    .findFirst()
                    .orElse(null);
            if (resultRow != null && Boolean.TRUE.equals(resultRow.getMIsSucess())) {
                syncStateService.markSuccess(prepared, resultRow.getMID(), resultRow.getMCode(), "U9 同步成功");
                resp.getRows().add(successRow(prepared, resultRow.getMID(), resultRow.getMCode()));
                return;
            }
            if (resultRow != null && Boolean.FALSE.equals(resultRow.getMIsSucess())) {
                String message = safeError(firstNonBlank(resultRow.getMErrorMsg(), "U9 明确拒绝创建销售订单"));
                syncStateService.markFailed(prepared, message);
                resp.getRows().add(failedRow(prepared.header(), message));
                return;
            }
            reconcileUnclearCreateResult(prepared, resp, "U9 创建返回缺少当前订单结果");
        } catch (RuntimeException error) {
            String message = safeError(error.getMessage());
            reconcileUnclearCreateResult(prepared, resp, "U9 创建结果不明", message, error);
        }
    }

    private void reconcileUnclearCreateResult(PreparedOrder prepared, SalesOrderU9SyncResp resp, String reason) {
        reconcileUnclearCreateResult(prepared, resp, reason, null, null);
    }

    private void reconcileUnclearCreateResult(
            PreparedOrder prepared,
            SalesOrderU9SyncResp resp,
            String reason,
            String errorMessage,
            RuntimeException error) {
        Result result = reconciliationService.reconcile(prepared);
        if (result.matched()) {
            syncStateService.markSuccess(prepared, result.u9OrderId(), result.u9OrderCode(), reason + "，核对后确认已同步");
            resp.getRows().add(successRow(prepared, result.u9OrderId(), result.u9OrderCode()));
            return;
        }
        String message = "结果待确认：" + reason + "，核对仍无法确认：" + result.message();
        syncStateService.markPending(prepared, message);
        resp.getRows().add(failedRow(prepared.header(), message));
        if (error != null) {
            log.error("Sales order U9 create result unclear salesOrderId={} reason={}",
                    prepared.header().getId(), safeError(firstNonBlank(errorMessage, message)),
                    sanitizedException(error, safeError(firstNonBlank(errorMessage, message))));
        }
    }

    private void applyReconcileResult(PreparedOrder prepared, Result result, boolean afterCreate) {
        if (result.matched()) {
            String message = afterCreate ? "U9 创建结果不明，核对后确认已同步" : "核对后确认 U9 已同步";
            syncStateService.markSuccess(prepared, result.u9OrderId(), result.u9OrderCode(), message);
        } else if (ResultCode.NOT_FOUND_CONFIRMED.equals(result.code())) {
            syncStateService.markFailed(prepared, result.message());
        } else {
            syncStateService.markPending(prepared, result.message());
        }
    }

    private SalesOrderU9ReconcileResp reconcileResp(PreparedOrder prepared, Result result) {
        SalesOrderU9ReconcileResp resp = new SalesOrderU9ReconcileResp();
        resp.setSalesOrderId(prepared.header().getId());
        resp.setSalesOrderNo(prepared.header().getSalesOrderNo());
        resp.setCode(result.code().name());
        resp.setMessage(result.message());
        resp.setU9SyncStatus(statusForReconcileResult(result));
        resp.setU9OrderId(result.matched() ? result.u9OrderId() : prepared.header().getU9OrderId());
        resp.setU9OrderCode(result.matched() ? result.u9OrderCode() : prepared.header().getU9OrderCode());
        resp.setWarnings(result.warnings());
        return resp;
    }

    private String statusForReconcileResult(Result result) {
        if (result.matched()) {
            return STATUS_SYNCED;
        }
        if (ResultCode.NOT_FOUND_CONFIRMED.equals(result.code())) {
            return STATUS_FAILED;
        }
        return STATUS_PENDING_CONFIRMATION;
    }

    private List<SalesOrderItem> loadItems(Long salesOrderId) {
        QueryWrapper<SalesOrderItem> query = new QueryWrapper<>();
        query.eq("sales_order_id", salesOrderId)
                .and(wrapper -> wrapper.eq("u9_change_excluded", false).or().isNull("u9_change_excluded"))
                .orderByAsc("line_no")
                .orderByAsc("id");
        List<SalesOrderItem> items = itemMapper.selectList(query);
        return items == null ? List.of() : items;
    }

    private long countItems(Long salesOrderId) {
        QueryWrapper<SalesOrderItem> query = new QueryWrapper<>();
        query.eq("sales_order_id", salesOrderId)
                .and(wrapper -> wrapper.eq("u9_change_excluded", false).or().isNull("u9_change_excluded"));
        Long total = itemMapper.selectCount(query);
        return total == null ? 0L : total;
    }

    private List<SalesOrderItem> loadPagedItems(Long salesOrderId, int page, int pageSize) {
        QueryWrapper<SalesOrderItem> query = new QueryWrapper<>();
        query.eq("sales_order_id", salesOrderId)
                .and(wrapper -> wrapper.eq("u9_change_excluded", false).or().isNull("u9_change_excluded"))
                .orderByAsc("line_no")
                .orderByAsc("id")
                .last("LIMIT " + pageSize + " OFFSET " + ((page - 1L) * pageSize));
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

    private void assertAssignmentEditable(SalesOrderHeader header) {
        assertPreSyncEditable(header, "销售员和销售部门");
    }

    private void assertOrderDateEditable(SalesOrderHeader header) {
        assertPreSyncEditable(header, "下单日期");
    }

    private void assertPreSyncEditable(SalesOrderHeader header, String targetLabel) {
        String status = trimToNull(header.getU9SyncStatus());
        if (STATUS_SYNCING.equals(status) || STATUS_PENDING_CONFIRMATION.equals(status) || STATUS_SYNCED.equals(status)) {
            throw new RuntimeException("U9 状态为" + status + "，不能修改" + targetLabel);
        }
    }

    private AssignmentSnapshot resolveAssignment(SalesOrderU9AssignmentReq req) {
        SalesOrderU9AssignmentReq safeReq = req == null ? new SalesOrderU9AssignmentReq() : req;
        String sellerCode = trimToNull(safeReq.getSellerCode());
        String departmentCode = trimToNull(safeReq.getSaleDepartmentCode());
        if (sellerCode == null && departmentCode == null) {
            return new AssignmentSnapshot(null, null, null, null);
        }

        SalesOrderU9SyncOptionsResp options = u9MasterDataService.options();
        Map<String, SalesOrderU9SellerOptionDTO> sellers = safeList(options == null ? null : options.getSellers()).stream()
                .filter(seller -> trimToNull(seller.getCode()) != null)
                .collect(Collectors.toMap(
                        seller -> trimToNull(seller.getCode()),
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new));
        Map<String, SalesOrderU9DepartmentOptionDTO> departments = safeList(options == null ? null : options.getDepartments()).stream()
                .filter(department -> trimToNull(department.getCode()) != null)
                .collect(Collectors.toMap(
                        department -> trimToNull(department.getCode()),
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new));

        String sellerName = null;
        if (sellerCode != null) {
            SalesOrderU9SellerOptionDTO seller = sellers.get(sellerCode);
            if (seller == null) {
                throw new RuntimeException("U9 销售员编码不存在或不可用：" + sellerCode);
            }
            sellerName = trimToNull(seller.getName());
        }

        String departmentName = null;
        if (departmentCode != null) {
            SalesOrderU9DepartmentOptionDTO department = departments.get(departmentCode);
            if (department == null) {
                throw new RuntimeException("U9 销售部门编码不存在或不可用：" + departmentCode);
            }
            departmentName = trimToNull(department.getName());
        }
        return new AssignmentSnapshot(sellerCode, sellerName, departmentCode, departmentName);
    }

    private AssignmentSnapshot assignmentSnapshot(SalesOrderHeader header) {
        if (header == null) {
            return new AssignmentSnapshot(null, null, null, null);
        }
        return new AssignmentSnapshot(
                trimToNull(header.getU9SellerCode()),
                trimToNull(header.getU9SellerName()),
                trimToNull(header.getU9SaleDepartmentCode()),
                trimToNull(header.getU9SaleDepartmentName())
        );
    }

    private void applyAssignment(SalesOrderHeader header, AssignmentSnapshot assignment, LocalDateTime updatedAt) {
        header.setU9SellerCode(assignment.sellerCode());
        header.setU9SellerName(assignment.sellerName());
        header.setU9SaleDepartmentCode(assignment.saleDepartmentCode());
        header.setU9SaleDepartmentName(assignment.saleDepartmentName());
        header.setUpdatedAt(updatedAt);
    }

    private SalesOrderU9AssignmentResp toAssignmentResp(SalesOrderHeader header) {
        SalesOrderU9AssignmentResp resp = new SalesOrderU9AssignmentResp();
        resp.setSalesOrderId(header.getId());
        resp.setSalesOrderNo(header.getSalesOrderNo());
        resp.setSellerCode(header.getU9SellerCode());
        resp.setSellerName(header.getU9SellerName());
        resp.setSaleDepartmentCode(header.getU9SaleDepartmentCode());
        resp.setSaleDepartmentName(header.getU9SaleDepartmentName());
        resp.setUpdatedAt(header.getUpdatedAt());
        return resp;
    }

    private SalesOrderU9SyncOrderRow toOrderRow(SalesOrderHeader header) {
        List<SalesOrderItem> items = loadItems(header.getId());
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
        row.setMissingMaterialItemCount(stats.missingOrUnconfirmed());
        String blockedReason = syncBlockedReason(header, items, stats);
        row.setBlockedReason(blockedReason);
        row.setSyncReady(isBlank(blockedReason));
        row.setUpdatedAt(header.getUpdatedAt());
        return row;
    }

    private SalesOrderU9SyncPreviewLine toPreviewLine(SalesOrderHeader header, SalesOrderItem item, SalesOrderU9PriceMode priceMode) {
        SalesOrderU9SyncPreviewLine line = new SalesOrderU9SyncPreviewLine();
        line.setSalesOrderItemId(item.getId());
        line.setLineNo(item.getLineNo());
        line.setPartNo(item.getPartNo());
        line.setVersion(item.getVersion());
        line.setSalesOrderDate(header.getSalesOrderDate());
        line.setCustomerOrderTitle(header.getCustomerOrderTitle());
        line.setCustomerOrderNo(item.getCustomerOrderNo());
        line.setCustomerProjectNo(item.getCustomerProjectNo());
        line.setInternalProjectNo(item.getInternalProjectNo());
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
        line.setTaxIncludedUnitPrice(item.getTaxIncludedUnitPrice());
        line.setTaxIncludedTotalPrice(item.getTaxIncludedTotalPrice());
        line.setUntaxedUnitPrice(item.getUntaxedUnitPrice());
        line.setUntaxedTotalPrice(item.getUntaxedTotalPrice());
        line.setDeliveryDate(preferredDeliveryDate(item));
        line.setRemark(item.getRemark());
        ValidationResult validation = contractValidator.validateItem(item, priceMode);
        line.setBlockingErrors(validation.blockingErrors());
        line.setWarnings(validation.warnings());
        line.setReady(!validation.hasBlockingErrors());
        line.setBlockedReason(validation.firstBlockingError());
        return line;
    }

    private void applyListFilters(
            QueryWrapper<SalesOrderHeader> query,
            String customerCode,
            String customerName,
            String keyword,
            String status,
            String u9SyncStatus,
            String materialReadyStatus) {
        if (!isBlank(customerCode)) {
            query.eq("customer_code", customerCode.trim());
        } else if (!isBlank(customerName)) {
            query.eq("customer_name", customerName.trim());
        }
        if (!isBlank(status)) {
            query.eq("status", status.trim());
        } else {
            query.and(wrapper -> wrapper.ne("status", QuoteStatusWorkflow.DISCARDED).or().isNull("status"));
        }
        if (!isBlank(u9SyncStatus)) {
            query.eq("u9_sync_status", u9SyncStatus.trim());
        } else {
            query.and(wrapper -> wrapper.isNull("u9_sync_status").or().ne("u9_sync_status", STATUS_SYNCED));
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
                    EXISTS (SELECT 1 FROM sales_order_item soi WHERE soi.sales_order_id = sales_order_header.id AND COALESCE(soi.u9_change_excluded, false) = false)
                    AND NOT EXISTS (
                        SELECT 1 FROM sales_order_item soi
                        WHERE soi.sales_order_id = sales_order_header.id
                        AND COALESCE(soi.u9_change_excluded, false) = false
                        AND (soi.material_no IS NULL OR TRIM(soi.material_no) = '' OR COALESCE(soi.material_no_status, '') <> '已确认')
                    )
                    """);
        } else if ("BLOCKED".equals(normalized) || "MISSING".equals(normalized)
                || "未就绪".equals(materialReadyStatus.trim()) || "缺物料号".equals(materialReadyStatus.trim())) {
            query.apply("""
                    EXISTS (
                        SELECT 1 FROM sales_order_item soi
                        WHERE soi.sales_order_id = sales_order_header.id
                        AND COALESCE(soi.u9_change_excluded, false) = false
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

    private SalesOrderU9SyncResultRow successRow(PreparedOrder prepared, String u9OrderId, String u9OrderCode) {
        SalesOrderU9SyncResultRow row = new SalesOrderU9SyncResultRow();
        row.setSuccess(true);
        row.setStatus(RESP_SUCCESS);
        row.setSalesOrderId(prepared.header().getId());
        row.setSalesOrderNo(prepared.header().getSalesOrderNo());
        row.setU9OrderId(u9OrderId);
        row.setU9OrderCode(u9OrderCode);
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

    private void audit(SalesOrderU9SyncResp resp, long startedAt, SalesOrderU9PriceMode priceMode) {
        if (auditService == null) {
            return;
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("status", resp.getStatus());
        summary.put("priceMode", priceMode.name());
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

    private void auditAssignment(SalesOrderHeader header, AssignmentSnapshot before, AssignmentSnapshot after) {
        if (auditService == null) {
            return;
        }
        auditService.record(
                ACTION_ASSIGNMENT,
                TARGET_TYPE,
                header.getId(),
                assignmentAuditData(header, before),
                assignmentAuditData(header, after),
                "MARKET",
                "SUCCESS",
                AuditService.labelForAction(ACTION_ASSIGNMENT),
                null
        );
    }

    private void auditOrderDate(SalesOrderHeader header, LocalDate before, LocalDate after) {
        if (auditService == null) {
            return;
        }
        auditService.record(
                ACTION_ORDER_DATE,
                TARGET_TYPE,
                header.getId(),
                orderDateAuditData(header, before),
                orderDateAuditData(header, after),
                "MARKET",
                "SUCCESS",
                AuditService.labelForAction(ACTION_ORDER_DATE),
                null
        );
    }

    private void auditReconcile(PreparedOrder prepared, Result result, long startedAt) {
        if (auditService == null) {
            return;
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("salesOrderId", prepared.header().getId());
        summary.put("salesOrderNo", prepared.header().getSalesOrderNo());
        summary.put("attemptId", trimToNull(prepared.attemptId()));
        summary.put("resultCode", result.code().name());
        summary.put("u9SyncStatus", statusForReconcileResult(result));
        summary.put("u9OrderId", result.matched() ? result.u9OrderId() : prepared.header().getU9OrderId());
        summary.put("u9OrderCode", result.matched() ? result.u9OrderCode() : prepared.header().getU9OrderCode());
        summary.put("message", safeError(result.message()));
        summary.put("durationMs", System.currentTimeMillis() - startedAt);
        auditService.record(
                ACTION_RECONCILE,
                TARGET_TYPE,
                prepared.header().getId(),
                null,
                summary,
                "MARKET",
                result.matched() ? "SUCCESS" : "FAILED",
                AuditService.labelForAction(ACTION_RECONCILE),
                result.matched() ? null : safeError(result.message())
        );
    }

    private Map<String, Object> assignmentAuditData(SalesOrderHeader header, AssignmentSnapshot snapshot) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("salesOrderId", header.getId());
        data.put("salesOrderNo", header.getSalesOrderNo());
        data.put("sellerCode", snapshot.sellerCode());
        data.put("sellerName", snapshot.sellerName());
        data.put("saleDepartmentCode", snapshot.saleDepartmentCode());
        data.put("saleDepartmentName", snapshot.saleDepartmentName());
        return data;
    }

    private Map<String, Object> orderDateAuditData(SalesOrderHeader header, LocalDate salesOrderDate) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("salesOrderId", header.getId());
        data.put("salesOrderNo", header.getSalesOrderNo());
        data.put("salesOrderDate", salesOrderDate);
        return data;
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
            return DEFAULT_PAGE_SIZE;
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
        String text = U9CommonEntityClient.sanitize(value).replaceAll("\\s+", " ").trim();
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

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private record MaterialStats(int total, int confirmed, int missingOrUnconfirmed) {
    }

    private record AssignmentSnapshot(
            String sellerCode,
            String sellerName,
            String saleDepartmentCode,
            String saleDepartmentName) {
    }
}
