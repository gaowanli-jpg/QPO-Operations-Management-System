package com.machining.quote.service;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.machining.quote.config.AuthContext;
import com.machining.quote.config.PreviewFingerprintConflictException;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.CustomerOptionDTO;
import com.machining.quote.dto.OrderItemDataReplacementExcelErrorDTO;
import com.machining.quote.dto.OrderItemDataReplacementExcelParseResult;
import com.machining.quote.dto.OrderItemDataReplacementExcelRowDTO;
import com.machining.quote.dto.OrderItemDataReplacementField;
import com.machining.quote.dto.OrderItemMaintenanceLineReq;
import com.machining.quote.dto.OrderItemMaintenanceUpdateReq;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.ProcessSheetDTO;
import com.machining.quote.dto.RfqExcelDTO;
import com.machining.quote.entity.ItemQuoteVersion;
import com.machining.quote.entity.OrderDeliveryTicket;
import com.machining.quote.entity.OrderRfq;
import com.machining.quote.mapper.ItemQuoteVersionMapper;
import com.machining.quote.mapper.OrderDeliveryTicketMapper;
import com.machining.quote.mapper.OrderRfqMapper;
import com.machining.quote.util.DrawingPartNoResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OrderManagementService {
    private static final Logger log = LoggerFactory.getLogger(OrderManagementService.class);

    @Autowired
    private OrderRfqMapper orderMapper;
    @Autowired
    private ItemQuoteVersionMapper itemMapper;
    @Autowired
    private OrderDeliveryTicketMapper ticketMapper;
    @Autowired
    private OrderFilePathService orderFilePathService;
    @Autowired
    private BusinessFileService businessFileService;
    @Autowired
    private AuditService auditService;
    @Autowired
    private DeliveryService deliveryService;
    @Autowired
    private LeadTimeService leadTimeService;
    @Autowired
    private OrderItemDataReplacementExcelParser orderItemDataReplacementExcelParser;
    @Autowired(required = false)
    private DataScopeService dataScopeService;
    @Autowired(required = false)
    private CustomerMasterService customerMasterService;

    public List<Map<String, Object>> listOrderSummaries(String customerName) {
        QueryWrapper<OrderRfq> qw = new QueryWrapper<>();
        applyOrderDataScope(qw);
        if (!isBlank(customerName)) {
            qw.eq("customer_name", customerName.trim());
        }
        qw.orderByDesc("id");
        return buildSummaries(orderMapper.selectList(qw));
    }

    public PageResult<Map<String, Object>> listOrderSummaries(
            String customerName,
            String keyword,
            String status,
            Integer page,
            Integer pageSize) {
        return listOrderSummaries(null, customerName, keyword, status, page, pageSize);
    }

    public PageResult<Map<String, Object>> listOrderSummaries(
            String customerCode,
            String customerName,
            String keyword,
            String status,
            Integer page,
            Integer pageSize) {
        int safePage = normalizePage(page);
        int safePageSize = normalizePageSize(pageSize);
        QueryWrapper<OrderRfq> qw = new QueryWrapper<>();
        applyOrderDataScope(qw);
        applyOrderFilters(qw, customerCode, customerName, keyword, status);
        Long total = orderMapper.selectCount(qw);
        if (total == null || total == 0) {
            return PageResult.of(List.of(), 0, safePage, safePageSize);
        }
        qw.orderByDesc("id");
        qw.last("LIMIT " + safePageSize + " OFFSET " + ((safePage - 1L) * safePageSize));
        List<OrderRfq> orders = orderMapper.selectList(qw);
        return PageResult.of(buildSummaries(orders), total, safePage, safePageSize);
    }

    public PageResult<OrderRfq> listOrders(String customerName, String keyword, String status, Integer page, Integer pageSize) {
        return listOrders(null, customerName, keyword, status, page, pageSize);
    }

    public PageResult<OrderRfq> listOrders(String customerCode, String customerName, String keyword, String status, Integer page, Integer pageSize) {
        int safePage = normalizePage(page);
        int safePageSize = normalizePageSize(pageSize);
        QueryWrapper<OrderRfq> qw = new QueryWrapper<>();
        applyOrderDataScope(qw);
        applyOrderFilters(qw, customerCode, customerName, keyword, status);
        Long total = orderMapper.selectCount(qw);
        if (total == null || total == 0) {
            return PageResult.of(List.of(), 0, safePage, safePageSize);
        }
        qw.orderByDesc("id");
        qw.last("LIMIT " + safePageSize + " OFFSET " + ((safePage - 1L) * safePageSize));
        return PageResult.of(orderMapper.selectList(qw), total, safePage, safePageSize);
    }

    public PageResult<OrderRfq> listQuoteProcessCandidateOrders(String customerName, String keyword, String status, Integer page, Integer pageSize) {
        return listQuoteProcessCandidateOrders(null, customerName, keyword, status, page, pageSize);
    }

    public PageResult<OrderRfq> listQuoteProcessCandidateOrders(String customerCode, String customerName, String keyword, String status, Integer page, Integer pageSize) {
        int safePage = normalizePage(page);
        int safePageSize = normalizePageSize(pageSize);
        String normalizedStatus = isBlank(status) ? null : status.trim();
        if (normalizedStatus != null && !QuoteStatusWorkflow.canReadQuoteProcess(normalizedStatus)) {
            return PageResult.of(List.of(), 0, safePage, safePageSize);
        }

        QueryWrapper<OrderRfq> qw = new QueryWrapper<>();
        applyOrderDataScope(qw);
        applyOrderFilters(qw, customerCode, customerName, keyword, null);
        if (normalizedStatus == null) {
            qw.in("status", QuoteStatusWorkflow.PRICED, QuoteStatusWorkflow.QUOTED);
        } else {
            qw.eq("status", normalizedStatus);
        }
        Long total = orderMapper.selectCount(qw);
        if (total == null || total == 0) {
            return PageResult.of(List.of(), 0, safePage, safePageSize);
        }
        qw.orderByDesc("id");
        qw.last("LIMIT " + safePageSize + " OFFSET " + ((safePage - 1L) * safePageSize));
        return PageResult.of(orderMapper.selectList(qw), total, safePage, safePageSize);
    }

    public PageResult<OrderRfq> listQuoteProcessSupplementalCandidateOrders(
            String customerCode,
            String customerName,
            String keyword,
            String status,
            Integer page,
            Integer pageSize) {
        int safePage = normalizePage(page);
        int safePageSize = normalizePageSize(pageSize);
        String normalizedStatus = isBlank(status) ? null : status.trim();
        if (normalizedStatus != null && !QuoteStatusWorkflow.isSupplementalQuoteOrderStatus(normalizedStatus)) {
            return PageResult.of(List.of(), 0, safePage, safePageSize);
        }

        QueryWrapper<OrderRfq> qw = new QueryWrapper<>();
        applyOrderDataScope(qw);
        applyOrderFilters(qw, customerCode, customerName, keyword, null);
        if (normalizedStatus == null) {
            qw.in("status", QuoteStatusWorkflow.QUOTED, QuoteStatusWorkflow.PARTIALLY_RELEASED, QuoteStatusWorkflow.RELEASED);
        } else {
            qw.eq("status", normalizedStatus);
        }
        qw.apply("EXISTS (SELECT 1 FROM item_quote_version item "
                + "WHERE item.rfq_id = order_rfq.id "
                + "AND NULLIF(BTRIM(item.drawing_url), '') IS NOT NULL "
                + "AND item.tax_included_unit_price IS NOT NULL "
                + "AND item.status = {0})", QuoteStatusWorkflow.PRICED);

        Long total = orderMapper.selectCount(qw);
        if (total == null || total == 0) {
            return PageResult.of(List.of(), 0, safePage, safePageSize);
        }
        qw.orderByDesc("id");
        qw.last("LIMIT " + safePageSize + " OFFSET " + ((safePage - 1L) * safePageSize));
        return PageResult.of(orderMapper.selectList(qw), total, safePage, safePageSize);
    }

    public PageResult<OrderRfq> listReleaseCandidateOrders(String customerName, String keyword, String status, Integer page, Integer pageSize) {
        return listReleaseCandidateOrders(null, customerName, keyword, status, page, pageSize);
    }

    public PageResult<OrderRfq> listReleaseCandidateOrders(String customerCode, String customerName, String keyword, String status, Integer page, Integer pageSize) {
        int safePage = normalizePage(page);
        int safePageSize = normalizePageSize(pageSize);
        String normalizedStatus = isBlank(status) ? null : status.trim();
        if (normalizedStatus != null && !QuoteStatusWorkflow.canGenerateDeliveryTicket(normalizedStatus)) {
            return PageResult.of(List.of(), 0, safePage, safePageSize);
        }

        QueryWrapper<OrderRfq> qw = new QueryWrapper<>();
        applyOrderDataScope(qw);
        applyOrderFilters(qw, customerCode, customerName, keyword, null);
        if (normalizedStatus == null) {
            qw.in("status", QuoteStatusWorkflow.QUOTED, QuoteStatusWorkflow.PARTIALLY_RELEASED, QuoteStatusWorkflow.RELEASED);
        } else {
            qw.eq("status", normalizedStatus);
        }
        Long total = orderMapper.selectCount(qw);
        if (total == null || total == 0) {
            return PageResult.of(List.of(), 0, safePage, safePageSize);
        }
        qw.orderByDesc("id");
        qw.last("LIMIT " + safePageSize + " OFFSET " + ((safePage - 1L) * safePageSize));
        return PageResult.of(orderMapper.selectList(qw), total, safePage, safePageSize);
    }

    public PageResult<Map<String, Object>> listReleaseOrderSources(
            String customerName,
            String keyword,
            String status,
            Integer page,
            Integer pageSize) {
        return listReleaseOrderSources(null, customerName, keyword, status, page, pageSize);
    }

    public PageResult<Map<String, Object>> listReleaseOrderSources(
            String customerCode,
            String customerName,
            String keyword,
            String status,
            Integer page,
            Integer pageSize) {
        int safePage = normalizePage(page);
        int safePageSize = normalizePageSize(pageSize);
        PageResult<OrderRfq> rfqPage = listReleaseCandidateOrders(customerCode, customerName, keyword, status, safePage, safePageSize);
        List<Map<String, Object>> rows = rfqPage.getRecords().stream()
                .map(this::buildReleaseRfqSourceRow)
                .toList();
        return PageResult.of(rows, rfqPage.getTotal(), safePage, safePageSize);
    }

    public List<String> listCustomers() {
        if (customerMasterService != null) {
            return customerMasterService.listCustomerNamesForCurrentScope();
        }
        QueryWrapper<OrderRfq> qw = new QueryWrapper<>();
        applyOrderDataScope(qw);
        qw.select("DISTINCT customer_name");
        qw.isNotNull("customer_name");
        qw.ne("customer_name", "");
        qw.orderByAsc("customer_name");
        return orderMapper.selectList(qw).stream()
                .map(OrderRfq::getCustomerName)
                .filter(customerName -> !isBlank(customerName))
                .toList();
    }

    public List<CustomerOptionDTO> listCustomerOptions(String field, String keyword) {
        if (customerMasterService != null) {
            return customerMasterService.listCustomerOptions(field, keyword);
        }
        return listCustomers().stream()
                .filter(customerName -> matchesCustomerKeyword(customerName, field, keyword))
                .map(this::toCustomerOption)
                .toList();
    }

    public PageResult<CustomerOptionDTO> pageCustomerOptions(String field, String keyword, Integer page, Integer pageSize) {
        if (customerMasterService != null) {
            return customerMasterService.pageCustomerOptions(field, keyword, page, pageSize);
        }
        List<CustomerOptionDTO> rows = listCustomers().stream()
                .filter(customerName -> matchesCustomerKeyword(customerName, field, keyword))
                .map(this::toCustomerOption)
                .toList();
        return pageRows(rows, page, pageSize);
    }

    public Map<String, Object> getOrderDetail(Integer rfqId) {
        OrderRfq order = requireOrder(rfqId);
        Map<String, Object> detail = new HashMap<>();
        detail.put("summary", buildSummary(order));
        detail.put("orderInfo", order);
        detail.put("fileRoot", buildOrderFileRoot(order));
        detail.put("drawingSummary", getDrawingSummary(rfqId));
        return detail;
    }

    public PageResult<ItemQuoteVersion> pageOrderItems(Integer rfqId, Integer page, Integer pageSize) {
        requireOrder(rfqId);
        int safePage = normalizePage(page);
        int safePageSize = pageSize == null || pageSize < 1 ? 50 : Math.min(pageSize, 100);
        long offset = (long) (safePage - 1) * safePageSize;
        QueryWrapper<ItemQuoteVersion> countQuery = new QueryWrapper<>();
        countQuery.eq("rfq_id", rfqId);
        Long total = itemMapper.selectCount(countQuery);
        if (total == null || total == 0) {
            return PageResult.of(List.of(), 0, safePage, safePageSize);
        }
        QueryWrapper<ItemQuoteVersion> pageQuery = new QueryWrapper<>();
        pageQuery.eq("rfq_id", rfqId)
                .orderByAsc("id")
                .last("LIMIT " + safePageSize + " OFFSET " + offset);
        return PageResult.of(itemMapper.selectList(pageQuery), total, safePage, safePageSize);
    }

    public PageResult<Map<String, Object>> pageOrderItemRows(Integer rfqId, Integer page, Integer pageSize) {
        requireOrder(rfqId);
        int safePage = normalizePage(page);
        int safePageSize = pageSize == null || pageSize < 1 ? 50 : Math.min(pageSize, 100);
        long offset = (long) (safePage - 1) * safePageSize;
        QueryWrapper<ItemQuoteVersion> countQuery = new QueryWrapper<>();
        countQuery.eq("rfq_id", rfqId);
        Long total = itemMapper.selectCount(countQuery);
        if (total == null || total == 0) {
            return PageResult.of(List.of(), 0, safePage, safePageSize);
        }
        QueryWrapper<ItemQuoteVersion> pageQuery = new QueryWrapper<>();
        pageQuery.eq("rfq_id", rfqId)
                .orderByAsc("id")
                .last("LIMIT " + safePageSize + " OFFSET " + offset);
        List<ItemQuoteVersion> items = safeList(itemMapper.selectList(pageQuery));
        Map<Integer, Integer> ticketedQtyByItemId = ticketedQuantities(rfqId, items);
        List<Map<String, Object>> rows = items.stream()
                .map(item -> buildOrderItemRow(item, ticketedQtyByItemId.getOrDefault(item.getId(), 0)))
                .toList();
        return PageResult.of(rows, total, safePage, safePageSize);
    }

    @Transactional
    public Map<String, Object> saveOrderItems(Integer rfqId, OrderItemMaintenanceUpdateReq req) {
        long startedAt = System.currentTimeMillis();
        OrderRfq order = requireOrderForUpdate(rfqId);
        List<OrderItemMaintenanceLineReq> lines = normalizeMaintenanceLines(req);
        assertNoDuplicateItemIds(lines);

        List<Integer> itemIds = lines.stream()
                .map(OrderItemMaintenanceLineReq::getItemId)
                .filter(Objects::nonNull)
                .toList();
        Map<Integer, Integer> ticketedQtyByItemId = ticketedQuantitiesForItemIds(rfqId, itemIds);
        int updatedCount = 0;
        Set<String> changedFields = new LinkedHashSet<>();
        List<Map<String, Object>> affectedRows = new ArrayList<>();
        for (OrderItemMaintenanceLineReq line : lines) {
            ItemQuoteVersion item = lockOrderItem(order, line.getItemId());
            int ticketedQty = ticketedQtyByItemId.getOrDefault(item.getId(), 0);
            Map<String, Object> before = buildOrderItemRow(item, ticketedQty);
            Set<String> rowChangedFields = applyMaintenanceLine(item, line, ticketedQty);
            if (rowChangedFields.isEmpty()) {
                continue;
            }
            recalculateItemTotals(item);
            if (itemMapper.updateById(item) <= 0) {
                throw new RuntimeException("询价订单零件信息保存失败，请刷新后重试");
            }
            changedFields.addAll(rowChangedFields);
            updatedCount++;
            affectedRows.add(buildOrderItemRow(item, ticketedQty));
            log.info("Order item updated rfqId={} itemId={} changedFields={}", rfqId, item.getId(), rowChangedFields);
            log.debug("Order item before update rfqId={} itemId={} before={}", rfqId, item.getId(), before);
        }

        if (updatedCount > 0) {
            Map<String, Object> auditSummary = new LinkedHashMap<>();
            auditSummary.put("rfqId", rfqId);
            auditSummary.put("rfqNo", order.getRfqNo());
            auditSummary.put("requestedCount", lines.size());
            auditSummary.put("updatedCount", updatedCount);
            auditSummary.put("changedFields", new ArrayList<>(changedFields));
            auditSummary.put("durationMs", System.currentTimeMillis() - startedAt);
            auditOrderSuccess(
                    "ORDER_ITEM_UPDATE",
                    order,
                    auditSummary);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "已保存 " + updatedCount + " 行询价订单零件信息。");
        result.put("rfqId", rfqId);
        result.put("updatedCount", updatedCount);
        result.put("changedFields", new ArrayList<>(changedFields));
        result.put("affectedRows", affectedRows);
        return result;
    }

    @Transactional
    public Map<String, Object> discardOrderItem(Integer rfqId, Integer itemId) {
        OrderRfq order = requireOrderForUpdate(rfqId);
        ItemQuoteVersion item = lockOrderItem(order, itemId);
        if (QuoteStatusWorkflow.DISCARDED.equals(item.getStatus())) {
            throw new RuntimeException("该询价订单零件已废弃");
        }

        int ticketedQty = ticketedQuantitiesForItemIds(rfqId, List.of(item.getId()))
                .getOrDefault(item.getId(), 0);
        String statusBefore = item.getStatus();
        item.setPreviousStatusBeforeDiscard(statusBefore);
        item.setStatus(QuoteStatusWorkflow.DISCARDED);
        if (itemMapper.updateById(item) <= 0) {
            throw new RuntimeException("询价订单零件废弃失败，请刷新后重试");
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("rfqId", rfqId);
        summary.put("rfqNo", order.getRfqNo());
        summary.put("itemId", item.getId());
        summary.put("partNo", item.getPartNo());
        summary.put("version", item.getVersion());
        summary.put("statusBefore", statusBefore);
        summary.put("statusAfter", item.getStatus());
        summary.put("ticketedQty", ticketedQty);
        auditOrderSuccess("ORDER_ITEM_DISCARD", order, summary);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "询价订单零件已废弃。");
        result.put("rfqId", rfqId);
        result.put("itemId", item.getId());
        result.put("statusBefore", statusBefore);
        result.put("statusAfter", item.getStatus());
        result.put("ticketedQty", ticketedQty);
        return result;
    }

    @Transactional
    public Map<String, Object> restoreOrderItem(Integer rfqId, Integer itemId) {
        OrderRfq order = requireOrderForUpdate(rfqId);
        ItemQuoteVersion item = lockOrderItem(order, itemId);
        if (!QuoteStatusWorkflow.DISCARDED.equals(item.getStatus())) {
            throw new RuntimeException("只有废弃状态的询价订单零件可以恢复");
        }

        int ticketedQty = ticketedQuantitiesForItemIds(rfqId, List.of(item.getId()))
                .getOrDefault(item.getId(), 0);
        String statusBefore = item.getStatus();
        String restoredStatus = trimToNull(item.getPreviousStatusBeforeDiscard());
        if (restoredStatus == null) {
            throw new RuntimeException("该零件缺少废弃前状态，不能自动恢复");
        }
        boolean resetPricing = shouldResetPricingAfterRestore(restoredStatus);
        UpdateWrapper<ItemQuoteVersion> update = new UpdateWrapper<>();
        update.eq("id", item.getId())
                .set("status", restoredStatus)
                .set("previous_status_before_discard", null);
        if (resetPricing) {
            resetPricingFields(update);
            resetPricingFields(item);
        }
        item.setStatus(restoredStatus);
        item.setPreviousStatusBeforeDiscard(null);
        if (itemMapper.update(null, update) <= 0) {
            throw new RuntimeException("询价订单零件恢复失败，请刷新后重试");
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("rfqId", rfqId);
        summary.put("rfqNo", order.getRfqNo());
        summary.put("itemId", item.getId());
        summary.put("partNo", item.getPartNo());
        summary.put("version", item.getVersion());
        summary.put("statusBefore", statusBefore);
        summary.put("statusAfter", item.getStatus());
        summary.put("ticketedQty", ticketedQty);
        auditOrderSuccess("ORDER_ITEM_RESTORE", order, summary);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "询价订单零件已恢复。");
        result.put("rfqId", rfqId);
        result.put("itemId", item.getId());
        result.put("statusBefore", statusBefore);
        result.put("statusAfter", item.getStatus());
        result.put("ticketedQty", ticketedQty);
        return result;
    }

    private boolean shouldResetPricingAfterRestore(String restoredStatus) {
        return QuoteStatusWorkflow.UNPRICED.equals(restoredStatus)
                || QuoteStatusWorkflow.NO_DRAWING.equals(restoredStatus)
                || QuoteStatusWorkflow.isLegacyUnpricedStatus(restoredStatus);
    }

    private void resetPricingFields(UpdateWrapper<ItemQuoteVersion> update) {
        update.set("material_data", null)
                .set("process_route", null)
                .set("fee_data", null)
                .set("base_cost", null)
                .set("tax_included_unit_price", null)
                .set("tax_included_total_price", null)
                .set("untaxed_unit_price", null)
                .set("untaxed_total_price", null)
                .set("repeat_match_eligible", false)
                .set("engineering_material_code", null)
                .set("engineering_material_name", null)
                .set("engineering_material_type_code", null)
                .set("engineering_material_type_name", null)
                .set("engineering_material_category", null)
                .set("material_shape", null)
                .set("material_gross_weight", null)
                .set("part_net_weight", null)
                .set("quote_mode", null)
                .set("assembly_fee", null);
    }

    private void resetPricingFields(ItemQuoteVersion item) {
        item.setMaterialData(null);
        item.setProcessRoute(null);
        item.setFeeData(null);
        item.setBaseCost(null);
        item.setTaxIncludedUnitPrice(null);
        item.setTaxIncludedTotalPrice(null);
        item.setUntaxedUnitPrice(null);
        item.setUntaxedTotalPrice(null);
        item.setRepeatMatchEligible(false);
        item.setEngineeringMaterialCode(null);
        item.setEngineeringMaterialName(null);
        item.setEngineeringMaterialTypeCode(null);
        item.setEngineeringMaterialTypeName(null);
        item.setEngineeringMaterialCategory(null);
        item.setMaterialShape(null);
        item.setMaterialGrossWeight(null);
        item.setPartNetWeight(null);
        item.setQuoteMode(null);
        item.setAssemblyFee(null);
    }

    @Transactional
    public Map<String, Object> appendOrderItems(
            Integer rfqId,
            MultipartFile excelFile,
            List<MultipartFile> drawings,
            List<MultipartFile> versionDrawings) {
        long startedAt = System.currentTimeMillis();
        OrderRfq order = requireOrderForUpdate(rfqId);
        String orderStatusBefore = order.getStatus();
        List<RfqExcelDTO> excelRows = parseAppendOrderItemsExcel(excelFile);
        if (excelRows.isEmpty()) {
            throw new RuntimeException("新增零件询价表没有可导入的零件明细");
        }

        Set<String> excelPartNos = new LinkedHashSet<>();
        Set<DrawingPartNoResolver.DrawingIdentity> excelPartNoVersions = new LinkedHashSet<>();
        for (RfqExcelDTO row : excelRows) {
            String partNo = normalizePartNo(row == null ? null : row.getPartNo());
            if (partNo.isEmpty()) {
                continue;
            }
            excelPartNos.add(partNo);
            String version = normalizeVersion(row.getVersion());
            if (!version.isEmpty()) {
                excelPartNoVersions.add(new DrawingPartNoResolver.DrawingIdentity(partNo, version));
            }
        }
        if (excelPartNos.isEmpty()) {
            throw new RuntimeException("新增零件询价表必须包含图号");
        }

        List<Map<String, Object>> unmatchedDrawings = new ArrayList<>();
        List<Map<String, Object>> skippedFiles = new ArrayList<>();
        Map<String, AppendDrawingFileInfo> drawingByPartNo = saveAppendDrawingsByPartNo(
                order,
                drawings,
                excelPartNos,
                unmatchedDrawings,
                skippedFiles);
        Map<String, AppendDrawingFileInfo> drawingByPartNoVersion = saveAppendDrawingsByPartNoVersion(
                order,
                versionDrawings,
                excelPartNos,
                excelPartNoVersions,
                unmatchedDrawings,
                skippedFiles);

        int insertedCount = 0;
        int boundCount = 0;
        List<Map<String, Object>> boundItems = new ArrayList<>();
        List<Map<String, Object>> missingDrawingItems = new ArrayList<>();
        for (RfqExcelDTO row : excelRows) {
            String partNo = trimToNull(row.getPartNo());
            String normalizedPartNo = normalizePartNo(partNo);
            if (normalizedPartNo.isEmpty()) {
                continue;
            }
            Integer qty = row.getQty();
            validateOrderItemQty(qty, 0);
            String normalizedVersion = normalizeVersion(row.getVersion());
            AppendDrawingFileInfo matchedDrawing = drawingByPartNoVersion.get(itemKey(normalizedPartNo, normalizedVersion));
            if (matchedDrawing == null) {
                matchedDrawing = drawingByPartNo.get(normalizedPartNo);
            }

            ItemQuoteVersion item = new ItemQuoteVersion();
            item.setRfqId(rfqId);
            item.setPartNo(partNo);
            item.setDescription(trimToNull(row.getDescription()));
            item.setMaterialName(trimToNull(row.getMaterialName()));
            item.setSurfaceTreatment(trimToNull(row.getSurfaceTreatment()));
            item.setVersion(normalizedVersion);
            item.setQty(qty);
            item.setUnit(trimToNull(row.getUnit()));
            item.setLeadTime(trimToNull(row.getLeadTime()));
            item.setRemark(trimToNull(row.getRemark()));
            item.setStatus(QuoteStatusWorkflow.UNPRICED);
            if (matchedDrawing != null) {
                item.setDrawingUrl(matchedDrawing.url());
            }
            if (itemMapper.insert(item) <= 0) {
                throw new RuntimeException("新增询价订单零件保存失败，请刷新后重试");
            }
            insertedCount++;
            if (matchedDrawing != null) {
                boundCount++;
                boundItems.add(buildItemResult(item, matchedDrawing.originalFilename(), "已绑定"));
            } else {
                missingDrawingItems.add(buildMissingAppendItem(row, normalizedPartNo));
            }
        }

        Map<String, Object> auditSummary = new LinkedHashMap<>();
        auditSummary.put("rfqId", rfqId);
        auditSummary.put("rfqNo", order.getRfqNo());
        auditSummary.put("insertedCount", insertedCount);
        auditSummary.put("boundCount", boundCount);
        auditSummary.put("missingDrawingCount", missingDrawingItems.size());
        auditSummary.put("unmatchedDrawingCount", unmatchedDrawings.size());
        auditSummary.put("skippedFileCount", skippedFiles.size());
        auditSummary.put("orderStatusBefore", orderStatusBefore);
        auditSummary.put("orderStatusAfter", order.getStatus());
        auditSummary.put("durationMs", System.currentTimeMillis() - startedAt);
        auditOrderSuccess("ORDER_ITEM_APPEND_IMPORT", order, auditSummary);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "新增零件导入完成，已新增 " + insertedCount + " 个零件，绑定图纸 " + boundCount + " 个。");
        result.put("rfqId", rfqId);
        result.put("insertedCount", insertedCount);
        result.put("boundCount", boundCount);
        result.put("missingDrawingCount", missingDrawingItems.size());
        result.put("unmatchedCount", unmatchedDrawings.size());
        result.put("skippedCount", skippedFiles.size());
        result.put("orderStatusBefore", orderStatusBefore);
        result.put("orderStatusAfter", order.getStatus());
        result.put("boundItems", boundItems);
        result.put("missingItems", missingDrawingItems);
        result.put("unmatchedDrawings", unmatchedDrawings);
        result.put("skippedFiles", skippedFiles);
        log.info("Order item append import completed rfqId={} insertedCount={} boundCount={} missingDrawingCount={} unmatchedDrawingCount={} skippedFileCount={} orderStatusBefore={} orderStatusAfter={} durationMs={}",
                rfqId, insertedCount, boundCount, missingDrawingItems.size(), unmatchedDrawings.size(), skippedFiles.size(),
                orderStatusBefore, order.getStatus(), System.currentTimeMillis() - startedAt);
        return result;
    }

    public PageResult<OrderDeliveryTicket> pageOrderTickets(Integer rfqId, Integer page, Integer pageSize) {
        requireOrder(rfqId);
        int safePage = normalizePage(page);
        int safePageSize = pageSize == null || pageSize < 1 ? 50 : Math.min(pageSize, 100);
        long offset = (long) (safePage - 1) * safePageSize;
        QueryWrapper<OrderDeliveryTicket> countQuery = new QueryWrapper<>();
        countQuery.eq("rfq_id", rfqId);
        Long total = ticketMapper.selectCount(countQuery);
        if (total == null || total == 0) {
            return PageResult.of(List.of(), 0, safePage, safePageSize);
        }
        QueryWrapper<OrderDeliveryTicket> pageQuery = new QueryWrapper<>();
        pageQuery.eq("rfq_id", rfqId)
                .orderByAsc("id")
                .last("LIMIT " + safePageSize + " OFFSET " + offset);
        return PageResult.of(ticketMapper.selectList(pageQuery), total, safePage, safePageSize);
    }

    public PageResult<ProcessSheetDTO> pageDynamicProcessSheets(Integer rfqId, Integer page, Integer pageSize) {
        requireOrder(rfqId);
        return deliveryService.pageProcessSheets(rfqId, page, pageSize);
    }

    public Map<String, Object> getDrawingSummary(Integer rfqId) {
        OrderRfq order = requireOrder(rfqId);
        QueryWrapper<ItemQuoteVersion> qw = new QueryWrapper<>();
        qw.select(
                "COUNT(*) AS item_count",
                "SUM(CASE WHEN drawing_url IS NOT NULL AND drawing_url <> '' THEN 1 ELSE 0 END) AS bound_drawing_count",
                "SUM(CASE WHEN drawing_url IS NULL OR drawing_url = '' THEN 1 ELSE 0 END) AS missing_drawing_count"
        );
        qw.eq("rfq_id", rfqId);
        Map<String, Object> row = itemMapper.selectMaps(qw).stream().findFirst().orElse(Map.of());
        long itemCount = numberValue(row.get("item_count")).longValue();
        long boundCount = numberValue(row.get("bound_drawing_count")).longValue();
        long missingCount = numberValue(row.get("missing_drawing_count")).longValue();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("rfqId", order.getId());
        summary.put("rfqNo", order.getRfqNo());
        summary.put("itemCount", itemCount);
        summary.put("boundDrawingCount", boundCount);
        summary.put("missingDrawingCount", missingCount);
        summary.put("fileRoot", buildOrderFileRoot(order));
        return summary;
    }

    @Transactional
    public Map<String, Object> supplementDrawings(Integer rfqId, List<MultipartFile> drawings) {
        return supplementDrawings(rfqId, drawings, null);
    }

    @Transactional
    public Map<String, Object> supplementDrawings(
            Integer rfqId,
            List<MultipartFile> drawings,
            List<MultipartFile> versionDrawings) {
        OrderRfq order = requireOrderForUpdate(rfqId);
        if (isEmptyFiles(drawings) && isEmptyFiles(versionDrawings)) {
            throw new RuntimeException("请至少上传一份 PDF 图纸");
        }

        List<ItemQuoteVersion> items = listItems(rfqId);
        Map<String, List<ItemQuoteVersion>> itemMapByPartNo = new HashMap<>();
        Map<String, List<ItemQuoteVersion>> itemMapByPartNoVersion = new HashMap<>();
        Set<DrawingPartNoResolver.DrawingIdentity> itemIdentities = new HashSet<>();
        for (ItemQuoteVersion item : items) {
            String normalizedPartNo = normalizePartNo(item.getPartNo());
            if (!normalizedPartNo.isEmpty()) {
                itemMapByPartNo.computeIfAbsent(normalizedPartNo, key -> new ArrayList<>()).add(item);
            }
            String normalizedVersion = DrawingPartNoResolver.normalizeVersion(item.getVersion());
            if (!normalizedPartNo.isEmpty() && !normalizedVersion.isEmpty()) {
                itemMapByPartNoVersion
                        .computeIfAbsent(itemKey(normalizedPartNo, normalizedVersion), key -> new ArrayList<>())
                        .add(item);
                itemIdentities.add(new DrawingPartNoResolver.DrawingIdentity(normalizedPartNo, normalizedVersion));
            }
        }

        List<Map<String, Object>> boundItems = new ArrayList<>();
        List<Map<String, Object>> alreadyBoundItems = new ArrayList<>();
        List<Map<String, Object>> unmatchedDrawings = new ArrayList<>();
        List<Map<String, Object>> skippedFiles = new ArrayList<>();
        int restoredUnpricedCount = 0;

        if (versionDrawings != null) {
            for (MultipartFile drawing : versionDrawings) {
                String cleanFileName = cleanFilename(drawing == null ? null : drawing.getOriginalFilename());
                if (isBlank(cleanFileName) || !cleanFileName.toLowerCase().endsWith(".pdf")) {
                    skippedFiles.add(buildFileResult(cleanFileName, "", "非 PDF 文件"));
                    continue;
                }

                DrawingPartNoResolver.DrawingIdentity identity =
                        DrawingPartNoResolver.extractPartNoVersion(cleanFileName, itemIdentities);
                String partNoFromFileName = identity == null
                        ? DrawingPartNoResolver.extractPartNo(cleanFileName, itemMapByPartNo.keySet())
                        : identity.partNo();
                if (identity == null) {
                    unmatchedDrawings.add(buildFileResult(cleanFileName, partNoFromFileName, "未找到该订单对应零件"));
                    continue;
                }
                List<ItemQuoteVersion> matchedItems = itemMapByPartNoVersion.get(itemKey(identity.partNo(), identity.version()));
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

                String fileUrl = businessFileService
                        .saveDrawing(order, drawing, "ORDER_SUPPLEMENT_DRAWING", String.valueOf(rfqId))
                        .publicPath();
                for (ItemQuoteVersion item : matchedItems) {
                    boolean hadDrawingBefore = !isBlank(item.getDrawingUrl());
                    if (hadDrawingBefore) {
                        alreadyBoundItems.add(buildItemResult(item, cleanFileName, "该零件已有图纸，未覆盖"));
                        continue;
                    }
                    String oldStatus = item.getStatus();
                    String nextStatus = QuoteStatusWorkflow.statusAfterSupplementDrawing(
                            oldStatus,
                            hadDrawingBefore,
                            item.getTaxIncludedUnitPrice() != null);
                    item.setDrawingUrl(fileUrl);
                    item.setStatus(nextStatus);
                    if (QuoteStatusWorkflow.UNPRICED.equals(nextStatus) && !QuoteStatusWorkflow.UNPRICED.equals(oldStatus)) {
                        restoredUnpricedCount++;
                    }
                    itemMapper.updateById(item);
                    boundItems.add(buildItemResult(item, cleanFileName, "已绑定"));
                }
            }
        }

        if (drawings != null) {
            for (MultipartFile drawing : drawings) {
                String cleanFileName = cleanFilename(drawing == null ? null : drawing.getOriginalFilename());
                if (isBlank(cleanFileName) || !cleanFileName.toLowerCase().endsWith(".pdf")) {
                    skippedFiles.add(buildFileResult(cleanFileName, "", "非 PDF 文件"));
                    continue;
                }

                String partNoFromFileName = DrawingPartNoResolver.extractPartNo(cleanFileName, itemMapByPartNo.keySet());
                List<ItemQuoteVersion> matchedItems = itemMapByPartNo.get(partNoFromFileName);
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

                String fileUrl = businessFileService
                        .saveDrawing(order, drawing, "ORDER_SUPPLEMENT_DRAWING", String.valueOf(rfqId))
                        .publicPath();
                for (ItemQuoteVersion item : matchedItems) {
                    boolean hadDrawingBefore = !isBlank(item.getDrawingUrl());
                    if (hadDrawingBefore) {
                        alreadyBoundItems.add(buildItemResult(item, cleanFileName, "该零件已有图纸，未覆盖"));
                        continue;
                    }
                    String oldStatus = item.getStatus();
                    String nextStatus = QuoteStatusWorkflow.statusAfterSupplementDrawing(
                            oldStatus,
                            hadDrawingBefore,
                            item.getTaxIncludedUnitPrice() != null);
                    item.setDrawingUrl(fileUrl);
                    item.setStatus(nextStatus);
                    if (QuoteStatusWorkflow.UNPRICED.equals(nextStatus) && !QuoteStatusWorkflow.UNPRICED.equals(oldStatus)) {
                        restoredUnpricedCount++;
                    }
                    itemMapper.updateById(item);
                    boundItems.add(buildItemResult(item, cleanFileName, "已绑定"));
                }
            }
        }

        if (!boundItems.isEmpty()) {
            auditService.record(
                    "ORDER_SUPPLEMENT_DRAWINGS",
                    "order_rfq",
                    rfqId,
                    null,
                    buildSupplementDrawingAuditSummary(
                            rfqId,
                            drawings,
                            versionDrawings,
                            boundItems,
                            alreadyBoundItems,
                            unmatchedDrawings,
                            skippedFiles,
                            restoredUnpricedCount));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "补传图纸处理完成，成功绑定 " + boundItems.size() + " 份。");
        result.put("boundCount", boundItems.size());
        result.put("alreadyBoundCount", alreadyBoundItems.size());
        result.put("unmatchedCount", unmatchedDrawings.size());
        result.put("skippedCount", skippedFiles.size());
        result.put("restoredUnpricedCount", restoredUnpricedCount);
        result.put("boundItems", boundItems);
        result.put("alreadyBoundItems", alreadyBoundItems);
        result.put("unmatchedDrawings", unmatchedDrawings);
        result.put("skippedFiles", skippedFiles);
        log.info("Order supplement drawings completed rfqId={} uploadCount={} boundCount={} alreadyBoundCount={} unmatchedCount={} skippedCount={} restoredUnpricedCount={}",
                rfqId, fileCount(drawings) + fileCount(versionDrawings), boundItems.size(), alreadyBoundItems.size(), unmatchedDrawings.size(), skippedFiles.size(), restoredUnpricedCount);
        if (!unmatchedDrawings.isEmpty() || !skippedFiles.isEmpty()) {
            log.warn("Order supplement drawings completed with warnings rfqId={} unmatchedCount={} skippedCount={}",
                    rfqId, unmatchedDrawings.size(), skippedFiles.size());
        }
        return result;
    }

    @Transactional
    public Map<String, Object> replaceDrawings(
            Integer rfqId,
            List<MultipartFile> drawings,
            List<MultipartFile> versionDrawings) {
        OrderRfq order = requireOrderForUpdate(rfqId);
        if (isEmptyFiles(drawings) && isEmptyFiles(versionDrawings)) {
            throw new RuntimeException("请至少上传一份 PDF 图纸");
        }

        List<ItemQuoteVersion> items = listItems(rfqId);
        Map<String, List<ItemQuoteVersion>> itemMapByPartNo = new HashMap<>();
        Map<String, List<ItemQuoteVersion>> itemMapByPartNoVersion = new HashMap<>();
        Set<DrawingPartNoResolver.DrawingIdentity> itemIdentities = new HashSet<>();
        for (ItemQuoteVersion item : items) {
            String normalizedPartNo = normalizePartNo(item.getPartNo());
            if (!normalizedPartNo.isEmpty()) {
                itemMapByPartNo.computeIfAbsent(normalizedPartNo, key -> new ArrayList<>()).add(item);
            }
            String normalizedVersion = DrawingPartNoResolver.normalizeVersion(item.getVersion());
            if (!normalizedPartNo.isEmpty() && !normalizedVersion.isEmpty()) {
                itemMapByPartNoVersion
                        .computeIfAbsent(itemKey(normalizedPartNo, normalizedVersion), key -> new ArrayList<>())
                        .add(item);
                itemIdentities.add(new DrawingPartNoResolver.DrawingIdentity(normalizedPartNo, normalizedVersion));
            }
        }

        List<Map<String, Object>> replacedItems = new ArrayList<>();
        List<Map<String, Object>> protectedSkippedItems = new ArrayList<>();
        List<Map<String, Object>> unmatchedDrawings = new ArrayList<>();
        List<Map<String, Object>> skippedFiles = new ArrayList<>();
        Set<String> protectedItemKeys = new HashSet<>();

        if (versionDrawings != null) {
            for (MultipartFile drawing : versionDrawings) {
                replaceDrawing(
                        order,
                        drawing,
                        true,
                        itemMapByPartNo,
                        itemMapByPartNoVersion,
                        itemIdentities,
                        protectedItemKeys,
                        replacedItems,
                        protectedSkippedItems,
                        unmatchedDrawings,
                        skippedFiles);
            }
        }
        if (drawings != null) {
            for (MultipartFile drawing : drawings) {
                replaceDrawing(
                        order,
                        drawing,
                        false,
                        itemMapByPartNo,
                        itemMapByPartNoVersion,
                        itemIdentities,
                        protectedItemKeys,
                        replacedItems,
                        protectedSkippedItems,
                        unmatchedDrawings,
                        skippedFiles);
            }
        }

        if (!replacedItems.isEmpty()) {
            auditOrderSuccess(
                    "ORDER_DRAWING_REPLACE",
                    order,
                    buildReplaceDrawingAuditSummary(rfqId, drawings, versionDrawings, replacedItems, unmatchedDrawings, skippedFiles));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "替换图纸处理完成，成功替换 " + replacedItems.size() + " 项。");
        result.put("replacedCount", replacedItems.size());
        result.put("boundCount", replacedItems.size());
        result.put("alreadyBoundCount", protectedSkippedItems.size());
        result.put("unmatchedCount", unmatchedDrawings.size());
        result.put("skippedCount", skippedFiles.size());
        result.put("replacedItems", replacedItems);
        result.put("boundItems", replacedItems);
        result.put("alreadyBoundItems", protectedSkippedItems);
        result.put("unmatchedDrawings", unmatchedDrawings);
        result.put("skippedFiles", skippedFiles);
        log.info("Order drawings replace completed rfqId={} uploadCount={} replacedCount={} unmatchedCount={} skippedCount={}",
                rfqId, fileCount(drawings) + fileCount(versionDrawings), replacedItems.size(), unmatchedDrawings.size(), skippedFiles.size());
        if (!unmatchedDrawings.isEmpty() || !skippedFiles.isEmpty()) {
            log.warn("Order drawings replace completed with warnings rfqId={} unmatchedCount={} skippedCount={}",
                    rfqId, unmatchedDrawings.size(), skippedFiles.size());
        }
        return result;
    }

    public Map<String, Object> previewItemDataReplacement(Integer rfqId, MultipartFile excelFile) {
        OrderRfq order = requireOrder(rfqId);
        OrderItemDataReplacementExcelParseResult parsed = parseOrderItemReplacementFile(excelFile);
        List<ItemQuoteVersion> items = listItems(rfqId);
        Map<Integer, Integer> ticketedQtyByItemId = ticketedQuantities(rfqId, items);
        return buildItemDataReplacementPreview(order, parsed, items, ticketedQtyByItemId);
    }

    @Transactional
    public Map<String, Object> confirmItemDataReplacement(
            Integer rfqId,
            MultipartFile excelFile,
            String previewFingerprint) {
        long startedAt = System.currentTimeMillis();
        OrderRfq order = requireOrderForUpdate(rfqId);
        OrderItemDataReplacementExcelParseResult parsed = parseOrderItemReplacementFile(excelFile);
        List<ItemQuoteVersion> currentItems = listItems(rfqId);
        Map<Integer, Integer> ticketedQtyByItemId = ticketedQuantities(rfqId, currentItems);
        Map<String, Object> preview = buildItemDataReplacementPreview(order, parsed, currentItems, ticketedQtyByItemId);
        if (!fingerprintsEqual(previewFingerprint, String.valueOf(preview.get("previewFingerprint")))) {
            throw new PreviewFingerprintConflictException("数据已发生变化，请重新预览");
        }
        if (!Boolean.TRUE.equals(preview.get("canConfirm"))) {
            throw new IllegalArgumentException("当前替换数据不能确认，请重新预览");
        }

        Map<Integer, ItemQuoteVersion> lockedItemsById = new LinkedHashMap<>();
        for (Map<String, Object> row : safeMapList(preview.get("rows"))) {
            if (!"CHANGED".equals(row.get("status"))) {
                continue;
            }
            Integer itemId = objectToInteger(row.get("itemId"));
            ItemQuoteVersion locked = lockOrderItem(order, itemId);
            lockedItemsById.put(itemId, locked);
        }

        int updatedCount = 0;
        Map<String, Integer> changedFieldCounts = new LinkedHashMap<>();
        List<Map<String, Object>> affectedRows = new ArrayList<>();
        for (Map<String, Object> row : safeMapList(preview.get("rows"))) {
            if (!"CHANGED".equals(row.get("status"))) {
                continue;
            }
            Integer itemId = objectToInteger(row.get("itemId"));
            ItemQuoteVersion item = lockedItemsById.get(itemId);
            if (item == null) {
                throw new PreviewFingerprintConflictException("数据已发生变化，请重新预览");
            }
            int ticketedQty = ticketedQtyByItemId.getOrDefault(item.getId(), 0);
            for (Map<String, Object> change : safeMapList(row.get("changes"))) {
                String field = String.valueOf(change.get("field"));
                applyImportedChange(item, field, change.get("afterValue"), ticketedQty);
                changedFieldCounts.merge(field, 1, Integer::sum);
            }
            recalculateItemTotals(item);
            if (itemMapper.updateById(item) <= 0) {
                throw new RuntimeException("询价订单表格数据替换保存失败，请刷新后重试");
            }
            updatedCount++;
            affectedRows.add(buildOrderItemRow(item, ticketedQty));
        }

        Map<String, Object> summary = new LinkedHashMap<>(safeMap(preview.get("summary")));
        summary.put("updatedItemCount", updatedCount);
        summary.put("quantityAppliedCount", 0);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "表格数据替换已确认，更新 " + updatedCount + " 行询价订单零件信息。");
        result.put("summary", summary);
        result.put("affectedRows", affectedRows);

        if (updatedCount > 0) {
            Map<String, Object> auditSummary = new LinkedHashMap<>();
            auditSummary.put("rfqId", rfqId);
            auditSummary.put("rfqNo", order.getRfqNo());
            auditSummary.put("importedCount", summary.getOrDefault("importedCount", 0));
            auditSummary.put("matchedCount", summary.getOrDefault("matchedCount", 0));
            auditSummary.put("updatedItemCount", updatedCount);
            auditSummary.put("unmatchedCount", summary.getOrDefault("unmatchedCount", 0));
            auditSummary.put("skippedCount", summary.getOrDefault("skippedCount", 0));
            auditSummary.put("errorCount", summary.getOrDefault("errorCount", 0));
            auditSummary.put("changedFieldCounts", changedFieldCounts);
            auditSummary.put("durationMs", System.currentTimeMillis() - startedAt);
            auditOrderSuccess(
                    "ORDER_ITEM_DATA_REPLACEMENT",
                    order,
                    auditSummary);
        }
        log.info("Order item data replacement completed rfqId={} updatedItemCount={} unmatchedCount={} errorCount={} durationMs={}",
                rfqId, updatedCount, summary.getOrDefault("unmatchedCount", 0), summary.getOrDefault("errorCount", 0),
                System.currentTimeMillis() - startedAt);
        return result;
    }

    private List<OrderItemMaintenanceLineReq> normalizeMaintenanceLines(OrderItemMaintenanceUpdateReq req) {
        if (req == null || req.getItems() == null || req.getItems().isEmpty()) {
            throw new RuntimeException("请提供需要保存的询价订单零件行");
        }
        List<OrderItemMaintenanceLineReq> lines = req.getItems().stream()
                .filter(Objects::nonNull)
                .toList();
        if (lines.isEmpty()) {
            throw new RuntimeException("请提供需要保存的询价订单零件行");
        }
        for (OrderItemMaintenanceLineReq line : lines) {
            if (line.getItemId() == null) {
                throw new RuntimeException("询价订单零件行 ID 不能为空");
            }
        }
        return lines;
    }

    private void assertNoDuplicateItemIds(List<OrderItemMaintenanceLineReq> lines) {
        Set<Integer> seen = new LinkedHashSet<>();
        for (OrderItemMaintenanceLineReq line : lines) {
            if (!seen.add(line.getItemId())) {
                throw new RuntimeException("询价订单零件行重复提交：" + line.getItemId());
            }
        }
    }

    private ItemQuoteVersion lockOrderItem(OrderRfq order, Integer itemId) {
        if (itemId == null) {
            throw new RuntimeException("询价订单零件行 ID 不能为空");
        }
        ItemQuoteVersion item = itemMapper.selectByIdForUpdate(itemId);
        if (item == null) {
            throw new RuntimeException("询价订单零件行不存在：" + itemId);
        }
        if (!Objects.equals(order.getId(), item.getRfqId())) {
            throw new RuntimeException("询价订单零件行 " + itemId + " 不属于当前询价订单");
        }
        return item;
    }

    private Set<String> applyMaintenanceLine(
            ItemQuoteVersion item,
            OrderItemMaintenanceLineReq line,
            int ticketedQty) {
        Set<String> changed = new LinkedHashSet<>();
        applyString(item.getPartNo(), normalizeRequiredText(line.getPartNo(), "图号", 100), "partNo", changed, item::setPartNo);
        applyString(item.getVersion(), normalizeText(line.getVersion(), "版本", 20), "version", changed, item::setVersion);

        Integer qty = line.getQty();
        validateOrderItemQty(qty, ticketedQty);
        if (!Objects.equals(item.getQty(), qty)) {
            item.setQty(qty);
            changed.add("qty");
        }

        applyString(item.getLeadTime(), normalizeLeadTime(line.getLeadTime(), "客户询价交期"), "leadTime", changed, item::setLeadTime);
        applyString(item.getQuoteLeadTime(), normalizeLeadTime(line.getQuoteLeadTime(), "报价承诺交期"), "quoteLeadTime", changed, item::setQuoteLeadTime);
        applyString(item.getInternalTargetLeadTime(), normalizeLeadTime(line.getInternalTargetLeadTime(), "内部目标交期"), "internalTargetLeadTime", changed, item::setInternalTargetLeadTime);
        applyString(item.getDescription(), normalizeText(line.getDescription(), "物料描述", 500), "description", changed, item::setDescription);
        applyString(item.getSurfaceTreatment(), normalizeText(line.getSurfaceTreatment(), "表面处理", 200), "surfaceTreatment", changed, item::setSurfaceTreatment);
        applyString(item.getRemark(), normalizeText(line.getRemark(), "备注", 500), "remark", changed, item::setRemark);

        if (!Objects.equals(item.getSalesOrderDate(), line.getSalesOrderDate())) {
            item.setSalesOrderDate(line.getSalesOrderDate());
            changed.add("salesOrderDate");
        }
        applyString(item.getCustomerOrderNo(), normalizeText(line.getCustomerOrderNo(), "客户订单号", 120), "customerOrderNo", changed, item::setCustomerOrderNo);
        applyString(item.getCustomerProjectNo(), normalizeText(line.getCustomerProjectNo(), "客户项目号", 120), "customerProjectNo", changed, item::setCustomerProjectNo);
        applyString(item.getCustomerOrderTitle(), normalizeText(line.getCustomerOrderTitle(), "客户下单抬头", 255), "customerOrderTitle", changed, item::setCustomerOrderTitle);
        applyString(item.getOrderType(), normalizeOrderType(line.getOrderType()), "orderType", changed, item::setOrderType);
        applyString(item.getInternalProjectNo(), normalizeText(line.getInternalProjectNo(), "内部项目号", 120), "internalProjectNo", changed, item::setInternalProjectNo);
        return changed;
    }

    private void applyString(
            String current,
            String next,
            String field,
            Set<String> changed,
            java.util.function.Consumer<String> setter) {
        if (!Objects.equals(trimToNull(current), next)) {
            changed.add(field);
        }
        setter.accept(next);
    }

    private void validateOrderItemQty(Integer qty, int ticketedQty) {
        if (qty == null || qty <= 0) {
            throw new RuntimeException("询价数量必须为正整数");
        }
        if (qty < ticketedQty) {
            throw new RuntimeException("询价数量不能小于已提单数量 " + ticketedQty);
        }
    }

    private String normalizeRequiredText(String value, String fieldName, int maxLength) {
        String normalized = normalizeText(value, fieldName, maxLength);
        if (normalized == null) {
            throw new RuntimeException(fieldName + "不能为空");
        }
        return normalized;
    }

    private String normalizeText(String value, String fieldName, int maxLength) {
        String normalized = trimToNull(value);
        if (normalized != null && normalized.length() > maxLength) {
            throw new RuntimeException(fieldName + "长度不能超过 " + maxLength + " 个字符");
        }
        return normalized;
    }

    private String normalizeLeadTime(String value, String fieldName) {
        String normalized = leadTimeService == null ? trimToNull(value) : leadTimeService.normalizeForQuote(value);
        if (normalized != null && normalized.length() > 100) {
            throw new RuntimeException(fieldName + "长度不能超过 100 个字符");
        }
        return normalized;
    }

    private String normalizeOrderType(String value) {
        String normalized = normalizeText(value, "订单类型", 20);
        if (normalized == null) {
            return null;
        }
        if (!Set.of("预测订单", "备货订单", "正式订单").contains(normalized)) {
            throw new RuntimeException("订单类型只能是预测订单、备货订单或正式订单");
        }
        return normalized;
    }

    private List<RfqExcelDTO> parseAppendOrderItemsExcel(MultipartFile excelFile) {
        if (excelFile == null || excelFile.isEmpty()) {
            throw new IllegalArgumentException("新增零件询价表不能为空");
        }
        try {
            List<RfqExcelDTO> rows = EasyExcel.read(excelFile.getInputStream())
                    .head(RfqExcelDTO.class)
                    .sheet()
                    .doReadSync();
            return safeList(rows).stream()
                    .filter(Objects::nonNull)
                    .toList();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("新增零件询价表解析失败：" + e.getMessage(), e);
        }
    }

    private Map<String, AppendDrawingFileInfo> saveAppendDrawingsByPartNo(
            OrderRfq order,
            List<MultipartFile> drawings,
            Set<String> excelPartNos,
            List<Map<String, Object>> unmatchedDrawings,
            List<Map<String, Object>> skippedFiles) {
        Map<String, AppendDrawingFileInfo> drawingByPartNo = new LinkedHashMap<>();
        if (drawings == null) {
            return drawingByPartNo;
        }
        for (MultipartFile drawing : drawings) {
            String cleanFileName = cleanFilename(drawing == null ? null : drawing.getOriginalFilename());
            if (drawing == null || isBlank(cleanFileName) || !cleanFileName.toLowerCase().endsWith(".pdf")) {
                skippedFiles.add(buildFileResult(cleanFileName, "", "非 PDF 文件"));
                continue;
            }
            String partNo = DrawingPartNoResolver.extractPartNo(cleanFileName, excelPartNos);
            if (isBlank(partNo) || !excelPartNos.contains(partNo)) {
                unmatchedDrawings.add(buildFileResult(cleanFileName, partNo, "未找到新增零件图号"));
                continue;
            }
            String url = businessFileService.saveDrawing(
                    order,
                    drawing,
                    "ORDER_ITEM_APPEND_DRAWING",
                    String.valueOf(order.getId())).publicPath();
            drawingByPartNo.put(partNo, new AppendDrawingFileInfo(partNo, "", cleanFileName, url));
        }
        return drawingByPartNo;
    }

    private Map<String, AppendDrawingFileInfo> saveAppendDrawingsByPartNoVersion(
            OrderRfq order,
            List<MultipartFile> versionDrawings,
            Set<String> excelPartNos,
            Set<DrawingPartNoResolver.DrawingIdentity> excelPartNoVersions,
            List<Map<String, Object>> unmatchedDrawings,
            List<Map<String, Object>> skippedFiles) {
        Map<String, AppendDrawingFileInfo> drawingByPartNoVersion = new LinkedHashMap<>();
        if (versionDrawings == null) {
            return drawingByPartNoVersion;
        }
        for (MultipartFile drawing : versionDrawings) {
            String cleanFileName = cleanFilename(drawing == null ? null : drawing.getOriginalFilename());
            if (drawing == null || isBlank(cleanFileName) || !cleanFileName.toLowerCase().endsWith(".pdf")) {
                skippedFiles.add(buildFileResult(cleanFileName, "", "非 PDF 文件"));
                continue;
            }
            DrawingPartNoResolver.DrawingIdentity identity =
                    DrawingPartNoResolver.extractPartNoVersion(cleanFileName, excelPartNoVersions);
            String partNo = identity == null
                    ? DrawingPartNoResolver.extractPartNo(cleanFileName, excelPartNos)
                    : identity.partNo();
            if (identity == null) {
                unmatchedDrawings.add(buildFileResult(cleanFileName, partNo, "未找到新增零件图号+版本"));
                continue;
            }
            String url = businessFileService.saveDrawing(
                    order,
                    drawing,
                    "ORDER_ITEM_APPEND_DRAWING",
                    String.valueOf(order.getId())).publicPath();
            drawingByPartNoVersion.put(
                    itemKey(identity.partNo(), identity.version()),
                    new AppendDrawingFileInfo(identity.partNo(), identity.version(), cleanFileName, url));
        }
        return drawingByPartNoVersion;
    }

    private void recalculateItemTotals(ItemQuoteVersion item) {
        Integer qty = item.getQty();
        if (qty == null) {
            item.setTaxIncludedTotalPrice(null);
            item.setUntaxedTotalPrice(null);
            return;
        }
        item.setTaxIncludedTotalPrice(item.getTaxIncludedUnitPrice() == null
                ? null
                : item.getTaxIncludedUnitPrice() * qty);
        item.setUntaxedTotalPrice(item.getUntaxedUnitPrice() == null
                ? null
                : item.getUntaxedUnitPrice() * qty);
    }

    private Map<Integer, Integer> ticketedQuantities(Integer rfqId, List<ItemQuoteVersion> items) {
        if (items == null || items.isEmpty()) {
            return Map.of();
        }
        QueryWrapper<OrderDeliveryTicket> query = new QueryWrapper<>();
        query.eq("rfq_id", rfqId);
        return ticketedQuantities(items, safeList(ticketMapper.selectList(query)));
    }

    private Map<Integer, Integer> ticketedQuantitiesForItemIds(Integer rfqId, List<Integer> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return Map.of();
        }
        Set<Integer> itemIdSet = new LinkedHashSet<>(itemIds);
        Map<Integer, Integer> quantities = new LinkedHashMap<>();
        itemIdSet.forEach(itemId -> quantities.put(itemId, 0));
        QueryWrapper<OrderDeliveryTicket> query = new QueryWrapper<>();
        query.eq("rfq_id", rfqId);
        for (OrderDeliveryTicket ticket : safeList(ticketMapper.selectList(query))) {
            Integer itemId = ticket.getItemId();
            if (itemId == null
                    && "RFQ".equalsIgnoreCase(ticket.getSourceType())
                    && ticket.getSourceItemId() != null
                    && ticket.getSourceItemId() <= Integer.MAX_VALUE) {
                itemId = ticket.getSourceItemId().intValue();
            }
            if (itemId != null && itemIdSet.contains(itemId)) {
                quantities.merge(itemId, safeInt(ticket.getQty()), Integer::sum);
            }
        }
        return quantities;
    }

    private Map<Integer, Integer> ticketedQuantities(List<ItemQuoteVersion> items, List<OrderDeliveryTicket> tickets) {
        Map<Integer, ItemQuoteVersion> itemsById = items.stream()
                .filter(Objects::nonNull)
                .filter(item -> item.getId() != null)
                .collect(Collectors.toMap(
                        ItemQuoteVersion::getId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new));
        Map<String, List<ItemQuoteVersion>> itemsByHistoricalKey = new LinkedHashMap<>();
        for (ItemQuoteVersion item : items) {
            if (item == null || item.getRfqId() == null) {
                continue;
            }
            String key = item.getRfqId() + "||" + itemKey(item.getPartNo(), item.getVersion());
            itemsByHistoricalKey.computeIfAbsent(key, ignored -> new ArrayList<>()).add(item);
        }

        Map<Integer, Integer> quantities = new LinkedHashMap<>();
        itemsById.keySet().forEach(itemId -> quantities.put(itemId, 0));
        for (OrderDeliveryTicket ticket : safeList(tickets)) {
            ItemQuoteVersion matched = exactTicketItem(ticket, itemsById);
            if (matched == null) {
                matched = historicalTicketItem(ticket, itemsByHistoricalKey);
            }
            if (matched != null && matched.getId() != null) {
                quantities.merge(matched.getId(), safeInt(ticket.getQty()), Integer::sum);
            }
        }
        return quantities;
    }

    private ItemQuoteVersion exactTicketItem(OrderDeliveryTicket ticket, Map<Integer, ItemQuoteVersion> itemsById) {
        if (ticket == null) {
            return null;
        }
        ItemQuoteVersion byItemId = ticket.getItemId() == null ? null : itemsById.get(ticket.getItemId());
        if (byItemId != null) {
            return byItemId;
        }
        if (!"RFQ".equalsIgnoreCase(ticket.getSourceType())
                || ticket.getSourceItemId() == null
                || ticket.getSourceItemId() > Integer.MAX_VALUE) {
            return null;
        }
        ItemQuoteVersion bySourceItemId = itemsById.get(ticket.getSourceItemId().intValue());
        if (bySourceItemId == null || ticket.getSourceOrderId() == null || bySourceItemId.getRfqId() == null) {
            return null;
        }
        return Objects.equals(ticket.getSourceOrderId(), bySourceItemId.getRfqId().longValue())
                ? bySourceItemId
                : null;
    }

    private ItemQuoteVersion historicalTicketItem(
            OrderDeliveryTicket ticket,
            Map<String, List<ItemQuoteVersion>> itemsByHistoricalKey) {
        if (ticket == null || ticket.getItemId() != null) {
            return null;
        }
        Integer rfqId = ticket.getRfqId();
        if (rfqId == null
                && "RFQ".equalsIgnoreCase(ticket.getSourceType())
                && ticket.getSourceOrderId() != null
                && ticket.getSourceOrderId() <= Integer.MAX_VALUE) {
            rfqId = ticket.getSourceOrderId().intValue();
        }
        if (rfqId == null) {
            return null;
        }
        List<ItemQuoteVersion> candidates = itemsByHistoricalKey.getOrDefault(
                rfqId + "||" + itemKey(ticket.getPartNo(), ticket.getVersion()),
                List.of());
        return candidates.size() == 1 ? candidates.get(0) : null;
    }

    private Map<String, Object> buildOrderItemRow(ItemQuoteVersion item, int ticketedQty) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", item.getId());
        row.put("itemId", item.getId());
        row.put("rfqId", item.getRfqId());
        row.put("partNo", item.getPartNo());
        row.put("version", item.getVersion());
        row.put("quoteMode", item.getQuoteMode());
        row.put("orderType", item.getOrderType());
        row.put("drawingUrl", item.getDrawingUrl());
        row.put("hasDrawing", !isBlank(item.getDrawingUrl()));
        row.put("qty", item.getQty());
        row.put("ticketedQty", ticketedQty);
        row.put("remainingQty", Math.max(0, safeInt(item.getQty()) - ticketedQty));
        row.put("leadTime", item.getLeadTime());
        row.put("quoteLeadTime", item.getQuoteLeadTime());
        row.put("internalTargetLeadTime", item.getInternalTargetLeadTime());
        row.put("materialName", item.getMaterialName());
        row.put("engineeringMaterialName", item.getEngineeringMaterialName());
        row.put("materialNo", item.getMaterialNo());
        row.put("materialNoStatus", item.getMaterialNoStatus());
        row.put("description", item.getDescription());
        row.put("surfaceTreatment", item.getSurfaceTreatment());
        row.put("remark", item.getRemark());
        row.put("salesOrderDate", item.getSalesOrderDate());
        row.put("customerOrderNo", item.getCustomerOrderNo());
        row.put("customerProjectNo", item.getCustomerProjectNo());
        row.put("customerOrderTitle", item.getCustomerOrderTitle());
        row.put("internalProjectNo", item.getInternalProjectNo());
        row.put("status", item.getStatus());
        row.put("taxIncludedUnitPrice", item.getTaxIncludedUnitPrice());
        row.put("taxIncludedTotalPrice", item.getTaxIncludedTotalPrice());
        row.put("untaxedUnitPrice", item.getUntaxedUnitPrice());
        row.put("untaxedTotalPrice", item.getUntaxedTotalPrice());
        return row;
    }

    private void replaceDrawing(
            OrderRfq order,
            MultipartFile drawing,
            boolean matchByPartNoVersion,
            Map<String, List<ItemQuoteVersion>> itemMapByPartNo,
            Map<String, List<ItemQuoteVersion>> itemMapByPartNoVersion,
            Set<DrawingPartNoResolver.DrawingIdentity> itemIdentities,
            Set<String> protectedItemKeys,
            List<Map<String, Object>> replacedItems,
            List<Map<String, Object>> protectedSkippedItems,
            List<Map<String, Object>> unmatchedDrawings,
            List<Map<String, Object>> skippedFiles) {
        String cleanFileName = cleanFilename(drawing == null ? null : drawing.getOriginalFilename());
        if (drawing == null || isBlank(cleanFileName) || !cleanFileName.toLowerCase().endsWith(".pdf")) {
            skippedFiles.add(buildFileResult(cleanFileName, "", "非 PDF 文件"));
            return;
        }

        String partNoFromFileName;
        List<ItemQuoteVersion> matchedItems;
        if (matchByPartNoVersion) {
            DrawingPartNoResolver.DrawingIdentity identity =
                    DrawingPartNoResolver.extractPartNoVersion(cleanFileName, itemIdentities);
            partNoFromFileName = identity == null
                    ? DrawingPartNoResolver.extractPartNo(cleanFileName, itemMapByPartNo.keySet())
                    : identity.partNo();
            matchedItems = identity == null ? null : itemMapByPartNoVersion.get(itemKey(identity.partNo(), identity.version()));
        } else {
            partNoFromFileName = DrawingPartNoResolver.extractPartNo(cleanFileName, itemMapByPartNo.keySet());
            matchedItems = itemMapByPartNo.get(partNoFromFileName);
        }

        if (matchedItems == null || matchedItems.isEmpty()) {
            unmatchedDrawings.add(buildFileResult(cleanFileName, partNoFromFileName, "未找到该订单对应零件"));
            return;
        }

        String fileUrl = businessFileService
                .saveDrawing(order, drawing, "ORDER_DRAWING_REPLACE", String.valueOf(order.getId()))
                .publicPath();
        for (ItemQuoteVersion item : matchedItems) {
            String key = String.valueOf(item.getId());
            if (!matchByPartNoVersion && protectedItemKeys.contains(key)) {
                protectedSkippedItems.add(buildItemResult(item, cleanFileName, "已由图号+版本图纸替换，本次按图号跳过"));
                continue;
            }
            item.setDrawingUrl(fileUrl);
            if (itemMapper.updateById(item) <= 0) {
                throw new RuntimeException("询价订单图纸替换保存失败，请刷新后重试");
            }
            if (matchByPartNoVersion) {
                protectedItemKeys.add(key);
            }
            replacedItems.add(buildItemResult(item, cleanFileName, "已替换"));
        }
    }

    private Map<String, Object> buildReplaceDrawingAuditSummary(
            Integer rfqId,
            List<MultipartFile> drawings,
            List<MultipartFile> versionDrawings,
            List<Map<String, Object>> replacedItems,
            List<Map<String, Object>> unmatchedDrawings,
            List<Map<String, Object>> skippedFiles) {
        int uploadedDrawingCount = fileCount(drawings);
        int versionUploadedDrawingCount = fileCount(versionDrawings);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("rfqId", rfqId);
        summary.put("uploadCount", uploadedDrawingCount + versionUploadedDrawingCount);
        summary.put("uploadedDrawingCount", uploadedDrawingCount);
        summary.put("versionUploadedDrawingCount", versionUploadedDrawingCount);
        summary.put("replacedCount", replacedItems.size());
        summary.put("unmatchedCount", unmatchedDrawings.size());
        summary.put("skippedCount", skippedFiles.size());
        summary.put("drawingMatchModes", Map.of(
                "partNo", uploadedDrawingCount,
                "partNoVersion", versionUploadedDrawingCount
        ));
        return summary;
    }

    private OrderItemDataReplacementExcelParseResult parseOrderItemReplacementFile(MultipartFile excelFile) {
        if (excelFile == null || excelFile.isEmpty()) {
            throw new IllegalArgumentException("表格数据替换文件不能为空");
        }
        try {
            return orderItemDataReplacementExcelParser.parse(excelFile.getInputStream());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("询价订单表格数据替换解析失败：" + e.getMessage(), e);
        }
    }

    private Map<String, Object> buildItemDataReplacementPreview(
            OrderRfq order,
            OrderItemDataReplacementExcelParseResult parsed,
            List<ItemQuoteVersion> items,
            Map<Integer, Integer> ticketedQtyByItemId) {
        Map<Integer, ItemQuoteVersion> itemsById = items.stream()
                .filter(Objects::nonNull)
                .filter(item -> item.getId() != null)
                .collect(Collectors.toMap(
                        ItemQuoteVersion::getId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new));
        Map<String, List<ItemQuoteVersion>> candidatesByKey = groupItemsByPartVersion(items);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (OrderItemDataReplacementExcelRowDTO excelRow : safeList(parsed.getRows())) {
            rows.add(previewReplacementRow(excelRow, order, itemsById, candidatesByKey, ticketedQtyByItemId));
        }
        Map<String, Object> summary = summarizeReplacementRows(rows);

        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("success", true);
        preview.put("recognizedFields", safeList(parsed.getRecognizedFields()));
        preview.put("rows", rows);
        preview.put("summary", summary);
        preview.put("canConfirm", numberValue(summary.get("errorCount")).intValue() == 0
                && numberValue(summary.get("changedCount")).intValue() > 0);
        preview.put("previewFingerprint", replacementFingerprint(order, parsed, rows, itemsById, ticketedQtyByItemId));
        return preview;
    }

    private Map<String, List<ItemQuoteVersion>> groupItemsByPartVersion(List<ItemQuoteVersion> items) {
        Map<String, List<ItemQuoteVersion>> grouped = new LinkedHashMap<>();
        for (ItemQuoteVersion item : safeList(items)) {
            grouped.computeIfAbsent(itemKey(item.getPartNo(), item.getVersion()), ignored -> new ArrayList<>()).add(item);
        }
        grouped.values().forEach(group -> group.sort(Comparator.comparing(ItemQuoteVersion::getId)));
        return grouped;
    }

    private Map<String, Object> previewReplacementRow(
            OrderItemDataReplacementExcelRowDTO excelRow,
            OrderRfq order,
            Map<Integer, ItemQuoteVersion> itemsById,
            Map<String, List<ItemQuoteVersion>> candidatesByKey,
            Map<Integer, Integer> ticketedQtyByItemId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("excelRowNumber", excelRow.getExcelRowNumber());
        row.put("partNo", excelRow.getPartNo());
        row.put("version", excelRow.getVersion());
        row.put("changes", new ArrayList<>());

        if (excelRow.hasErrors()) {
            row.put("status", "ERROR");
            row.put("reason", excelRow.getErrors().stream()
                    .map(OrderItemDataReplacementExcelErrorDTO::getMessage)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining("；")));
            return row;
        }

        ItemQuoteVersion item = null;
        if (excelRow.getItemId() != null) {
            item = itemsById.get(excelRow.getItemId());
            if (item == null) {
                row.put("status", "UNMATCHED");
                row.put("reason", "当前询价订单未找到该零件ID");
                return row;
            }
        } else {
            List<ItemQuoteVersion> candidates = candidatesByKey.getOrDefault(
                    itemKey(excelRow.getNormalizedPartNo(), excelRow.getNormalizedVersion()),
                    List.of());
            if (candidates.isEmpty()) {
                row.put("status", "UNMATCHED");
                row.put("reason", "当前询价订单未找到对应图号和版本的零件");
                return row;
            }
            if (candidates.size() > 1) {
                row.put("status", "ERROR");
                row.put("reason", "当前询价订单存在多个相同图号和版本的零件，请使用零件ID匹配");
                return row;
            }
            item = candidates.get(0);
        }

        row.put("rfqId", item.getRfqId());
        row.put("rfqNo", order.getRfqNo());
        row.put("itemId", item.getId());
        int ticketedQty = ticketedQtyByItemId.getOrDefault(item.getId(), 0);
        List<Map<String, Object>> changes = replacementChanges(item, excelRow.getValues(), ticketedQty);
        row.put("changes", changes);
        row.put("status", changes.isEmpty() ? "UNCHANGED" : "CHANGED");
        row.put("reason", "");
        return row;
    }

    private List<Map<String, Object>> replacementChanges(
            ItemQuoteVersion item,
            Map<String, Object> values,
            int ticketedQty) {
        List<Map<String, Object>> changes = new ArrayList<>();
        for (Map.Entry<String, Object> entry : safeMap(values).entrySet()) {
            String field = entry.getKey();
            Object beforeValue = currentReplacementValue(item, field);
            Object afterValue = normalizeReplacementValue(field, entry.getValue(), ticketedQty);
            if (Objects.equals(beforeValue, afterValue)) {
                continue;
            }
            Map<String, Object> change = new LinkedHashMap<>();
            change.put("field", field);
            change.put("label", replacementFieldLabel(field));
            change.put("beforeValue", beforeValue);
            change.put("afterValue", afterValue);
            changes.add(change);
        }
        return changes;
    }

    private Object currentReplacementValue(ItemQuoteVersion item, String field) {
        return switch (field) {
            case "partNo" -> item.getPartNo();
            case "version" -> item.getVersion();
            case "qty" -> item.getQty();
            case "leadTime" -> item.getLeadTime();
            case "quoteLeadTime" -> item.getQuoteLeadTime();
            case "internalTargetLeadTime" -> item.getInternalTargetLeadTime();
            case "description" -> item.getDescription();
            case "surfaceTreatment" -> item.getSurfaceTreatment();
            case "remark" -> item.getRemark();
            case "salesOrderDate" -> item.getSalesOrderDate();
            case "customerOrderNo" -> item.getCustomerOrderNo();
            case "customerProjectNo" -> item.getCustomerProjectNo();
            case "customerOrderTitle" -> item.getCustomerOrderTitle();
            case "orderType" -> item.getOrderType();
            case "internalProjectNo" -> item.getInternalProjectNo();
            default -> null;
        };
    }

    private Object normalizeReplacementValue(String field, Object value, int ticketedQty) {
        return switch (field) {
            case "partNo" -> normalizeRequiredText(stringValue(value), "图号", 100);
            case "version" -> normalizeText(stringValue(value), "版本", 20);
            case "qty" -> {
                Integer qty = objectToInteger(value);
                validateOrderItemQty(qty, ticketedQty);
                yield qty;
            }
            case "leadTime" -> normalizeLeadTime(stringValue(value), "客户询价交期");
            case "quoteLeadTime" -> normalizeLeadTime(stringValue(value), "报价承诺交期");
            case "internalTargetLeadTime" -> normalizeLeadTime(stringValue(value), "内部目标交期");
            case "description" -> normalizeText(stringValue(value), "物料描述", 500);
            case "surfaceTreatment" -> normalizeText(stringValue(value), "表面处理", 200);
            case "remark" -> normalizeText(stringValue(value), "备注", 500);
            case "salesOrderDate" -> value;
            case "customerOrderNo" -> normalizeText(stringValue(value), "客户订单号", 120);
            case "customerProjectNo" -> normalizeText(stringValue(value), "客户项目号", 120);
            case "customerOrderTitle" -> normalizeText(stringValue(value), "客户下单抬头", 255);
            case "orderType" -> normalizeOrderType(stringValue(value));
            case "internalProjectNo" -> normalizeText(stringValue(value), "内部项目号", 120);
            default -> throw new IllegalArgumentException("存在不支持的替换字段");
        };
    }

    private void applyImportedChange(ItemQuoteVersion item, String field, Object value, int ticketedQty) {
        Object normalized = normalizeReplacementValue(field, value, ticketedQty);
        switch (field) {
            case "partNo" -> item.setPartNo((String) normalized);
            case "version" -> item.setVersion((String) normalized);
            case "qty" -> item.setQty((Integer) normalized);
            case "leadTime" -> item.setLeadTime((String) normalized);
            case "quoteLeadTime" -> item.setQuoteLeadTime((String) normalized);
            case "internalTargetLeadTime" -> item.setInternalTargetLeadTime((String) normalized);
            case "description" -> item.setDescription((String) normalized);
            case "surfaceTreatment" -> item.setSurfaceTreatment((String) normalized);
            case "remark" -> item.setRemark((String) normalized);
            case "salesOrderDate" -> item.setSalesOrderDate((LocalDate) normalized);
            case "customerOrderNo" -> item.setCustomerOrderNo((String) normalized);
            case "customerProjectNo" -> item.setCustomerProjectNo((String) normalized);
            case "customerOrderTitle" -> item.setCustomerOrderTitle((String) normalized);
            case "orderType" -> item.setOrderType((String) normalized);
            case "internalProjectNo" -> item.setInternalProjectNo((String) normalized);
            default -> throw new IllegalArgumentException("存在不支持的替换字段");
        }
    }

    private String replacementFieldLabel(String field) {
        return OrderItemDataReplacementField.replaceableFields().stream()
                .filter(candidate -> candidate.getPropertyName().equals(field))
                .findFirst()
                .map(OrderItemDataReplacementField::getHeader)
                .orElse(field);
    }

    private Map<String, Object> summarizeReplacementRows(List<Map<String, Object>> rows) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("importedCount", rows.size());
        summary.put("matchedCount", 0);
        summary.put("changedCount", 0);
        summary.put("quantityOnlyCount", 0);
        summary.put("unchangedCount", 0);
        summary.put("unmatchedCount", 0);
        summary.put("skippedCount", 0);
        summary.put("errorCount", 0);
        summary.put("updatedItemCount", 0);
        summary.put("quantityAppliedCount", 0);
        for (Map<String, Object> row : rows) {
            if (row.get("itemId") != null) {
                summary.put("matchedCount", numberValue(summary.get("matchedCount")).intValue() + 1);
            }
            String status = String.valueOf(row.get("status"));
            switch (status) {
                case "CHANGED" -> summary.put("changedCount", numberValue(summary.get("changedCount")).intValue() + 1);
                case "UNCHANGED" -> summary.put("unchangedCount", numberValue(summary.get("unchangedCount")).intValue() + 1);
                case "UNMATCHED" -> summary.put("unmatchedCount", numberValue(summary.get("unmatchedCount")).intValue() + 1);
                case "SKIPPED" -> summary.put("skippedCount", numberValue(summary.get("skippedCount")).intValue() + 1);
                case "ERROR" -> summary.put("errorCount", numberValue(summary.get("errorCount")).intValue() + 1);
                default -> {
                }
            }
        }
        return summary;
    }

    private String replacementFingerprint(
            OrderRfq order,
            OrderItemDataReplacementExcelParseResult parsed,
            List<Map<String, Object>> previewRows,
            Map<Integer, ItemQuoteVersion> itemsById,
            Map<Integer, Integer> ticketedQtyByItemId) {
        StringBuilder canonical = new StringBuilder();
        AuthContext.SessionUser user = AuthContext.get();
        appendToken(canonical, "user", user == null || user.id() == null ? "anonymous" : user.id());
        appendToken(canonical, "rfqId", order.getId());
        appendToken(canonical, "rfqStatus", order.getStatus());
        appendToken(canonical, "rfqCustomer", trimToNull(order.getCustomerName()));
        safeList(parsed.getRecognizedFields()).stream()
                .sorted()
                .forEach(field -> appendToken(canonical, "field", field));

        for (Map<String, Object> row : safeList(previewRows)) {
            appendToken(canonical, "excelRow", row.get("excelRowNumber"));
            appendToken(canonical, "status", row.get("status"));
            appendToken(canonical, "itemId", row.get("itemId"));
            for (Map<String, Object> change : safeMapList(row.get("changes"))) {
                appendToken(canonical, "changeField", change.get("field"));
                appendToken(canonical, "changeAfter", canonicalValue(change.get("afterValue")));
            }
            Integer itemId = objectToInteger(row.get("itemId"));
            ItemQuoteVersion item = itemId == null ? null : itemsById.get(itemId);
            if (item != null) {
                appendToken(canonical, "currentItem", item.getId());
                appendToken(canonical, "currentPartNo", item.getPartNo());
                appendToken(canonical, "currentVersion", item.getVersion());
                appendToken(canonical, "currentQty", item.getQty());
                appendToken(canonical, "ticketedQty", ticketedQtyByItemId.getOrDefault(item.getId(), 0));
                appendToken(canonical, "currentLeadTime", item.getLeadTime());
                appendToken(canonical, "currentQuoteLeadTime", item.getQuoteLeadTime());
                appendToken(canonical, "currentInternalTargetLeadTime", item.getInternalTargetLeadTime());
                appendToken(canonical, "currentDescription", item.getDescription());
                appendToken(canonical, "currentSurfaceTreatment", item.getSurfaceTreatment());
                appendToken(canonical, "currentRemark", item.getRemark());
                appendToken(canonical, "currentSalesOrderDate", item.getSalesOrderDate());
                appendToken(canonical, "currentCustomerOrderNo", item.getCustomerOrderNo());
                appendToken(canonical, "currentCustomerProjectNo", item.getCustomerProjectNo());
                appendToken(canonical, "currentCustomerOrderTitle", item.getCustomerOrderTitle());
                appendToken(canonical, "currentOrderType", item.getOrderType());
                appendToken(canonical, "currentInternalProjectNo", item.getInternalProjectNo());
            }
        }
        return sha256(canonical.toString());
    }

    private void auditOrderSuccess(String action, OrderRfq order, Map<String, Object> summary) {
        if (auditService == null) {
            return;
        }
        auditService.record(
                action,
                "order_rfq",
                order.getId(),
                null,
                summary,
                "ORDER",
                "SUCCESS",
                AuditService.labelForAction(action),
                null);
    }

    private boolean fingerprintsEqual(String expected, String actual) {
        if (expected == null || !expected.trim().matches("(?i)[0-9a-f]{64}")) {
            throw new IllegalArgumentException("预览指纹格式不正确");
        }
        byte[] expectedBytes = HexFormat.of().parseHex(expected.trim());
        byte[] actualBytes = HexFormat.of().parseHex(actual);
        return MessageDigest.isEqual(expectedBytes, actualBytes);
    }

    private void appendToken(StringBuilder canonical, String key, Object value) {
        String text = canonicalValue(value);
        canonical.append(key.length()).append(':').append(key)
                .append(text.length()).append(':').append(text).append('\n');
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private String canonicalValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer objectToInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private <T> List<T> safeList(Collection<T> values) {
        return values == null ? List.of() : new ArrayList<>(values);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> safeMapList(Object values) {
        if (!(values instanceof Collection<?> collection)) {
            return List.of();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object value : collection) {
            if (value instanceof Map<?, ?> map) {
                rows.add((Map<String, Object>) map);
            }
        }
        return rows;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> safeMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private Map<String, Object> buildSummary(OrderRfq order) {
        ItemStats itemStats = loadItemStats(List.of(order.getId())).getOrDefault(order.getId(), new ItemStats());
        TicketStats ticketStats = loadTicketStats(List.of(order.getId())).getOrDefault(order.getId(), new TicketStats());
        return buildSummary(order, itemStats, ticketStats);
    }

    private List<Map<String, Object>> buildSummaries(List<OrderRfq> orders) {
        if (orders == null || orders.isEmpty()) {
            return List.of();
        }
        List<Integer> rfqIds = orders.stream()
                .map(OrderRfq::getId)
                .filter(id -> id != null)
                .toList();
        Map<Integer, ItemStats> itemStatsMap = loadItemStats(rfqIds);
        Map<Integer, TicketStats> ticketStatsMap = loadTicketStats(rfqIds);

        return orders.stream()
                .map(order -> buildSummary(order,
                        itemStatsMap.getOrDefault(order.getId(), new ItemStats()),
                        ticketStatsMap.getOrDefault(order.getId(), new TicketStats())))
                .toList();
    }

    private Map<String, Object> buildSummary(OrderRfq order, ItemStats itemStats, TicketStats ticketStats) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("id", order.getId());
        summary.put("rfqNo", order.getRfqNo());
        summary.put("customerName", order.getCustomerName());
        summary.put("projectName", order.getProjectName());
        summary.put("status", order.getStatus());
        summary.put("createdAt", order.getCreatedAt());
        summary.put("itemCount", itemStats.itemCount);
        summary.put("quotedCount", itemStats.quotedCount);
        summary.put("missingDrawingCount", itemStats.missingDrawingCount);
        summary.put("ticketCount", ticketStats.ticketCount);
        summary.put("ticketQty", ticketStats.ticketQty);
        summary.put("processSheetCount", ticketStats.ticketCount);
        summary.put("fileRoot", buildOrderFileRoot(order));
        return summary;
    }

    private Map<String, Object> buildReleaseRfqSourceRow(OrderRfq order) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", order.getId());
        row.put("sourceType", "RFQ");
        row.put("sourceOrderId", order.getId());
        row.put("sourceOrderNo", order.getRfqNo());
        row.put("rfqNo", order.getRfqNo());
        row.put("customerName", order.getCustomerName());
        row.put("status", order.getStatus());
        row.put("orderType", order.getOrderType());
        row.put("createdAt", order.getCreatedAt());
        row.put("sortId", order.getId());
        return row;
    }

    private Map<Integer, ItemStats> loadItemStats(List<Integer> rfqIds) {
        if (rfqIds.isEmpty()) {
            return Map.of();
        }
        QueryWrapper<ItemQuoteVersion> qw = new QueryWrapper<>();
        qw.select(
                "rfq_id",
                "COUNT(*) AS item_count",
                "SUM(CASE WHEN tax_included_unit_price IS NOT NULL THEN 1 ELSE 0 END) AS quoted_count",
                "SUM(CASE WHEN drawing_url IS NULL OR drawing_url = '' THEN 1 ELSE 0 END) AS missing_drawing_count"
        );
        qw.in("rfq_id", rfqIds);
        qw.groupBy("rfq_id");
        return itemMapper.selectMaps(qw).stream()
                .map(ItemStats::from)
                .collect(Collectors.toMap(stats -> stats.rfqId, Function.identity()));
    }

    private Map<Integer, TicketStats> loadTicketStats(List<Integer> rfqIds) {
        if (rfqIds.isEmpty()) {
            return Map.of();
        }
        QueryWrapper<OrderDeliveryTicket> qw = new QueryWrapper<>();
        qw.select("rfq_id", "COUNT(*) AS ticket_count", "COALESCE(SUM(qty), 0) AS ticket_qty");
        qw.in("rfq_id", rfqIds);
        qw.groupBy("rfq_id");
        return ticketMapper.selectMaps(qw).stream()
                .map(TicketStats::from)
                .collect(Collectors.toMap(stats -> stats.rfqId, Function.identity()));
    }

    private void applyOrderFilters(QueryWrapper<OrderRfq> qw, String customerCode, String customerName, String keyword, String status) {
        if (!isBlank(customerCode)) {
            qw.eq("customer_code", customerCode.trim());
        } else if (!isBlank(customerName)) {
            qw.eq("customer_name", customerName.trim());
        }
        if (!isBlank(status)) {
            qw.eq("status", status.trim());
        }
        if (!isBlank(keyword)) {
            String text = keyword.trim();
            qw.and(wrapper -> wrapper
                    .like("rfq_no", text)
                    .or()
                    .like("status", text));
        }
    }

    private int normalizePage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 20;
        }
        return Math.min(pageSize, 100);
    }

    private <T> PageResult<T> pageRows(List<T> allRows, Integer page, Integer pageSize) {
        List<T> rows = allRows == null ? List.of() : allRows;
        int safePage = normalizePage(page);
        int safePageSize = pageSize == null || pageSize < 1 ? 50 : Math.min(pageSize, 100);
        int fromIndex = (int) Math.min((long) (safePage - 1) * safePageSize, rows.size());
        int toIndex = Math.min(fromIndex + safePageSize, rows.size());
        return PageResult.of(rows.subList(fromIndex, toIndex), rows.size(), safePage, safePageSize);
    }

    private static Number numberValue(Object value) {
        return value instanceof Number number ? number : 0;
    }

    private static class ItemStats {
        private Integer rfqId;
        private int itemCount;
        private int quotedCount;
        private int missingDrawingCount;

        private static ItemStats from(Map<String, Object> row) {
            ItemStats stats = new ItemStats();
            stats.rfqId = numberValue(row.get("rfq_id")).intValue();
            stats.itemCount = numberValue(row.get("item_count")).intValue();
            stats.quotedCount = numberValue(row.get("quoted_count")).intValue();
            stats.missingDrawingCount = numberValue(row.get("missing_drawing_count")).intValue();
            return stats;
        }
    }

    private static class TicketStats {
        private Integer rfqId;
        private int ticketCount;
        private int ticketQty;

        private static TicketStats from(Map<String, Object> row) {
            TicketStats stats = new TicketStats();
            stats.rfqId = numberValue(row.get("rfq_id")).intValue();
            stats.ticketCount = numberValue(row.get("ticket_count")).intValue();
            stats.ticketQty = numberValue(row.get("ticket_qty")).intValue();
            return stats;
        }
    }

    private OrderRfq requireOrder(Integer rfqId) {
        if (rfqId == null) {
            throw new RuntimeException("订单 ID 不能为空");
        }
        OrderRfq order = orderMapper.selectById(rfqId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        assertOrderDataScope(order);
        return order;
    }

    private OrderRfq requireOrderForUpdate(Integer rfqId) {
        if (rfqId == null) {
            throw new RuntimeException("订单 ID 不能为空");
        }
        OrderRfq order = orderMapper.selectByIdForUpdate(rfqId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        assertOrderDataScope(order);
        return order;
    }

    private void applyOrderDataScope(QueryWrapper<OrderRfq> qw) {
        if (dataScopeService != null) {
            dataScopeService.applyOrderScope(qw);
        }
    }

    private void assertOrderDataScope(OrderRfq order) {
        if (dataScopeService != null) {
            dataScopeService.assertCanAccessOrder(order);
        }
    }
    private List<ItemQuoteVersion> listItems(Integer rfqId) {
        QueryWrapper<ItemQuoteVersion> qw = new QueryWrapper<>();
        qw.eq("rfq_id", rfqId).orderByAsc("id");
        return itemMapper.selectList(qw);
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

    private String normalizeVersion(String version) {
        return DrawingPartNoResolver.normalizeVersion(version);
    }

    private String itemKey(String partNo, String version) {
        return normalizePartNo(partNo) + "||" + normalizeVersion(version);
    }

    private boolean isEmptyFiles(List<MultipartFile> files) {
        return files == null || files.isEmpty();
    }

    private int fileCount(List<MultipartFile> files) {
        return files == null ? 0 : files.size();
    }

    private Map<String, Object> buildSupplementDrawingAuditSummary(
            Integer rfqId,
            List<MultipartFile> drawings,
            List<MultipartFile> versionDrawings,
            List<Map<String, Object>> boundItems,
            List<Map<String, Object>> alreadyBoundItems,
            List<Map<String, Object>> unmatchedDrawings,
            List<Map<String, Object>> skippedFiles,
            int restoredUnpricedCount) {
        int uploadedDrawingCount = fileCount(drawings);
        int versionUploadedDrawingCount = fileCount(versionDrawings);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("rfqId", rfqId);
        summary.put("uploadCount", uploadedDrawingCount + versionUploadedDrawingCount);
        summary.put("uploadedDrawingCount", uploadedDrawingCount);
        summary.put("versionUploadedDrawingCount", versionUploadedDrawingCount);
        summary.put("boundCount", boundItems.size());
        summary.put("alreadyBoundCount", alreadyBoundItems.size());
        summary.put("unmatchedCount", unmatchedDrawings.size());
        summary.put("skippedCount", skippedFiles.size());
        summary.put("restoredUnpricedCount", restoredUnpricedCount);
        summary.put("drawingMatchModes", Map.of(
                "partNo", uploadedDrawingCount,
                "partNoVersion", versionUploadedDrawingCount
        ));
        return summary;
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

    private Map<String, Object> buildMissingAppendItem(RfqExcelDTO item, String normalizedPartNo) {
        Map<String, Object> missingItem = new LinkedHashMap<>();
        String partNo = item == null ? null : item.getPartNo();
        missingItem.put("partNo", isBlank(partNo) ? normalizedPartNo : partNo.trim());
        missingItem.put("version", item == null ? "" : normalizeVersion(item.getVersion()));
        missingItem.put("description", item == null ? null : item.getDescription());
        missingItem.put("qty", item == null ? null : item.getQty());
        return missingItem;
    }

    private record AppendDrawingFileInfo(
            String partNo,
            String version,
            String originalFilename,
            String url) {
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean matchesCustomerKeyword(String customerName, String field, String keyword) {
        if (isBlank(keyword)) {
            return true;
        }
        String normalizedField = field == null ? "all" : field.replace("_", "").replace("-", "").trim().toLowerCase();
        if (!"all".equals(normalizedField) && !"name".equals(normalizedField)) {
            return false;
        }
        return customerName != null && customerName.toLowerCase().contains(keyword.trim().toLowerCase());
    }

    private CustomerOptionDTO toCustomerOption(String customerName) {
        CustomerOptionDTO option = new CustomerOptionDTO();
        option.setCustomerCode(customerName);
        option.setCustomerShortName(customerName);
        option.setCustomerName(customerName);
        return option;
    }
}
