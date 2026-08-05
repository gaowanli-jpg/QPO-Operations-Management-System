package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.config.AuthContext;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.PermissionInfoDTO;
import com.machining.quote.dto.dashboard.DashboardAttentionItemDTO;
import com.machining.quote.dto.dashboard.DashboardChartItemDTO;
import com.machining.quote.dto.dashboard.DashboardCustomerCountRow;
import com.machining.quote.dto.dashboard.DashboardCustomerMatrixRowDTO;
import com.machining.quote.dto.dashboard.DashboardOverviewDTO;
import com.machining.quote.dto.dashboard.DashboardStatusCountRow;
import com.machining.quote.dto.dashboard.DashboardSummaryCardDTO;
import com.machining.quote.dto.dashboard.DashboardTodoCardDTO;
import com.machining.quote.mapper.DashboardAggregationMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
public class DashboardOverviewService {
    private static final List<String> RFQ_DASHBOARD_STATUSES = List.of(
            QuoteStatusWorkflow.UNPRICED,
            QuoteStatusWorkflow.PRICED,
            QuoteStatusWorkflow.QUOTED
    );
    private static final int SALES_CUSTOMER_CHART_LIMIT = 8;
    private static final int CUSTOMER_MATRIX_LIMIT = 20;
    private static final List<String> RFQ_DOMAIN_PERMISSIONS = List.of(
            "market:order:view",
            "engineer:setup:view",
            "engineer:pricing:view",
            "market:quote-process:view",
            "market:release:view"
    );
    private static final List<String> SALES_DOMAIN_PERMISSIONS = List.of(
            "market:sales-order:view",
            "market:sales-match:view",
            "market:release:view",
            "market:u9-sync:view"
    );
    private static final List<String> DELIVERED_DOMAIN_PERMISSIONS = List.of("market:delivered-item:view");
    private static final List<String> PRODUCTION_DOMAIN_PERMISSIONS = List.of("market:production-item:view");
    private static final List<String> U9_DOMAIN_PERMISSIONS = List.of("market:u9-sync:view");

    @Autowired(required = false)
    private DashboardAggregationMapper aggregationMapper;
    @Autowired(required = false)
    private DataScopeService dataScopeService;
    @Autowired(required = false)
    private PermissionService permissionService;

    public DashboardOverviewService() {
    }

    public DashboardOverviewService(DashboardAggregationMapper aggregationMapper, DataScopeService dataScopeService) {
        this.aggregationMapper = aggregationMapper;
        this.dataScopeService = dataScopeService;
    }

    public DashboardOverviewService(
            DashboardAggregationMapper aggregationMapper,
            DataScopeService dataScopeService,
            PermissionService permissionService) {
        this.aggregationMapper = aggregationMapper;
        this.dataScopeService = dataScopeService;
        this.permissionService = permissionService;
    }

