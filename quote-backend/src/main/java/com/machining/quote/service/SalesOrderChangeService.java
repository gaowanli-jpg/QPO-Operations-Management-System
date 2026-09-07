package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.dto.CustomerOptionDTO;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.SalesOrderChangeOrderRow;
import com.machining.quote.dto.SalesOrderChangePreviewResp;
import com.machining.quote.dto.SalesOrderU9SyncPreviewLine;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class SalesOrderChangeService {
    private static final String STATUS_SYNCED = "已同步";
    private static final String SOURCE_RFQ_RELEASE = "RFQ_RELEASE";
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    @Autowired
    private SalesOrderHeaderMapper headerMapper;
    @Autowired
    private SalesOrderItemMapper itemMapper;
    @Autowired(required = false)
    private DataScopeService dataScopeService;

    public PageResult<CustomerOptionDTO> pageCustomerOptions(String field, String keyword, Integer page, Integer pageSize) {
        if (dataScopeService == null) {
            return PageResult.of(List.of(), 0, normalizePage(page), normalizePageSize(pageSize));
        }
        return dataScopeService.pageCustomerOptions(field, keyword, page, pageSize);
    }

    public PageResult<SalesOrderChangeOrderRow> listOrders(
            String customerCode,
            String customerName,
            String keyword,
            String status,
            Integer page,
            Integer pageSize) {
        int safePage = normalizePage(page);
        int safePageSize = normalizePageSize(pageSize);
        QueryWrapper<SalesOrderHeader> query = new QueryWrapper<>();
        applySalesOrderScope(query);
        query.eq("u9_sync_status", STATUS_SYNCED);
        applyListFilters(query, customerCode, customerName, keyword, status);

        Long total = headerMapper.selectCount(query);
        if (total == null || total == 0) {
            return PageResult.of(List.of(), 0, safePage, safePageSize);
        }

        query.orderByDesc("updated_at").orderByDesc("id");
        query.last("LIMIT " + safePageSize + " OFFSET " + ((safePage - 1L) * safePageSize));
        List<SalesOrderChangeOrderRow> records = safeList(headerMapper.selectList(query)).stream()
                .map(this::toOrderRow)
                .toList();
        return PageResult.of(records, total, safePage, safePageSize);
    }

    public SalesOrderChangePreviewResp preview(Long salesOrderId) {
        return preview(salesOrderId, DEFAULT_PAGE, DEFAULT_PAGE_SIZE);
    }

    public SalesOrderChangePreviewResp preview(Long salesOrderId, Integer page, Integer pageSize) {
        int safePage = normalizePage(page);
        int safePageSize = normalizePageSize(pageSize);
        SalesOrderHeader header = requireSalesOrder(salesOrderId);
        assertSynced(header);
        long total = countItems(header.getId());
        List<SalesOrderItem> items = loadPagedItems(header.getId(), safePage, safePageSize);

        SalesOrderChangePreviewResp resp = new SalesOrderChangePreviewResp();
        resp.setSalesOrderId(header.getId());
        resp.setSalesOrderNo(header.getSalesOrderNo());
        resp.setSourceType(header.getSourceType());
        resp.setSourceLabel(sourceLabel(header.getSourceType()));
        resp.setDocumentNo(header.getDocumentNo());
        resp.setCustomerName(header.getCustomerName());
        resp.setCustomerShortName(header.getCustomerShortName());
        resp.setCustomerCode(header.getCustomerCode());
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
        resp.setChangeEditable(true);
        resp.setChangeBlockedReason(null);
        resp.setItemCount(total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total);
        resp.setTotal(total);
        resp.setPage(safePage);
        resp.setPageSize(safePageSize);
        List<SalesOrderU9SyncPreviewLine> records = items.stream().map(item -> toPreviewLine(header, item)).toList();
        resp.setRecords(records);
        resp.setLines(records);
        return resp;
    }

    private SalesOrderHeader requireSalesOrder(Long salesOrderId) {
        if (salesOrderId == null) {
            throw new RuntimeException("销售订单 ID 不能为空");
        }
        SalesOrderHeader header = headerMapper.selectById(salesOrderId);
        if (header == null) {
            throw new RuntimeException("销售订单不存在");
        }
        if (dataScopeService != null) {
            dataScopeService.assertCanAccessSalesOrder(header);
        }
        return header;
    }

    private void assertSynced(SalesOrderHeader header) {
        if (!STATUS_SYNCED.equals(header.getU9SyncStatus())) {
            throw new RuntimeException("只允许变更已同步 U9 的销售订单");
        }
    }

    private void applyListFilters(
            QueryWrapper<SalesOrderHeader> query,
            String customerCode,
            String customerName,
            String keyword,
            String status) {
        if (!isBlank(customerCode)) {
            query.eq("customer_code", customerCode.trim());
        } else if (!isBlank(customerName)) {
            query.eq("customer_name", customerName.trim());
        }
        if (!isBlank(status)) {
            query.eq("status", status.trim());
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
    }

    private SalesOrderChangeOrderRow toOrderRow(SalesOrderHeader header) {
        List<SalesOrderItem> items = loadItems(header.getId());
        SalesOrderChangeOrderRow row = new SalesOrderChangeOrderRow();
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
        row.setU9SyncStatus(header.getU9SyncStatus());
        row.setU9OrderCode(header.getU9OrderCode());
        row.setU9SyncMessage(header.getU9SyncMessage());
        row.setItemCount(items.size());
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
        line.setCustomerOrderTitle(firstNonBlank(item.getCustomerOrderTitle(), header.getCustomerOrderTitle()));
        line.setCustomerOrderNo(firstNonBlank(item.getCustomerOrderNo(), header.getCustomerOrderNo()));
        line.setCustomerProjectNo(firstNonBlank(item.getCustomerProjectNo(), header.getCustomerProjectNo()));
        line.setInternalProjectNo(firstNonBlank(item.getInternalProjectNo(), header.getInternalProjectNo()));
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
        line.setReady(true);
        return line;
    }

    private List<SalesOrderItem> loadItems(Long salesOrderId) {
        QueryWrapper<SalesOrderItem> query = new QueryWrapper<>();
        query.eq("sales_order_id", salesOrderId)
                .and(wrapper -> wrapper.eq("u9_change_excluded", false).or().isNull("u9_change_excluded"))
                .orderByAsc("line_no")
                .orderByAsc("id");
        return safeList(itemMapper.selectList(query));
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
        return safeList(itemMapper.selectList(query));
    }

    private void applySalesOrderScope(QueryWrapper<SalesOrderHeader> query) {
        if (dataScopeService != null) {
            dataScopeService.applySalesOrderScope(query);
        }
    }

    private LocalDate preferredDeliveryDate(SalesOrderItem item) {
        return item.getInternalDeliveryDate() == null ? item.getCustomerDeliveryDate() : item.getInternalDeliveryDate();
    }

    private String sourceLabel(String sourceType) {
        return SOURCE_RFQ_RELEASE.equalsIgnoreCase(sourceType)
                ? "RFQ 议价提单派生"
                : "销售订单导入";
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

    private <T> List<T> safeList(List<T> list) {
        return list == null ? List.of() : list;
    }

    private String firstNonBlank(String primary, String fallback) {
        return isBlank(primary) ? fallback : primary;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
