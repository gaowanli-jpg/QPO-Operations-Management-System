package com.machining.quote.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.machining.quote.domain.QuoteStatusWorkflow;
import com.machining.quote.dto.DeliveryBatchResultDTO;
import com.machining.quote.dto.PageResult;
import com.machining.quote.dto.RfqReleaseAppendSalesOrderReq;
import com.machining.quote.dto.SalesOrderU9SyncOrderRow;
import com.machining.quote.entity.SalesOrderHeader;
import com.machining.quote.entity.SalesOrderItem;
import com.machining.quote.mapper.SalesOrderHeaderMapper;
import com.machining.quote.mapper.SalesOrderItemMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RfqReleaseAppendSalesOrderService {
    private static final String U9_UNSYNCED = "未同步";
    private static final String U9_FAILED = "同步失败";
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    @Autowired
    private SalesOrderHeaderMapper headerMapper;
    @Autowired
    private SalesOrderItemMapper itemMapper;
    @Autowired
    private RfqReleaseSalesOrderService releaseSalesOrderService;
    @Autowired(required = false)
    private DataScopeService dataScopeService;

    public PageResult<SalesOrderU9SyncOrderRow> listAppendTargetSalesOrders(
            String customerCode,
            String customerName,
            String keyword,
            String status,
            Integer page,
            Integer pageSize) {
        int safePage = normalizePage(page);
        int safePageSize = normalizePageSize(pageSize);
        if (!isBlank(status) && !QuoteStatusWorkflow.RELEASED.equals(status.trim())) {
            return PageResult.of(List.of(), 0, safePage, safePageSize);
        }
        QueryWrapper<SalesOrderHeader> query = new QueryWrapper<>();
        applySalesOrderScope(query);
        query.eq("status", QuoteStatusWorkflow.RELEASED);
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

    public DeliveryBatchResultDTO appendToSalesOrder(RfqReleaseAppendSalesOrderReq req) {
        return releaseSalesOrderService.appendToExistingSalesOrder(req);
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
        return item != null
                && !isBlank(item.getMaterialNo())
                && "已确认".equals(item.getMaterialNoStatus());
    }

    private void applySalesOrderScope(QueryWrapper<SalesOrderHeader> query) {
        if (dataScopeService != null) {
            dataScopeService.applySalesOrderScope(query);
        }
    }

    private String sourceLabel(String sourceType) {
        return "RFQ_RELEASE".equalsIgnoreCase(sourceType) ? "RFQ 议价提单派生" : "销售订单导入";
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

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private record MaterialStats(int total, int confirmed, int missing) {
    }
}