    public DashboardOverviewDTO overview() {
        DashboardOverviewDTO overview = DashboardOverviewDTO.empty();
        if (aggregationMapper == null) {
            return overview;
        }

        PermissionGate permissionGate = currentPermissionGate();
        boolean canSeeRfq = permissionGate.hasAny(RFQ_DOMAIN_PERMISSIONS);
        boolean canSeeSales = permissionGate.hasAny(SALES_DOMAIN_PERMISSIONS);
        boolean canSeeDelivered = permissionGate.hasAny(DELIVERED_DOMAIN_PERMISSIONS);
        boolean canSeeProduction = permissionGate.hasAny(PRODUCTION_DOMAIN_PERMISSIONS);
        boolean canSeeU9 = permissionGate.hasAny(U9_DOMAIN_PERMISSIONS);

        List<DashboardStatusCountRow> rfqStatusRows = canSeeRfq
                ? safeList(aggregationMapper.countRfqByStatus(rfqStatusQuery()))
                : List.of();
        List<DashboardCustomerCountRow> salesQuotedRows = canSeeSales
                ? safeList(aggregationMapper.countSalesQuotedByCustomer(salesQuotedQuery()))
                : List.of();
        List<DashboardCustomerCountRow> rfqCustomerRows = canSeeRfq
                ? safeList(aggregationMapper.countRfqByCustomerAndStatus(rfqCustomerQuery()))
                : List.of();

        long rfqUnpriced = countForStatus(rfqStatusRows, QuoteStatusWorkflow.UNPRICED);
        long rfqPriced = countForStatus(rfqStatusRows, QuoteStatusWorkflow.PRICED);
        long rfqQuoted = countForStatus(rfqStatusRows, QuoteStatusWorkflow.QUOTED);
        long salesQuoted = salesQuotedRows.stream().mapToLong(this::rowCount).sum();
        long deliveredActive = canSeeDelivered ? count(() -> aggregationMapper.countDeliveredActive(deliveredActiveQuery())) : 0L;
        long lockedProductionTodo = canSeeProduction ? count(() -> aggregationMapper.countLockedProductionTodo(lockedProductionTodoQuery())) : 0L;
        long deliveredNeedMaterial = canSeeDelivered ? count(() -> aggregationMapper.countDeliveredNeedMaterial(deliveredNeedMaterialQuery())) : 0L;
        long deliveredNeedWms = canSeeDelivered ? count(() -> aggregationMapper.countDeliveredNeedWms(deliveredNeedWmsQuery())) : 0L;
        long mesCallbackPending = canSeeDelivered ? count(() -> aggregationMapper.countDeliveredMesCallbackPending(deliveredMesStatusQuery("CALLBACK_PENDING"))) : 0L;
        long mesFailed = canSeeDelivered ? count(() -> aggregationMapper.countDeliveredMesFailed(deliveredMesStatusQuery("FAILED"))) : 0L;
        long wmsFailed = canSeeDelivered ? count(() -> aggregationMapper.countDeliveredWmsFailed(deliveredWmsStatusQuery("FAILED"))) : 0L;
        long lockedProductionFailed = canSeeProduction ? count(() -> aggregationMapper.countLockedMesProductionFailed(lockedProductionFailedQuery())) : 0L;
        long salesMatchTodo = permissionGate.canAccess("market:sales-match:view")
                ? count(() -> aggregationMapper.countSalesMatchTodo(salesMatchTodoQuery()))
                : 0L;
        long u9Ready = canSeeU9 ? count(() -> aggregationMapper.countU9Ready(u9ReadyQuery())) : 0L;
        long u9Failed = canSeeU9 ? count(() -> aggregationMapper.countU9Failed(u9FailedQuery())) : 0L;

        overview.setRfqStatusDistribution(canSeeRfq ? rfqStatusDistribution(rfqStatusRows) : List.of());
        overview.setSalesQuotedCustomerDistribution(canSeeSales ? salesCustomerDistribution(salesQuotedRows) : List.of());
        overview.setCustomerMatrix(customerMatrix(rfqCustomerRows, salesQuotedRows));
        overview.setSummaryCards(filterSummaryCards(
                summaryCards(rfqUnpriced + rfqPriced + rfqQuoted, salesQuoted, deliveredActive, lockedProductionTodo),
                permissionGate));
        overview.setTodoCards(filterTodoCards(
                todoCards(rfqUnpriced, rfqPriced, rfqQuoted, salesMatchTodo, deliveredNeedMaterial,
                        deliveredNeedWms, lockedProductionTodo, u9Ready),
                permissionGate));
        overview.setAttentionItems(filterAttentionItems(
                attentionItems(mesCallbackPending, mesFailed, wmsFailed, u9Failed, lockedProductionFailed),
                permissionGate));
        return overview;
    }

    private PermissionGate currentPermissionGate() {
        if (permissionService == null) {
            return PermissionGate.fullAccess();
        }
        AuthContext.SessionUser user = AuthContext.get();
        if (user == null) {
            return PermissionGate.fullAccess();
        }
        PermissionInfoDTO permissionInfo = permissionService.getPermissionInfo(user.id(), user.role());
        if (permissionInfo == null) {
            return PermissionGate.none();
        }
        if (permissionInfo.isSuperAdmin()) {
            return PermissionGate.fullAccess();
        }
        return new PermissionGate(false, new LinkedHashSet<>(safeList(permissionInfo.getPermissions())));
    }

    private List<DashboardSummaryCardDTO> filterSummaryCards(
            List<DashboardSummaryCardDTO> cards,
            PermissionGate permissionGate) {
        return safeList(cards).stream()
                .filter(card -> permissionGate.canAccess(card.getPermission()))
                .toList();
    }

    private List<DashboardTodoCardDTO> filterTodoCards(
            List<DashboardTodoCardDTO> cards,
            PermissionGate permissionGate) {
        return safeList(cards).stream()
                .filter(card -> permissionGate.canAccess(card.getPermission()))
                .toList();
    }

    private List<DashboardAttentionItemDTO> filterAttentionItems(
            List<DashboardAttentionItemDTO> items,
            PermissionGate permissionGate) {
        return safeList(items).stream()
                .filter(item -> permissionGate.canAccess(item.getPermission()))
                .toList();
    }

    private QueryWrapper<Object> rfqStatusQuery() {
        QueryWrapper<Object> query = new QueryWrapper<>();
        query.in("rfq.status", RFQ_DASHBOARD_STATUSES);
        applyOrderScope(query);
        return query;
    }

    private QueryWrapper<Object> rfqCustomerQuery() {
        return rfqStatusQuery();
    }

    private QueryWrapper<Object> salesQuotedQuery() {
        QueryWrapper<Object> query = new QueryWrapper<>();
        query.eq("so.status", QuoteStatusWorkflow.QUOTED);
        applySalesOrderScope(query);
        return query;
    }

    private QueryWrapper<Object> salesMatchTodoQuery() {
        QueryWrapper<Object> query = salesQuotedQuery();
        query.and(wrapper -> wrapper
                .apply("COALESCE(so.total_item_count, 0) = 0")
                .or()
                .apply("COALESCE(so.matched_item_count, 0) = 0")
                .or()
                .apply("COALESCE(so.unmatched_item_count, 0) > 0"));
        return query;
    }

    private QueryWrapper<Object> u9ReadyQuery() {
        QueryWrapper<Object> query = new QueryWrapper<>();
        query.eq("so.status", QuoteStatusWorkflow.RELEASED);
        query.and(wrapper -> wrapper.isNull("so.u9_sync_status").or().ne("so.u9_sync_status", "已同步"));
        query.apply("""
                EXISTS (SELECT 1 FROM sales_order_item soi WHERE soi.sales_order_id = so.id)
                AND NOT EXISTS (
                    SELECT 1 FROM sales_order_item soi
                    WHERE soi.sales_order_id = so.id
                    AND (soi.material_no IS NULL OR BTRIM(soi.material_no) = '' OR COALESCE(soi.material_no_status, '') <> '已确认')
                )
                """);
        applySalesOrderScope(query);
        return query;
    }

    private QueryWrapper<Object> u9FailedQuery() {
        QueryWrapper<Object> query = new QueryWrapper<>();
        query.eq("so.u9_sync_status", "同步失败");
        applySalesOrderScope(query);
        return query;
    }

    private QueryWrapper<Object> deliveredActiveQuery() {
        QueryWrapper<Object> query = new QueryWrapper<>();
        query.and(active -> active
                .apply("NULLIF(BTRIM(ticket.material_no), '') IS NULL")
                .or()
                .apply("COALESCE(ticket.mes_sync_status, 'PENDING') <> 'SUCCESS'")
                .or()
                .apply("COALESCE(ticket.wms_allocate_status, 'PENDING') <> 'SUCCESS'")
                .or()
                .apply("NOT EXISTS (SELECT 1 FROM order_delivery_ticket_allocation alloc_done WHERE alloc_done.ticket_id = ticket.id)"));
        applyDeliveryTicketScope(query);
        return query;
    }

    private QueryWrapper<Object> deliveredNeedMaterialQuery() {
        QueryWrapper<Object> query = new QueryWrapper<>();
        query.and(needMaterial -> needMaterial
                .apply("NULLIF(BTRIM(ticket.material_no), '') IS NULL")
                .or()
                .apply("COALESCE(ticket.mes_sync_status, 'PENDING') <> 'SUCCESS'"));
        applyDeliveryTicketScope(query);
        return query;
    }

    private QueryWrapper<Object> deliveredNeedWmsQuery() {
        QueryWrapper<Object> query = new QueryWrapper<>();
        query.apply("NULLIF(BTRIM(ticket.material_no), '') IS NOT NULL");
        query.eq("ticket.mes_sync_status", "SUCCESS");
        query.apply("COALESCE(ticket.wms_allocate_status, 'PENDING') <> 'SUCCESS'");
        applyDeliveryTicketScope(query);
        return query;
    }

    private QueryWrapper<Object> deliveredMesStatusQuery(String status) {
        QueryWrapper<Object> query = new QueryWrapper<>();
        query.eq("ticket.mes_sync_status", status);
        applyDeliveryTicketScope(query);
        return query;
    }

    private QueryWrapper<Object> deliveredWmsStatusQuery(String status) {
        QueryWrapper<Object> query = new QueryWrapper<>();
        query.eq("ticket.wms_allocate_status", status);
        applyDeliveryTicketScope(query);
        return query;
    }

    private QueryWrapper<Object> lockedProductionTodoQuery() {
        QueryWrapper<Object> query = new QueryWrapper<>();
        query.eq("alloc.allocation_type", "PRODUCTION_REQUIRED");
        query.and(wrapper -> wrapper.isNull("alloc.mes_production_status").or().ne("alloc.mes_production_status", "SUCCESS"));
        applyDeliveryTicketScope(query);
        return query;
    }

    private QueryWrapper<Object> lockedProductionFailedQuery() {
        QueryWrapper<Object> query = new QueryWrapper<>();
        query.eq("alloc.allocation_type", "PRODUCTION_REQUIRED");
        query.eq("alloc.mes_production_status", "FAILED");
        applyDeliveryTicketScope(query);
        return query;
    }

    private void applyOrderScope(QueryWrapper<Object> query) {
        if (dataScopeService != null) {
            dataScopeService.applyOrderScope(query, "rfq");
        }
    }

    private void applySalesOrderScope(QueryWrapper<Object> query) {
        if (dataScopeService != null) {
            dataScopeService.applySalesOrderScope(query, "so");
        }
    }

    private void applyDeliveryTicketScope(QueryWrapper<Object> query) {
        if (dataScopeService != null) {
            dataScopeService.applyDeliveryTicketScope(query, "ticket");
        }
    }

    private List<DashboardChartItemDTO> rfqStatusDistribution(List<DashboardStatusCountRow> rows) {
        Map<String, Long> counts = safeList(rows).stream()
                .filter(row -> RFQ_DASHBOARD_STATUSES.contains(row.getStatus()))
                .collect(Collectors.toMap(DashboardStatusCountRow::getStatus, this::rowCount, Long::sum, LinkedHashMap::new));
        List<DashboardChartItemDTO> result = new ArrayList<>();
        result.add(rfqChartItem(QuoteStatusWorkflow.UNPRICED, counts.getOrDefault(QuoteStatusWorkflow.UNPRICED, 0L), "/engineering"));
        result.add(rfqChartItem(QuoteStatusWorkflow.PRICED, counts.getOrDefault(QuoteStatusWorkflow.PRICED, 0L), "/quote-process"));
        result.add(rfqChartItem(QuoteStatusWorkflow.QUOTED, counts.getOrDefault(QuoteStatusWorkflow.QUOTED, 0L), "/release"));
        return result;
    }

    private DashboardChartItemDTO rfqChartItem(String status, long value, String targetPath) {
        DashboardChartItemDTO item = new DashboardChartItemDTO();
        item.setName(status);
        item.setValue(value);
        item.setTargetPath(targetPath);
        item.setQuery(query("status", status));
        return item;
    }

    private List<DashboardChartItemDTO> salesCustomerDistribution(List<DashboardCustomerCountRow> rows) {
        List<DashboardCustomerCountRow> ordered = new ArrayList<>(safeList(rows));
        ordered.sort(Comparator.comparingLong((DashboardCustomerCountRow row) -> rowCount(row)).reversed());
        List<DashboardChartItemDTO> result = new ArrayList<>();
        long otherCount = 0L;
        for (int i = 0; i < ordered.size(); i++) {
            DashboardCustomerCountRow row = ordered.get(i);
            if (i >= SALES_CUSTOMER_CHART_LIMIT) {
                otherCount += rowCount(row);
                continue;
            }
            result.add(salesCustomerChartItem(row));
        }
        if (otherCount > 0) {
            DashboardChartItemDTO other = new DashboardChartItemDTO();
            other.setName("其他客户");
            other.setCustomerName("其他客户");
            other.setValue(otherCount);
            other.setTargetPath("/sales-orders");
            other.setQuery(query("status", QuoteStatusWorkflow.QUOTED));
            result.add(other);
        }
        return result;
    }

    private DashboardChartItemDTO salesCustomerChartItem(DashboardCustomerCountRow row) {
        DashboardChartItemDTO item = new DashboardChartItemDTO();
        item.setCustomerId(row.getCustomerId());
        item.setCustomerCode(row.getCustomerCode());
        item.setCustomerName(customerName(row));
        item.setCustomerShortName(row.getCustomerShortName());
        item.setName(displayCustomerName(row));
        item.setValue(rowCount(row));
        item.setTargetPath("/sales-orders");
        item.setQuery(query("status", QuoteStatusWorkflow.QUOTED, "customerId", row.getCustomerId(), "customerName", customerName(row)));
        return item;
    }

    private List<DashboardCustomerMatrixRowDTO> customerMatrix(
            List<DashboardCustomerCountRow> rfqRows,
            List<DashboardCustomerCountRow> salesRows) {
        Map<String, DashboardCustomerMatrixRowDTO> rows = new LinkedHashMap<>();
        for (DashboardCustomerCountRow row : safeList(rfqRows)) {
            if (!RFQ_DASHBOARD_STATUSES.contains(row.getStatus())) {
                continue;
            }
            DashboardCustomerMatrixRowDTO target = rows.computeIfAbsent(customerKey(row), ignored -> matrixRow(row));
            if (QuoteStatusWorkflow.UNPRICED.equals(row.getStatus())) {
                target.setRfqUnpriced(target.getRfqUnpriced() + rowCount(row));
            } else if (QuoteStatusWorkflow.PRICED.equals(row.getStatus())) {
                target.setRfqPriced(target.getRfqPriced() + rowCount(row));
            } else if (QuoteStatusWorkflow.QUOTED.equals(row.getStatus())) {
                target.setRfqQuoted(target.getRfqQuoted() + rowCount(row));
            }
        }
        for (DashboardCustomerCountRow row : safeList(salesRows)) {
            DashboardCustomerMatrixRowDTO target = rows.computeIfAbsent(customerKey(row), ignored -> matrixRow(row));
            target.setSalesQuoted(target.getSalesQuoted() + rowCount(row));
        }
        return rows.values().stream()
                .peek(row -> row.setTotalFocus(row.getRfqUnpriced() + row.getRfqPriced() + row.getRfqQuoted() + row.getSalesQuoted()))
                .sorted(Comparator.comparingLong(DashboardCustomerMatrixRowDTO::getTotalFocus).reversed())
                .limit(CUSTOMER_MATRIX_LIMIT)
                .toList();
    }

    private DashboardCustomerMatrixRowDTO matrixRow(DashboardCustomerCountRow source) {
        DashboardCustomerMatrixRowDTO row = new DashboardCustomerMatrixRowDTO();
        row.setCustomerId(source.getCustomerId());
        row.setCustomerCode(source.getCustomerCode());
        row.setCustomerName(customerName(source));
        row.setCustomerShortName(source.getCustomerShortName());
        return row;
    }

    private List<DashboardSummaryCardDTO> summaryCards(long rfqTodo, long salesQuoted, long deliveredActive, long lockedProduction) {
        return List.of(
                summaryCard("rfqTodo", "询价待推进", rfqTodo, "单", "未核价、已核价、已报价",
                        "优先关注工程核价积压", "primary", "/orders", query("status", QuoteStatusWorkflow.UNPRICED), "market:order:view"),
                summaryCard("salesQuoted", "销售已报价", salesQuoted, "单", "按客户查看销售订单",
                        "进入销售订单继续提单或治理", "success", "/sales-orders", query("status", QuoteStatusWorkflow.QUOTED), "market:sales-order:view"),
                summaryCard("deliveredActive", "已提未完成", deliveredActive, "行", "等待物料号或库存信息",
                        "处理 MES / WMS 待办", "warning", "/delivered-items", query("flowScope", "ACTIVE"), "market:delivered-item:view"),
                summaryCard("lockedProduction", "待生产下发", lockedProduction, "行", "WMS 锁库后仍需生产",
                        "进入待产零件下发生产", "info", "/production-items", query("mesProductionStatus", "PENDING"), "market:production-item:view")
        );
    }

    private DashboardSummaryCardDTO summaryCard(
            String key,
            String title,
            long value,
            String unit,
            String helper,
            String trendText,
            String tone,
            String path,
            Map<String, Object> query,
            String permission) {
        DashboardSummaryCardDTO card = new DashboardSummaryCardDTO();
        card.setKey(key);
        card.setTitle(title);
        card.setValue(value);
        card.setUnit(unit);
        card.setHelper(helper);
        card.setTrendText(trendText);
        card.setTone(tone);
        card.setTarget(target(path, query));
        card.setPermission(permission);
        return card;
    }

    private List<DashboardTodoCardDTO> todoCards(
            long rfqUnpriced,
            long rfqPriced,
            long rfqQuoted,
            long salesMatchTodo,
            long deliveredNeedMaterial,
            long deliveredNeedWms,
            long lockedProductionTodo,
            long u9Ready) {
        return List.of(
                todoCard("rfqPricing", "待核价询价订单", rfqUnpriced, "单", "进入零件核价处理未核价订单",
                        "/engineering", query("status", QuoteStatusWorkflow.UNPRICED), "engineer:setup:view", "info"),
                todoCard("rfqQuote", "待报价询价订单", rfqPriced, "单", "进入报价处理已核价订单",
                        "/quote-process", query("status", QuoteStatusWorkflow.PRICED), "market:quote-process:view", "primary"),
                todoCard("rfqRelease", "可提单询价订单", rfqQuoted, "单", "进入议价提单处理已报价询价订单",
                        "/release", query("sourceType", "RFQ", "status", QuoteStatusWorkflow.QUOTED), "market:release:view", "success"),
                todoCard("salesMatch", "待补全销售订单", salesMatchTodo, "单", "按销售订单补全真实材料和生产快照",
                        "/sales-match", query("status", QuoteStatusWorkflow.QUOTED), "market:sales-match:view", "primary"),
                todoCard("materialTodo", "待物料号已提零件", deliveredNeedMaterial, "行", "进入已提零件获取物料号",
                        "/delivered-items", query("flowStage", "NEED_MATERIAL"), "market:delivered-item:view", "warning"),
                todoCard("wmsTodo", "待锁库已提零件", deliveredNeedWms, "行", "进入已提零件获取库存信息",
                        "/delivered-items", query("flowStage", "NEED_WMS"), "market:delivered-item:view", "warning"),
                todoCard("mesProductionTodo", "待下发 MES 生产", lockedProductionTodo, "行", "进入待产零件下发 MES 生产",
                        "/production-items", query("mesProductionStatus", "PENDING"), "market:production-item:view", "info"),
                todoCard("u9SyncReady", "可同步 U9 订单", u9Ready, "单", "整单物料号就绪后进入 U9 同步",
                        "/u9-sync", query("materialReadyStatus", "READY"), "market:u9-sync:view", "success")
        );
    }

    private DashboardTodoCardDTO todoCard(
            String key,
            String title,
            long value,
            String unit,
            String description,
            String path,
            Map<String, Object> query,
            String permission,
            String tone) {
        DashboardTodoCardDTO card = new DashboardTodoCardDTO();
        card.setKey(key);
        card.setTitle(title);
        card.setValue(value);
        card.setUnit(unit);
        card.setDescription(description);
        card.setPath(path);
        card.setQuery(query);
        card.setPermission(permission);
        card.setTone(tone);
        return card;
    }

    private List<DashboardAttentionItemDTO> attentionItems(
            long mesCallbackPending,
            long mesFailed,
            long wmsFailed,
            long u9Failed,
            long mesProductionFailed) {
        return List.of(
                attentionItem("mesCallbackPending", "待 MES 回调", mesCallbackPending, "行",
                        "物料号请求已提交，等待数据中心转回结果", "/delivered-items",
                        query("flowStage", "MES_CALLBACK_PENDING"), "market:delivered-item:view", "warning"),
                attentionItem("mesFailed", "MES 物料号失败", mesFailed, "行",
                        "进入已提零件排查或重新获取物料号", "/delivered-items",
                        query("flowStage", "MES_FAILED"), "market:delivered-item:view", "danger"),
                attentionItem("wmsFailed", "WMS 库存失败", wmsFailed, "行",
                        "进入已提零件重试库存信息获取", "/delivered-items",
                        query("flowStage", "WMS_FAILED"), "market:delivered-item:view", "danger"),
                attentionItem("u9Failed", "U9 同步失败", u9Failed, "单",
                        "销售订单同步 U9 失败，可进入 U9 同步重试", "/u9-sync",
                        query("u9SyncStatus", "同步失败"), "market:u9-sync:view", "danger"),
                attentionItem("mesProductionFailed", "MES 生产下发失败", mesProductionFailed, "行",
                        "待产零件下发 MES 失败", "/production-items",
                        query("mesProductionStatus", "FAILED"), "market:production-item:view", "danger")
        );
    }

    private DashboardAttentionItemDTO attentionItem(
            String key,
            String title,
            long value,
            String unit,
            String description,
            String path,
            Map<String, Object> query,
            String permission,
            String severity) {
        DashboardAttentionItemDTO item = new DashboardAttentionItemDTO();
        item.setKey(key);
        item.setTitle(title);
        item.setValue(value);
        item.setUnit(unit);
        item.setDescription(description);
        item.setPath(path);
        item.setQuery(query);
        item.setPermission(permission);
        item.setSeverity(severity);
        return item;
    }

    private Map<String, Object> target(String path, Map<String, Object> query) {
        Map<String, Object> target = new LinkedHashMap<>();
        target.put("path", path);
        target.put("query", query);
        return target;
    }

    private Map<String, Object> query(Object... values) {
        Map<String, Object> query = new LinkedHashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            if (values[i + 1] != null) {
                query.put(String.valueOf(values[i]), values[i + 1]);
            }
        }
        return query;
    }

    private long countForStatus(List<DashboardStatusCountRow> rows, String status) {
        return safeList(rows).stream()
                .filter(row -> status.equals(row.getStatus()))
                .mapToLong(this::rowCount)
                .sum();
    }

    private long rowCount(DashboardStatusCountRow row) {
        return row == null || row.getCount() == null ? 0L : row.getCount();
    }

    private long rowCount(DashboardCustomerCountRow row) {
        return row == null || row.getCount() == null ? 0L : row.getCount();
    }

    private long count(Supplier<Long> supplier) {
        Long value = supplier.get();
        return value == null ? 0L : value;
    }

    private String customerKey(DashboardCustomerCountRow row) {
        if (row.getCustomerId() != null) {
            return "id:" + row.getCustomerId();
        }
        return "name:" + displayCustomerName(row);
    }

    private String displayCustomerName(DashboardCustomerCountRow row) {
        String shortName = row == null ? null : row.getCustomerShortName();
        if (hasText(shortName)) {
            return shortName.trim();
        }
        return customerName(row);
    }

    private String customerName(DashboardCustomerCountRow row) {
        String customerName = row == null ? null : row.getCustomerName();
        return hasText(customerName) ? customerName.trim() : "未识别客户";
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private record PermissionGate(boolean allowAll, Set<String> permissions) {
        private static PermissionGate fullAccess() {
            return new PermissionGate(true, Set.of());
        }

        private static PermissionGate none() {
            return new PermissionGate(false, Set.of());
        }

        private boolean hasAny(List<String> permissionCodes) {
            return allowAll || safePermissions(permissionCodes).stream().anyMatch(this::canAccess);
        }

        private boolean canAccess(String permissionCode) {
            return allowAll || permissionCode == null || permissionCode.isBlank() || permissions.contains(permissionCode);
        }

        private static List<String> safePermissions(List<String> permissionCodes) {
            return permissionCodes == null ? List.of() : permissionCodes;
        }
    }
}
